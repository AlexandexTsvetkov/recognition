package com.example.recognition.service;

import com.example.recognition.conf.RecognitionConfig;
import com.example.recognition.exception.SpeechRecognitionException;
import com.example.recognition.grpc.SpeechKitGrpcClient;
import com.example.recognition.model.*;
import com.example.recognition.model.kafka.KafkaRecognitionMessage;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpeechRecognitionService {

    private final SpeechKitGrpcClient speechKitClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final RecognitionConfig recognitionConfig;

    private Counter recognitionRequestsCounter;
    private Timer recognitionTimer;
    private Counter recognitionErrorsCounter;
    private Counter recognitionSuccessCounter;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.recognitionRequestsCounter = Counter.builder("speech.recognition.requests")
                .description("Total speech recognition requests")
                .register(meterRegistry);

        this.recognitionTimer = Timer.builder("speech.recognition.duration")
                .description("Speech recognition processing time")
                .register(meterRegistry);

        this.recognitionErrorsCounter = Counter.builder("speech.recognition.errors")
                .description("Speech recognition errors")
                .register(meterRegistry);

        this.recognitionSuccessCounter = Counter.builder("speech.recognition.success")
                .description("Successful speech recognitions")
                .register(meterRegistry);
    }

    @Async("taskExecutor")
    @Retry(name = "speechkit-api", fallbackMethod = "fallbackRecognition")
    @RateLimiter(name = "speechkit-api")
    @CircuitBreaker(name = "speechkit-api", fallbackMethod = "fallbackRecognition")
    public CompletableFuture<SpeechRecognitionResponse> processSpeechRecognition(
            SpeechRecognitionRequest request, String userId, String questionId, RecognitionType recognitionType) {

        return CompletableFuture.supplyAsync(() -> {
            recognitionRequestsCounter.increment();

            return recognitionTimer.record(() -> {
                try {
                    log.info("Processing {} speech recognition for user: {}, question: {}, file: {}",
                            recognitionType, userId, questionId, request.fileName());

                    AudioFormat format = request.audioFormat() != null ?
                            request.audioFormat() : detectAudioFormat(request.fileName());

                    String operationId = speechKitClient.recognizeAudio(
                            request.audioData(),
                            format,
                            recognitionType
                    );

                    String recognizedText = waitForRecognitionResult(operationId);

                    sendToKafka(userId, questionId, recognizedText, operationId, recognitionType);

                    recognitionSuccessCounter.increment();
                    log.info("{} recognition completed for operation: {}, text length: {}",
                            recognitionType, operationId, recognizedText.length());

                    return new SpeechRecognitionResponse(
                            operationId,
                            recognizedText,
                            RecognitionStatus.SUCCESS,
                            Instant.now(),
                            recognitionType
                    );

                } catch (Exception e) {
                    recognitionErrorsCounter.increment();
                    log.error("{} recognition failed for user: {}, question: {}",
                            recognitionType, userId, questionId, e);
                    throw new SpeechRecognitionException("Speech recognition processing failed", e);
                }
            });
        });
    }

    protected String waitForRecognitionResult(String operationId) {
        Instant start = Instant.now();
        Duration timeout = Duration.ofSeconds(recognitionConfig.getRecognitionTimeout());
        int maxAttempts = recognitionConfig.getMaxRetryAttempts();
        int attempt = 0;

        while (attempt < maxAttempts && Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
            try {
                String result = speechKitClient.getRecognitionResult(operationId);
                if (result != null && !result.trim().isEmpty()) {
                    log.debug("Recognition result received for operation: {}", operationId);
                    return formatRecognizedText(result);
                }

                Thread.sleep(3000);
                attempt++;
                log.debug("Waiting for recognition result, attempt: {}/{}", attempt, maxAttempts);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SpeechRecognitionException("Recognition wait interrupted", e);
            } catch (Exception e) {
                log.warn("Error checking recognition status for operation: {}, attempt: {}", operationId, attempt, e);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SpeechRecognitionException("Wait interrupted", ie);
                }
            }
        }

        throw new SpeechRecognitionException(
                String.format("Recognition timeout exceeded for operation: %s (timeout: %ds)",
                        operationId, recognitionConfig.getRecognitionTimeout()));
    }

    private String formatRecognizedText(String text) {
        // Дополнительная обработка текста для улучшения читаемости
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String formatted = text.trim();

        // Убеждаемся, что текст заканчивается точкой, если это предложение
        if (!formatted.endsWith(".") && !formatted.endsWith("!") && !formatted.endsWith("?") &&
                !formatted.endsWith("...") && formatted.length() > 10) {
            formatted += ".";
        }

        // Заменяем множественные пробелы на одинарные
        formatted = formatted.replaceAll("\\s+", " ");

        return formatted;
    }

    protected void sendToKafka(String userId, String questionId, String recognizedText,
                               String operationId, RecognitionType recognitionType) {
        try {
            KafkaRecognitionMessage message = new KafkaRecognitionMessage(
                    userId,
                    questionId,
                    recognizedText,
                    operationId,
                    Instant.now(),
                    recognitionType,
                    Map.of(
                            "source", "speech-recognition-service",
                            "timestamp", Instant.now().toString(),
                            "textLength", recognizedText.length(),
                            "recognitionType", recognitionType.name(),
                            "hasPunctuation", recognizedText.matches(".*[.,!?;:].*")
                    )
            );

            kafkaTemplate.send("speech-recognition-results", userId, message)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.error("Failed to send message to Kafka for user: {}", userId, error);
                        } else {
                            log.debug("Message sent to Kafka for user: {}, offset: {}",
                                    userId, result.getRecordMetadata().offset());
                        }
                    });

        } catch (Exception e) {
            log.error("Failed to send recognition result to Kafka for user: {}", userId, e);
        }
    }

    private AudioFormat detectAudioFormat(String fileName) {
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            if (lowerFileName.endsWith(".mp3")) {
                return AudioFormat.MP3;
            } else if (lowerFileName.endsWith(".ogg") || lowerFileName.endsWith(".opus")) {
                return AudioFormat.OGG_OPUS;
            } else if (lowerFileName.endsWith(".wav")) {
                return AudioFormat.WAV;
            }
        }
        return AudioFormat.WAV;
    }

    public CompletableFuture<SpeechRecognitionResponse> fallbackRecognition(
            SpeechRecognitionRequest request, String userId, String questionId,
            RecognitionType recognitionType, Exception e) {

        log.warn("Using fallback for {} recognition for user: {}, error: {}",
                recognitionType, userId, e.getMessage());

        return CompletableFuture.completedFuture(new SpeechRecognitionResponse(
                "fallback-" + Instant.now().toEpochMilli(),
                "[Fallback] Распознавание речи временно недоступно. Пожалуйста, попробуйте позже.",
                RecognitionStatus.FAILED,
                Instant.now(),
                recognitionType
        ));
    }

    public record KafkaRecognitionMessage(
            String userId,
            String questionId,
            String recognizedText,
            String operationId,
            Instant recognizedAt,
            RecognitionType recognitionType,
            Map<String, Object> metadata
    ) {}
}
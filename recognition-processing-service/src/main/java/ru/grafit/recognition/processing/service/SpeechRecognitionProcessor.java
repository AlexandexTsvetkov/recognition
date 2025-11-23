package ru.grafit.recognition.processing.service;

import ru.grafit.recognition.common.kafka.RecognitionTaskMessage;
import ru.grafit.recognition.common.storage.StorageService;
import ru.grafit.recognition.processing.client.SpeechKitGrpcClient;
import ru.grafit.recognition.processing.config.RecognitionConfig;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class SpeechRecognitionProcessor {

    private final SpeechKitGrpcClient speechKitClient;
    private final RecognitionConfig recognitionConfig;
    private final StorageService storageService;

    @Retry(name = "speechkit-api")
    @RateLimiter(name = "speechkit-api")
    @CircuitBreaker(name = "speechkit-api")
    public Mono<RecognitionResult> processRecognition(RecognitionTaskMessage message) {
        return Mono.fromCallable(() -> {
            log.info("Starting recognition for task: {}, type: {}", 
                    message.getTaskId(), message.getRecognitionType());

            // Скачиваем файл из хранилища
            byte[] audioData = storageService.downloadAudio(message.getAudioFileUrl());
            log.debug("Downloaded audio file from storage: url={}, size={} bytes", 
                    message.getAudioFileUrl(), audioData.length);

            String operationId = speechKitClient.recognizeAudio(
                    audioData,
                    message.getAudioFormat(),
                    message.getRecognitionType()
            );

            log.info("Recognition operation started: {} for task: {}", operationId, message.getTaskId());

            String recognizedText = waitForRecognitionResult(operationId);

            log.info("Recognition completed for task: {}, text length: {}", 
                    message.getTaskId(), recognizedText.length());

            // Удаляем файл из хранилища после обработки
            try {
                storageService.deleteAudio(message.getAudioFileUrl());
                log.debug("Deleted audio file from storage: url={}", message.getAudioFileUrl());
            } catch (Exception e) {
                log.warn("Failed to delete audio file from storage: url={}", message.getAudioFileUrl(), e);
                // Не бросаем исключение, т.к. удаление не критично
            }

            return new RecognitionResult(operationId, recognizedText);
        })
        .timeout(Duration.ofSeconds(recognitionConfig.getRecognitionTimeout()))
        .onErrorMap(Exception.class, e -> {
            log.error("Recognition processing failed for task: {}", message.getTaskId(), e);
            return new RuntimeException("Recognition processing failed: " + e.getMessage(), e);
        });
    }

    private String waitForRecognitionResult(String operationId) {
        Instant start = Instant.now();
        Duration timeout = Duration.ofSeconds(recognitionConfig.getRecognitionTimeout());
        int maxAttempts = recognitionConfig.getMaxRetryAttempts();
        int attempt = 0;

        while (attempt < maxAttempts && Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
            try {
                String result = speechKitClient.getRecognitionResult(operationId);
                if (result != null && !result.trim().isEmpty()) {
                    return formatRecognizedText(result);
                }

                Thread.sleep(3000);
                attempt++;
                log.debug("Waiting for recognition result, attempt: {}/{}", attempt, maxAttempts);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Recognition wait interrupted", e);
            } catch (Exception e) {
                log.warn("Error checking recognition status for operation: {}, attempt: {}", 
                        operationId, attempt, e);
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Wait interrupted", ie);
                }
            }
        }

        throw new RuntimeException(
                String.format("Recognition timeout exceeded for operation: %s (timeout: %ds)",
                        operationId, recognitionConfig.getRecognitionTimeout()));
    }

    private String formatRecognizedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String formatted = text.trim();
        formatted = formatted.replaceAll("\\s+", " ");

        if (!formatted.endsWith(".") && !formatted.endsWith("!") && !formatted.endsWith("?") &&
                !formatted.endsWith("...") && formatted.length() > 10) {
            formatted += ".";
        }

        return formatted;
    }

    public record RecognitionResult(String operationId, String recognizedText) {}
}


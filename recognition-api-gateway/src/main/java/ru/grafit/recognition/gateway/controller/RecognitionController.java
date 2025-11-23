package ru.grafit.recognition.gateway.controller;

import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.dto.RecognitionTaskResponse;
import ru.grafit.recognition.common.model.AudioFormat;
import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.common.model.RecognitionType;
import ru.grafit.recognition.common.storage.StorageService;
import ru.grafit.recognition.gateway.service.RecognitionRequestService;
import jakarta.validation.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/speech")
@Validated
@Slf4j
@RequiredArgsConstructor
public class RecognitionController {

    private final RecognitionRequestService requestService;
    private final StorageService storageService;

    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<RecognitionTaskResponse>> recognizeSpeech(
            @RequestPart("file") FilePart filePart,
            @RequestPart("userId") String userId,
            @RequestPart("questionId") String questionId,
            @RequestPart(value = "recognitionType", required = false) String recognitionTypeStr,
            @RequestPart(value = "language", required = false) String languageCode,
            @RequestPart(value = "profanityFilter", required = false) String profanityFilterStr) {

        // Валидация обязательных полей
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ValidationException("User ID is required"));
        }
        if (questionId == null || questionId.isBlank()) {
            return Mono.error(new ValidationException("Question ID is required"));
        }

        boolean profanityFilter = Boolean.parseBoolean(profanityFilterStr != null ? profanityFilterStr : "true");
        RecognitionType recognitionType = parseRecognitionType(recognitionTypeStr);

        log.info("Processing {} speech recognition for user: {}, question: {}, file: {}",
                recognitionType, userId, questionId, filePart.filename());

        return convertFilePartToBytes(filePart)
                .flatMap(audioBytes -> {
                    try {
                        validateAudioFile(audioBytes, filePart.headers().getContentType());

                        // Генерируем временный taskId для загрузки в хранилище
                        String tempTaskId = java.util.UUID.randomUUID().toString();

                        // Загружаем файл в S3/MinIO
                        String audioFileUrl = storageService.uploadAudio(audioBytes, tempTaskId);

                        log.info("Audio file uploaded to storage: url={}, size={} bytes",
                                audioFileUrl, audioBytes.length);

                        RecognitionTaskRequest request = RecognitionTaskRequest.builder()
                                .userId(userId)
                                .questionId(questionId)
                                .audioFileUrl(audioFileUrl)
                                .fileName(filePart.filename())
                                .audioFormat(detectAudioFormat(filePart.filename()))
                                .recognitionType(recognitionType)
                                .languageCode(languageCode != null ? languageCode : "ru-RU")
                                .enableProfanityFilter(profanityFilter)
                                .createdAt(Instant.now())
                                .build();

                        return requestService.createRecognitionTask(request)
                                .map(taskId -> {
                                    RecognitionTaskResponse response = RecognitionTaskResponse.builder()
                                            .taskId(taskId)
                                            .status(RecognitionStatus.PENDING)
                                            .userId(userId)
                                            .questionId(questionId)
                                            .recognitionType(recognitionType)
                                            .createdAt(Instant.now())
                                            .build();
                                    return ResponseEntity.accepted().body(response);
                                });

                    } catch (ValidationException e) {
                        return Mono.error(e);
                    }
                })
                .onErrorResume(ValidationException.class, e -> {
                    log.error("Validation error: {}", e.getMessage());
                    RecognitionTaskResponse errorResponse = createErrorResponse(e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error: {}", e.getMessage(), e);
                    RecognitionTaskResponse errorResponse = createErrorResponse("Internal server error");
                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                });
    }

    @PostMapping(value = "/recognize-interview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<RecognitionTaskResponse>> recognizeInterviewSpeech(
            @RequestPart("file") FilePart filePart,
            @RequestPart("userId") String userId,
            @RequestPart("questionId") String questionId,
            @RequestPart(value = "interviewType", required = false) String interviewType) {

        // Валидация
        if (userId == null || userId.isBlank()) {
            return Mono.error(new ValidationException("User ID is required"));
        }
        if (questionId == null || questionId.isBlank()) {
            return Mono.error(new ValidationException("Question ID is required"));
        }

        String interviewTypeValue = interviewType != null ? interviewType : "technical";

        // Определяем тип распознавания в зависимости от типа собеседования
        RecognitionType recognitionType = switch (interviewTypeValue.toLowerCase()) {
            case "technical" -> RecognitionType.TECHNICAL;
            case "hr", "general" -> RecognitionType.LITERATURE;
            case "fast" -> RecognitionType.FAST;
            default -> RecognitionType.PRECISE;
        };

        log.info("Processing {} interview recognition for user: {}, question: {}, file: {}",
                interviewTypeValue, userId, questionId, filePart.filename());

        return convertFilePartToBytes(filePart)
                .flatMap(audioBytes -> {
                    try {
                        validateAudioFile(audioBytes, filePart.headers().getContentType());

                        // Генерируем временный taskId для загрузки в хранилище
                        String tempTaskId = java.util.UUID.randomUUID().toString();

                        // Загружаем файл в S3/MinIO
                        String audioFileUrl = storageService.uploadAudio(audioBytes, tempTaskId);

                        RecognitionTaskRequest request = RecognitionTaskRequest.builder()
                                .userId(userId)
                                .questionId(questionId)
                                .audioFileUrl(audioFileUrl)
                                .fileName(filePart.filename())
                                .audioFormat(detectAudioFormat(filePart.filename()))
                                .recognitionType(recognitionType)
                                .languageCode("ru-RU")
                                .enableProfanityFilter(false)
                                .createdAt(Instant.now())
                                .build();

                        return requestService.createRecognitionTask(request)
                                .map(taskId -> {
                                    RecognitionTaskResponse response = RecognitionTaskResponse.builder()
                                            .taskId(taskId)
                                            .status(RecognitionStatus.PENDING)
                                            .userId(userId)
                                            .questionId(questionId)
                                            .recognitionType(recognitionType)
                                            .createdAt(Instant.now())
                                            .build();
                                    return ResponseEntity.accepted().body(response);
                                });

                    } catch (ValidationException e) {
                        return Mono.error(e);
                    }
                })
                .onErrorResume(ValidationException.class, e -> {
                    log.error("Validation error in interview recognition: {}", e.getMessage());
                    RecognitionTaskResponse errorResponse = createErrorResponse(e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Interview recognition error: {}", e.getMessage(), e);
                    RecognitionTaskResponse errorResponse = createErrorResponse("Internal server error");
                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                });
    }

    @GetMapping("/tasks/{taskId}")
    public Mono<ResponseEntity<RecognitionTaskResponse>> getTaskStatus(@PathVariable String taskId) {
        return requestService.getTaskStatus(taskId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/recognition-types")
    public Mono<ResponseEntity<Map<String, Object>>> getRecognitionTypes() {
        var types = Map.of(
                "recognitionTypes", Map.of(
                        "GENERAL", Map.of(
                                "name", "Общее распознавание",
                                "description", "Баланс скорости и качества",
                                "recommendedFor", "Универсальное использование",
                                "punctuation", true
                        ),
                        "TECHNICAL", Map.of(
                                "name", "Техническое собеседование",
                                "description", "Лучшее качество для технических терминов",
                                "recommendedFor", "Технические собеседования, код-ревью",
                                "punctuation", true
                        ),
                        "LITERATURE", Map.of(
                                "name", "Литературный текст",
                                "description", "Лучшая пунктуация и форматирование",
                                "recommendedFor", "HR собеседования, презентации",
                                "punctuation", true
                        ),
                        "FAST", Map.of(
                                "name", "Быстрое распознавание",
                                "description", "Максимальная скорость",
                                "recommendedFor", "Быстрая обработка, короткие сообщения",
                                "punctuation", false
                        ),
                        "PRECISE", Map.of(
                                "name", "Точное распознавание",
                                "description", "Максимальное качество с пунктуацией",
                                "recommendedFor", "Важные собеседования, документация",
                                "punctuation", true
                        )
                ),
                "defaultType", "GENERAL",
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity.ok(types));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Object>> healthCheck() {
        return Mono.just(ResponseEntity.ok()
                .body(Map.of(
                        "status", "UP",
                        "service", "recognition-api-gateway",
                        "timestamp", Instant.now().toString()
                )));
    }

    @GetMapping("/formats")
    public Mono<ResponseEntity<Map<String, Object>>> getSupportedFormats() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "supportedFormats", Map.of(
                        "WAV", "Рекомендуемый формат, лучшее качество",
                        "OGG_OPUS", "Хорошее сжатие с сохранением качества",
                        "MP3", "Широко распространенный формат"
                ),
                "maxFileSize", "10MB",
                "recommendedFormat", "WAV",
                "maxDuration", "5 минут"
        )));
    }

    // Вспомогательные методы
    private RecognitionType parseRecognitionType(String recognitionTypeStr) {
        if (recognitionTypeStr == null || recognitionTypeStr.isBlank()) {
            return RecognitionType.GENERAL;
        }

        try {
            return RecognitionType.valueOf(recognitionTypeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid recognition type: {}, using default GENERAL", recognitionTypeStr);
            return RecognitionType.GENERAL;
        }
    }

    private Mono<byte[]> convertFilePartToBytes(FilePart filePart) {
        return filePart.content()
                .collect(ByteArrayOutputStream::new, (outputStream, dataBuffer) -> {
                    try {
                        Channels.newChannel(outputStream).write(dataBuffer.asByteBuffer().asReadOnlyBuffer());
                        DataBufferUtils.release(dataBuffer);
                    } catch (Exception e) {
                        DataBufferUtils.release(dataBuffer);
                        throw new RuntimeException("Failed to convert file part to bytes", e);
                    }
                })
                .map(ByteArrayOutputStream::toByteArray);
    }

    private void validateAudioFile(byte[] audioData, org.springframework.http.MediaType contentType) {
        if (audioData == null || audioData.length == 0) {
            throw new ValidationException("Audio file cannot be empty");
        }

        if (audioData.length > 10 * 1024 * 1024) {
            throw new ValidationException("Audio file too large. Maximum size is 10MB");
        }

        if (audioData.length < 100) {
            throw new ValidationException("Audio file too small. Minimum size is 100 bytes");
        }

        // Исправленная логика проверки типа контента
        if (contentType != null && !contentType.getType().equals("audio")) {
            throw new ValidationException("Invalid file type. Expected audio file, got: " + contentType);
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

    private RecognitionTaskResponse createErrorResponse(String errorMessage) {
        return RecognitionTaskResponse.builder()
                .taskId("error-" + Instant.now().toEpochMilli())
                .status(RecognitionStatus.FAILED)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
    }
}
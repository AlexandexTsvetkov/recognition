package ru.grafit.recognition.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.dto.RecognitionTaskResponse;
import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.common.model.RecognitionType;
import ru.grafit.recognition.gateway.service.RecognitionRequestService;
import ru.grafit.recognition.common.storage.StorageService;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(RecognitionController.class)
class RecognitionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RecognitionRequestService requestService;

    @MockBean
    private StorageService storageService;

    @Test
    void testRecognizeSpeech_Success() {
        // Arrange
        String taskId = "test-task-123";

        // Создаем файл правильного размера
        byte[] audioData = new byte[1024]; // 1KB - достаточно для теста
        for (int i = 0; i < audioData.length; i++) {
            audioData[i] = (byte) (i % 256);
        }

        when(storageService.uploadAudio(any(), any())).thenReturn("audio/test-task-123");
        when(requestService.createRecognitionTask(any(RecognitionTaskRequest.class)))
                .thenReturn(Mono.just(taskId));

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", audioData)
                .contentType(MediaType  .parseMediaType("audio/wav")) // Правильный аудио тип
                .filename("test.wav");
        bodyBuilder.part("userId", "user-123");
        bodyBuilder.part("questionId", "question-456");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/speech/recognize")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.taskId").isEqualTo(taskId)
                .jsonPath("$.status").isEqualTo("PENDING");
    }

    @Test
    void testRecognizeSpeech_InvalidContentType() {
        // Arrange
        byte[] audioData = new byte[150];
        for (int i = 0; i < audioData.length; i++) {
            audioData[i] = (byte) (i % 256);
        }

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", audioData)
                .contentType(MediaType.IMAGE_JPEG) // Неправильный тип - изображение
                .filename("test.wav");
        bodyBuilder.part("userId", "user-123");
        bodyBuilder.part("questionId", "question-456");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/speech/recognize")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.errorMessage").value(containsString("Invalid file type"));
    }

    @Test
    void testRecognizeSpeech_FileTooSmall() {
        // Arrange - создаем слишком маленький файл
        byte[] smallAudioData = new byte[50]; // Меньше 100 байт

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", smallAudioData)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .filename("test.wav");
        bodyBuilder.part("userId", "user-123");
        bodyBuilder.part("questionId", "question-456");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/speech/recognize")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.errorMessage").isEqualTo("Audio file too small. Minimum size is 100 bytes");
    }

    @Test
    void testRecognizeSpeech_FileTooLarge() {
        // Arrange - создаем слишком большой файл
        byte[] largeAudioData = new byte[11 * 1024 * 1024]; // Больше 10MB

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", largeAudioData)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .filename("test.wav");
        bodyBuilder.part("userId", "user-123");
        bodyBuilder.part("questionId", "question-456");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/speech/recognize")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(bodyBuilder.build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FAILED")
                .jsonPath("$.errorMessage").isEqualTo("Audio file too large. Maximum size is 10MB");
    }

    @Test
    void testGetTaskStatus_Success() {
        // Arrange
        String taskId = "test-task-123";
        RecognitionTaskResponse mockResponse = RecognitionTaskResponse.builder()
                .taskId(taskId)
                .status(RecognitionStatus.SUCCESS)
                .userId("user-123")
                .questionId("question-456")
                .recognitionType(RecognitionType.GENERAL)
                .createdAt(Instant.now())
                .build();

        when(requestService.getTaskStatus(taskId)).thenReturn(Mono.just(mockResponse));

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/speech/tasks/{taskId}", taskId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.taskId").isEqualTo(taskId)
                .jsonPath("$.status").isEqualTo("SUCCESS");
    }

    @Test
    void testGetTaskStatus_NotFound() {
        // Arrange
        String taskId = "non-existent-task";
        when(requestService.getTaskStatus(taskId)).thenReturn(Mono.empty());

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/speech/tasks/{taskId}", taskId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void testHealthCheck() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/speech/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("recognition-api-gateway");
    }
}
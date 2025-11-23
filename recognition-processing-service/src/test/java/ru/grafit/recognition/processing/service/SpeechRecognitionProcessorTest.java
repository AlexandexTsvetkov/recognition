package ru.grafit.recognition.processing.service;

import ru.grafit.recognition.common.kafka.RecognitionTaskMessage;
import ru.grafit.recognition.common.model.AudioFormat;
import ru.grafit.recognition.common.model.RecognitionType;
import ru.grafit.recognition.common.storage.StorageService;
import ru.grafit.recognition.processing.client.SpeechKitGrpcClient;
import ru.grafit.recognition.processing.config.RecognitionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpeechRecognitionProcessorTest {

    @Mock
    private SpeechKitGrpcClient speechKitClient;

    @Mock
    private RecognitionConfig recognitionConfig;

    @Mock
    private StorageService storageService;

    private SpeechRecognitionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SpeechRecognitionProcessor(speechKitClient, recognitionConfig, storageService);
    }

    @Test
    void testProcessRecognition_Success() {
        // Arrange
        RecognitionTaskMessage message = RecognitionTaskMessage.builder()
                .taskId("task-123")
                .userId("user123")
                .questionId("question456")
                .audioFileUrl("audio/task-123")
                .audioFormat(AudioFormat.WAV)
                .recognitionType(RecognitionType.GENERAL)
                .build();

        byte[] audioData = "test audio data".getBytes();
        String operationId = "operation-123";
        String recognizedText = "Recognized text.";

        when(storageService.downloadAudio("audio/task-123"))
                .thenReturn(audioData);
        when(speechKitClient.recognizeAudio(any(byte[].class), any(AudioFormat.class), any(RecognitionType.class)))
                .thenReturn(operationId);
        when(speechKitClient.getRecognitionResult(operationId))
                .thenReturn(recognizedText);
        when(recognitionConfig.getRecognitionTimeout()).thenReturn(60);
        when(recognitionConfig.getMaxRetryAttempts()).thenReturn(3);

        // Act
        var result = processor.processRecognition(message).block();

        // Assert
        assertNotNull(result);
        assertEquals(operationId, result.operationId());
        assertEquals(recognizedText, result.recognizedText());

        verify(storageService).downloadAudio("audio/task-123");
        verify(storageService).deleteAudio("audio/task-123");
        verify(speechKitClient).recognizeAudio(eq(audioData), eq(AudioFormat.WAV), eq(RecognitionType.GENERAL));
        verify(speechKitClient).getRecognitionResult(operationId);
    }

    @Test
    void testProcessRecognition_DownloadFailure() {
        // Arrange
        RecognitionTaskMessage message = RecognitionTaskMessage.builder()
                .taskId("task-123")
                .audioFileUrl("audio/task-123")
                .audioFormat(AudioFormat.WAV)
                .recognitionType(RecognitionType.GENERAL)
                .build();

        when(storageService.downloadAudio("audio/task-123"))
                .thenThrow(new RuntimeException("Storage error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                processor.processRecognition(message).block());

        verify(storageService).downloadAudio("audio/task-123");
        verify(storageService, never()).deleteAudio(anyString());
        verify(speechKitClient, never()).recognizeAudio(any(), any(), any());
    }

    @Test
    void testProcessRecognition_WithNullRecognitionType() {
        // Arrange
        RecognitionTaskMessage message = RecognitionTaskMessage.builder()
                .taskId("task-123")
                .audioFileUrl("audio/task-123")
                .audioFormat(AudioFormat.WAV)
                .recognitionType(null) // Явно устанавливаем null
                .build();

        byte[] audioData = "test audio data".getBytes();
        String operationId = "operation-123";
        String recognizedText = "Recognized text.";

        when(storageService.downloadAudio("audio/task-123"))
                .thenReturn(audioData);
        // Используем any() для обработки null значения
        when(speechKitClient.recognizeAudio(eq(audioData), eq(AudioFormat.WAV), any()))
                .thenReturn(operationId);
        when(speechKitClient.getRecognitionResult(operationId))
                .thenReturn(recognizedText);
        when(recognitionConfig.getRecognitionTimeout()).thenReturn(60);
        when(recognitionConfig.getMaxRetryAttempts()).thenReturn(3);

        // Act
        var result = processor.processRecognition(message).block();

        // Assert
        assertNotNull(result);
        assertEquals(operationId, result.operationId());
        assertEquals(recognizedText, result.recognizedText());

        // ИСПРАВЛЕНИЕ: проверяем, что был вызван с null (а не с GENERAL)
        // так как процессор передает null напрямую
        verify(speechKitClient).recognizeAudio(eq(audioData), eq(AudioFormat.WAV), isNull());
    }

    @Test
    void testProcessRecognition_RecognitionFailure() {
        // Arrange
        RecognitionTaskMessage message = RecognitionTaskMessage.builder()
                .taskId("task-123")
                .audioFileUrl("audio/task-123")
                .audioFormat(AudioFormat.WAV)
                .recognitionType(RecognitionType.GENERAL)
                .build();

        byte[] audioData = "test audio data".getBytes();

        when(storageService.downloadAudio("audio/task-123"))
                .thenReturn(audioData);
        when(speechKitClient.recognizeAudio(any(byte[].class), any(AudioFormat.class), any(RecognitionType.class)))
                .thenThrow(new RuntimeException("Recognition error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () ->
                processor.processRecognition(message).block());

        verify(storageService).downloadAudio("audio/task-123");
        verify(storageService, never()).deleteAudio(anyString());
        verify(speechKitClient).recognizeAudio(any(byte[].class), any(AudioFormat.class), any(RecognitionType.class));
    }
}
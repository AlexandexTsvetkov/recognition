package ru.grafit.recognition.gateway.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService storageService;
    private static final String BUCKET_NAME = "test-bucket";
    private static final String AUDIO_PREFIX = "audio/";

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(minioClient, BUCKET_NAME, AUDIO_PREFIX);
    }

    @Test
    void testUploadAudio() throws Exception {
        // Arrange
        String taskId = "test-task-123";
        byte[] audioData = "test audio data".getBytes();
        String expectedObjectName = AUDIO_PREFIX + taskId;

        // Убрали ненужный мок bucketExists
        // Act
        String result = storageService.uploadAudio(audioData, taskId);

        // Assert
        assertEquals(expectedObjectName, result);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void testDownloadAudio() throws Exception {
        // Arrange
        String fileKey = "audio/test-task-123";
        byte[] expectedData = "test audio data".getBytes();

        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn(expectedData);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        // Act
        byte[] result = storageService.downloadAudio(fileKey);

        // Assert
        assertArrayEquals(expectedData, result);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void testDownloadAudio_WithException() throws Exception {
        // Arrange
        String fileKey = "audio/test-task-123";
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> storageService.downloadAudio(fileKey));
    }

    @Test
    void testDeleteAudio() throws Exception {
        // Arrange
        String fileKey = "audio/test-task-123";

        // Act
        storageService.deleteAudio(fileKey);

        // Assert
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void testExists_FileExists() throws Exception {
        // Arrange
        String fileKey = "audio/existing-file";

        // statObject не выбрасывает исключение если файл существует
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(null);

        // Act
        boolean result = storageService.exists(fileKey);

        // Assert
        assertTrue(result);
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    void testExists_FileNotExists() throws Exception {
        // Arrange
        String fileKey = "audio/non-existing-file";

        // Создаем правильное исключение
        ErrorResponse errorResponse = mock(ErrorResponse.class);
        when(errorResponse.message()).thenReturn("Object does not exist");

        ErrorResponseException exception = new ErrorResponseException(errorResponse, null, null);

        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(exception);

        // Act
        boolean result = storageService.exists(fileKey);

        // Assert
        assertFalse(result);
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }
}
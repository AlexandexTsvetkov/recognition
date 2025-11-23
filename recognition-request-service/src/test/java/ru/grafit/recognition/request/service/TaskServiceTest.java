package ru.grafit.recognition.request.service;

import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.kafka.RecognitionTaskMessage;
import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.common.model.RecognitionType;
import ru.grafit.recognition.request.entity.RecognitionTask;
import ru.grafit.recognition.request.repository.RecognitionTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private RecognitionTaskRepository taskRepository;

    @Mock
    private KafkaTemplate<String, RecognitionTaskMessage> kafkaTemplate;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, kafkaTemplate);
    }

    @Test
    void testCreateTask_Success() {
        RecognitionTaskRequest request = RecognitionTaskRequest.builder()
                .userId("user123")
                .questionId("question456")
                .audioFileUrl("audio/task-123")
                .fileName("test.wav")
                .recognitionType(RecognitionType.GENERAL)
                .languageCode("ru-RU")
                .enableProfanityFilter(true)
                .createdAt(Instant.now())
                .build();

        RecognitionTask savedTask = RecognitionTask.builder()
                .id(1L)
                .taskId("task-123")
                .userId("user123")
                .questionId("question456")
                .status(RecognitionStatus.PENDING)
                .recognitionType(RecognitionType.GENERAL)
                .createdAt(Instant.now())
                .build();

        when(taskRepository.save(any(RecognitionTask.class)))
                .thenReturn(Mono.just(savedTask));
        when(kafkaTemplate.send(anyString(), anyString(), any(RecognitionTaskMessage.class)))
                .thenReturn(null);

        var result = taskService.createTask(request).block();

        assertNotNull(result);
        assertEquals("task-123", result.getTaskId());
        assertEquals(RecognitionStatus.PENDING, result.getStatus());

        ArgumentCaptor<RecognitionTaskMessage> messageCaptor = 
                ArgumentCaptor.forClass(RecognitionTaskMessage.class);
        verify(kafkaTemplate).send(eq("recognition-tasks"), anyString(), messageCaptor.capture());
        
        RecognitionTaskMessage sentMessage = messageCaptor.getValue();
        assertEquals("audio/task-123", sentMessage.getAudioFileUrl());
        assertEquals("user123", sentMessage.getUserId());
    }

    @Test
    void testGetTask_Success() {
        String taskId = "task-123";
        RecognitionTask task = RecognitionTask.builder()
                .taskId(taskId)
                .userId("user123")
                .questionId("question456")
                .status(RecognitionStatus.SUCCESS)
                .recognizedText("Recognized text")
                .createdAt(Instant.now())
                .processedAt(Instant.now())
                .build();

        when(taskRepository.findByTaskId(taskId))
                .thenReturn(Mono.just(task));

        var result = taskService.getTask(taskId).block();

        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        assertEquals(RecognitionStatus.SUCCESS, result.getStatus());
        assertEquals("Recognized text", result.getRecognizedText());
    }
}


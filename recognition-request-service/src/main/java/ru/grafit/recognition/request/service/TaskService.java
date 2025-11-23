package ru.grafit.recognition.request.service;

import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.dto.RecognitionTaskResponse;
import ru.grafit.recognition.common.kafka.RecognitionTaskMessage;
import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.request.entity.RecognitionTask;
import ru.grafit.recognition.request.repository.RecognitionTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final RecognitionTaskRepository taskRepository;
    private final KafkaTemplate<String, RecognitionTaskMessage> kafkaTemplate;
    private static final String RECOGNITION_TASKS_TOPIC = "recognition-tasks";

    public Mono<RecognitionTaskResponse> createTask(RecognitionTaskRequest request) {
        String taskId = UUID.randomUUID().toString();
        
        RecognitionTask task = RecognitionTask.builder()
                .taskId(taskId)
                .userId(request.getUserId())
                .questionId(request.getQuestionId())
                .status(RecognitionStatus.PENDING)
                .recognitionType(request.getRecognitionType())
                .createdAt(Instant.now())
                .build();

        return taskRepository.save(task)
                .flatMap(savedTask -> {
                    RecognitionTaskMessage message = RecognitionTaskMessage.builder()
                            .taskId(taskId)
                            .userId(request.getUserId())
                            .questionId(request.getQuestionId())
                            .audioFileUrl(request.getAudioFileUrl())
                            .fileName(request.getFileName())
                            .audioFormat(request.getAudioFormat())
                            .recognitionType(request.getRecognitionType())
                            .languageCode(request.getLanguageCode())
                            .enableProfanityFilter(request.getEnableProfanityFilter())
                            .createdAt(Instant.now())
                            .build();

                    return Mono.fromFuture(CompletableFuture.supplyAsync(() -> {
                        try {
                            kafkaTemplate.send(RECOGNITION_TASKS_TOPIC, taskId, message)
                                    .whenComplete((result, error) -> {
                                        if (error != null) {
                                            log.error("Failed to send task to Kafka: {}", taskId, error);
                                        } else {
                                            log.info("Task sent to Kafka: {}, partition: {}, offset: {}", 
                                                    taskId, 
                                                    result.getRecordMetadata().partition(),
                                                    result.getRecordMetadata().offset());
                                        }
                                    });
                        } catch (Exception e) {
                            log.error("Error sending task to Kafka: {}", taskId, e);
                        }
                        return savedTask;
                    }));
                })
                .map(this::toResponse);
    }

    public Mono<RecognitionTaskResponse> getTask(String taskId) {
        return taskRepository.findByTaskId(taskId)
                .map(this::toResponse);
    }

    public Mono<Void> updateTaskStatus(String taskId, RecognitionStatus status, String recognizedText, String errorMessage) {
        return taskRepository.findByTaskId(taskId)
                .flatMap(task -> {
                    task.setStatus(status);
                    task.setRecognizedText(recognizedText);
                    task.setErrorMessage(errorMessage);
                    task.setProcessedAt(Instant.now());
                    return taskRepository.save(task);
                })
                .then()
                .doOnError(error -> log.error("Failed to update task status: {}", taskId, error));
    }

    private RecognitionTaskResponse toResponse(RecognitionTask task) {
        return RecognitionTaskResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus())
                .recognizedText(task.getRecognizedText())
                .recognitionType(task.getRecognitionType())
                .userId(task.getUserId())
                .questionId(task.getQuestionId())
                .createdAt(task.getCreatedAt())
                .processedAt(task.getProcessedAt())
                .errorMessage(task.getErrorMessage())
                .build();
    }
}


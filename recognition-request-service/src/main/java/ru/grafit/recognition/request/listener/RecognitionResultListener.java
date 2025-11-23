package ru.grafit.recognition.request.listener;

import ru.grafit.recognition.common.kafka.RecognitionResultMessage;
import ru.grafit.recognition.request.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecognitionResultListener {

    private final TaskService taskService;

    @KafkaListener(topics = "recognition-results", groupId = "request-service")
    public void handleRecognitionResult(RecognitionResultMessage message) {
        log.info("Received recognition result for task: {}, status: {}", 
                message.getTaskId(), message.getStatus());
        
        taskService.updateTaskStatus(
                message.getTaskId(),
                message.getStatus(),
                message.getRecognizedText(),
                message.getErrorMessage()
        ).subscribe(
                null,
                error -> log.error("Failed to update task status: {}", message.getTaskId(), error)
        );
    }
}


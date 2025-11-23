package ru.grafit.recognition.result.listener;

import ru.grafit.recognition.common.kafka.RecognitionResultMessage;
import ru.grafit.recognition.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecognitionResultListener {

    private final ResultService resultService;

    @KafkaListener(
            topics = "recognition-results",
            groupId = "result-service",
            concurrency = "3"
    )
    public void handleRecognitionResult(
            @Payload RecognitionResultMessage message,
            @Header(KafkaHeaders.RECEIVED_KEY) String taskId,
            Acknowledgment acknowledgment) {
        
        log.info("Received recognition result for task: {}, status: {}", 
                taskId, message.getStatus());

        resultService.saveResult(message)
                .doOnSuccess(result -> {
                    log.info("Result saved for task: {}", taskId);
                    acknowledgment.acknowledge();
                })
                .doOnError(error -> {
                    log.error("Failed to save result for task: {}", taskId, error);
                    acknowledgment.acknowledge(); // Acknowledge to avoid reprocessing
                })
                .subscribe();
    }
}


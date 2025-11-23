package ru.grafit.recognition.processing.listener;

import ru.grafit.recognition.common.kafka.RecognitionResultMessage;
import ru.grafit.recognition.common.kafka.RecognitionTaskMessage;
import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.processing.service.SpeechRecognitionProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecognitionTaskListener {

    private final SpeechRecognitionProcessor processor;
    private final KafkaTemplate<String, RecognitionResultMessage> kafkaTemplate;
    private final String recognitionResultsTopic = "recognition-results";

    @KafkaListener(
            topics = "recognition-tasks",
            groupId = "processing-service",
            concurrency = "5"
    )
    public void processRecognitionTask(
            @Payload RecognitionTaskMessage message,
            @Header(KafkaHeaders.RECEIVED_KEY) String taskId,
            Acknowledgment acknowledgment) {
        
        log.info("Processing recognition task: {}, user: {}, question: {}", 
                taskId, message.getUserId(), message.getQuestionId());

        processor.processRecognition(message)
                .doOnSuccess(result -> {
                    RecognitionResultMessage resultMessage = RecognitionResultMessage.builder()
                            .taskId(taskId)
                            .userId(message.getUserId())
                            .questionId(message.getQuestionId())
                            .status(RecognitionStatus.SUCCESS)
                            .recognizedText(result.recognizedText())
                            .recognitionType(message.getRecognitionType())
                            .operationId(result.operationId())
                            .processedAt(Instant.now())
                            .build();

                    kafkaTemplate.send(recognitionResultsTopic, taskId, resultMessage)
                            .whenComplete((sendResult, error) -> {
                                if (error != null) {
                                    log.error("Failed to send result to Kafka: {}", taskId, error);
                                } else {
                                    log.info("Result sent to Kafka: {}, partition: {}, offset: {}", 
                                            taskId,
                                            sendResult.getRecordMetadata().partition(),
                                            sendResult.getRecordMetadata().offset());
                                }
                            });
                    
                    acknowledgment.acknowledge();
                })
                .doOnError(error -> {
                    log.error("Failed to process recognition task: {}", taskId, error);
                    
                    RecognitionResultMessage errorMessage = RecognitionResultMessage.builder()
                            .taskId(taskId)
                            .userId(message.getUserId())
                            .questionId(message.getQuestionId())
                            .status(RecognitionStatus.FAILED)
                            .recognitionType(message.getRecognitionType())
                            .errorMessage(error.getMessage())
                            .processedAt(Instant.now())
                            .build();

                    kafkaTemplate.send(recognitionResultsTopic, taskId, errorMessage)
                            .whenComplete((sendResult, sendError) -> {
                                if (sendError != null) {
                                    log.error("Failed to send error result to Kafka: {}", taskId, sendError);
                                }
                            });
                    
                    acknowledgment.acknowledge();
                })
                .subscribe();
    }
}


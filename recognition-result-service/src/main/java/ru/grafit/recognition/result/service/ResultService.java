package ru.grafit.recognition.result.service;

import ru.grafit.recognition.common.kafka.RecognitionResultMessage;
import ru.grafit.recognition.result.entity.RecognitionResult;
import ru.grafit.recognition.result.repository.RecognitionResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResultService {

    private final RecognitionResultRepository resultRepository;

    public Mono<RecognitionResult> saveResult(RecognitionResultMessage message) {
        RecognitionResult result = RecognitionResult.builder()
                .taskId(message.getTaskId())
                .userId(message.getUserId())
                .questionId(message.getQuestionId())
                .status(message.getStatus())
                .recognizedText(message.getRecognizedText())
                .recognitionType(message.getRecognitionType())
                .operationId(message.getOperationId())
                .errorMessage(message.getErrorMessage())
                .processedAt(message.getProcessedAt())
                .build();

        return resultRepository.save(result)
                .doOnSuccess(saved -> log.info("Result saved: taskId={}, status={}", 
                        saved.getTaskId(), saved.getStatus()))
                .doOnError(error -> log.error("Failed to save result: taskId={}", 
                        message.getTaskId(), error));
    }
}


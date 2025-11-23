package ru.grafit.recognition.result.repository;

import ru.grafit.recognition.result.entity.RecognitionResult;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface RecognitionResultRepository extends R2dbcRepository<RecognitionResult, Long> {
    Mono<RecognitionResult> findByTaskId(String taskId);
    Flux<RecognitionResult> findByUserId(String userId);
    Flux<RecognitionResult> findByQuestionId(String questionId);
}


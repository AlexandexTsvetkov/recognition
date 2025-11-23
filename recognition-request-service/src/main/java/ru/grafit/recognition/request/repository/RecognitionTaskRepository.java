package ru.grafit.recognition.request.repository;

import ru.grafit.recognition.request.entity.RecognitionTask;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface RecognitionTaskRepository extends R2dbcRepository<RecognitionTask, Long> {
    Mono<RecognitionTask> findByTaskId(String taskId);
}


package ru.grafit.recognition.request.controller;

import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.dto.RecognitionTaskResponse;
import ru.grafit.recognition.request.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/tasks")
@Slf4j
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public Mono<ResponseEntity<RecognitionTaskResponse>> createTask(
            @Valid @RequestBody RecognitionTaskRequest request) {
        log.info("Creating recognition task for user: {}, question: {}",
                request.getUserId(), request.getQuestionId());

        return taskService.createTask(request)
                .map(taskResponse -> ResponseEntity.accepted().body(taskResponse))
                .doOnSuccess(response -> log.info("Task created successfully"))
                .doOnError(error -> log.error("Failed to create task", error));
    }

    @GetMapping("/{taskId}")
    public Mono<ResponseEntity<RecognitionTaskResponse>> getTask(@PathVariable String taskId) {
        log.info("Getting task status: {}", taskId);

        return taskService.getTask(taskId)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .doOnSuccess(response -> {
                    if (response.getStatusCode().is4xxClientError()) {
                        log.warn("Task not found: {}", taskId);
                    } else {
                        log.info("Task retrieved successfully: {}", taskId);
                    }
                });
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Object>> healthCheck() {
        return Mono.just(ResponseEntity.ok()
                .body(java.util.Map.of(
                        "status", "UP",
                        "service", "recognition-request-service",
                        "timestamp", java.time.Instant.now().toString()
                )));
    }
}
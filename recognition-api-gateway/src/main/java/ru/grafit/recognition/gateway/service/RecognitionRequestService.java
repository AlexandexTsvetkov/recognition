package ru.grafit.recognition.gateway.service;

import ru.grafit.recognition.common.dto.RecognitionTaskRequest;
import ru.grafit.recognition.common.dto.RecognitionTaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecognitionRequestService {

    @Value("${app.request-service.url:http://localhost:8082}")
    private String requestServiceUrl;

    private final WebClient.Builder webClientBuilder;

    public Mono<String> createRecognitionTask(RecognitionTaskRequest request) {
        WebClient webClient = webClientBuilder.baseUrl(requestServiceUrl).build();
        
        return webClient.post()
                .uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RecognitionTaskResponse.class)
                .timeout(Duration.ofSeconds(10))
                .map(RecognitionTaskResponse::getTaskId)
                .doOnSuccess(taskId -> log.info("Task created: {}", taskId))
                .doOnError(error -> log.error("Failed to create task", error));
    }

    public Mono<RecognitionTaskResponse> getTaskStatus(String taskId) {
        WebClient webClient = webClientBuilder.baseUrl(requestServiceUrl).build();
        
        return webClient.get()
                .uri("/api/v1/tasks/{taskId}", taskId)
                .retrieve()
                .bodyToMono(RecognitionTaskResponse.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("Failed to get task status: {}", taskId, error));
    }
}


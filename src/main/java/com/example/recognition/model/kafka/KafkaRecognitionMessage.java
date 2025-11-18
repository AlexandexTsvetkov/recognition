package com.example.recognition.model.kafka;

import java.time.Instant;
import java.util.Map;

public record KafkaRecognitionMessage(
        String userId,
        String questionId,
        String recognizedText,
        String operationId,
        Instant recognizedAt,
        Map<String, Object> metadata
) {}

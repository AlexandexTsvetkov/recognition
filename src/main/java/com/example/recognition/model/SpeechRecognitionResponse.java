package com.example.recognition.model;

import java.time.Instant;

public record SpeechRecognitionResponse(
        String operationId,
        String recognizedText,
        RecognitionStatus status,
        Instant processedAt,
        RecognitionType recognitionType
) {}

package com.example.recognition.model;

public record SpeechRecognitionRequest(
        byte[] audioData,
        String fileName,
        String languageCode,
        Boolean enableProfanityFilter,
        AudioFormat audioFormat,
        RecognitionType recognitionType
) {
    public SpeechRecognitionRequest {
        if (recognitionType == null) {
            recognitionType = RecognitionType.GENERAL;
        }
    }
}
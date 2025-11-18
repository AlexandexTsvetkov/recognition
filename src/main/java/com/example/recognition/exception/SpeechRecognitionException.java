package com.example.recognition.exception;

public class SpeechRecognitionException extends RuntimeException {
    public SpeechRecognitionException(String message) {
        super(message);
    }

    public SpeechRecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

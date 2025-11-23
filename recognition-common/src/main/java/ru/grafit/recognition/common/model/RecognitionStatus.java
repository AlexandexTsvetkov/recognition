package ru.grafit.recognition.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecognitionStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED;

    @JsonValue
    public String getName() {
        return name();
    }
}


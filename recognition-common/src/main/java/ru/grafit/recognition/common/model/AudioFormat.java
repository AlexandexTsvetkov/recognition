package ru.grafit.recognition.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AudioFormat {
    WAV,
    OGG_OPUS,
    MP3;

    @JsonValue
    public String getName() {
        return name();
    }
}


package ru.grafit.recognition.common.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RecognitionType {
    GENERAL("general", true, true),
    TECHNICAL("general:rc", true, true),
    LITERATURE("general:rc", true, true),
    FAST("general", false, false),
    PRECISE("general:rc", true, true);

    private final String model;
    private final boolean enablePunctuation;
    private final boolean enableLiteratureText;

    RecognitionType(String model, boolean enablePunctuation, boolean enableLiteratureText) {
        this.model = model;
        this.enablePunctuation = enablePunctuation;
        this.enableLiteratureText = enableLiteratureText;
    }

    @JsonValue
    public String getModel() { return model; }
    public boolean isEnablePunctuation() { return enablePunctuation; }
    public boolean isEnableLiteratureText() { return enableLiteratureText; }
}


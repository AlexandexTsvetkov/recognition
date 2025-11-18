package com.example.recognition.conf;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.speechkit.recognition")
public class RecognitionConfig {
    private String model = "general:rc";
    private boolean enablePunctuation = true;
    private boolean enableLiteratureText = true;
    private boolean enableProfanityFilter = false;
    private String languageCode = "ru-RU";
    private boolean enableSpeakerAnalysis = false;
    private int recognitionTimeout = 60;
    private int maxRetryAttempts = 3;
}

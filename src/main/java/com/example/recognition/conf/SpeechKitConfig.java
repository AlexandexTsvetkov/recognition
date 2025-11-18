package com.example.recognition.conf;

import com.example.recognition.grpc.SpeechKitGrpcClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SpeechKitConfig {

    private final SpeechKitGrpcClient speechKitClient;

    @Value("${app.speechkit.endpoint}")
    private String endpoint;

    @Value("${app.speechkit.api-key}")
    private String apiKey;

    @PostConstruct
    public void initializeSpeechKitClient() {
        speechKitClient.initialize(endpoint, apiKey);
    }
}

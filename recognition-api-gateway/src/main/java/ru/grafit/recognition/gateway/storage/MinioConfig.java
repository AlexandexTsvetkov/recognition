package ru.grafit.recognition.gateway.storage;

import ru.grafit.recognition.common.storage.StorageService;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Value("${app.storage.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${app.storage.access-key:minioadmin}")
    private String accessKey;

    @Value("${app.storage.secret-key:minioadmin}")
    private String secretKey;

    @Value("${app.storage.bucket-name:recognition-audio}")
    private String bucketName;

    @Value("${app.storage.audio-prefix:audio/}")
    private String audioPrefix;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public StorageService storageService(MinioClient minioClient) {
        return new MinioStorageService(minioClient, bucketName, audioPrefix);
    }
}
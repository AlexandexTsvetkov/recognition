package ru.grafit.recognition.processing.storage;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import ru.grafit.recognition.common.storage.StorageService;

import java.io.ByteArrayInputStream;


@Slf4j
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String audioPrefix;

    public MinioStorageService(MinioClient minioClient, String bucketName, String audioPrefix) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.audioPrefix = audioPrefix;
        initializeBucket();
    }

    @Override
    public String uploadAudio(byte[] audioData, String taskId) {
        try {
            String objectName = audioPrefix + taskId;

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(audioData), audioData.length, -1)
                            .contentType("application/octet-stream")
                            .build()
            );

            log.info("Audio file uploaded to storage: bucket={}, object={}, size={} bytes",
                    bucketName, objectName, audioData.length);

            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload audio file to storage: taskId={}", taskId, e);
            throw new RuntimeException("Failed to upload audio file", e);
        }
    }

    @Override
    public byte[] downloadAudio(String fileKey) {
        try {
            var stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileKey)
                            .build()
            );

            byte[] audioData = stream.readAllBytes();
            stream.close();

            log.debug("Audio file downloaded from storage: fileKey={}, size={} bytes",
                    fileKey, audioData.length);

            return audioData;
        } catch (Exception e) {
            log.error("Failed to download audio file from storage: fileKey={}", fileKey, e);
            throw new RuntimeException("Failed to download audio file", e);
        }
    }

    @Override
    public void deleteAudio(String fileKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileKey)
                            .build()
            );

            log.info("Audio file deleted from storage: fileKey={}", fileKey);
        } catch (Exception e) {
            log.error("Failed to delete audio file from storage: fileKey={}", fileKey, e);
        }
    }

    @Override
    public boolean exists(String fileKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileKey)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeBucket() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());

            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
                log.info("Created bucket: {}", bucketName);
            } else {
                log.info("Bucket already exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize bucket: bucket={}", bucketName, e);
            throw new RuntimeException("Failed to initialize bucket", e);
        }
    }
}


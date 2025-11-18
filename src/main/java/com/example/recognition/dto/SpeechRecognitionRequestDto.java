package com.example.recognition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SpeechRecognitionRequestDto {

    @NotNull(message = "File is required")
    private MultipartFile file;

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Question ID is required")
    private String questionId;

    private String language;

    private boolean profanityFilter = true;
}

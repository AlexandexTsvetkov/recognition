package ru.grafit.recognition.common.dto;

import ru.grafit.recognition.common.model.AudioFormat;
import ru.grafit.recognition.common.model.RecognitionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionTaskRequest {
    @NotBlank(message = "User ID is required")
    @JsonProperty("userId")
    private String userId;
    
    @NotBlank(message = "Question ID is required")
    @JsonProperty("questionId")
    private String questionId;
    
    @NotBlank(message = "Audio file URL is required")
    @JsonProperty("audioFileUrl")
    private String audioFileUrl;
    
    @JsonProperty("fileName")
    private String fileName;
    
    @JsonProperty("audioFormat")
    private AudioFormat audioFormat;
    
    @JsonProperty("recognitionType")
    private RecognitionType recognitionType;
    
    @JsonProperty("languageCode")
    private String languageCode;
    
    @JsonProperty("enableProfanityFilter")
    private Boolean enableProfanityFilter;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
}


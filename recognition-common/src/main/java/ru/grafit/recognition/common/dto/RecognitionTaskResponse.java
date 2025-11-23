package ru.grafit.recognition.common.dto;

import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.common.model.RecognitionType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionTaskResponse {
    @JsonProperty("taskId")
    private String taskId;
    
    @JsonProperty("status")
    private RecognitionStatus status;
    
    @JsonProperty("recognizedText")
    private String recognizedText;
    
    @JsonProperty("recognitionType")
    private RecognitionType recognitionType;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("questionId")
    private String questionId;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("processedAt")
    private Instant processedAt;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}


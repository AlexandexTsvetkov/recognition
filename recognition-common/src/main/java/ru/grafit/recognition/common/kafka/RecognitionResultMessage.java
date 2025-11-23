package ru.grafit.recognition.common.kafka;

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
public class RecognitionResultMessage {
    @JsonProperty("taskId")
    private String taskId;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("questionId")
    private String questionId;
    
    @JsonProperty("status")
    private RecognitionStatus status;
    
    @JsonProperty("recognizedText")
    private String recognizedText;
    
    @JsonProperty("recognitionType")
    private RecognitionType recognitionType;
    
    @JsonProperty("operationId")
    private String operationId;
    
    @JsonProperty("processedAt")
    private Instant processedAt;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
}


package ru.grafit.recognition.common.kafka;

import ru.grafit.recognition.common.model.AudioFormat;
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
public class RecognitionTaskMessage {
    @JsonProperty("taskId")
    private String taskId;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("questionId")
    private String questionId;
    
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


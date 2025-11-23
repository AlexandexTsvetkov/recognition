package ru.grafit.recognition.result.entity;

import ru.grafit.recognition.common.model.RecognitionStatus;
import ru.grafit.recognition.common.model.RecognitionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("recognition_results")
public class RecognitionResult {
    @Id
    private Long id;
    private String taskId;
    private String userId;
    private String questionId;
    private RecognitionStatus status;
    private RecognitionType recognitionType;
    private String recognizedText;
    private String operationId;
    private String errorMessage;
    private Instant processedAt;
}


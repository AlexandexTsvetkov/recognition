package ru.grafit.recognition.request.entity;

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
@Table("recognition_tasks")
public class RecognitionTask {
    @Id
    private Long id;
    private String taskId;
    private String userId;
    private String questionId;
    private RecognitionStatus status;
    private RecognitionType recognitionType;
    private String recognizedText;
    private String errorMessage;
    private Instant createdAt;
    private Instant processedAt;
}


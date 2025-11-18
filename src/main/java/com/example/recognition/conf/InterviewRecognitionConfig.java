package com.example.recognition.conf;

import com.google.protobuf.ByteString;
import yandex.cloud.api.ai.stt.v3.Stt;

public class InterviewRecognitionConfig {

    // Для технических собеседований
    public static Stt.RecognizeFileRequest buildTechnicalInterviewRequest(byte[] audioData) {
        return buildBaseRequest(audioData)
                .setRecognitionModel(buildTechnicalRecognitionModel())
                .build();
    }

    // Для общих собеседований
    public static Stt.RecognizeFileRequest buildGeneralInterviewRequest(byte[] audioData) {
        return buildBaseRequest(audioData)
                .setRecognitionModel(buildGeneralRecognitionModel())
                .build();
    }

    private static Stt.RecognizeFileRequest.Builder buildBaseRequest(byte[] audioData) {
        return Stt.RecognizeFileRequest.newBuilder()
                .setContent(ByteString.copyFrom(audioData));
    }

    private static Stt.RecognitionModelOptions buildTechnicalRecognitionModel() {
        return Stt.RecognitionModelOptions.newBuilder()
                .setModel("general:rc")
                .setAudioFormat(buildAudioFormat())
                .setTextNormalization(Stt.TextNormalizationOptions.newBuilder()
                        .setTextNormalization(Stt.TextNormalizationOptions.TextNormalization.TEXT_NORMALIZATION_ENABLED)
                        .setLiteratureText(true)
                        .build())
                .setLanguageRestriction(Stt.LanguageRestrictionOptions.newBuilder()
                        .setRestrictionType(Stt.LanguageRestrictionOptions.LanguageRestrictionType.WHITELIST)
                        .addLanguageCode("ru-RU")
                        .addLanguageCode("en-US") // Для технических терминов на английском
                        .build())
                .build();
    }

    private static Stt.RecognitionModelOptions buildGeneralRecognitionModel() {
        return Stt.RecognitionModelOptions.newBuilder()
                .setModel("general:rc")
                .setAudioFormat(buildAudioFormat())
                .setTextNormalization(Stt.TextNormalizationOptions.newBuilder()
                        .setTextNormalization(Stt.TextNormalizationOptions.TextNormalization.TEXT_NORMALIZATION_ENABLED)
                        .setLiteratureText(true)
                        .build())
                .build();
    }

    private static Stt.AudioFormatOptions buildAudioFormat() {
        return Stt.AudioFormatOptions.newBuilder()
                .setContainerAudio(Stt.ContainerAudio.newBuilder()
                        .setContainerAudioType(Stt.ContainerAudio.ContainerAudioType.WAV)
                        .build())
                .build();
    }
}
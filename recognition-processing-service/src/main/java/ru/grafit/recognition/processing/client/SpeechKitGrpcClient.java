package ru.grafit.recognition.processing.client;

import ru.grafit.recognition.common.model.AudioFormat;
import ru.grafit.recognition.common.model.RecognitionType;
import ru.grafit.recognition.processing.config.RecognitionConfig;
import com.google.protobuf.ByteString;
import io.grpc.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import yandex.cloud.api.ai.stt.v3.*;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
@RequiredArgsConstructor
public class SpeechKitGrpcClient {

    @Value("${app.speechkit.endpoint:stt.api.cloud.yandex.net:443}")
    private String endpoint;

    @Value("${app.speechkit.api-key}")
    private String apiKey;

    private final RecognitionConfig recognitionConfig;

    private ManagedChannel channel;
    private AsyncRecognizerGrpc.AsyncRecognizerBlockingStub asyncRecognizerStub;

    @PostConstruct
    public void initialize() {
        this.channel = ManagedChannelBuilder.forTarget(endpoint)
                .intercept(new ApiKeyInterceptor(apiKey))
                .useTransportSecurity()
                .build();

        this.asyncRecognizerStub = AsyncRecognizerGrpc.newBlockingStub(channel);
        log.info("SpeechKit gRPC client initialized for endpoint: {}", endpoint);
    }

    private static class ApiKeyInterceptor implements ClientInterceptor {
        private final String apiKey;

        public ApiKeyInterceptor(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return next.newCall(method, callOptions.withCallCredentials(new CallCredentials() {
                @Override
                public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor,
                                                 MetadataApplier applier) {
                    appExecutor.execute(() -> {
                        try {
                            Metadata headers = new Metadata();
                            headers.put(Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER),
                                    "Api-Key " + apiKey);
                            headers.put(Metadata.Key.of("x-node-alias", Metadata.ASCII_STRING_MARSHALLER),
                                    "speechkit.stt.api.cloud.yandex.net");
                            applier.apply(headers);
                        } catch (Throwable e) {
                            applier.fail(Status.UNAUTHENTICATED.withCause(e));
                        }
                    });
                }

                @Override
                public void thisUsesUnstableApi() {}
            }));
        }
    }

    public String recognizeAudio(byte[] audioData, AudioFormat format, RecognitionType recognitionType) {
        try {
            Stt.RecognizeFileRequest request = buildRecognitionRequest(audioData, format, recognitionType);

            yandex.cloud.api.operation.OperationOuterClass.Operation operation = 
                    asyncRecognizerStub.recognizeFile(request);
            log.info("Recognition operation started: {} (type: {})", operation.getId(), recognitionType);

            return operation.getId();

        } catch (StatusRuntimeException e) {
            log.error("gRPC recognition failed for type {}: {}", recognitionType, e.getStatus(), e);
            throw new RuntimeException("Recognition request failed", e);
        }
    }

    public String getRecognitionResult(String operationId) {
        try {
            SttService.GetRecognitionRequest request = SttService.GetRecognitionRequest.newBuilder()
                    .setOperationId(operationId)
                    .build();

            StringBuilder recognizedText = new StringBuilder();
            AtomicInteger finalCount = new AtomicInteger();
            AtomicInteger partialCount = new AtomicInteger();

            asyncRecognizerStub.getRecognition(request)
                    .forEachRemaining(response -> {
                        if (response.hasFinal()) {
                            finalCount.getAndIncrement();
                            processFinalResponse(response.getFinal(), recognizedText);
                        } else if (response.hasPartial()) {
                            partialCount.getAndIncrement();
                            processPartialResponse(response.getPartial());
                        } else if (response.hasFinalRefinement()) {
                            processFinalRefinement(response.getFinalRefinement(), recognizedText);
                        } else if (response.hasStatusCode()) {
                            processStatusCode(response.getStatusCode());
                        }
                    });

            log.info("Recognition completed. Finals: {}, Partials: {}, Text length: {}",
                    finalCount, partialCount, recognizedText.length());

            String result = recognizedText.toString().trim();
            if (!result.isEmpty()) {
                return result;
            } else {
                log.warn("No final text found in responses");
                return null;
            }

        } catch (StatusRuntimeException e) {
            log.error("Failed to get recognition result for operation {}: {}", operationId, e.getStatus(), e);
            throw new RuntimeException("Failed to get recognition result", e);
        }
    }

    private void processFinalResponse(Stt.AlternativeUpdate finalUpdate, StringBuilder recognizedText) {
        if (!finalUpdate.getAlternativesList().isEmpty()) {
            String finalText = finalUpdate.getAlternatives(0).getText().trim();
            if (!finalText.isEmpty()) {
                if (recognizedText.length() > 0 && !finalText.startsWith(".")) {
                    recognizedText.append(" ");
                }
                recognizedText.append(finalText);

                if (finalText.endsWith(".") && !finalText.endsWith(". ")) {
                    recognizedText.append(" ");
                }
            }
        }
    }

    private void processPartialResponse(Stt.AlternativeUpdate partialUpdate) {
        if (!partialUpdate.getAlternativesList().isEmpty()) {
            String partialText = partialUpdate.getAlternatives(0).getText();
            log.trace("Partial result: {}", partialText);
        }
    }

    private void processFinalRefinement(Stt.FinalRefinement refinement, StringBuilder recognizedText) {
        if (refinement.hasNormalizedText() &&
                !refinement.getNormalizedText().getAlternativesList().isEmpty()) {
            String refinedText = refinement.getNormalizedText().getAlternatives(0).getText();
            log.debug("Text refinement applied: {}", refinedText);
        }
    }

    private void processStatusCode(Stt.StatusCode statusCode) {
        log.debug("Recognition status: {} - {}", statusCode.getCodeType(), statusCode.getMessage());
    }

    private Stt.RecognizeFileRequest buildRecognitionRequest(byte[] audioData, AudioFormat format,
                                                             RecognitionType recognitionType) {
        Stt.ContainerAudio.ContainerAudioType containerType = mapAudioFormat(format);

        Stt.AudioFormatOptions audioFormat = Stt.AudioFormatOptions.newBuilder()
                .setContainerAudio(Stt.ContainerAudio.newBuilder()
                        .setContainerAudioType(containerType)
                        .build())
                .build();

        Stt.TextNormalizationOptions.Builder textNormalizationBuilder = Stt.TextNormalizationOptions.newBuilder()
                .setTextNormalization(Stt.TextNormalizationOptions.TextNormalization.TEXT_NORMALIZATION_ENABLED);

        if (recognitionType.isEnablePunctuation()) {
            textNormalizationBuilder.setLiteratureText(recognitionType.isEnableLiteratureText());
        }

        if (recognitionConfig.isEnableProfanityFilter()) {
            textNormalizationBuilder.setProfanityFilter(true);
        }

        Stt.LanguageRestrictionOptions languageRestriction = Stt.LanguageRestrictionOptions.newBuilder()
                .setRestrictionType(Stt.LanguageRestrictionOptions.LanguageRestrictionType.WHITELIST)
                .addLanguageCode(recognitionConfig.getLanguageCode())
                .build();

        Stt.RecognitionModelOptions recognitionModel = Stt.RecognitionModelOptions.newBuilder()
                .setModel(recognitionType.getModel())
                .setAudioFormat(audioFormat)
                .setTextNormalization(textNormalizationBuilder.build())
                .setLanguageRestriction(languageRestriction)
                .build();

        return Stt.RecognizeFileRequest.newBuilder()
                .setContent(ByteString.copyFrom(audioData))
                .setRecognitionModel(recognitionModel)
                .build();
    }

    private Stt.ContainerAudio.ContainerAudioType mapAudioFormat(AudioFormat format) {
        return switch (format) {
            case WAV -> Stt.ContainerAudio.ContainerAudioType.WAV;
            case OGG_OPUS -> Stt.ContainerAudio.ContainerAudioType.OGG_OPUS;
            case MP3 -> Stt.ContainerAudio.ContainerAudioType.MP3;
        };
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
                log.info("SpeechKit gRPC client shutdown completed");
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
                log.warn("SpeechKit gRPC client shutdown interrupted");
            }
        }
    }
}


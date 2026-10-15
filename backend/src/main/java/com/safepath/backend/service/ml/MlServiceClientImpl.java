package com.safepath.backend.service.ml;

import com.safepath.backend.config.MlServiceProperties;
import com.safepath.backend.dto.ml.*;
import com.safepath.backend.exception.MlServiceException;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.exception.MlServiceValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;

/**
 * Robust REST client for interacting with the FastAPI ML inference service.
 * Enforces timeout bounds, batch chunking, strict segment-to-prediction mapping,
 * and comprehensive error handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlServiceClientImpl implements MlServiceClient {

    private final RestClient mlRestClient;
    private final MlServiceProperties properties;

    @Override
    public MlHealthResponseDto checkHealth() {
        try {
            log.debug("Checking ML service health at {}/health", properties.getUrl());
            return mlRestClient.get()
                    .uri("/health")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MlHealthResponseDto.class);
        } catch (ResourceAccessException e) {
            log.warn("ML service is unreachable during health check: {}", e.getMessage());
            return MlHealthResponseDto.builder()
                    .status("DOWN")
                    .modelsLoaded(false)
                    .isSyntheticDemonstration(false)
                    .detail("Connection refused or timed out connecting to ML service.")
                    .build();
        } catch (RestClientResponseException e) {
            log.warn("ML service returned HTTP {} on /health", e.getStatusCode());
            return MlHealthResponseDto.builder()
                    .status("DEGRADED")
                    .modelsLoaded(false)
                    .isSyntheticDemonstration(false)
                    .detail("ML service returned HTTP error: " + e.getStatusCode())
                    .build();
        } catch (Exception e) {
            log.warn("Unexpected error checking ML service health: {}", e.getMessage());
            return MlHealthResponseDto.builder()
                    .status("DOWN")
                    .modelsLoaded(false)
                    .isSyntheticDemonstration(false)
                    .detail("Unexpected exception during health check.")
                    .build();
        }
    }

    @Override
    public PredictRiskResponseDto predictRisk(String segmentId, RiskFeaturesDto features) {
        if (segmentId == null || segmentId.trim().isEmpty()) {
            throw new MlServiceValidationException("segment_id cannot be null or empty");
        }

        PredictRiskRequestDto request = PredictRiskRequestDto.builder()
                .segmentId(segmentId)
                .features(features != null ? features : new RiskFeaturesDto())
                .build();

        try {
            log.debug("Sending single risk prediction request for segment {}", segmentId);
            PredictRiskResponseDto response = mlRestClient.post()
                    .uri("/predict-risk")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PredictRiskResponseDto.class);

            if (response == null) {
                throw new MlServiceException("Received empty response from ML prediction endpoint");
            }
            return response;

        } catch (ResourceAccessException e) {
            log.error("Timeout or connection failure during single risk prediction: {}", e.getMessage());
            throw new MlServiceUnavailableException("ML inference service is unreachable or timed out.", e);
        } catch (RestClientResponseException e) {
            handleRestException(e);
            throw new MlServiceException("Failed to process prediction response", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (MlServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during single risk prediction: {}", e.getMessage());
            throw new MlServiceException("Unexpected error communicating with ML service.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Map<String, PredictRiskResponseDto> predictRiskBatch(List<PredictRiskRequestDto> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, PredictRiskResponseDto> resultMap = new HashMap<>();
        int maxBatch = Math.max(1, properties.getMaxBatchSize());
        int totalSegments = segments.size();

        log.debug("Dispatching batch risk prediction for {} segments (max chunk size: {})", totalSegments, maxBatch);

        for (int i = 0; i < totalSegments; i += maxBatch) {
            List<PredictRiskRequestDto> chunk = segments.subList(i, Math.min(i + maxBatch, totalSegments));
            BatchPredictRequestDto batchRequest = BatchPredictRequestDto.builder()
                    .segments(chunk)
                    .build();

            try {
                BatchPredictResponseDto response = mlRestClient.post()
                        .uri("/predict-risk/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(batchRequest)
                        .retrieve()
                        .body(BatchPredictResponseDto.class);

                if (response != null && response.getPredictions() != null) {
                    for (PredictRiskResponseDto prediction : response.getPredictions()) {
                        if (prediction != null && prediction.getSegmentId() != null) {
                            resultMap.put(prediction.getSegmentId(), prediction);
                        }
                    }
                }

            } catch (ResourceAccessException e) {
                log.error("Timeout or connection failure during batch prediction (chunk {} to {}): {}",
                        i, Math.min(i + maxBatch, totalSegments), e.getMessage());
                throw new MlServiceUnavailableException("ML inference service is unreachable or timed out during batch evaluation.", e);
            } catch (RestClientResponseException e) {
                handleRestException(e);
            } catch (MlServiceException e) {
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error during batch prediction: {}", e.getMessage());
                throw new MlServiceException("Unexpected error during batch ML inference communication.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        return resultMap;
    }

    @Override
    public MlExplainResponseDto explain(String segmentId, RiskFeaturesDto features) {
        if (segmentId == null || segmentId.trim().isEmpty()) {
            throw new MlServiceValidationException("segment_id cannot be null or empty for explain request");
        }

        MlExplainRequestDto request = MlExplainRequestDto.builder()
                .segmentId(segmentId)
                .features(features != null ? features : new RiskFeaturesDto())
                .build();

        try {
            log.debug("Requesting SHAP explanation for segment {}", segmentId);
            MlExplainResponseDto response = mlRestClient.post()
                    .uri("/explain")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(MlExplainResponseDto.class);

            if (response == null) {
                throw new MlServiceException("Received empty response from ML explain endpoint");
            }
            return response;

        } catch (ResourceAccessException e) {
            log.error("Timeout or connection failure during explanation request: {}", e.getMessage());
            throw new MlServiceUnavailableException("ML inference service is unreachable or timed out.", e);
        } catch (RestClientResponseException e) {
            handleRestException(e);
            throw new MlServiceException("Failed to process explain response", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (MlServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during explain request: {}", e.getMessage());
            throw new MlServiceException("Unexpected error communicating with ML explain service.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void handleRestException(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            log.warn("ML service is running but models are unready (503 Service Unavailable)");
            throw new MlServiceUnavailableException("ML models are unready or not loaded on server.", e);
        } else if (status == HttpStatus.BAD_REQUEST.value() || status == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
            log.warn("ML service rejected request with status {}: {} - Body: {}", status, e.getStatusText(), e.getResponseBodyAsString());
            throw new MlServiceValidationException("ML request rejected: " + e.getStatusText() + " - " + e.getResponseBodyAsString());
        } else if (status == HttpStatus.NOT_IMPLEMENTED.value()) {
            log.warn("ML explainer is not supported for current model (501 Not Implemented)");
            throw new MlServiceException("SHAP explainer is not available for active model.", HttpStatus.NOT_IMPLEMENTED);
        } else {
            log.error("ML service returned HTTP error {}", status);
            throw new MlServiceException("ML service HTTP error: " + status, HttpStatus.valueOf(status));
        }
    }
}

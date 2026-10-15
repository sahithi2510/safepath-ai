package com.safepath.backend.integration;

import com.safepath.backend.config.MlServiceProperties;
import com.safepath.backend.dto.ml.MlHealthResponseDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.dto.ml.RiskFeaturesDto;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.MlServiceClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.web.client.RestClient;

import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 26 Live Infrastructure Validation Test:
 * Tests live HTTP connectivity, JSON serialization, and error handling between Spring Boot's
 * MlServiceClientImpl and a running FastAPI ML service instance on localhost:8001.
 *
 * If FastAPI is not running on port 8001, this test safely skips without breaking the build,
 * while executing real end-to-end network calls when the ML daemon is active.
 */
class LiveMlServiceConnectivityTest {

    private MlServiceClient mlServiceClient;
    private static final String ML_SERVICE_URL = "http://127.0.0.1:8001";

    static boolean isMlServiceRunning() {
        try (Socket socket = new Socket("127.0.0.1", 8001)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        MlServiceProperties properties = new MlServiceProperties();
        properties.setUrl(ML_SERVICE_URL);
        properties.setTimeoutMs(5000);
        properties.setConnectTimeoutMs(2000);
        properties.setMaxBatchSize(500);
        properties.setEnabled(true);

        org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2000);
        requestFactory.setReadTimeout(5000);

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);

        RestClient restClient = RestClient.builder()
                .baseUrl(ML_SERVICE_URL)
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper)))
                .build();

        mlServiceClient = new MlServiceClientImpl(restClient, properties);
    }

    @Test
    @DisplayName("Verify Spring Boot can query live FastAPI /health endpoint over HTTP")
    @EnabledIf("isMlServiceRunning")
    void testLiveFastApiHealthCheck() {
        MlHealthResponseDto health = mlServiceClient.checkHealth();
        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getModelsLoaded()).isTrue();
        assertThat(health.getActiveModel()).isEqualTo("xgboost");
        assertThat(health.getIsSyntheticDemonstration()).isTrue();
    }

    @Test
    @DisplayName("Verify Spring Boot can perform live risk prediction against FastAPI /predict-risk")
    @EnabledIf("isMlServiceRunning")
    void testLiveFastApiPredictRisk() {
        RiskFeaturesDto features = RiskFeaturesDto.builder()
                .crimeDensity500m(0.25)
                .lightingScore(0.85)
                .hourOfDay(22)
                .build();

        PredictRiskResponseDto response = mlServiceClient.predictRisk("live-test-seg-01", features);
        assertThat(response).isNotNull();
        assertThat(response.getSegmentId()).isEqualTo("live-test-seg-01");
        assertThat(response.getRiskScore()).isNotNull();
        assertThat(response.getSafetyScore()).isNotNull();
        assertThat(response.getRiskLevel()).isNotNull();
        assertThat(response.getIsSyntheticDemonstration()).isTrue();
        assertThat(response.getImputedFeatures()).contains("crime_density_1km", "activity_proxy");
    }

    @Test
    @DisplayName("Verify Spring Boot can execute live batch prediction against FastAPI /predict-risk/batch")
    @EnabledIf("isMlServiceRunning")
    void testLiveFastApiPredictBatch() {
        PredictRiskRequestDto seg1 = PredictRiskRequestDto.builder()
                .segmentId("batch-seg-01")
                .features(RiskFeaturesDto.builder().hourOfDay(10).lightingScore(0.9).build())
                .build();

        PredictRiskRequestDto seg2 = PredictRiskRequestDto.builder()
                .segmentId("batch-seg-02")
                .features(RiskFeaturesDto.builder().hourOfDay(23).lightingScore(0.2).build())
                .build();

        Map<String, PredictRiskResponseDto> batchResults = mlServiceClient.predictRiskBatch(List.of(seg1, seg2));
        assertThat(batchResults).hasSize(2);
        assertThat(batchResults).containsKey("batch-seg-01");
        assertThat(batchResults).containsKey("batch-seg-02");
        assertThat(batchResults.get("batch-seg-01").getRiskScore()).isNotNull();
        assertThat(batchResults.get("batch-seg-02").getRiskScore()).isNotNull();
    }
}

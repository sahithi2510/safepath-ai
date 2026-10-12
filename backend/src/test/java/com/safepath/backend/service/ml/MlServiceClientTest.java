package com.safepath.backend.service.ml;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safepath.backend.config.MlServiceProperties;
import com.safepath.backend.dto.ml.*;
import com.safepath.backend.exception.MlServiceException;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.exception.MlServiceValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MlServiceClientTest {

    private MockRestServiceServer mockServer;
    private MlServiceClient mlServiceClient;
    private MlServiceProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new MlServiceProperties();
        properties.setUrl("http://localhost:8001");
        properties.setTimeoutMs(5000);
        properties.setConnectTimeoutMs(2000);
        properties.setMaxBatchSize(500);
        properties.setEnabled(true);

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getUrl());
        mockServer = MockRestServiceServer.bindTo(builder).build();
        mlServiceClient = new MlServiceClientImpl(builder.build(), properties);
    }

    @Test
    @DisplayName("predictRisk sends exact 15 feature names in snake_case JSON schema")
    void testPredictRisk_Exact15FeatureSchema() {
        RiskFeaturesDto features = RiskFeaturesDto.builder()
                .crimeDensity500m(0.25)
                .crimeDensity1km(0.40)
                .severityWeightedDensity(0.15)
                .recentCrimeDensity(0.10)
                .nightCrimeDensity(0.08)
                .hourOfDay(21)
                .dayOfWeek(5)
                .lightingScore(0.85)
                .activityProxy(0.70)
                .nearestPoliceM(600.0)
                .nearestHospitalM(1200.0)
                .nearestTransportM(350.0)
                .weatherPrecipitation(0.0)
                .weatherVisibility(10.0)
                .verifiedReportCount(2)
                .build();

        String responseJson = """
                {
                    "segment_id": "seg-101",
                    "raw_model_output": 0.32,
                    "risk_score": 0.32,
                    "safety_score": 0.68,
                    "risk_level": "LOW",
                    "model_type": "xgboost",
                    "model_version": "v1.0.0",
                    "feature_completeness": 1.0,
                    "imputed_features": [],
                    "is_synthetic_demonstration": true,
                    "demo": false
                }
                """;

        mockServer.expect(requestTo("http://localhost:8001/predict-risk"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                // Verify all 15 exact feature names
                .andExpect(jsonPath("$.segment_id").value("seg-101"))
                .andExpect(jsonPath("$.features.crime_density_500m").value(0.25))
                .andExpect(jsonPath("$.features.crime_density_1km").value(0.40))
                .andExpect(jsonPath("$.features.severity_weighted_density").value(0.15))
                .andExpect(jsonPath("$.features.recent_crime_density").value(0.10))
                .andExpect(jsonPath("$.features.night_crime_density").value(0.08))
                .andExpect(jsonPath("$.features.hour_of_day").value(21))
                .andExpect(jsonPath("$.features.day_of_week").value(5))
                .andExpect(jsonPath("$.features.lighting_score").value(0.85))
                .andExpect(jsonPath("$.features.activity_proxy").value(0.70))
                .andExpect(jsonPath("$.features.nearest_police_m").value(600.0))
                .andExpect(jsonPath("$.features.nearest_hospital_m").value(1200.0))
                .andExpect(jsonPath("$.features.nearest_transport_m").value(350.0))
                .andExpect(jsonPath("$.features.weather_precipitation").value(0.0))
                .andExpect(jsonPath("$.features.weather_visibility").value(10.0))
                .andExpect(jsonPath("$.features.verified_report_count").value(2))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        PredictRiskResponseDto result = mlServiceClient.predictRisk("seg-101", features);

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.getSegmentId()).isEqualTo("seg-101");
        assertThat(result.getRiskScore()).isEqualTo(0.32);
        assertThat(result.getSafetyScore()).isEqualTo(0.68);
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
        assertThat(result.getModelType()).isEqualTo("xgboost");
        assertThat(result.getModelVersion()).isEqualTo("v1.0.0");
        assertThat(result.getIsSyntheticDemonstration()).isTrue();
    }

    @Test
    @DisplayName("predictRiskBatch parses batch responses and maps predictions by segment ID even when out-of-order")
    void testPredictRiskBatch_OutOfOrderMapping() {
        List<PredictRiskRequestDto> requests = List.of(
                PredictRiskRequestDto.builder().segmentId("seg-A").features(new RiskFeaturesDto()).build(),
                PredictRiskRequestDto.builder().segmentId("seg-B").features(new RiskFeaturesDto()).build(),
                PredictRiskRequestDto.builder().segmentId("seg-C").features(new RiskFeaturesDto()).build()
        );

        // FastAPI returns segments out-of-order (seg-C, seg-A, seg-B)
        String responseJson = """
                {
                    "predictions": [
                        {
                            "segment_id": "seg-C",
                            "raw_model_output": 0.85,
                            "risk_score": 0.85,
                            "safety_score": 0.15,
                            "risk_level": "VERY_HIGH",
                            "model_type": "xgboost",
                            "model_version": "v1.0.0",
                            "is_synthetic_demonstration": true
                        },
                        {
                            "segment_id": "seg-A",
                            "raw_model_output": 0.12,
                            "risk_score": 0.12,
                            "safety_score": 0.88,
                            "risk_level": "VERY_LOW",
                            "model_type": "xgboost",
                            "model_version": "v1.0.0",
                            "is_synthetic_demonstration": true
                        },
                        {
                            "segment_id": "seg-B",
                            "raw_model_output": 0.45,
                            "risk_score": 0.45,
                            "safety_score": 0.55,
                            "risk_level": "MODERATE",
                            "model_type": "xgboost",
                            "model_version": "v1.0.0",
                            "is_synthetic_demonstration": true
                        }
                    ],
                    "count": 3
                }
                """;

        mockServer.expect(requestTo("http://localhost:8001/predict-risk/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.segments.length()").value(3))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        Map<String, PredictRiskResponseDto> resultMap = mlServiceClient.predictRiskBatch(requests);

        mockServer.verify();
        assertThat(resultMap).hasSize(3);
        // Correct segment ID mapping verified
        assertThat(resultMap.get("seg-A").getRiskScore()).isEqualTo(0.12);
        assertThat(resultMap.get("seg-A").getRiskLevel()).isEqualTo("VERY_LOW");
        assertThat(resultMap.get("seg-B").getRiskScore()).isEqualTo(0.45);
        assertThat(resultMap.get("seg-B").getRiskLevel()).isEqualTo("MODERATE");
        assertThat(resultMap.get("seg-C").getRiskScore()).isEqualTo(0.85);
        assertThat(resultMap.get("seg-C").getRiskLevel()).isEqualTo("VERY_HIGH");
    }

    @Test
    @DisplayName("predictRiskBatch splits requests larger than maxBatchSize into chunks")
    void testPredictRiskBatch_Chunking() {
        properties.setMaxBatchSize(2); // Small batch size for chunking test

        List<PredictRiskRequestDto> requests = List.of(
                PredictRiskRequestDto.builder().segmentId("s1").features(new RiskFeaturesDto()).build(),
                PredictRiskRequestDto.builder().segmentId("s2").features(new RiskFeaturesDto()).build(),
                PredictRiskRequestDto.builder().segmentId("s3").features(new RiskFeaturesDto()).build()
        );

        String chunk1Response = """
                {
                    "predictions": [
                        {"segment_id": "s1", "risk_score": 0.20, "safety_score": 0.80},
                        {"segment_id": "s2", "risk_score": 0.40, "safety_score": 0.60}
                    ],
                    "count": 2
                }
                """;

        String chunk2Response = """
                {
                    "predictions": [
                        {"segment_id": "s3", "risk_score": 0.60, "safety_score": 0.40}
                    ],
                    "count": 1
                }
                """;

        // Chunk 1 expectation (s1, s2)
        mockServer.expect(requestTo("http://localhost:8001/predict-risk/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andRespond(withSuccess(chunk1Response, MediaType.APPLICATION_JSON));

        // Chunk 2 expectation (s3)
        mockServer.expect(requestTo("http://localhost:8001/predict-risk/batch"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.segments.length()").value(1))
                .andRespond(withSuccess(chunk2Response, MediaType.APPLICATION_JSON));

        Map<String, PredictRiskResponseDto> resultMap = mlServiceClient.predictRiskBatch(requests);

        mockServer.verify();
        assertThat(resultMap).hasSize(3);
        assertThat(resultMap.get("s1").getRiskScore()).isEqualTo(0.20);
        assertThat(resultMap.get("s2").getRiskScore()).isEqualTo(0.40);
        assertThat(resultMap.get("s3").getRiskScore()).isEqualTo(0.60);
    }

    @Test
    @DisplayName("predictRisk handles HTTP 503 by throwing MlServiceUnavailableException")
    void testPredictRisk_Http503_ModelNotLoaded() {
        mockServer.expect(requestTo("http://localhost:8001/predict-risk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> mlServiceClient.predictRisk("seg-1", new RiskFeaturesDto()))
                .isInstanceOf(MlServiceUnavailableException.class)
                .hasMessageContaining("unready or not loaded");

        mockServer.verify();
    }

    @Test
    @DisplayName("predictRisk handles HTTP 422 by throwing MlServiceValidationException")
    void testPredictRisk_Http422_ValidationError() {
        mockServer.expect(requestTo("http://localhost:8001/predict-risk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThatThrownBy(() -> mlServiceClient.predictRisk("seg-1", new RiskFeaturesDto()))
                .isInstanceOf(MlServiceValidationException.class)
                .hasMessageContaining("ML request rejected");

        mockServer.verify();
    }

    @Test
    @DisplayName("predictRisk handles HTTP 500 by throwing MlServiceException")
    void testPredictRisk_Http500_ServerError() {
        mockServer.expect(requestTo("http://localhost:8001/predict-risk"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> mlServiceClient.predictRisk("seg-1", new RiskFeaturesDto()))
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("ML service HTTP error: 500");

        mockServer.verify();
    }

    @Test
    @DisplayName("predictRisk rejects empty or null segmentId")
    void testPredictRisk_EmptySegmentId() {
        assertThatThrownBy(() -> mlServiceClient.predictRisk("", new RiskFeaturesDto()))
                .isInstanceOf(MlServiceValidationException.class)
                .hasMessageContaining("segment_id cannot be null or empty");

        assertThatThrownBy(() -> mlServiceClient.predictRisk(null, new RiskFeaturesDto()))
                .isInstanceOf(MlServiceValidationException.class)
                .hasMessageContaining("segment_id cannot be null or empty");
    }

    @Test
    @DisplayName("checkHealth correctly parses UP status when models are loaded")
    void testCheckHealth_Healthy() {
        String healthJson = """
                {
                    "status": "UP",
                    "models_loaded": true,
                    "active_model": "xgboost",
                    "model_version": "v1.0.0",
                    "model_trained_at": "2026-10-12T10:00:00",
                    "is_synthetic_demonstration": true,
                    "detail": "Service is running normally with trained models loaded."
                }
                """;

        mockServer.expect(requestTo("http://localhost:8001/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(healthJson, MediaType.APPLICATION_JSON));

        MlHealthResponseDto health = mlServiceClient.checkHealth();

        mockServer.verify();
        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo("UP");
        assertThat(health.getModelsLoaded()).isTrue();
        assertThat(health.getActiveModel()).isEqualTo("xgboost");
        assertThat(health.getModelVersion()).isEqualTo("v1.0.0");
        assertThat(health.getIsSyntheticDemonstration()).isTrue();
    }

    @Test
    @DisplayName("checkHealth returns DEGRADED status when ML service returns 503")
    void testCheckHealth_Degraded503() {
        mockServer.expect(requestTo("http://localhost:8001/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        MlHealthResponseDto health = mlServiceClient.checkHealth();

        mockServer.verify();
        assertThat(health).isNotNull();
        assertThat(health.getStatus()).isEqualTo("DEGRADED");
        assertThat(health.getModelsLoaded()).isFalse();
    }

    @Test
    @DisplayName("explain sends valid request and parses SHAP contributions and baseline")
    void testExplain_Success() {
        RiskFeaturesDto features = RiskFeaturesDto.builder()
                .hourOfDay(23)
                .lightingScore(0.10)
                .build();

        String explainJson = """
                {
                    "segment_id": "seg-night",
                    "raw_model_output": 0.72,
                    "risk_score": 0.72,
                    "safety_score": 0.28,
                    "base_risk": 0.28,
                    "model_type": "xgboost",
                    "model_version": "v1.0.0",
                    "factors": [
                        {
                            "name": "lighting_score",
                            "value": 0.10,
                            "contribution": 0.22,
                            "direction": "increases_risk",
                            "description": "Poor lighting is statistically associated with higher relative risk in training distribution."
                        },
                        {
                            "name": "hour_of_day",
                            "value": 23.0,
                            "contribution": 0.18,
                            "direction": "increases_risk",
                            "description": "Late night hours are statistically associated with higher relative risk in training distribution."
                        }
                    ],
                    "reconstruction_valid": true,
                    "is_synthetic_demonstration": true,
                    "disclaimer": "These explanations reflect statistical attributions.",
                    "causality_warning": "Not physical causation."
                }
                """;

        mockServer.expect(requestTo("http://localhost:8001/explain"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.segment_id").value("seg-night"))
                .andExpect(jsonPath("$.features.hour_of_day").value(23))
                .andExpect(jsonPath("$.features.lighting_score").value(0.10))
                .andRespond(withSuccess(explainJson, MediaType.APPLICATION_JSON));

        MlExplainResponseDto explain = mlServiceClient.explain("seg-night", features);

        mockServer.verify();
        assertThat(explain).isNotNull();
        assertThat(explain.getSegmentId()).isEqualTo("seg-night");
        assertThat(explain.getBaseRisk()).isEqualTo(0.28);
        assertThat(explain.getRiskScore()).isEqualTo(0.72);
        assertThat(explain.getFactors()).hasSize(2);
        assertThat(explain.getFactors().get(0).getName()).isEqualTo("lighting_score");
        assertThat(explain.getFactors().get(0).getDirection()).isEqualTo("increases_risk");
        assertThat(explain.getReconstructionValid()).isTrue();
        assertThat(explain.getIsSyntheticDemonstration()).isTrue();
    }
}

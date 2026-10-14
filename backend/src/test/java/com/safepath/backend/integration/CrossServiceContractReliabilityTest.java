package com.safepath.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.dto.ml.RiskFeaturesDto;
import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.entity.CommunityReport;
import com.safepath.backend.entity.SafeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Milestone 24: Cross-Service Contract and Schema Compatibility Tests.
 *
 * Verifies exact serialization and deserialization contracts between:
 * 1. Spring Boot <-> FastAPI ML Service (15-feature schema, snake_case properties).
 * 2. Spring Boot <-> React Native Mobile Client (camelCase DTOs, GeoJSON geometries).
 * 3. Privacy isolation guards (strict omission of reporter PII).
 * 4. Honest representation of unobserved and fallback metadata.
 */
class CrossServiceContractReliabilityTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("Verify 15-feature ML schema exact field names and units in JSON serialization")
    void testRiskFeaturesDto_Exact15FeatureSchema() throws Exception {
        RiskFeaturesDto features = RiskFeaturesDto.builder()
                .crimeDensity500m(0.45)
                .crimeDensity1km(0.82)
                .severityWeightedDensity(0.35)
                .recentCrimeDensity(0.12)
                .nightCrimeDensity(0.28)
                .hourOfDay(21)
                .dayOfWeek(3)
                .lightingScore(0.8)
                .activityProxy(0.6)
                .nearestPoliceM(1250.0)
                .nearestHospitalM(1800.0)
                .nearestTransportM(320.0)
                .weatherPrecipitation(4.5)  // feature #13: mm
                .weatherVisibility(8.5)      // feature #14: km
                .verifiedReportCount(2)      // feature #15: integer count
                .build();

        String json = objectMapper.writeValueAsString(features);
        JsonNode node = objectMapper.readTree(json);

        // Verify exact snake_case naming matching FastAPI app/services/preprocessor.py FEATURE_NAMES
        assertThat(node.get("crime_density_500m").asDouble()).isEqualTo(0.45);
        assertThat(node.get("crime_density_1km").asDouble()).isEqualTo(0.82);
        assertThat(node.get("severity_weighted_density").asDouble()).isEqualTo(0.35);
        assertThat(node.get("recent_crime_density").asDouble()).isEqualTo(0.12);
        assertThat(node.get("night_crime_density").asDouble()).isEqualTo(0.28);
        assertThat(node.get("hour_of_day").asInt()).isEqualTo(21);
        assertThat(node.get("day_of_week").asInt()).isEqualTo(3);
        assertThat(node.get("lighting_score").asDouble()).isEqualTo(0.8);
        assertThat(node.get("activity_proxy").asDouble()).isEqualTo(0.6);
        assertThat(node.get("nearest_police_m").asDouble()).isEqualTo(1250.0);
        assertThat(node.get("nearest_hospital_m").asDouble()).isEqualTo(1800.0);
        assertThat(node.get("nearest_transport_m").asDouble()).isEqualTo(320.0);
        assertThat(node.get("weather_precipitation").asDouble()).isEqualTo(4.5);
        assertThat(node.get("weather_visibility").asDouble()).isEqualTo(8.5);
        assertThat(node.get("verified_report_count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Verify unobserved ML features serialize as null or omit without fabricating zeros")
    void testRiskFeaturesDto_PreservesNullsForImputation() throws Exception {
        RiskFeaturesDto sparseFeatures = RiskFeaturesDto.builder()
                .hourOfDay(12)
                .dayOfWeek(0)
                .build();

        String json = objectMapper.writeValueAsString(sparseFeatures);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("hour_of_day").asInt()).isEqualTo(12);
        assertThat(node.get("day_of_week").asInt()).isEqualTo(0);
        // Unobserved features must serialize as null (not fabricated 0.0) so FastAPI tracks imputation
        assertThat(node.get("weather_precipitation").isNull()).isTrue();
        assertThat(node.get("weather_visibility").isNull()).isTrue();
        assertThat(node.get("lighting_score").isNull()).isTrue();
        assertThat(node.get("crime_density_500m").isNull()).isTrue();
    }

    @Test
    @DisplayName("Verify FastAPI PredictRiskResponseDto deserialization with calibration metrics")
    void testPredictRiskResponseDto_Deserialization() throws Exception {
        String fastApiResponseJson = """
            {
                "segment_id": "seg-node-101-102",
                "raw_model_output": 0.3214,
                "risk_score": 0.2845,
                "safety_score": 0.7155,
                "risk_level": "LOW",
                "is_synthetic_demonstration": true,
                "model_version": "xgboost-v1.0.0",
                "feature_completeness": 0.867,
                "imputed_features": ["weather_precipitation", "weather_visibility"],
                "prediction_timestamp": "2026-10-14T14:00:00Z"
            }
            """;

        PredictRiskResponseDto dto = objectMapper.readValue(fastApiResponseJson, PredictRiskResponseDto.class);

        assertThat(dto.getSegmentId()).isEqualTo("seg-node-101-102");
        assertThat(dto.getRiskScore()).isEqualTo(0.2845);
        assertThat(dto.getSafetyScore()).isEqualTo(0.7155);
        assertThat(dto.getRiskLevel()).isEqualTo("LOW");
        assertThat(dto.getIsSyntheticDemonstration()).isTrue();
        assertThat(dto.getModelVersion()).isEqualTo("xgboost-v1.0.0");
        assertThat(dto.getFeatureCompleteness()).isEqualTo(0.867);
        assertThat(dto.getImputedFeatures()).containsExactly("weather_precipitation", "weather_visibility");
    }

    @Test
    @DisplayName("Verify RouteDto.SearchResponse contract matching mobile RouteSearchResponse interface")
    void testRouteSearchResponse_MobileContractCompatibility() throws Exception {
        RouteDto.RouteResult fastest = RouteDto.RouteResult.builder()
                .routeMode("FASTEST")
                .distanceMeters(1420.5)
                .durationSeconds(1020.0)
                .safetyScore(null)
                .riskLevel("UNSCORED")
                .safetyLabel("Unscored (ML independent)")
                .coordinates(List.of(
                        RouteDto.CoordinateDto.builder().latitude(12.9716).longitude(77.5946).build(),
                        RouteDto.CoordinateDto.builder().latitude(12.9750).longitude(77.5980).build()
                ))
                .geometry(Map.of("type", "LineString", "coordinates", List.of(List.of(77.5946, 12.9716), List.of(77.5980, 12.9750))))
                .isDistinct(true)
                .build();

        RouteDto.RouteResult safest = RouteDto.RouteResult.builder()
                .routeMode("SAFEST")
                .distanceMeters(1650.0)
                .durationSeconds(1200.0)
                .safetyScore(78.5)
                .riskLevel("LOW")
                .safetyLabel("Safe")
                .coordinates(List.of(
                        RouteDto.CoordinateDto.builder().latitude(12.9716).longitude(77.5946).build(),
                        RouteDto.CoordinateDto.builder().latitude(12.9780).longitude(77.6010).build()
                ))
                .isDistinct(true)
                .build();

        RouteDto.SearchResponse response = RouteDto.SearchResponse.builder()
                .requestId("req-12345")
                .fastestRoute(fastest)
                .balancedRoute(fastest) // e.g. Coincides
                .safestRoute(safest)
                .hasDistinctAlternatives(true)
                .distinctRouteCount(2)
                .mlServiceAvailable(true)
                .weatherContextAvailable(true)
                .tradeOffExplanation("Safest route adds 180s walking duration but reduces exposure to unlit corridors.")
                .computedAt(OffsetDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        // Verify top-level mobile fields
        assertThat(node.has("requestId")).isTrue();
        assertThat(node.has("fastestRoute")).isTrue();
        assertThat(node.has("balancedRoute")).isTrue();
        assertThat(node.has("safestRoute")).isTrue();
        assertThat(node.has("hasDistinctAlternatives")).isTrue();
        assertThat(node.has("distinctRouteCount")).isTrue();
        assertThat(node.has("mlServiceAvailable")).isTrue();
        assertThat(node.has("weatherContextAvailable")).isTrue();
        assertThat(node.has("tradeOffExplanation")).isTrue();

        // Verify RouteResult fields
        JsonNode safestNode = node.get("safestRoute");
        assertThat(safestNode.get("routeMode").asText()).isEqualTo("SAFEST");
        assertThat(safestNode.get("safetyScore").asDouble()).isEqualTo(78.5);
        assertThat(safestNode.get("riskLevel").asText()).isEqualTo("LOW");
        assertThat(safestNode.get("safetyLabel").asText()).isEqualTo("Safe");
        assertThat(safestNode.get("isDistinct").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Verify WeatherDto strictly separates current observation from forecast context")
    void testWeatherDto_SeparationOfObservationAndForecast() throws Exception {
        WeatherDto dto = WeatherDto.builder()
                .latitude(12.9716)
                .longitude(77.5946)
                .available(true)
                .observation(WeatherDto.CurrentObservation.builder()
                        .temperatureC(26.4)
                        .precipitationMm(0.0)
                        .windSpeedKmh(12.1)
                        .relativeHumidity(65.0)
                        .visibilityKm(10.0)
                        .condition("Clear sky")
                        .icon("weather-sunny")
                        .isDay(true)
                        .observedAt("2026-10-14T14:00")
                        .build())
                .forecast(WeatherDto.ForecastContext.builder()
                        .precipitationProbabilityPercent(15.0)
                        .forecastTimestamp("2026-10-14T15:00")
                        .build())
                .units(WeatherDto.WeatherUnits.builder().build())
                .source("Open-Meteo (open-meteo.com)")
                .cached(false)
                .disclaimer("Environmental context feature. Not causal evidence of crime.")
                .build();

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("available").asBoolean()).isTrue();
        // Observation object contains current observed metrics
        JsonNode obs = node.get("observation");
        assertThat(obs.get("temperatureC").asDouble()).isEqualTo(26.4);
        assertThat(obs.get("precipitationMm").asDouble()).isEqualTo(0.0);
        assertThat(obs.get("visibilityKm").asDouble()).isEqualTo(10.0);
        assertThat(obs.get("condition").asText()).isEqualTo("Clear sky");

        // Forecast object contains distinct prediction context
        JsonNode fc = node.get("forecast");
        assertThat(fc.get("precipitationProbabilityPercent").asDouble()).isEqualTo(15.0);
        assertThat(fc.get("forecastTimestamp").asText()).isEqualTo("2026-10-14T15:00");

        // Units and disclaimer
        assertThat(node.get("units").get("temperature").asText()).isEqualTo("°C");
        assertThat(node.get("units").get("precipitation").asText()).isEqualTo("mm");
        assertThat(node.get("units").get("visibility").asText()).isEqualTo("km");
        assertThat(node.get("disclaimer").asText()).contains("Not causal evidence");
    }

    @Test
    @DisplayName("Verify CommunityReportDto.Response strictly excludes reporter personal identity (PII)")
    void testCommunityReportDto_StrictPrivacyGuards() throws Exception {
        CommunityReportDto.Response publicReport = CommunityReportDto.Response.builder()
                .id(UUID.randomUUID())
                .reportType(CommunityReport.ReportType.POOR_LIGHTING)
                .description("Broken street lamp near bus stop")
                .latitude(12.975)
                .longitude(77.598)
                .locationPrecision(CommunityReport.LocationPrecision.EXACT_COORDINATE)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .distanceMeters(145.2)
                .incidentDatetime(OffsetDateTime.now().minusHours(2))
                .createdAt(OffsetDateTime.now().minusHours(1))
                .build();

        String json = objectMapper.writeValueAsString(publicReport);
        JsonNode node = objectMapper.readTree(json);

        // Required public fields
        assertThat(node.has("id")).isTrue();
        assertThat(node.has("reportType")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("locationPrecision")).isTrue();
        assertThat(node.get("status").asText()).isEqualTo("VERIFIED");

        // Strict PII isolation checks: must NEVER expose user details in response
        assertThat(node.has("userId")).isFalse();
        assertThat(node.has("user_id")).isFalse();
        assertThat(node.has("user")).isFalse();
        assertThat(node.has("email")).isFalse();
        assertThat(node.has("author")).isFalse();
        assertThat(node.has("reporterName")).isFalse();
        assertThat(node.has("phoneNumber")).isFalse();
    }

    @Test
    @DisplayName("Verify SafeZoneDto serialization with facility metadata and verification status")
    void testSafeZoneDto_Serialization() throws Exception {
        SafeZoneDto dto = SafeZoneDto.builder()
                .id(UUID.randomUUID())
                .name("Cubbon Park Police Station")
                .zoneType(SafeZone.ZoneType.POLICE_STATION)
                .latitude(12.978)
                .longitude(77.596)
                .address("Kasturba Road, Bangalore")
                .phoneNumber("080-22942555")
                .is24h(true)
                .verificationStatus(SafeZone.VerificationStatus.VERIFIED)
                .source("BANGALORE_OPEN_CITY_DIRECTORY")
                .distanceMeters(230.5)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("name").asText()).isEqualTo("Cubbon Park Police Station");
        assertThat(node.get("zoneType").asText()).isEqualTo("POLICE_STATION");
        assertThat(node.get("latitude").asDouble()).isEqualTo(12.978);
        assertThat(node.get("longitude").asDouble()).isEqualTo(77.596);
        assertThat(node.get("is24h").asBoolean()).isTrue();
        assertThat(node.get("verificationStatus").asText()).isEqualTo("VERIFIED");
        assertThat(node.get("source").asText()).isEqualTo("BANGALORE_OPEN_CITY_DIRECTORY");
        assertThat(node.get("distanceMeters").asDouble()).isEqualTo(230.5);
    }

    @Test
    @DisplayName("Verify HeatmapDto.Response GeoJSON and scoringStatus serialization")
    void testHeatmapDto_Serialization() throws Exception {
        HeatmapDto.SegmentRisk segmentRisk = HeatmapDto.SegmentRisk.builder()
                .segmentId("seg-101")
                .roadName("Brigade Road")
                .roadType("primary")
                .isLit(true)
                .lengthMeters(250.0)
                .riskScore(0.35)
                .safetyScore(65.0)
                .riskLevel("LOW")
                .scoringStatus("SCORED")
                .colorHex("#34C759")
                .geometry(Map.of("type", "LineString", "coordinates", List.of(List.of(77.60, 12.97), List.of(77.61, 12.98))))
                .build();

        HeatmapDto.Response response = HeatmapDto.Response.builder()
                .boundingBox("12.96,77.58,12.98,77.60")
                .totalSegments(1)
                .scoredSegments(1)
                .unscoredSegments(0)
                .mlServiceAvailable(true)
                .segments(List.of(segmentRisk))
                .disclaimer("Heatmap displays statistical relative risk. Not real-time crime warning.")
                .build();

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("totalSegments").asInt()).isEqualTo(1);
        assertThat(node.get("scoredSegments").asInt()).isEqualTo(1);
        assertThat(node.get("mlServiceAvailable").asBoolean()).isTrue();

        JsonNode segNode = node.get("segments").get(0);
        assertThat(segNode.get("roadName").asText()).isEqualTo("Brigade Road");
        assertThat(segNode.get("scoringStatus").asText()).isEqualTo("SCORED");
        assertThat(segNode.get("colorHex").asText()).isEqualTo("#34C759");
        assertThat(segNode.get("geometry").get("type").asText()).isEqualTo("LineString");
    }
}

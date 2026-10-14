package com.safepath.backend.service.ml;

import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.RiskFeaturesDto;
import com.safepath.backend.entity.RoadSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RoadSegmentFeatureServiceTest {

    private RoadSegmentFeatureService featureService;

    @BeforeEach
    void setUp() {
        featureService = new RoadSegmentFeatureService();
    }

    @Test
    @DisplayName("extractFeatures derives correct temporal, lighting, and activity features")
    void testExtractFeatures_Complete() {
        UUID segmentId = UUID.randomUUID();
        RoadSegment segment = new RoadSegment();
        segment.setId(segmentId);
        segment.setRoadType("primary");
        segment.setIsLit(true);
        segment.setStartNodeId(100L);
        segment.setEndNodeId(200L);

        // Friday 2026-10-16 at 22:30 (Friday is index 4 in Python 0-6 convention)
        LocalDateTime evaluationTime = LocalDateTime.of(2026, 10, 16, 22, 30);

        RiskFeaturesDto features = featureService.extractFeatures(segment, evaluationTime);

        assertThat(features).isNotNull();
        assertThat(features.getHourOfDay()).isEqualTo(22);
        assertThat(features.getDayOfWeek()).isEqualTo(4); // Monday=0, Friday=4
        assertThat(features.getLightingScore()).isEqualTo(1.0);
        assertThat(features.getActivityProxy()).isEqualTo(0.8);
    }

    @Test
    @DisplayName("extractFeatures handles unlit segments and pedestrian road types")
    void testExtractFeatures_UnlitAndPedestrian() {
        RoadSegment segment = new RoadSegment();
        segment.setRoadType("footway");
        segment.setIsLit(false);

        // Sunday 2026-10-18 at 03:00 (Sunday is index 6)
        LocalDateTime evaluationTime = LocalDateTime.of(2026, 10, 18, 3, 0);

        RiskFeaturesDto features = featureService.extractFeatures(segment, evaluationTime);

        assertThat(features).isNotNull();
        assertThat(features.getHourOfDay()).isEqualTo(3);
        assertThat(features.getDayOfWeek()).isEqualTo(6); // Sunday=6
        assertThat(features.getLightingScore()).isEqualTo(0.0);
        assertThat(features.getActivityProxy()).isEqualTo(0.3);
    }

    @Test
    @DisplayName("extractFeatures preserves nulls for missing attributes to allow ML service imputation")
    void testExtractFeatures_MissingAttributes() {
        RoadSegment segment = new RoadSegment();
        segment.setRoadType(null);
        segment.setIsLit(null);

        RiskFeaturesDto features = featureService.extractFeatures(segment, LocalDateTime.now());

        assertThat(features).isNotNull();
        assertThat(features.getLightingScore()).isNull();
        assertThat(features.getActivityProxy()).isNull();
        assertThat(features.getCrimeDensity500m()).isNull();
    }

    @Test
    @DisplayName("resolveSegmentId returns entity UUID when present, or deterministic node-based ID")
    void testResolveSegmentId() {
        UUID uuid = UUID.randomUUID();
        RoadSegment segmentWithId = new RoadSegment();
        segmentWithId.setId(uuid);

        RoadSegment segmentWithoutId = new RoadSegment();
        segmentWithoutId.setStartNodeId(50L);
        segmentWithoutId.setEndNodeId(60L);

        assertThat(featureService.resolveSegmentId(segmentWithId)).isEqualTo(uuid.toString());
        assertThat(featureService.resolveSegmentId(segmentWithoutId)).isEqualTo("node-50-60");
    }

    @Test
    @DisplayName("prepareBatchRequests builds valid request list for road segments")
    void testPrepareBatchRequests() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        RoadSegment seg1 = new RoadSegment();
        seg1.setId(id1);
        seg1.setRoadType("secondary");
        seg1.setIsLit(true);

        RoadSegment seg2 = new RoadSegment();
        seg2.setId(id2);
        seg2.setRoadType("residential");
        seg2.setIsLit(false);

        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 14, 0);
        List<PredictRiskRequestDto> requests = featureService.prepareBatchRequests(List.of(seg1, seg2), now);

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getSegmentId()).isEqualTo(id1.toString());
        assertThat(requests.get(0).getFeatures().getActivityProxy()).isEqualTo(0.6);
        assertThat(requests.get(0).getFeatures().getLightingScore()).isEqualTo(1.0);
        assertThat(requests.get(1).getSegmentId()).isEqualTo(id2.toString());
        assertThat(requests.get(1).getFeatures().getActivityProxy()).isEqualTo(0.4);
        assertThat(requests.get(1).getFeatures().getLightingScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("extractFeatures populates weather precipitation and visibility when observed")
    void testExtractFeatures_WithWeatherContext() {
        RoadSegment segment = new RoadSegment();
        segment.setId(UUID.randomUUID());
        segment.setRoadType("primary");
        segment.setIsLit(true);

        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 15, 30);
        RiskFeaturesDto features = featureService.extractFeatures(segment, now, 2.5, 8.0);

        assertThat(features).isNotNull();
        assertThat(features.getWeatherPrecipitation()).isEqualTo(2.5);
        assertThat(features.getWeatherVisibility()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("extractFeatures and prepareBatchRequests leave weather features null when weather is unobserved")
    void testExtractFeatures_UnobservedWeatherLeavesNull() {
        RoadSegment segment = new RoadSegment();
        segment.setId(UUID.randomUUID());

        RiskFeaturesDto features = featureService.extractFeatures(segment, LocalDateTime.now(), null, null);
        assertThat(features.getWeatherPrecipitation()).isNull();
        assertThat(features.getWeatherVisibility()).isNull();

        List<PredictRiskRequestDto> requests = featureService.prepareBatchRequests(
                List.of(segment), LocalDateTime.now(), null, null);
        assertThat(requests.get(0).getFeatures().getWeatherPrecipitation()).isNull();
        assertThat(requests.get(0).getFeatures().getWeatherVisibility()).isNull();
    }
}

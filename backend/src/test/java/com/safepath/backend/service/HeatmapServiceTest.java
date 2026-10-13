package com.safepath.backend.service;

import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.repository.RoadSegmentRepository;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.RoadSegmentFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for Milestone 20: Safety Heatmap Service.
 *
 * Verifies:
 * 1. Geographic bounding box validation (bounds, ordering, max span limits).
 * 2. Proper geometry serialization and color mapping for scored segments.
 * 3. Graceful degradation when ML service is offline (UNAVAILABLE / UNSCORED, never fabricated).
 * 4. Synthetic demonstration flag propagation and non-probabilistic disclaimers.
 * 5. Segment limit clamping.
 */
@ExtendWith(MockitoExtension.class)
class HeatmapServiceTest {

    @Mock
    private RoadSegmentRepository roadSegmentRepository;

    @Mock
    private RoadSegmentFeatureService roadSegmentFeatureService;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private GeometryFactory geometryFactory;

    @InjectMocks
    private HeatmapService heatmapService;

    private RoadSegment testSegment;
    private Polygon mockPolygon;

    @BeforeEach
    void setUp() {
        GeometryFactory realGf = new GeometryFactory();
        testSegment = new RoadSegment();
        testSegment.setId(UUID.randomUUID());
        testSegment.setRoadName("Church Street");
        testSegment.setRoadType("pedestrian");
        testSegment.setLengthMeters(250.0);
        testSegment.setIsLit(true);
        testSegment.setWalkable(true);

        LineString line = realGf.createLineString(new Coordinate[]{
                new Coordinate(77.6000, 12.9700),
                new Coordinate(77.6025, 12.9725)
        });
        testSegment.setGeometry(line);

        Coordinate[] ring = new Coordinate[]{
                new Coordinate(77.59, 12.96),
                new Coordinate(77.61, 12.96),
                new Coordinate(77.61, 12.98),
                new Coordinate(77.59, 12.98),
                new Coordinate(77.59, 12.96)
        };
        mockPolygon = realGf.createPolygon(ring);
        lenient().when(geometryFactory.createPolygon(any(Coordinate[].class))).thenReturn(mockPolygon);
    }

    @Test
    @DisplayName("Bounding box validation rejects null coordinates")
    void testValidateBoundingBox_RejectsNull() {
        assertThatThrownBy(() -> heatmapService.validateBoundingBox(null, 77.0, 13.0, 77.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box parameters");
    }

    @Test
    @DisplayName("Bounding box validation rejects out-of-range coordinates")
    void testValidateBoundingBox_RejectsOutOfRange() {
        assertThatThrownBy(() -> heatmapService.validateBoundingBox(-95.0, 77.0, 13.0, 77.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude coordinates must be between -90.0 and 90.0");

        assertThatThrownBy(() -> heatmapService.validateBoundingBox(12.0, -185.0, 13.0, 77.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude coordinates must be between -180.0 and 180.0");
    }

    @Test
    @DisplayName("Bounding box validation rejects inverted bounds")
    void testValidateBoundingBox_RejectsInvertedBounds() {
        assertThatThrownBy(() -> heatmapService.validateBoundingBox(13.0, 77.0, 12.0, 77.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be greater than maxLat");

        assertThatThrownBy(() -> heatmapService.validateBoundingBox(12.0, 78.0, 13.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be greater than maxLon");
    }

    @Test
    @DisplayName("Bounding box validation rejects spans exceeding 0.15 degrees")
    void testValidateBoundingBox_RejectsOversizedSpan() {
        // Lat span = 0.20 degrees (> 0.15)
        assertThatThrownBy(() -> heatmapService.validateBoundingBox(12.0, 77.0, 12.20, 77.05))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box span is too large");

        // Lon span = 0.20 degrees (> 0.15)
        assertThatThrownBy(() -> heatmapService.validateBoundingBox(12.0, 77.0, 12.05, 77.20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box span is too large");
    }

    @Test
    @DisplayName("getHeatmap returns scored segments with valid geometry, risk levels, and color mapping")
    void testGetHeatmap_ScoredSegments_PropagatesPredictionsAndColors() {
        when(roadSegmentRepository.findSegmentsInBoundingBox(any(), anyInt()))
                .thenReturn(List.of(testSegment));

        String segKey = testSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(testSegment)).thenReturn(segKey);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId(segKey).build()));

        PredictRiskResponseDto prediction = PredictRiskResponseDto.builder()
                .segmentId(segKey)
                .riskScore(0.15)
                .safetyScore(0.85) // fraction in [0, 1]
                .riskLevel("VERY_LOW")
                .modelVersion("xgb-v1.0")
                .isSyntheticDemonstration(true)
                .build();

        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(Map.of(segKey, prediction));

        HeatmapDto.Response response = heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, 100);

        assertThat(response).isNotNull();
        assertThat(response.getTotalSegments()).isEqualTo(1);
        assertThat(response.getScoredSegments()).isEqualTo(1);
        assertThat(response.getUnscoredSegments()).isEqualTo(0);
        assertThat(response.isMlServiceAvailable()).isTrue();
        assertThat(response.getIsSyntheticDemonstration()).isTrue();
        assertThat(response.getModelVersion()).isEqualTo("xgb-v1.0");
        assertThat(response.getDisclaimer()).contains("NOT represent verified physical probabilities");

        HeatmapDto.SegmentRisk segRisk = response.getSegments().get(0);
        assertThat(segRisk.getSegmentId()).isEqualTo(segKey);
        assertThat(segRisk.getRoadName()).isEqualTo("Church Street");
        assertThat(segRisk.getScoringStatus()).isEqualTo("SCORED");
        assertThat(segRisk.getRiskScore()).isEqualTo(0.15);
        assertThat(segRisk.getSafetyScore()).isEqualTo(85.0);
        assertThat(segRisk.getRiskLevel()).isEqualTo("VERY_LOW");
        assertThat(segRisk.getColorHex()).isEqualTo("#34C759"); // Green
        assertThat(segRisk.getCoordinates()).hasSize(2);
        assertThat(segRisk.getNotes()).contains("synthetic demonstration ML model");
    }

    @Test
    @DisplayName("getHeatmap when ML service is down degrades gracefully to UNAVAILABLE without fabricating safe scores")
    void testGetHeatmap_MlServiceUnavailable_DegradesGracefullyWithoutFabrication() {
        when(roadSegmentRepository.findSegmentsInBoundingBox(any(), anyInt()))
                .thenReturn(List.of(testSegment));

        String segKey = testSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(testSegment)).thenReturn(segKey);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId(segKey).build()));

        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new MlServiceUnavailableException("FastAPI ML service connection refused"));

        HeatmapDto.Response response = heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, 50);

        assertThat(response).isNotNull();
        assertThat(response.getTotalSegments()).isEqualTo(1);
        assertThat(response.getScoredSegments()).isEqualTo(0);
        assertThat(response.getUnscoredSegments()).isEqualTo(1);
        assertThat(response.isMlServiceAvailable()).isFalse();

        HeatmapDto.SegmentRisk segRisk = response.getSegments().get(0);
        assertThat(segRisk.getScoringStatus()).isEqualTo("UNAVAILABLE");
        assertThat(segRisk.getRiskScore()).isNull(); // Never fabricated
        assertThat(segRisk.getSafetyScore()).isNull(); // Never fabricated
        assertThat(segRisk.getRiskLevel()).isEqualTo("UNSCORED");
        assertThat(segRisk.getColorHex()).isEqualTo("#8E8E93"); // Slate gray
        assertThat(segRisk.getNotes()).contains("ML service offline");
    }

    @Test
    @DisplayName("getHeatmap returns empty response when no road segments exist in bounding box")
    void testGetHeatmap_EmptyRoadNetwork_ReturnsEmptyResponse() {
        when(roadSegmentRepository.findSegmentsInBoundingBox(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        HeatmapDto.Response response = heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, 100);

        assertThat(response).isNotNull();
        assertThat(response.getTotalSegments()).isEqualTo(0);
        assertThat(response.getSegments()).isEmpty();
        verifyNoInteractions(mlServiceClient);
    }

    @Test
    @DisplayName("getHeatmap clamps limit parameter to MAX_SEGMENT_LIMIT (1000)")
    void testGetHeatmap_ClampsOversizedLimit() {
        when(roadSegmentRepository.findSegmentsInBoundingBox(any(), eq(1000)))
                .thenReturn(Collections.emptyList());

        HeatmapDto.Response response = heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, 5000);

        assertThat(response).isNotNull();
        verify(roadSegmentRepository).findSegmentsInBoundingBox(any(), eq(1000));
    }

    @Test
    @DisplayName("deriveRiskCategory and mapRiskToColor map score thresholds correctly")
    void testRiskCategoryAndColorMappings() {
        // VERY_LOW (safety >= 0.8) -> #34C759
        assertThat(heatmapService.deriveRiskCategory(0.85)).isEqualTo("VERY_LOW");
        assertThat(heatmapService.mapRiskToColor("VERY_LOW")).isEqualTo("#34C759");

        // LOW (0.6 <= safety < 0.8) -> #34C759
        assertThat(heatmapService.deriveRiskCategory(0.70)).isEqualTo("LOW");
        assertThat(heatmapService.mapRiskToColor("LOW")).isEqualTo("#34C759");

        // MODERATE (0.4 <= safety < 0.6) -> #FF9500
        assertThat(heatmapService.deriveRiskCategory(0.50)).isEqualTo("MODERATE");
        assertThat(heatmapService.mapRiskToColor("MODERATE")).isEqualTo("#FF9500");

        // HIGH (0.2 <= safety < 0.4) -> #FF3B30
        assertThat(heatmapService.deriveRiskCategory(0.30)).isEqualTo("HIGH");
        assertThat(heatmapService.mapRiskToColor("HIGH")).isEqualTo("#FF3B30");

        // VERY_HIGH (safety < 0.2) -> #D70015
        assertThat(heatmapService.deriveRiskCategory(0.10)).isEqualTo("VERY_HIGH");
        assertThat(heatmapService.mapRiskToColor("VERY_HIGH")).isEqualTo("#D70015");

        // UNSCORED (null) -> #8E8E93
        assertThat(heatmapService.deriveRiskCategory(null)).isEqualTo("UNSCORED");
        assertThat(heatmapService.mapRiskToColor("UNSCORED")).isEqualTo("#8E8E93");
    }
}

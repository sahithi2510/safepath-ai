package com.safepath.backend.service;

import com.safepath.backend.dto.RouteRequestDto;
import com.safepath.backend.dto.RouteResponseDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.entity.*;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.repository.RoadSegmentRepository;
import com.safepath.backend.repository.RouteRequestRepository;
import com.safepath.backend.repository.RouteResultRepository;
import com.safepath.backend.repository.UserRepository;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.RoadSegmentFeatureService;
import com.safepath.backend.service.routing.AStarRoutingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceMlIntegrationTest {

    @Mock
    private RouteRequestRepository routeRequestRepository;

    @Mock
    private RouteResultRepository routeResultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoadSegmentRepository roadSegmentRepository;

    @Mock
    private GeometryFactory geometryFactory;

    @Mock
    private AStarRoutingEngine aStarRoutingEngine;

    @Mock
    private MlServiceClient mlServiceClient;

    @Mock
    private RoadSegmentFeatureService roadSegmentFeatureService;

    @InjectMocks
    private RouteService routeService;

    private User testUser;
    private RoadSegment testSegment;
    private Point startPoint;
    private Point endPoint;
    private LineString testLineString;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("user@example.com");

        testSegment = new RoadSegment();
        testSegment.setId(UUID.randomUUID());
        testSegment.setStartNodeId(10L);
        testSegment.setEndNodeId(20L);
        testSegment.setLengthMeters(200.0);
        testSegment.setEstimatedWalkTimeS(140.0);
        testSegment.setIsOneway(false);

        Coordinate[] coords = new Coordinate[]{
                new Coordinate(77.5946, 12.9716),
                new Coordinate(77.5950, 12.9720)
        };
        GeometryFactory realGf = new GeometryFactory();
        startPoint = realGf.createPoint(coords[0]);
        endPoint = realGf.createPoint(coords[1]);
        testLineString = realGf.createLineString(coords);
        testSegment.setGeometry(testLineString);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(testUser));
        when(geometryFactory.createPoint(any(Coordinate.class))).thenReturn(startPoint, endPoint);
        when(routeRequestRepository.save(any(RouteRequest.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(routeResultRepository.save(any(RouteResult.class))).thenAnswer(i -> {
            RouteResult r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    @Test
    @DisplayName("planRoute with SAFEST preference calculates length-weighted safety score and propagates ML metadata")
    void testPlanRoute_Safest_MlScoredSuccessfully() {
        RouteRequestDto requestDto = RouteRequestDto.builder()
                .startLon(77.5946)
                .startLat(12.9716)
                .endLon(77.5950)
                .endLat(12.9720)
                .preference(RoutePreference.SAFEST)
                .build();

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble()))
                .thenReturn(List.of(testSegment));
        when(roadSegmentRepository.findNearestSegment(any())).thenReturn(testSegment);

        String segIdStr = testSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(testSegment)).thenReturn(segIdStr);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId(segIdStr).build()));

        PredictRiskResponseDto prediction = PredictRiskResponseDto.builder()
                .segmentId(segIdStr)
                .riskScore(0.15)
                .safetyScore(0.85) // fraction in [0.0, 1.0]
                .riskLevel("VERY_LOW")
                .modelVersion("xgb-v1.0")
                .isSyntheticDemonstration(true)
                .build();

        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(Map.of(segIdStr, prediction));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.SAFEST), anyMap(), anyMap()))
                .thenReturn(List.of(testSegment));
        when(geometryFactory.createLineString(any(Coordinate[].class))).thenReturn(testLineString);

        RouteResponseDto response = routeService.planRoute("user@example.com", requestDto);

        assertThat(response).isNotNull();
        assertThat(response.getIsMlScored()).isTrue();
        assertThat(response.getIsSyntheticDemonstration()).isTrue();
        assertThat(response.getModelVersion()).isEqualTo("xgb-v1.0");
        assertThat(response.getSafetyScore()).isEqualTo(85.0); // 0.85 * 100
        assertThat(response.getRiskLevel()).isEqualTo("VERY_LOW");
        assertThat(response.getNotes()).contains("Scored using ML model xgb-v1.0");
    }

    @Test
    @DisplayName("planRoute with SAFEST preference throws MlServiceUnavailableException when ML is down (never claims safe)")
    void testPlanRoute_Safest_ThrowsWhenMlUnavailable() {
        RouteRequestDto requestDto = RouteRequestDto.builder()
                .startLon(77.5946)
                .startLat(12.9716)
                .endLon(77.5950)
                .endLat(12.9720)
                .preference(RoutePreference.SAFEST)
                .build();

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble()))
                .thenReturn(List.of(testSegment));
        when(roadSegmentRepository.findNearestSegment(any())).thenReturn(testSegment);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId("seg-1").build()));

        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new MlServiceUnavailableException("FastAPI ML service connection refused"));

        assertThatThrownBy(() -> routeService.planRoute("user@example.com", requestDto))
                .isInstanceOf(MlServiceUnavailableException.class)
                .hasMessageContaining("connection refused");

        verify(aStarRoutingEngine, never()).findRoute(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("planRoute with FASTEST preference succeeds independently when ML service is down")
    void testPlanRoute_Fastest_SucceedsWhenMlUnavailable() {
        RouteRequestDto requestDto = RouteRequestDto.builder()
                .startLon(77.5946)
                .startLat(12.9716)
                .endLon(77.5950)
                .endLat(12.9720)
                .preference(RoutePreference.FASTEST)
                .build();

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble()))
                .thenReturn(List.of(testSegment));
        when(roadSegmentRepository.findNearestSegment(any())).thenReturn(testSegment);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId("seg-1").build()));

        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new MlServiceUnavailableException("FastAPI ML service connection timed out"));

        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.FASTEST), anyMap(), anyMap()))
                .thenReturn(List.of(testSegment));
        when(geometryFactory.createLineString(any(Coordinate[].class))).thenReturn(testLineString);

        RouteResponseDto response = routeService.planRoute("user@example.com", requestDto);

        assertThat(response).isNotNull();
        assertThat(response.getIsMlScored()).isFalse();
        assertThat(response.getSafetyScore()).isNull();
        assertThat(response.getRiskLevel()).isEqualTo("UNSCORED");
        assertThat(response.getNotes()).contains("FASTEST route prioritized travel time independently of risk scoring");
    }

    @Test
    @DisplayName("planRoute with BALANCED preference falls back cleanly without claiming safety when ML is down")
    void testPlanRoute_Balanced_FallsBackWhenMlUnavailable() {
        RouteRequestDto requestDto = RouteRequestDto.builder()
                .startLon(77.5946)
                .startLat(12.9716)
                .endLon(77.5950)
                .endLat(12.9720)
                .preference(RoutePreference.BALANCED)
                .build();

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble()))
                .thenReturn(List.of(testSegment));
        when(roadSegmentRepository.findNearestSegment(any())).thenReturn(testSegment);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any()))
                .thenReturn(List.of(PredictRiskRequestDto.builder().segmentId("seg-1").build()));

        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new MlServiceUnavailableException("ML service 503 Service Unavailable"));

        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.BALANCED), anyMap(), anyMap()))
                .thenReturn(List.of(testSegment));
        when(geometryFactory.createLineString(any(Coordinate[].class))).thenReturn(testLineString);

        RouteResponseDto response = routeService.planRoute("user@example.com", requestDto);

        assertThat(response).isNotNull();
        assertThat(response.getIsMlScored()).isFalse();
        assertThat(response.getSafetyScore()).isNull();
        assertThat(response.getRiskLevel()).isEqualTo("UNSCORED");
        assertThat(response.getNotes()).contains("ML risk predictions unavailable; route generated without safety scoring");
    }
}

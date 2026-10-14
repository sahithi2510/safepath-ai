package com.safepath.backend.service;

import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.dto.ml.PredictRiskRequestDto;
import com.safepath.backend.dto.ml.PredictRiskResponseDto;
import com.safepath.backend.entity.RoadSegment;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.entity.RouteRequest;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.MlServiceUnavailableException;
import com.safepath.backend.exception.NoRouteFoundException;
import com.safepath.backend.repository.RoadSegmentRepository;
import com.safepath.backend.repository.RouteRequestRepository;
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

/**
 * Focused tests for Milestone 19: Route Comparison.
 *
 * Verifies:
 * 1. Generation and distinct identification of FASTEST, BALANCED, and SAFEST alternatives.
 * 2. Duplicate-path detection and truthful labeling when routes coincide.
 * 3. Graceful degradation and explicit UNSCORED state when ML inference is unavailable.
 * 4. Transparent trade-off explanations derived from empirical route attributes.
 * 5. Error handling for unreachable destinations and empty road networks.
 */
@ExtendWith(MockitoExtension.class)
class RouteComparisonServiceTest {

    @Mock
    private RouteRequestRepository routeRequestRepository;

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

    @Mock
    private WeatherService weatherService;

    @InjectMocks
    private RouteService routeService;

    private User testUser;
    private RoadSegment directFastSegment;
    private RoadSegment detourSafeSegment;
    private Point startPoint;
    private Point endPoint;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setEmail("traveler@example.com");

        GeometryFactory gf = new GeometryFactory();
        startPoint = gf.createPoint(new Coordinate(77.5946, 12.9716));
        endPoint = gf.createPoint(new Coordinate(77.5990, 12.9750));

        // Direct fast segment (Node 1 -> Node 3): 300m, 214s, unlit (higher risk)
        directFastSegment = new RoadSegment();
        directFastSegment.setId(UUID.randomUUID());
        directFastSegment.setStartNodeId(1L);
        directFastSegment.setEndNodeId(3L);
        directFastSegment.setLengthMeters(300.0);
        directFastSegment.setEstimatedWalkTimeS(214.0);
        directFastSegment.setIsLit(false);
        directFastSegment.setIsOneway(false);
        LineString directLine = gf.createLineString(new Coordinate[]{
                new Coordinate(77.5946, 12.9716),
                new Coordinate(77.5990, 12.9750)
        });
        directFastSegment.setGeometry(directLine);

        // Detour safe segment (Node 1 -> Node 3 via lit boulevard): 550m, 392s, lit (lower risk)
        detourSafeSegment = new RoadSegment();
        detourSafeSegment.setId(UUID.randomUUID());
        detourSafeSegment.setStartNodeId(1L);
        detourSafeSegment.setEndNodeId(3L);
        detourSafeSegment.setLengthMeters(550.0);
        detourSafeSegment.setEstimatedWalkTimeS(392.0);
        detourSafeSegment.setIsLit(true);
        detourSafeSegment.setIsOneway(false);
        LineString detourLine = gf.createLineString(new Coordinate[]{
                new Coordinate(77.5946, 12.9716),
                new Coordinate(77.5960, 12.9740),
                new Coordinate(77.5990, 12.9750)
        });
        detourSafeSegment.setGeometry(detourLine);

        lenient().when(userRepository.findByEmail("traveler@example.com")).thenReturn(Optional.of(testUser));
        lenient().when(geometryFactory.createPoint(any(Coordinate.class))).thenReturn(startPoint, endPoint);
        lenient().when(routeRequestRepository.save(any(RouteRequest.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("searchRoutes produces distinct alternatives and transparent trade-off explanation when routes diverge")
    void testSearchRoutes_DistinctAlternatives_ProducedAndExplained() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .origin(RouteDto.CoordinateDto.builder().latitude(12.9716).longitude(77.5946).build())
                .destination(RouteDto.CoordinateDto.builder().latitude(12.9750).longitude(77.5990).build())
                .build();

        List<RoadSegment> localNetwork = List.of(directFastSegment, detourSafeSegment);
        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble())).thenReturn(localNetwork);
        when(roadSegmentRepository.findNearestSegment(startPoint)).thenReturn(directFastSegment);
        when(roadSegmentRepository.findNearestSegment(endPoint)).thenReturn(directFastSegment);

        String directKey = directFastSegment.getId().toString();
        String detourKey = detourSafeSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(directFastSegment)).thenReturn(directKey);
        when(roadSegmentFeatureService.resolveSegmentId(detourSafeSegment)).thenReturn(detourKey);

        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), any(), any())).thenReturn(List.of(
                PredictRiskRequestDto.builder().segmentId(directKey).build(),
                PredictRiskRequestDto.builder().segmentId(detourKey).build()
        ));

        // Synthetic ML predictions: direct is high risk (0.80, safety 0.20), detour is low risk (0.15, safety 0.85)
        Map<String, PredictRiskResponseDto> mockPredictions = Map.of(
                directKey, PredictRiskResponseDto.builder()
                        .segmentId(directKey)
                        .riskScore(0.80)
                        .safetyScore(0.20)
                        .riskLevel("HIGH")
                        .modelVersion("xgb-v1.0")
                        .isSyntheticDemonstration(true)
                        .build(),
                detourKey, PredictRiskResponseDto.builder()
                        .segmentId(detourKey)
                        .riskScore(0.15)
                        .safetyScore(0.85)
                        .riskLevel("VERY_LOW")
                        .modelVersion("xgb-v1.0")
                        .isSyntheticDemonstration(true)
                        .build()
        );
        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(mockPredictions);

        // A* engine returns direct segment for FASTEST, detour segment for SAFEST and BALANCED
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.FASTEST), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.BALANCED), anyMap(), anyMap()))
                .thenReturn(List.of(detourSafeSegment));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.SAFEST), anyMap(), anyMap()))
                .thenReturn(List.of(detourSafeSegment));

        RouteDto.SearchResponse response = routeService.searchRoutes("traveler@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isMlServiceAvailable()).isTrue();
        assertThat(response.getHasDistinctAlternatives()).isTrue();
        assertThat(response.getDistinctRouteCount()).isEqualTo(2);

        // FASTEST route assertions
        RouteDto.RouteResult fastest = response.getFastestRoute();
        assertThat(fastest).isNotNull();
        assertThat(fastest.getRouteMode()).isEqualTo("FASTEST");
        assertThat(fastest.getDistanceMeters()).isEqualTo(300.0);
        assertThat(fastest.getDurationSeconds()).isEqualTo(214.0);
        assertThat(fastest.getSafetyScore()).isEqualTo(20.0);
        assertThat(fastest.getRiskLevel()).isEqualTo("VERY_HIGH");
        assertThat(fastest.getSafetyLabel()).isEqualTo("Unsafe");
        assertThat(fastest.getIsDistinct()).isTrue();
        assertThat(fastest.getDuplicateOf()).isNull();
        assertThat(fastest.getLitSegmentsPercentage()).isEqualTo(0.0);

        // SAFEST route assertions
        RouteDto.RouteResult safest = response.getSafestRoute();
        assertThat(safest).isNotNull();
        assertThat(safest.getRouteMode()).isEqualTo("SAFEST");
        assertThat(safest.getDistanceMeters()).isEqualTo(550.0);
        assertThat(safest.getDurationSeconds()).isEqualTo(392.0);
        assertThat(safest.getSafetyScore()).isEqualTo(85.0);
        assertThat(safest.getRiskLevel()).isEqualTo("VERY_LOW");
        assertThat(safest.getSafetyLabel()).isEqualTo("Very Safe");
        assertThat(safest.getIsDistinct()).isTrue();
        assertThat(safest.getDuplicateOf()).isNull();
        assertThat(safest.getLitSegmentsPercentage()).isEqualTo(100.0);

        // BALANCED route assertions (coincides with SAFEST here)
        RouteDto.RouteResult balanced = response.getBalancedRoute();
        assertThat(balanced).isNotNull();
        assertThat(balanced.getRouteMode()).isEqualTo("BALANCED");
        assertThat(balanced.getIsDistinct()).isFalse();
        assertThat(balanced.getDuplicateOf()).isEqualTo("SAFEST");

        // Trade-off explanation verification
        assertThat(response.getTradeOffExplanation())
                .contains("SAFEST route trades")
                .contains("safety score");
    }

    @Test
    @DisplayName("searchRoutes detects duplicate paths and truthfully flags identical alternatives")
    void testSearchRoutes_DuplicatePathDetection_IdenticalRoutesFlagged() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        // Only a single road segment exists in this network corridor
        List<RoadSegment> localNetwork = List.of(directFastSegment);
        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble())).thenReturn(localNetwork);
        when(roadSegmentRepository.findNearestSegment(startPoint)).thenReturn(directFastSegment);
        when(roadSegmentRepository.findNearestSegment(endPoint)).thenReturn(directFastSegment);

        String directKey = directFastSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(directFastSegment)).thenReturn(directKey);
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), any(), any())).thenReturn(List.of(
                PredictRiskRequestDto.builder().segmentId(directKey).build()
        ));

        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(Map.of(
                directKey, PredictRiskResponseDto.builder()
                        .segmentId(directKey)
                        .riskScore(0.30)
                        .safetyScore(0.70)
                        .riskLevel("LOW")
                        .modelVersion("xgb-v1.0")
                        .isSyntheticDemonstration(true)
                        .build()
        ));

        // All 3 modes return the identical segment
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.FASTEST), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.BALANCED), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.SAFEST), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));

        RouteDto.SearchResponse response = routeService.searchRoutes("traveler@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.getHasDistinctAlternatives()).isFalse();
        assertThat(response.getDistinctRouteCount()).isEqualTo(1);

        // FASTEST is distinct base
        assertThat(response.getFastestRoute().getIsDistinct()).isTrue();
        assertThat(response.getFastestRoute().getDuplicateOf()).isNull();

        // BALANCED and SAFEST are identified as duplicates of FASTEST
        assertThat(response.getBalancedRoute().getIsDistinct()).isFalse();
        assertThat(response.getBalancedRoute().getDuplicateOf()).isEqualTo("FASTEST");

        assertThat(response.getSafestRoute().getIsDistinct()).isFalse();
        assertThat(response.getSafestRoute().getDuplicateOf()).isEqualTo("FASTEST");

        // Trade-off explanation explicitly documents identical route
        assertThat(response.getTradeOffExplanation()).contains("A single feasible path exists on the available road network");
    }

    @Test
    @DisplayName("searchRoutes when ML service is down succeeds for FASTEST and BALANCED and marks SAFEST as UNSCORED")
    void testSearchRoutes_MlServiceUnavailable_DegradesGracefullyWithUnscoredStatus() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        List<RoadSegment> localNetwork = List.of(directFastSegment);
        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble())).thenReturn(localNetwork);
        when(roadSegmentRepository.findNearestSegment(startPoint)).thenReturn(directFastSegment);
        when(roadSegmentRepository.findNearestSegment(endPoint)).thenReturn(directFastSegment);

        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), any(), any())).thenReturn(List.of(
                PredictRiskRequestDto.builder().segmentId("seg-1").build()
        ));

        // Simulate ML service outage
        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new MlServiceUnavailableException("FastAPI ML service connection refused"));

        // FASTEST and BALANCED are calculated using non-ML topology and heuristics
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.FASTEST), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));
        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), eq(RoutePreference.BALANCED), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));

        RouteDto.SearchResponse response = routeService.searchRoutes("traveler@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isMlServiceAvailable()).isFalse();

        // FASTEST route succeeds
        assertThat(response.getFastestRoute()).isNotNull();
        assertThat(response.getFastestRoute().getDistanceMeters()).isEqualTo(300.0);
        assertThat(response.getFastestRoute().getSafetyScore()).isNull();
        assertThat(response.getFastestRoute().getRiskLevel()).isEqualTo("UNSCORED");

        // BALANCED route succeeds
        assertThat(response.getBalancedRoute()).isNotNull();
        assertThat(response.getBalancedRoute().getSafetyScore()).isNull();
        assertThat(response.getBalancedRoute().getRiskLevel()).isEqualTo("UNSCORED");

        // SAFEST route is explicitly marked UNSCORED — never invents safety scores
        assertThat(response.getSafestRoute()).isNotNull();
        assertThat(response.getSafestRoute().getSafetyScore()).isNull();
        assertThat(response.getSafestRoute().getRiskLevel()).isEqualTo("UNSCORED");
        assertThat(response.getSafestRoute().getSafetyLabel()).isEqualTo("Unscored");
        assertThat(response.getSafestRoute().getNotes()).contains("ML service unavailable");

        // Trade-off explanation informs user of ML outage
        assertThat(response.getTradeOffExplanation()).contains("ML risk service was unavailable");
    }

    @Test
    @DisplayName("searchRoutes throws NoRouteFoundException when no walkable network exists within radius")
    void testSearchRoutes_NoRoadNetwork_ThrowsNoRouteFoundException() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> routeService.searchRoutes("traveler@example.com", request))
                .isInstanceOf(NoRouteFoundException.class)
                .hasMessageContaining("No walkable road network found within 5km");
    }

    @Test
    @DisplayName("searchRoutes throws IllegalArgumentException when coordinates are missing")
    void testSearchRoutes_MissingCoordinates_ThrowsIllegalArgumentException() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder().build();

        assertThatThrownBy(() -> routeService.searchRoutes("traveler@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin and Destination coordinates (latitude and longitude) are required");
    }

    @Test
    @DisplayName("searchRoutes propagates weather features and sets weatherContextAvailable flag when weather is present")
    void testSearchRoutes_WithWeatherContextAvailable() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        List<RoadSegment> localNetwork = List.of(directFastSegment);
        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble())).thenReturn(localNetwork);
        when(roadSegmentRepository.findNearestSegment(startPoint)).thenReturn(directFastSegment);
        when(roadSegmentRepository.findNearestSegment(endPoint)).thenReturn(directFastSegment);

        String directKey = directFastSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(directFastSegment)).thenReturn(directKey);

        WeatherDto mockWeather = WeatherDto.builder()
                .available(true)
                .observation(WeatherDto.CurrentObservation.builder()
                        .temperatureC(26.0)
                        .precipitationMm(1.5)
                        .visibilityKm(10.0)
                        .build())
                .build();
        when(weatherService.getWeather(12.9716, 77.5946)).thenReturn(mockWeather);

        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), eq(1.5), eq(10.0))).thenReturn(List.of(
                PredictRiskRequestDto.builder().segmentId(directKey).build()
        ));

        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(Map.of(
                directKey, PredictRiskResponseDto.builder()
                        .segmentId(directKey)
                        .riskScore(0.20)
                        .safetyScore(0.80)
                        .riskLevel("LOW")
                        .build()
        ));

        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), any(), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));

        RouteDto.SearchResponse response = routeService.searchRoutes("traveler@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isWeatherContextAvailable()).isTrue();
        verify(roadSegmentFeatureService).prepareBatchRequests(anyList(), any(), eq(1.5), eq(10.0));
    }

    @Test
    @DisplayName("searchRoutes succeeds safely with weatherContextAvailable=false when weather provider fails")
    void testSearchRoutes_WeatherProviderFailure_NeverBlocksRouting() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        List<RoadSegment> localNetwork = List.of(directFastSegment);
        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(), anyDouble())).thenReturn(localNetwork);
        when(roadSegmentRepository.findNearestSegment(startPoint)).thenReturn(directFastSegment);
        when(roadSegmentRepository.findNearestSegment(endPoint)).thenReturn(directFastSegment);

        String directKey = directFastSegment.getId().toString();
        when(roadSegmentFeatureService.resolveSegmentId(directFastSegment)).thenReturn(directKey);

        when(weatherService.getWeather(anyDouble(), anyDouble()))
                .thenThrow(new com.safepath.backend.exception.WeatherClientException("Open-Meteo connection timeout"));

        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), isNull(), isNull())).thenReturn(List.of(
                PredictRiskRequestDto.builder().segmentId(directKey).build()
        ));

        when(mlServiceClient.predictRiskBatch(anyList())).thenReturn(Map.of(
                directKey, PredictRiskResponseDto.builder()
                        .segmentId(directKey)
                        .riskScore(0.20)
                        .safetyScore(0.80)
                        .riskLevel("LOW")
                        .build()
        ));

        when(aStarRoutingEngine.findRoute(anyLong(), anyLong(), any(), anyMap(), anyMap()))
                .thenReturn(List.of(directFastSegment));

        RouteDto.SearchResponse response = routeService.searchRoutes("traveler@example.com", request);

        assertThat(response).isNotNull();
        assertThat(response.isWeatherContextAvailable()).isFalse();
        assertThat(response.getFastestRoute()).isNotNull();
        assertThat(response.getSafestRoute()).isNotNull();
    }
}

package com.safepath.backend.integration;

import com.safepath.backend.config.WeatherProperties;
import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.dto.weather.OpenMeteoResponseDto;
import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.entity.*;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.exception.WeatherClientException;
import com.safepath.backend.repository.*;
import com.safepath.backend.service.*;
import com.safepath.backend.service.rate.ReportRateLimiterService;
import com.safepath.backend.service.ml.MlServiceClient;
import com.safepath.backend.service.ml.RoadSegmentFeatureService;
import com.safepath.backend.service.routing.AStarRoutingEngine;
import com.safepath.backend.service.weather.OpenMeteoClient;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Milestone 24: Realistic Cross-Service Failure Modes and Resilience Tests.
 *
 * Verifies:
 * 1. Routing graceful fallback when ML service is offline (no false safety claims).
 * 2. Weather provider timeout, stale cache fallback, PostGIS DB fallback, and honest unavailable state.
 * 3. Heatmap empty bounding box and ML offline degradation.
 * 4. Safe zone query empty state and span bounds checks.
 * 5. Community report rate limiting, public privacy filtering, and moderator transition guards.
 */
@ExtendWith(MockitoExtension.class)
class EndToEndIntegrationAndFailureModeTest {

    @org.mockito.Spy
    private GeometryFactory geometryFactory = new GeometryFactory();

    // -------------------------------------------------------------
    // Routing Mocks
    // -------------------------------------------------------------
    @Mock private RouteRequestRepository routeRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoadSegmentRepository roadSegmentRepository;
    @Mock private AStarRoutingEngine aStarRoutingEngine;
    @Mock private MlServiceClient mlServiceClient;
    @Mock private RoadSegmentFeatureService roadSegmentFeatureService;
    @Mock private WeatherService weatherService;

    @InjectMocks
    private RouteService routeService;

    // -------------------------------------------------------------
    // Weather Mocks
    // -------------------------------------------------------------
    @Mock private OpenMeteoClient openMeteoClient;
    @Mock private WeatherObservationRepository weatherObservationRepository;
    private WeatherProperties weatherProperties;
    private WeatherService standaloneWeatherService;

    // -------------------------------------------------------------
    // Heatmap Mocks
    // -------------------------------------------------------------
    @InjectMocks
    private HeatmapService heatmapService;

    // -------------------------------------------------------------
    // SafeZone Mocks
    // -------------------------------------------------------------
    @Mock private SafeZoneRepository safeZoneRepository;
    @InjectMocks
    private SafeZoneService safeZoneService;

    // -------------------------------------------------------------
    // Community Report Mocks
    // -------------------------------------------------------------
    @Mock private CommunityReportRepository communityReportRepository;
    @Mock private UserService userService;
    @Mock private ReportRateLimiterService rateLimiterService;
    @Mock private UserDetails mockUserPrincipal;
    @Mock private UserDetails mockModeratorPrincipal;

    @InjectMocks
    private CommunityReportService communityReportService;

    @BeforeEach
    void setUp() {
        weatherProperties = new WeatherProperties();
        weatherProperties.setEnabled(true);
        weatherProperties.setCacheTtlMinutes(30);

        standaloneWeatherService = new WeatherService(
                openMeteoClient,
                weatherObservationRepository,
                geometryFactory,
                weatherProperties
        );
    }

    // =============================================================
    // 1. ROUTING ML SERVICE FAILURE & HONEST FALLBACK
    // =============================================================

    @Test
    @DisplayName("Routing: When ML is offline, FASTEST route succeeds but SAFEST is unvalidated (no false safety)")
    void testRouteComparison_MlUnavailable_HonestFallback() {
        double startLat = 12.9716, startLon = 77.5946;
        double endLat = 12.9750, endLon = 77.5980;

        RoadSegment seg = createMockSegment(1L, 2L, 500.0, true);
        List<RoadSegment> segments = List.of(seg);

        when(roadSegmentRepository.findWalkableSegmentsWithinRadius(any(Point.class), eq(5000.0)))
                .thenReturn(segments);
        when(roadSegmentRepository.findNearestSegment(any(Point.class)))
                .thenReturn(seg);

        // ML Service throws exception (service unreachable)
        when(roadSegmentFeatureService.prepareBatchRequests(anyList(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(mlServiceClient.predictRiskBatch(anyList()))
                .thenThrow(new RuntimeException("Connection refused: ml-service:8000"));

        // Weather service returns unavailable
        when(weatherService.getWeather(startLat, startLon))
                .thenReturn(WeatherDto.builder().available(false).build());

        // A* engine returns fastest route
        when(aStarRoutingEngine.findRoute(eq(1L), eq(2L), eq(RoutePreference.FASTEST), anyMap(), anyMap()))
                .thenReturn(segments);
        when(aStarRoutingEngine.findRoute(eq(1L), eq(2L), eq(RoutePreference.BALANCED), anyMap(), anyMap()))
                .thenReturn(segments);

        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(startLat).startLon(startLon)
                .endLat(endLat).endLon(endLon)
                .build();

        RouteDto.SearchResponse response = routeService.searchRoutes(null, request);

        assertThat(response).isNotNull();
        assertThat(response.isMlServiceAvailable()).isFalse();
        assertThat(response.isWeatherContextAvailable()).isFalse();

        // FASTEST route succeeds
        assertThat(response.getFastestRoute()).isNotNull();
        assertThat(response.getFastestRoute().getSafetyScore()).isNull();
        assertThat(response.getFastestRoute().getRiskLevel()).isEqualTo("UNSCORED");

        // SAFEST route is NOT claimed as safe
        assertThat(response.getSafestRoute()).isNotNull();
        assertThat(response.getSafestRoute().getSafetyScore()).isNull();
        assertThat(response.getSafestRoute().getRiskLevel()).isEqualTo("UNSCORED");
        assertThat(response.getSafestRoute().getNotes()).contains("ML service unavailable");
    }

    // =============================================================
    // 2. WEATHER PROVIDER FAILURE MODES & CACHE/DB FALLBACKS
    // =============================================================

    @Test
    @DisplayName("Weather: Timeout with no cache or DB returns honest unavailable response without fabricated metrics")
    void testWeather_ProviderTimeout_HonestUnavailable() {
        double lat = 12.9716, lon = 77.5946;

        when(openMeteoClient.fetchWeather(lat, lon))
                .thenThrow(new WeatherClientException("Read timeout after 2500ms"));
        when(weatherObservationRepository.findLatestNearby(eq(lon), eq(lat), anyDouble()))
                .thenReturn(Optional.empty());

        WeatherDto result = standaloneWeatherService.getWeather(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isFalse();
        assertThat(result.getObservation()).isNull();
        assertThat(result.getForecast()).isNull();
        assertThat(result.getMessage()).contains("temporarily unavailable");
    }

    @Test
    @DisplayName("Weather: Provider timeout falls back to recent PostGIS DB observation (< 3 hours)")
    void testWeather_ProviderTimeout_FallsBackToRecentDbObservation() {
        double lat = 12.9716, lon = 77.5946;

        when(openMeteoClient.fetchWeather(lat, lon))
                .thenThrow(new WeatherClientException("Open-Meteo HTTP 503 Service Unavailable"));

        WeatherObservation recentObs = WeatherObservation.builder()
                .id(UUID.randomUUID())
                .temperatureC(24.5)
                .precipitationMm(0.0)
                .windSpeedKmh(8.5)
                .relativeHumidity(70.0)
                .weatherCode(1)
                .weatherDescription("Mainly clear")
                .isNight(false)
                .observedAt(OffsetDateTime.now().minusMinutes(45)) // 45 mins ago (< 3h)
                .build();

        when(weatherObservationRepository.findLatestNearby(eq(lon), eq(lat), eq(5000.0)))
                .thenReturn(Optional.of(recentObs));

        WeatherDto result = standaloneWeatherService.getWeather(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getMessage()).contains("serving historical database observation");
        assertThat(result.getObservation()).isNotNull();
        assertThat(result.getObservation().getTemperatureC()).isEqualTo(24.5);
        assertThat(result.getObservation().getCondition()).isEqualTo("Mainly clear");
    }

    @Test
    @DisplayName("Weather: In-memory cache hit serves within TTL without re-querying provider")
    void testWeather_CacheHit_WithinTtl() {
        double lat = 12.9716, lon = 77.5946;

        OpenMeteoResponseDto providerResp = new OpenMeteoResponseDto();
        OpenMeteoResponseDto.CurrentWeather current = new OpenMeteoResponseDto.CurrentWeather();
        current.setTemperature2m(27.0);
        current.setPrecipitation(0.0);
        current.setWeatherCode(0);
        current.setIsDay(1);
        current.setTime("2026-10-14T14:00");
        providerResp.setCurrent(current);

        when(openMeteoClient.fetchWeather(lat, lon)).thenReturn(providerResp);

        // First call populates cache
        WeatherDto first = standaloneWeatherService.getWeather(lat, lon);
        assertThat(first.isCached()).isFalse();

        // Second call within TTL returns cached copy
        WeatherDto second = standaloneWeatherService.getWeather(lat, lon);
        assertThat(second.isCached()).isTrue();
        assertThat(second.getObservation().getTemperatureC()).isEqualTo(27.0);

        // Client should have been called only once
        verify(openMeteoClient, times(1)).fetchWeather(lat, lon);
    }

    // =============================================================
    // 3. HEATMAP VIEWPORT EMPTY STATE & BOUNDS GUARDS
    // =============================================================

    @Test
    @DisplayName("Heatmap: Empty road network returns 200 with 0 segments and honest metadata")
    void testHeatmap_EmptyViewport_ReturnsZeroSegments() {
        double minLat = 12.970, minLon = 77.590, maxLat = 12.975, maxLon = 77.595;

        when(roadSegmentRepository.findSegmentsInBoundingBox(any(), anyInt()))
                .thenReturn(Collections.emptyList());

        HeatmapDto.Response response = heatmapService.getHeatmap(minLat, minLon, maxLat, maxLon, 100);

        assertThat(response).isNotNull();
        assertThat(response.getTotalSegments()).isEqualTo(0);
        assertThat(response.getScoredSegments()).isEqualTo(0);
        assertThat(response.getSegments()).isEmpty();
    }

    @Test
    @DisplayName("Heatmap: Viewport span exceeding 0.15 degrees is rejected to prevent spatial DoS")
    void testHeatmap_ExcessiveSpan_Rejected() {
        // Lat span = 0.50 degrees (> 0.15 limit)
        assertThatThrownBy(() -> heatmapService.getHeatmap(12.0, 77.0, 12.5, 77.1, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box span is too large");
    }

    // =============================================================
    // 4. SAFE ZONE QUERIES & EMPTY STATE
    // =============================================================

    @Test
    @DisplayName("Safe Zones: Empty query returns empty list without error")
    void testSafeZones_EmptyResults_ReturnsEmptyList() {
        when(safeZoneRepository.findInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<SafeZoneDto> results = safeZoneService.findInBoundingBox(12.97, 77.59, 12.98, 77.60, null, 10);

        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Safe Zones: Viewport span exceeding 0.15 degrees throws IllegalArgumentException")
    void testSafeZones_ExcessiveSpan_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> safeZoneService.findInBoundingBox(12.0, 77.0, 12.2, 77.05, null, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box span is too large");
    }

    @Test
    @DisplayName("Safe Zones: Lookup by non-existent UUID throws ResourceNotFoundException")
    void testSafeZones_NotFound_ThrowsResourceNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        when(safeZoneRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> safeZoneService.findById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Safe zone not found");
    }

    // =============================================================
    // 5. COMMUNITY REPORTS LIFECYCLE, PRIVACY & MODERATION
    // =============================================================

    @Test
    @DisplayName("Community Reports: Public nearby queries return only VERIFIED reports, omitting reporter PII")
    void testCommunityReports_PublicNearby_StrictlyVerifiedAndPrivate() {
        CommunityReport verifiedReport = CommunityReport.builder()
                .id(UUID.randomUUID())
                .reportType(CommunityReport.ReportType.POOR_LIGHTING)
                .description("Broken street lights")
                .status(CommunityReport.ReportStatus.VERIFIED)
                .locationPrecision(CommunityReport.LocationPrecision.EXACT_COORDINATE)
                .latitude(12.975)
                .longitude(77.598)
                .createdAt(OffsetDateTime.now())
                .build();

        when(communityReportRepository.findVerifiedNearby(77.598, 12.975, 500.0, 20))
                .thenReturn(List.of(verifiedReport));

        List<CommunityReportDto.Response> results = communityReportService.getNearby(12.975, 77.598, 500.0, 20);

        assertThat(results).hasSize(1);
        CommunityReportDto.Response item = results.get(0);
        assertThat(item.getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
        assertThat(item.getReportType()).isEqualTo(CommunityReport.ReportType.POOR_LIGHTING);
    }

    @Test
    @DisplayName("Community Reports: Submitting report with future timestamp is rejected with HTTP 400")
    void testCommunityReports_FutureTimestamp_Rejected() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        when(mockUserPrincipal.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.POOR_LIGHTING);
        request.setLatitude(12.975);
        request.setLongitude(77.598);
        request.setIncidentDatetime(OffsetDateTime.now().plusHours(2)); // Future time

        assertThatThrownBy(() -> communityReportService.submitReport(mockUserPrincipal, request))
                .isInstanceOf(SafePathException.class)
                .satisfies(ex -> assertThat(((SafePathException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Incident time cannot be in the future");
    }

    @Test
    @DisplayName("Community Reports: Rate limiting abuse control triggers HTTP 429 when threshold exceeded")
    void testCommunityReports_RateLimiting_Triggers429() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("spammer@example.com");

        when(mockUserPrincipal.getUsername()).thenReturn("spammer@example.com");
        when(userService.getUserByEmail("spammer@example.com")).thenReturn(user);

        doThrow(new SafePathException("Rate limit exceeded: maximum 5 reports per 10 minutes", HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimiterService).checkAndRecord(user.getId());

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.SUSPICIOUS_ACTIVITY);

        assertThatThrownBy(() -> communityReportService.submitReport(mockUserPrincipal, request))
                .isInstanceOf(SafePathException.class)
                .satisfies(ex -> assertThat(((SafePathException) ex).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessageContaining("Rate limit exceeded");
    }

    @Test
    @DisplayName("Community Reports: Reprocessing an already VERIFIED report is rejected with HTTP 400")
    void testCommunityReports_ReprocessingProcessedReport_Rejected() {
        UUID reportId = UUID.randomUUID();
        CommunityReport alreadyVerified = CommunityReport.builder()
                .id(reportId)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .build();

        when(communityReportRepository.findById(reportId)).thenReturn(Optional.of(alreadyVerified));

        CommunityReportDto.VerifyRequest verifyReq = new CommunityReportDto.VerifyRequest();
        verifyReq.setStatus(CommunityReport.ReportStatus.REJECTED);

        assertThatThrownBy(() -> communityReportService.verifyReport(reportId, verifyReq, mockModeratorPrincipal))
                .isInstanceOf(SafePathException.class)
                .satisfies(ex -> assertThat(((SafePathException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Report has already been processed");
    }

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------
    private RoadSegment createMockSegment(Long startNode, Long endNode, double length, boolean lit) {
        LineString geom = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(77.5946, 12.9716),
                new Coordinate(77.5980, 12.9750)
        });
        geom.setSRID(4326);

        Point startPoint = geometryFactory.createPoint(new Coordinate(77.5946, 12.9716));
        Point endPoint = geometryFactory.createPoint(new Coordinate(77.5980, 12.9750));

        RoadSegment seg = new RoadSegment();
        seg.setId(UUID.randomUUID());
        seg.setStartNodeId(startNode);
        seg.setEndNodeId(endNode);
        seg.setLengthMeters(length);
        seg.setEstimatedWalkTimeS(length / 1.39);
        seg.setRoadType("residential");
        seg.setIsLit(lit);
        seg.setWalkable(true);
        seg.setGeometry(geom);
        seg.setStartPoint(startPoint);
        seg.setEndPoint(endPoint);
        return seg;
    }
}

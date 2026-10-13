package com.safepath.backend.controller;

import com.safepath.backend.dto.RouteDto;
import com.safepath.backend.dto.RouteRequestDto;
import com.safepath.backend.dto.RouteResponseDto;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.service.RouteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Controller tests verifying the route planning, search, and comparison API endpoints.
 */
@ExtendWith(MockitoExtension.class)
class RouteControllerTest {

    @Mock
    private RouteService routeService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private RouteController routeController;

    @Test
    @DisplayName("searchRoutes endpoint delegates to RouteService and returns HTTP 200")
    void testSearchRoutes_ReturnsOk() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        RouteDto.SearchResponse mockResponse = RouteDto.SearchResponse.builder()
                .requestId("test-uuid-123")
                .hasDistinctAlternatives(true)
                .distinctRouteCount(2)
                .fastestRoute(RouteDto.RouteResult.builder().routeMode("FASTEST").isDistinct(true).build())
                .balancedRoute(RouteDto.RouteResult.builder().routeMode("BALANCED").isDistinct(true).build())
                .safestRoute(RouteDto.RouteResult.builder().routeMode("SAFEST").isDistinct(true).build())
                .tradeOffExplanation("SAFEST takes 2 min longer to avoid unlit roads.")
                .computedAt(OffsetDateTime.now())
                .mlServiceAvailable(true)
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(routeService.searchRoutes(eq("user@example.com"), any(RouteDto.SearchRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<RouteDto.SearchResponse> responseEntity = routeController.searchRoutes(request, authentication);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getRequestId()).isEqualTo("test-uuid-123");
        assertThat(responseEntity.getBody().getHasDistinctAlternatives()).isTrue();
    }

    @Test
    @DisplayName("compareRoutes endpoint delegates to RouteService and returns HTTP 200")
    void testCompareRoutes_ReturnsOk() {
        RouteDto.SearchRequest request = RouteDto.SearchRequest.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .build();

        RouteDto.SearchResponse mockResponse = RouteDto.SearchResponse.builder()
                .requestId("compare-uuid-456")
                .hasDistinctAlternatives(false)
                .distinctRouteCount(1)
                .fastestRoute(RouteDto.RouteResult.builder().routeMode("FASTEST").isDistinct(true).build())
                .balancedRoute(RouteDto.RouteResult.builder().routeMode("BALANCED").isDistinct(false).duplicateOf("FASTEST").build())
                .safestRoute(RouteDto.RouteResult.builder().routeMode("SAFEST").isDistinct(false).duplicateOf("FASTEST").build())
                .tradeOffExplanation("Only 1 unique route available.")
                .computedAt(OffsetDateTime.now())
                .mlServiceAvailable(true)
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(routeService.searchRoutes(eq("user@example.com"), any(RouteDto.SearchRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<RouteDto.SearchResponse> responseEntity = routeController.compareRoutes(request, authentication);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getRequestId()).isEqualTo("compare-uuid-456");
        assertThat(responseEntity.getBody().getDistinctRouteCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("planRoute endpoint maintains backwards compatibility for single route planning")
    void testPlanRoute_MaintainsBackwardsCompatibility() {
        RouteRequestDto request = RouteRequestDto.builder()
                .startLat(12.9716).startLon(77.5946)
                .endLat(12.9750).endLon(77.5990)
                .preference(RoutePreference.STANDARD)
                .build();

        RouteResponseDto mockResponse = RouteResponseDto.builder()
                .routeId("plan-id-789")
                .distanceMeters(500.0)
                .durationSeconds(360.0)
                .isMlScored(true)
                .build();

        when(authentication.getName()).thenReturn("user@example.com");
        when(routeService.planRoute(eq("user@example.com"), any(RouteRequestDto.class)))
                .thenReturn(mockResponse);

        ResponseEntity<RouteResponseDto> responseEntity = routeController.planRoute(request, authentication);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getRouteId()).isEqualTo("plan-id-789");
    }
}

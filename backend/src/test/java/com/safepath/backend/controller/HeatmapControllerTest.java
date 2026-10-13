package com.safepath.backend.controller;

import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.service.HeatmapService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller tests for Safety Heatmap REST endpoints.
 */
@ExtendWith(MockitoExtension.class)
class HeatmapControllerTest {

    @Mock
    private HeatmapService heatmapService;

    @InjectMocks
    private HeatmapController heatmapController;

    @Test
    @DisplayName("GET /api/heatmap delegates to HeatmapService and returns HTTP 200")
    void testGetHeatmap_ReturnsOk() {
        HeatmapDto.Response mockResponse = HeatmapDto.Response.builder()
                .boundingBox("[12.9600, 77.5900, 12.9800, 77.6100]")
                .totalSegments(0)
                .scoredSegments(0)
                .unscoredSegments(0)
                .segments(Collections.emptyList())
                .mlServiceAvailable(true)
                .timestamp(OffsetDateTime.now())
                .disclaimer("Disclaimed")
                .build();

        when(heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, 500))
                .thenReturn(mockResponse);

        ResponseEntity<HeatmapDto.Response> responseEntity =
                heatmapController.getHeatmap(12.96, 77.59, 12.98, 77.61, 500);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getBoundingBox()).isEqualTo("[12.9600, 77.5900, 12.9800, 77.6100]");
        verify(heatmapService).getHeatmap(12.96, 77.59, 12.98, 77.61, 500);
    }

    @Test
    @DisplayName("GET /api/routes/heatmap alias delegates to HeatmapService and returns HTTP 200")
    void testGetRoutesHeatmapAlias_ReturnsOk() {
        HeatmapDto.Response mockResponse = HeatmapDto.Response.builder()
                .boundingBox("[12.9600, 77.5900, 12.9800, 77.6100]")
                .totalSegments(0)
                .segments(Collections.emptyList())
                .mlServiceAvailable(false)
                .timestamp(OffsetDateTime.now())
                .build();

        when(heatmapService.getHeatmap(12.96, 77.59, 12.98, 77.61, null))
                .thenReturn(mockResponse);

        ResponseEntity<HeatmapDto.Response> responseEntity =
                heatmapController.getHeatmap(12.96, 77.59, 12.98, 77.61, null);

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isMlServiceAvailable()).isFalse();
    }
}

package com.safepath.backend.controller;

import com.safepath.backend.dto.HeatmapDto;
import com.safepath.backend.service.HeatmapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for retrieving road-segment-level safety heatmap data.
 */
@RestController
@RequiredArgsConstructor
public class HeatmapController {

    private final HeatmapService heatmapService;

    @GetMapping({"/api/heatmap", "/api/routes/heatmap"})
    public ResponseEntity<HeatmapDto.Response> getHeatmap(
            @RequestParam("minLat") Double minLat,
            @RequestParam("minLon") Double minLon,
            @RequestParam("maxLat") Double maxLat,
            @RequestParam("maxLon") Double maxLon,
            @RequestParam(value = "limit", required = false) Integer limit) {

        HeatmapDto.Response response = heatmapService.getHeatmap(minLat, minLon, maxLat, maxLon, limit);
        return ResponseEntity.ok(response);
    }
}

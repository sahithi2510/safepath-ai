package com.safepath.backend.controller;

import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.service.SafeZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Safe zone controller — public endpoint (no authentication required).
 */
@RestController
@RequestMapping("/api/safe-zones")
@RequiredArgsConstructor
@Tag(name = "Safe Zones", description = "Find nearby police stations, hospitals, pharmacies")
public class SafeZoneController {

    private final SafeZoneService safeZoneService;

    @Operation(summary = "Find nearby safe zones")
    @GetMapping("/nearby")
    public ResponseEntity<List<SafeZoneDto>> getNearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(defaultValue = "1000") @Min(10) @Max(10000) double radiusMeters,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(
                safeZoneService.findNearby(lat, lon, radiusMeters, types, limit));
    }
}

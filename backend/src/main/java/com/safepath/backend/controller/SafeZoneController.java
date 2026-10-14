package com.safepath.backend.controller;

import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.service.SafeZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Safe zone controller — public endpoints (no authentication required).
 * Provides verified assistance facilities (police stations, hospitals, pharmacies, transport hubs).
 */
@Validated
@RestController
@RequestMapping("/api/safe-zones")
@RequiredArgsConstructor
@Tag(name = "Safe Zones", description = "Query verified safety facilities such as police stations and hospitals")
public class SafeZoneController {

    private final SafeZoneService safeZoneService;

    @Operation(summary = "Query safe zones in viewport bounding box or nearby coordinates")
    @GetMapping
    public ResponseEntity<List<SafeZoneDto>> getSafeZones(
            @Parameter(description = "Southern latitude boundary")
            @RequestParam(required = false) Double minLat,
            @Parameter(description = "Western longitude boundary")
            @RequestParam(required = false) Double minLon,
            @Parameter(description = "Northern latitude boundary")
            @RequestParam(required = false) Double maxLat,
            @Parameter(description = "Eastern longitude boundary")
            @RequestParam(required = false) Double maxLon,
            @Parameter(description = "Center latitude for proximity search")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "Center longitude for proximity search")
            @RequestParam(required = false) Double lon,
            @Parameter(description = "Proximity radius in meters (default 1000, max 10000)")
            @RequestParam(required = false, defaultValue = "1000") Double radiusMeters,
            @Parameter(description = "Optional category filters (e.g., POLICE_STATION, HOSPITAL, PHARMACY, PUBLIC_TRANSPORT)")
            @RequestParam(required = false) List<String> types,
            @Parameter(description = "Max records to return")
            @RequestParam(required = false) Integer limit) {

        if (minLat != null && minLon != null && maxLat != null && maxLon != null) {
            return ResponseEntity.ok(
                    safeZoneService.findInBoundingBox(minLat, minLon, maxLat, maxLon, types, limit));
        }

        if (lat != null && lon != null) {
            double effectiveRadius = radiusMeters != null ? radiusMeters : 1000.0;
            int effectiveLimit = limit != null ? limit : 10;
            return ResponseEntity.ok(
                    safeZoneService.findNearby(lat, lon, effectiveRadius, types, effectiveLimit));
        }

        throw new IllegalArgumentException(
                "Either viewport bounding box (minLat, minLon, maxLat, maxLon) or search coordinates (lat, lon) must be provided.");
    }

    @Operation(summary = "Find nearby safe zones within a specified radius")
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

    @Operation(summary = "Get a specific safe zone facility by ID")
    @GetMapping("/{id}")
    public ResponseEntity<SafeZoneDto> getById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(safeZoneService.findById(id));
    }
}

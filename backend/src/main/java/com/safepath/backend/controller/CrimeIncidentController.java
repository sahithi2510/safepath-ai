package com.safepath.backend.controller;

import com.safepath.backend.dto.CrimeIncidentDto;
import com.safepath.backend.service.CrimeIncidentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crime-incidents")
@RequiredArgsConstructor
@Tag(name = "Crime Incidents", description = "Query real verified crime incidents and hazard spots")
public class CrimeIncidentController {

    private final CrimeIncidentService crimeIncidentService;

    @Operation(summary = "Get crime incidents and hazard spots within a geographic bounding box / corridor")
    @GetMapping("/corridor")
    public ResponseEntity<List<CrimeIncidentDto.Response>> getCorridorCrimeSpots(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLon,
            @RequestParam double maxLon,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(crimeIncidentService.getCorridorCrimeSpots(minLat, maxLat, minLon, maxLon, limit));
    }

    @Operation(summary = "Get crime incidents near a specific location")
    @GetMapping("/nearby")
    public ResponseEntity<List<CrimeIncidentDto.Response>> getNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "2000") double radiusMeters,
            @RequestParam(defaultValue = "25") int limit) {
        return ResponseEntity.ok(crimeIncidentService.getNearby(lat, lon, radiusMeters, limit));
    }
}

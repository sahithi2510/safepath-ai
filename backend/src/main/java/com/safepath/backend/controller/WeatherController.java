package com.safepath.backend.controller;

import com.safepath.backend.dto.weather.WeatherDto;
import com.safepath.backend.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Weather controller — provides ambient weather context (temperature, precipitation, wind, visibility).
 * Public endpoint (no authentication required).
 */
@Validated
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@Tag(name = "Weather", description = "Query ambient weather conditions and environmental context")
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "Get ambient weather context for a validated geographic location")
    @GetMapping
    public ResponseEntity<WeatherDto> getWeather(
            @Parameter(description = "Latitude in degrees [-90.0, 90.0]")
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @Parameter(description = "Longitude in degrees [-180.0, 180.0]")
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lon) {

        WeatherDto weather = weatherService.getWeather(lat, lon);
        return ResponseEntity.ok(weather);
    }
}

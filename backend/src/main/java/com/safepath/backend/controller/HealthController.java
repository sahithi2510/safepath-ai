package com.safepath.backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Health check controller.
 * Returns application status. No authentication required.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Application health check")
public class HealthController {

    @Value("${spring.application.name:safepath-backend}")
    private String appName;

    @Operation(summary = "Health check")
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        // Basic health response. DB and ML service health will be added in Milestone 17.
        return ResponseEntity.ok(HealthResponse.builder()
                .status("UP")
                .application(appName)
                .timestamp(OffsetDateTime.now())
                .build());
    }

    @Data
    @Builder
    public static class HealthResponse {
        private String status;
        private String application;
        private OffsetDateTime timestamp;
    }
}

package com.safepath.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * DTOs for route search request and response.
 */
public class RouteDto {

    /** Request body for POST /api/routes/search */
    @Data
    public static class SearchRequest {

        @NotNull(message = "Origin is required")
        private CoordinateDto origin;

        @NotNull(message = "Destination is required")
        private CoordinateDto destination;

        private String travelMode = "WALKING";

        private String routePreference;

        private OffsetDateTime departureTime;
    }

    /** A single latitude/longitude coordinate. */
    @Data
    public static class CoordinateDto {
        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        private Double longitude;
    }

    /** A single route result (one of: fastest, balanced, safest). */
    @Data
    @lombok.Builder
    public static class RouteResult {
        private String routeMode;               // FASTEST, BALANCED, SAFEST
        private Object geometry;                // GeoJSON LineString
        private Double distanceMeters;
        private Double durationSeconds;
        private Double safetyScore;             // null if ML unavailable
        private String riskLevel;               // null if ML unavailable
        private String safetyLabel;             // Very Safe / Safe / Moderate / Unsafe / Very Unsafe
        private java.util.List<String> importantFactors;
        private Object explanation;             // SHAP factors; null if unavailable
        private Boolean demo;                   // true only if demo data
    }

    /** Full response for POST /api/routes/search */
    @Data
    @lombok.Builder
    public static class SearchResponse {
        private String requestId;
        private RouteResult fastestRoute;
        private RouteResult balancedRoute;
        private RouteResult safestRoute;
        private OffsetDateTime computedAt;
        private boolean mlServiceAvailable;
        private boolean weatherContextAvailable;
    }
}

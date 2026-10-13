package com.safepath.backend.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTOs for route search, comparison, and results.
 */
public class RouteDto {

    /** Request body for POST /api/routes/search and POST /api/routes/compare */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchRequest {

        private CoordinateDto origin;

        private CoordinateDto destination;

        // Optional flat coordinates for client convenience
        private Double startLat;
        private Double startLon;
        private Double endLat;
        private Double endLon;

        @Builder.Default
        private String travelMode = "WALKING";

        private String routePreference;

        private OffsetDateTime departureTime;

        public Double getEffectiveStartLat() {
            if (origin != null && origin.getLatitude() != null) return origin.getLatitude();
            return startLat;
        }

        public Double getEffectiveStartLon() {
            if (origin != null && origin.getLongitude() != null) return origin.getLongitude();
            return startLon;
        }

        public Double getEffectiveEndLat() {
            if (destination != null && destination.getLatitude() != null) return destination.getLatitude();
            return endLat;
        }

        public Double getEffectiveEndLon() {
            if (destination != null && destination.getLongitude() != null) return destination.getLongitude();
            return endLon;
        }
    }

    /** A single latitude/longitude coordinate. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteResult {
        private String routeMode;               // FASTEST, BALANCED, SAFEST
        private Object geometry;                // GeoJSON LineString Map: { type: "LineString", coordinates: [...] }
        private List<CoordinateDto> coordinates; // List of coordinate objects for map rendering
        private Double distanceMeters;
        private Double durationSeconds;
        private Double safetyScore;             // null if ML unavailable (0.0 - 100.0)
        private String riskLevel;               // VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH, or UNSCORED
        private String safetyLabel;             // Very Safe / Safe / Moderate / Unsafe / Very Unsafe / Unscored
        private List<String> importantFactors;
        private Object explanation;             // SHAP factors; null if unavailable
        private Boolean demo;                   // true only if demo data
        private Boolean isDistinct;             // true if distinct from other route options
        private String duplicateOf;             // e.g., "FASTEST", or null if distinct
        private Boolean isMlScored;
        private String modelVersion;
        private Double litSegmentsPercentage;
        private String notes;
        private String disclaimer;
    }

    /** Full response for POST /api/routes/search and POST /api/routes/compare */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResponse {
        private String requestId;
        private RouteResult fastestRoute;
        private RouteResult balancedRoute;
        private RouteResult safestRoute;
        private String tradeOffExplanation;
        private Boolean hasDistinctAlternatives;
        private Integer distinctRouteCount;
        private OffsetDateTime computedAt;
        private boolean mlServiceAvailable;
        private boolean weatherContextAvailable;
        private String notes;
    }
}


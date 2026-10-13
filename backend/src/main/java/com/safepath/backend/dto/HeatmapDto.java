package com.safepath.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data Transfer Objects for the Safety Heatmap feature.
 */
public class HeatmapDto {

    /** Request query parameters for GET /api/heatmap */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private Double minLat;
        private Double minLon;
        private Double maxLat;
        private Double maxLon;
        private Integer limit;
    }

    /** Single road segment with risk and geometry details for heatmap display */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SegmentRisk {
        private String segmentId;
        private String roadName;
        private String roadType;
        private Double lengthMeters;
        private Boolean isLit;
        private Object geometry; // GeoJSON LineString Map: { type: "LineString", coordinates: [...] }
        private List<RouteDto.CoordinateDto> coordinates; // List of {latitude, longitude} for direct map rendering
        private Double riskScore; // [0.0, 1.0] relative risk score (null if unscored)
        private Double safetyScore; // [0.0, 100.0] derived user safety score (null if unscored)
        private String riskLevel; // VERY_LOW, LOW, MODERATE, HIGH, VERY_HIGH, UNSCORED, UNAVAILABLE
        private String scoringStatus; // SCORED, UNSCORED, UNAVAILABLE
        private String colorHex; // Hex color code for map overlay
        private Boolean isSyntheticDemonstration;
        private String modelVersion;
        private String notes;
    }

    /** Response payload for GET /api/heatmap */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private String boundingBox;
        private int totalSegments;
        private int scoredSegments;
        private int unscoredSegments;
        private List<SegmentRisk> segments;
        private boolean mlServiceAvailable;
        private Boolean isSyntheticDemonstration;
        private String modelVersion;
        private OffsetDateTime timestamp;
        private String disclaimer;
    }
}

package com.safepath.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RouteResponseDto {
    private String routeId;
    private List<CoordinateDto> coordinates;
    private Double distanceMeters;
    private Double durationSeconds;
    private Double safetyScore;
    private Boolean isMlScored;
    private Boolean isSyntheticDemonstration;
    private String riskLevel;
    private String modelVersion;
    private String notes;
    
    @Data
    @Builder
    public static class CoordinateDto {
        private Double latitude;
        private Double longitude;
    }
}

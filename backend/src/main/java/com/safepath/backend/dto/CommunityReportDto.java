package com.safepath.backend.dto;

import com.safepath.backend.entity.CommunityReport;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTOs for community report operations.
 */
public class CommunityReportDto {

    /** Request to submit a new community report. */
    @Data
    public static class CreateRequest {

        @NotNull(message = "Report type is required")
        private CommunityReport.ReportType reportType;

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        private String description;

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0", message = "Latitude must be >= -90")
        @DecimalMax(value = "90.0",  message = "Latitude must be <= 90")
        private Double latitude;

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
        private Double longitude;

        private OffsetDateTime incidentDatetime;
    }

    /** Response DTO for a community report. */
    @Data
    @Builder
    public static class Response {
        private UUID id;
        private CommunityReport.ReportType reportType;
        private String description;
        private Double latitude;
        private Double longitude;
        private CommunityReport.ReportStatus status;
        private OffsetDateTime incidentDatetime;
        private OffsetDateTime createdAt;
        private Double distanceMeters; // populated in nearby queries
    }

    /** Submit confirmation response. */
    @Data
    @Builder
    public static class SubmitResponse {
        private UUID id;
        private CommunityReport.ReportType reportType;
        private CommunityReport.ReportStatus status;
        private OffsetDateTime createdAt;
        private String message;
    }

    /** Request to verify or reject a report (admin/moderator only). */
    @Data
    public static class VerifyRequest {
        @NotNull(message = "Status is required")
        private CommunityReport.ReportStatus status;

        @Size(max = 500)
        private String verificationNote;
    }
}

package com.safepath.backend.dto;

import com.safepath.backend.entity.SafeZone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public DTO for safe zone response.
 *
 * Data Integrity and Scientific Honesty:
 * - Coordinates are preserved in EPSG:4326.
 * - Missing address or operating hours are represented honestly as null.
 * - Verification status reflects ground-truth or administrative verification.
 * - Includes a prominent disclaimer: facility presence does not guarantee personal safety.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafeZoneDto {
    private UUID id;
    private String name;
    private SafeZone.ZoneType zoneType;
    private SafeZone.VerificationStatus verificationStatus;
    private Double latitude;
    private Double longitude;
    private String address;
    private String phoneNumber;
    private String operatingHours;
    private Boolean is24h;
    private String source;
    private Long osmNodeId;
    private Double distanceMeters; // Populated in nearby queries
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String disclaimer;
}

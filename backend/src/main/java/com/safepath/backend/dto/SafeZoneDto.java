package com.safepath.backend.dto;

import com.safepath.backend.entity.SafeZone;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * DTO for safe zone response.
 */
@Data
@Builder
public class SafeZoneDto {
    private UUID id;
    private String name;
    private SafeZone.ZoneType zoneType;
    private Double latitude;
    private Double longitude;
    private String address;
    private String phoneNumber;
    private String operatingHours;
    private Boolean is24h;
    private Double distanceMeters; // populated in nearby queries
}

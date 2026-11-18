package com.safepath.backend.dto;

import com.safepath.backend.entity.CrimeIncident;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for crime incidents exposed to clients and the routing engine.
 */
public class CrimeIncidentDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String crimeType;
        private String crimeSubtype;
        private Integer severity;
        private Double latitude;
        private Double longitude;
        private CrimeIncident.LocationPrecision locationPrecision;
        private String address;
        private String locality;
        private String city;
        private String source;
        private OffsetDateTime incidentDatetime;
        private OffsetDateTime createdAt;
    }
}

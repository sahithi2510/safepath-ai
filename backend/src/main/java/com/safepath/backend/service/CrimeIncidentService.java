package com.safepath.backend.service;

import com.safepath.backend.dto.CrimeIncidentDto;
import com.safepath.backend.entity.CrimeIncident;
import com.safepath.backend.repository.CommunityReportRepository;
import com.safepath.backend.repository.CrimeIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrimeIncidentService {

    private final CrimeIncidentRepository crimeIncidentRepository;
    private final CommunityReportRepository communityReportRepository;

    @Transactional(readOnly = true)
    public List<CrimeIncidentDto.Response> getCorridorCrimeSpots(
            double minLat, double maxLat, double minLon, double maxLon, int limit) {

        int clampedLimit = Math.max(1, Math.min(limit, 100));
        List<CrimeIncidentDto.Response> results = new ArrayList<>();

        // 1. Fetch from crime_incidents
        try {
            List<CrimeIncident> crimes = crimeIncidentRepository.findInBoundingBox(
                    minLat, maxLat, minLon, maxLon, clampedLimit);
            for (CrimeIncident c : crimes) {
                results.add(toDto(c));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch from crime_incidents table: {}", e.getMessage());
        }

        // 2. Fetch verified community reports within bounding box
        try {
            var reports = communityReportRepository.findAll();
            for (var r : reports) {
                if (r.hasLocation() &&
                        r.getLatitude() >= minLat && r.getLatitude() <= maxLat &&
                        r.getLongitude() >= minLon && r.getLongitude() <= maxLon) {

                    results.add(CrimeIncidentDto.Response.builder()
                            .id(r.getId())
                            .crimeType(r.getReportType() != null ? r.getReportType().name() : "INCIDENT")
                            .crimeSubtype("Community Verified Incident")
                            .severity(r.getReportType() != null && r.getReportType().name().contains("HARASSMENT") ? 4 : 3)
                            .latitude(r.getLatitude())
                            .longitude(r.getLongitude())
                            .locationPrecision(CrimeIncident.LocationPrecision.EXACT_COORDINATE)
                            .address(r.getDescription())
                            .locality(null)
                            .city(null)
                            .source("Community Verified Safety Report")
                            .incidentDatetime(r.getIncidentDatetime() != null ? r.getIncidentDatetime() : r.getCreatedAt())
                            .createdAt(r.getCreatedAt())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to merge community reports into corridor crime spots: {}", e.getMessage());
        }

        return results.stream().limit(clampedLimit).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CrimeIncidentDto.Response> getNearby(double lat, double lon, double radiusMeters, int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, 50));
        return crimeIncidentRepository.findNearby(lat, lon, radiusMeters, clampedLimit)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private CrimeIncidentDto.Response toDto(CrimeIncident c) {
        return CrimeIncidentDto.Response.builder()
                .id(c.getId())
                .crimeType(c.getCrimeType())
                .crimeSubtype(c.getCrimeSubtype())
                .severity(c.getSeverity())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .locationPrecision(c.getLocationPrecision())
                .address(c.getOriginalAddress())
                .locality(c.getLocality())
                .city(c.getCity())
                .source(c.getSource())
                .incidentDatetime(c.getIncidentDatetime())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

package com.safepath.backend.service;

import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.entity.SafeZone;
import com.safepath.backend.repository.SafeZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Safe zone service — provides nearby safe zone lookup using PostGIS.
 */
@Service
@RequiredArgsConstructor
public class SafeZoneService {

    private final SafeZoneRepository safeZoneRepository;

    private static final int MAX_RADIUS_M = 10_000;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    /**
     * Find nearby safe zones within a given radius.
     *
     * @param lat latitude of the search center
     * @param lon longitude of the search center
     * @param radiusMeters search radius (clamped to MAX_RADIUS_M)
     * @param types optional list of zone types to filter by
     * @param limit maximum results (clamped to MAX_LIMIT)
     */
    @Transactional(readOnly = true)
    public List<SafeZoneDto> findNearby(
            double lat, double lon, double radiusMeters,
            List<String> types, int limit) {

        double clampedRadius = Math.min(radiusMeters, MAX_RADIUS_M);
        int clampedLimit = Math.min(limit, MAX_LIMIT);

        List<SafeZone> zones;
        if (types != null && !types.isEmpty()) {
            zones = safeZoneRepository.findNearbyByTypes(lon, lat, clampedRadius, types, clampedLimit);
        } else {
            zones = safeZoneRepository.findNearby(lon, lat, clampedRadius, clampedLimit);
        }

        return zones.stream().map(this::toDto).collect(Collectors.toList());
    }

    private SafeZoneDto toDto(SafeZone zone) {
        return SafeZoneDto.builder()
                .id(zone.getId())
                .name(zone.getName())
                .zoneType(zone.getZoneType())
                .latitude(zone.getLatitude())
                .longitude(zone.getLongitude())
                .address(zone.getAddress())
                .phoneNumber(zone.getPhoneNumber())
                .operatingHours(zone.getOperatingHours())
                .is24h(zone.getIs24h())
                .build();
    }
}

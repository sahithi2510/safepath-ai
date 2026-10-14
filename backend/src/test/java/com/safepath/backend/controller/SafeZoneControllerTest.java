package com.safepath.backend.controller;

import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.entity.SafeZone;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.service.SafeZoneService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SafeZoneController endpoints.
 */
@ExtendWith(MockitoExtension.class)
class SafeZoneControllerTest {

    @Mock
    private SafeZoneService safeZoneService;

    @InjectMocks
    private SafeZoneController safeZoneController;

    private SafeZoneDto mockPoliceDto;
    private UUID sampleId;

    @BeforeEach
    void setUp() {
        sampleId = UUID.randomUUID();
        mockPoliceDto = SafeZoneDto.builder()
                .id(sampleId)
                .name("Koramangala Police Station")
                .zoneType(SafeZone.ZoneType.POLICE_STATION)
                .verificationStatus(SafeZone.VerificationStatus.VERIFIED)
                .latitude(12.9352)
                .longitude(77.6245)
                .address("80 Feet Rd, Koramangala")
                .phoneNumber("080-2294-2565")
                .operatingHours("24 Hours")
                .is24h(true)
                .source("osm")
                .disclaimer(SafeZoneService.SAFE_ZONE_DISCLAIMER)
                .build();
    }

    @Test
    @DisplayName("GET /api/safe-zones: delegates bounding box search to SafeZoneService")
    void testGetSafeZones_BoundingBox_ReturnsOk() {
        when(safeZoneService.findInBoundingBox(12.90, 77.58, 12.96, 77.64, List.of("police"), 20))
                .thenReturn(List.of(mockPoliceDto));

        ResponseEntity<List<SafeZoneDto>> response = safeZoneController.getSafeZones(
                12.90, 77.58, 12.96, 77.64,
                null, null, null,
                List.of("police"), 20);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Koramangala Police Station");
        assertThat(response.getBody().get(0).getVerificationStatus()).isEqualTo(SafeZone.VerificationStatus.VERIFIED);
        verify(safeZoneService).findInBoundingBox(12.90, 77.58, 12.96, 77.64, List.of("police"), 20);
    }

    @Test
    @DisplayName("GET /api/safe-zones: delegates center coordinates proximity search to SafeZoneService")
    void testGetSafeZones_Proximity_ReturnsOk() {
        when(safeZoneService.findNearby(12.93, 77.62, 1500.0, null, 10))
                .thenReturn(List.of(mockPoliceDto));

        ResponseEntity<List<SafeZoneDto>> response = safeZoneController.getSafeZones(
                null, null, null, null,
                12.93, 77.62, 1500.0,
                null, 10);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        verify(safeZoneService).findNearby(12.93, 77.62, 1500.0, null, 10);
    }

    @Test
    @DisplayName("GET /api/safe-zones: throws IllegalArgumentException when neither bbox nor coords are given")
    void testGetSafeZones_NoParams_ThrowsException() {
        assertThatThrownBy(() -> safeZoneController.getSafeZones(
                null, null, null, null,
                null, null, null,
                null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either viewport bounding box");
    }

    @Test
    @DisplayName("GET /api/safe-zones/nearby: delegates to SafeZoneService")
    void testGetNearby_ReturnsOk() {
        when(safeZoneService.findNearby(12.9352, 77.6245, 1000.0, null, 10))
                .thenReturn(List.of(mockPoliceDto));

        ResponseEntity<List<SafeZoneDto>> response = safeZoneController.getNearby(
                12.9352, 77.6245, 1000.0, null, 10);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull().hasSize(1);
        verify(safeZoneService).findNearby(12.9352, 77.6245, 1000.0, null, 10);
    }

    @Test
    @DisplayName("GET /api/safe-zones/{id}: returns individual record when found")
    void testGetById_Found_ReturnsOk() {
        when(safeZoneService.findById(sampleId)).thenReturn(mockPoliceDto);

        ResponseEntity<SafeZoneDto> response = safeZoneController.getById(sampleId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(sampleId);
        verify(safeZoneService).findById(sampleId);
    }

    @Test
    @DisplayName("GET /api/safe-zones/{id}: propagates ResourceNotFoundException when not found")
    void testGetById_NotFound_PropagatesException() {
        UUID unknownId = UUID.randomUUID();
        when(safeZoneService.findById(unknownId))
                .thenThrow(new ResourceNotFoundException("Safe zone not found with ID: " + unknownId));

        assertThatThrownBy(() -> safeZoneController.getById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Safe zone not found with ID");
    }
}

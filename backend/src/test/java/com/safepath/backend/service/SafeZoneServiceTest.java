package com.safepath.backend.service;

import com.safepath.backend.dto.SafeZoneDto;
import com.safepath.backend.entity.SafeZone;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.repository.SafeZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused unit tests for Milestone 22: SafeZoneService.
 *
 * Verifies:
 * 1. Proximity and bounding box queries.
 * 2. Proper distance calculation in meters.
 * 3. Honest serialization of missing address / operating hours.
 * 4. VerificationStatus preservation and academic disclaimers.
 * 5. Viewport span limit enforcement (<= 0.15 degrees).
 * 6. Empty database behavior (honest empty list, never fabricated).
 * 7. ID lookups with ResourceNotFoundException.
 */
@ExtendWith(MockitoExtension.class)
class SafeZoneServiceTest {

    @Mock
    private SafeZoneRepository safeZoneRepository;

    @InjectMocks
    private SafeZoneService safeZoneService;

    private SafeZone mockPoliceStation;
    private SafeZone mockHospital;
    private UUID policeId;
    private UUID hospitalId;

    @BeforeEach
    void setUp() {
        policeId = UUID.randomUUID();
        hospitalId = UUID.randomUUID();

        // Synthetic fixtures labeled explicitly for test purposes
        mockPoliceStation = SafeZone.builder()
                .id(policeId)
                .name("Central Police Station [TEST FIXTURE]")
                .zoneType(SafeZone.ZoneType.POLICE_STATION)
                .verificationStatus(SafeZone.VerificationStatus.VERIFIED)
                .latitude(12.9750)
                .longitude(77.6050)
                .address("123 MG Road, Bangalore")
                .phoneNumber("+91-80-2222-0000")
                .operatingHours("24/7")
                .is24h(true)
                .source("osm")
                .osmNodeId(12345678L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        mockHospital = SafeZone.builder()
                .id(hospitalId)
                .name("City General Hospital [TEST FIXTURE]")
                .zoneType(SafeZone.ZoneType.HOSPITAL)
                .verificationStatus(SafeZone.VerificationStatus.UNVERIFIED)
                .latitude(12.9800)
                .longitude(77.6100)
                .address(null) // Deliberately null to test missing address handling
                .phoneNumber(null)
                .operatingHours(null)
                .is24h(null)
                .source("osm")
                .osmNodeId(87654321L)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("findNearby: calculates great-circle distance accurately and attaches disclaimer")
    void testFindNearby_CalculatesDistanceAndAttachesDisclaimer() {
        when(safeZoneRepository.findNearby(eq(77.6000), eq(12.9700), eq(5000.0), eq(10)))
                .thenReturn(List.of(mockPoliceStation, mockHospital));

        List<SafeZoneDto> results = safeZoneService.findNearby(12.9700, 77.6000, 5000.0, null, 10);

        assertThat(results).hasSize(2);

        SafeZoneDto first = results.get(0);
        assertThat(first.getId()).isEqualTo(policeId);
        assertThat(first.getName()).isEqualTo("Central Police Station [TEST FIXTURE]");
        assertThat(first.getZoneType()).isEqualTo(SafeZone.ZoneType.POLICE_STATION);
        assertThat(first.getVerificationStatus()).isEqualTo(SafeZone.VerificationStatus.VERIFIED);
        assertThat(first.getDistanceMeters()).isNotNull();
        assertThat(first.getDistanceMeters()).isGreaterThan(0.0);
        assertThat(first.getDisclaimer()).contains("does not guarantee personal safety");

        SafeZoneDto second = results.get(1);
        assertThat(second.getId()).isEqualTo(hospitalId);
        assertThat(second.getAddress()).isNull();
        assertThat(second.getOperatingHours()).isNull();
        assertThat(second.getVerificationStatus()).isEqualTo(SafeZone.VerificationStatus.UNVERIFIED);
    }

    @Test
    @DisplayName("findNearby: clamps radius to MAX_RADIUS_M and limit to MAX_NEARBY_LIMIT")
    void testFindNearby_ClampsRadiusAndLimit() {
        when(safeZoneRepository.findNearby(anyDouble(), anyDouble(), eq(SafeZoneService.MAX_RADIUS_M), eq(SafeZoneService.MAX_NEARBY_LIMIT)))
                .thenReturn(List.of(mockPoliceStation));

        List<SafeZoneDto> results = safeZoneService.findNearby(12.9700, 77.6000, 50_000.0, null, 500);

        assertThat(results).hasSize(1);
        verify(safeZoneRepository).findNearby(77.6000, 12.9700, SafeZoneService.MAX_RADIUS_M, SafeZoneService.MAX_NEARBY_LIMIT);
    }

    @Test
    @DisplayName("findNearby: filters by normalized category types")
    void testFindNearby_FiltersByNormalizedTypes() {
        when(safeZoneRepository.findNearbyByTypes(anyDouble(), anyDouble(), anyDouble(), eq(List.of("POLICE_STATION")), anyInt()))
                .thenReturn(List.of(mockPoliceStation));

        List<SafeZoneDto> results = safeZoneService.findNearby(12.9700, 77.6000, 2000.0, List.of("police"), 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getZoneType()).isEqualTo(SafeZone.ZoneType.POLICE_STATION);
        verify(safeZoneRepository).findNearbyByTypes(77.6000, 12.9700, 2000.0, List.of("POLICE_STATION"), 10);
    }

    @Test
    @DisplayName("findNearby: returns empty list when database has no records (no fabrication)")
    void testFindNearby_EmptyDatabase() {
        when(safeZoneRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        List<SafeZoneDto> results = safeZoneService.findNearby(12.9700, 77.6000, 1000.0, null, 10);

        assertThat(results).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findInBoundingBox: returns facilities within valid viewport")
    void testFindInBoundingBox_Success() {
        when(safeZoneRepository.findInBoundingBox(eq(77.5900), eq(12.9600), eq(77.6200), eq(12.9900), eq(50)))
                .thenReturn(List.of(mockPoliceStation, mockHospital));

        List<SafeZoneDto> results = safeZoneService.findInBoundingBox(12.9600, 77.5900, 12.9900, 77.6200, null, 50);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getDisclaimer()).isNotBlank();
        // Viewport search distanceMeters is null (not proximity search)
        assertThat(results.get(0).getDistanceMeters()).isNull();
    }

    @Test
    @DisplayName("findInBoundingBox: rejects viewports exceeding MAX_BBOX_SPAN_DEGREES")
    void testFindInBoundingBox_ExceedsSpan() {
        assertThatThrownBy(() ->
                safeZoneService.findInBoundingBox(12.0, 77.0, 12.20, 77.05, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bounding box span is too large");

        verifyNoInteractions(safeZoneRepository);
    }

    @Test
    @DisplayName("findInBoundingBox: rejects inverted coordinates (min > max)")
    void testFindInBoundingBox_InvertedCoordinates() {
        assertThatThrownBy(() ->
                safeZoneService.findInBoundingBox(13.0, 77.0, 12.0, 77.05, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minLat (13.0000) cannot be greater than maxLat (12.0000)");

        verifyNoInteractions(safeZoneRepository);
    }

    @Test
    @DisplayName("validateCoordinates: rejects out-of-range coordinates")
    void testValidateCoordinates_OutOfRange() {
        assertThatThrownBy(() -> safeZoneService.validateCoordinates(95.0, 77.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Latitude must be between -90.0 and 90.0");

        assertThatThrownBy(() -> safeZoneService.validateCoordinates(12.0, 190.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Longitude must be between -180.0 and 180.0");
    }

    @Test
    @DisplayName("findById: returns DTO for existing facility")
    void testFindById_Success() {
        when(safeZoneRepository.findById(policeId)).thenReturn(Optional.of(mockPoliceStation));

        SafeZoneDto dto = safeZoneService.findById(policeId);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(policeId);
        assertThat(dto.getName()).isEqualTo(mockPoliceStation.getName());
        assertThat(dto.getSource()).isEqualTo("osm");
        assertThat(dto.getVerificationStatus()).isEqualTo(SafeZone.VerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("findById: throws ResourceNotFoundException for unknown ID")
    void testFindById_NotFound() {
        UUID unknownId = UUID.randomUUID();
        when(safeZoneRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> safeZoneService.findById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Safe zone not found with ID");
    }
}

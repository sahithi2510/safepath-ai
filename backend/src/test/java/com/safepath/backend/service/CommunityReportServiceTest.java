package com.safepath.backend.service;

import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.entity.CommunityReport;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.CommunityReportRepository;
import com.safepath.backend.service.rate.ReportRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityReportServiceTest {

    @Mock
    private CommunityReportRepository reportRepository;

    @Mock
    private UserService userService;

    @Spy
    private ReportRateLimiterService rateLimiterService = new ReportRateLimiterService();

    @InjectMocks
    private CommunityReportService reportService;

    private User mockUser;
    private User mockModerator;
    private UserDetails userDetails;
    private UserDetails moderatorDetails;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @BeforeEach
    void setUp() {
        rateLimiterService.resetAll();

        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("reporter@example.com")
                .fullName("Test Reporter")
                .build();

        mockModerator = User.builder()
                .id(UUID.randomUUID())
                .email("moderator@safepath.org")
                .fullName("SafePath Moderator")
                .build();

        userDetails = mock(UserDetails.class);
        org.mockito.Mockito.lenient().when(userDetails.getUsername()).thenReturn("reporter@example.com");

        moderatorDetails = mock(UserDetails.class);
    }

    @Test
    @DisplayName("Submit report with confirmed location creates PENDING report with EXACT_COORDINATE precision")
    void submitReport_withLocation_success() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.POOR_LIGHTING);
        request.setDescription("Streetlights are not working on 100ft road.");
        request.setLatitude(12.9716);
        request.setLongitude(77.5946);
        request.setLocationConfirmed(true);
        request.setIncidentDatetime(OffsetDateTime.now().minusHours(1));

        when(reportRepository.save(any(CommunityReport.class))).thenAnswer(inv -> {
            CommunityReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        CommunityReportDto.SubmitResponse response = reportService.submitReport(userDetails, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(CommunityReport.ReportStatus.PENDING);
        assertThat(response.getLocationPrecision()).isEqualTo(CommunityReport.LocationPrecision.EXACT_COORDINATE);

        ArgumentCaptor<CommunityReport> captor = ArgumentCaptor.forClass(CommunityReport.class);
        verify(reportRepository).save(captor.capture());
        CommunityReport saved = captor.getValue();
        assertThat(saved.getLatitude()).isEqualTo(12.9716);
        assertThat(saved.getLongitude()).isEqualTo(77.5946);
        assertThat(saved.getGeometry()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(CommunityReport.ReportStatus.PENDING);
    }

    @Test
    @DisplayName("Submit report without location creates PENDING report with UNKNOWN precision")
    void submitReport_withoutLocation_success() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.SUSPICIOUS_ACTIVITY);
        request.setDescription("Suspicious activity observed near commercial hub.");
        request.setLatitude(null);
        request.setLongitude(null);

        when(reportRepository.save(any(CommunityReport.class))).thenAnswer(inv -> {
            CommunityReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(OffsetDateTime.now());
            return r;
        });

        CommunityReportDto.SubmitResponse response = reportService.submitReport(userDetails, request);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(CommunityReport.ReportStatus.PENDING);
        assertThat(response.getLocationPrecision()).isEqualTo(CommunityReport.LocationPrecision.UNKNOWN);

        ArgumentCaptor<CommunityReport> captor = ArgumentCaptor.forClass(CommunityReport.class);
        verify(reportRepository).save(captor.capture());
        CommunityReport saved = captor.getValue();
        assertThat(saved.getLatitude()).isNull();
        assertThat(saved.getLongitude()).isNull();
        assertThat(saved.getGeometry()).isNull();
    }

    @Test
    @DisplayName("Submit report with mismatched coordinates throws 400 Bad Request")
    void submitReport_mismatchedCoordinates_throwsBadRequest() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.UNSAFE_ROAD);
        request.setLatitude(12.9716);
        request.setLongitude(null); // Latitude without longitude

        assertThatThrownBy(() -> reportService.submitReport(userDetails, request))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Both latitude and longitude must be provided together")
                .extracting(e -> ((SafePathException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit report with future incident time throws 400 Bad Request")
    void submitReport_futureIncidentTime_throwsBadRequest() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.HARASSMENT);
        request.setIncidentDatetime(OffsetDateTime.now().plusHours(2));

        assertThatThrownBy(() -> reportService.submitReport(userDetails, request))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Incident time cannot be in the future")
                .extracting(e -> ((SafePathException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit report exceeding rate limit throws 429 Too Many Requests")
    void submitReport_rateLimitExceeded_throwsTooManyRequests() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.OTHER);
        request.setDescription("General report");

        when(reportRepository.save(any(CommunityReport.class))).thenAnswer(inv -> {
            CommunityReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        // Submit 5 valid reports within the window
        for (int i = 0; i < 5; i++) {
            reportService.submitReport(userDetails, request);
        }

        // 6th submission must be rejected with 429
        assertThatThrownBy(() -> reportService.submitReport(userDetails, request))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Rate limit exceeded")
                .extracting(e -> ((SafePathException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Verify report from PENDING to VERIFIED succeeds and records audit info")
    void verifyReport_pendingToVerified_success() {
        UUID reportId = UUID.randomUUID();
        when(moderatorDetails.getUsername()).thenReturn("moderator@safepath.org");
        when(userService.getUserByEmail("moderator@safepath.org")).thenReturn(mockModerator);

        CommunityReport existing = CommunityReport.builder()
                .id(reportId)
                .user(mockUser)
                .reportType(CommunityReport.ReportType.BROKEN_STREETLIGHT)
                .status(CommunityReport.ReportStatus.PENDING)
                .latitude(12.9716)
                .longitude(77.5946)
                .createdAt(OffsetDateTime.now().minusHours(2))
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(CommunityReport.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityReportDto.VerifyRequest verifyReq = new CommunityReportDto.VerifyRequest();
        verifyReq.setStatus(CommunityReport.ReportStatus.VERIFIED);
        verifyReq.setVerificationNote("Confirmed by municipal patrol log.");

        CommunityReportDto.Response response = reportService.verifyReport(reportId, verifyReq, moderatorDetails);

        assertThat(response.getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
        assertThat(existing.getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
        assertThat(existing.getVerifiedBy()).isEqualTo(mockModerator.getId());
        assertThat(existing.getVerifiedAt()).isNotNull();
        assertThat(existing.getVerificationNote()).isEqualTo("Confirmed by municipal patrol log.");
    }

    @Test
    @DisplayName("Verify report from PENDING to REJECTED succeeds and records audit info")
    void verifyReport_pendingToRejected_success() {
        UUID reportId = UUID.randomUUID();
        when(moderatorDetails.getUsername()).thenReturn("moderator@safepath.org");
        when(userService.getUserByEmail("moderator@safepath.org")).thenReturn(mockModerator);

        CommunityReport existing = CommunityReport.builder()
                .id(reportId)
                .user(mockUser)
                .reportType(CommunityReport.ReportType.ROAD_BLOCKAGE)
                .status(CommunityReport.ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now().minusHours(1))
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any(CommunityReport.class))).thenAnswer(inv -> inv.getArgument(0));

        CommunityReportDto.VerifyRequest verifyReq = new CommunityReportDto.VerifyRequest();
        verifyReq.setStatus(CommunityReport.ReportStatus.REJECTED);
        verifyReq.setVerificationNote("Duplicate submission.");

        CommunityReportDto.Response response = reportService.verifyReport(reportId, verifyReq, moderatorDetails);

        assertThat(response.getStatus()).isEqualTo(CommunityReport.ReportStatus.REJECTED);
        assertThat(existing.getStatus()).isEqualTo(CommunityReport.ReportStatus.REJECTED);
    }

    @Test
    @DisplayName("Verify report that is already processed throws 400 Bad Request")
    void verifyReport_alreadyProcessed_throwsBadRequest() {
        UUID reportId = UUID.randomUUID();
        CommunityReport existing = CommunityReport.builder()
                .id(reportId)
                .user(mockUser)
                .status(CommunityReport.ReportStatus.VERIFIED) // Already verified!
                .build();

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(existing));

        CommunityReportDto.VerifyRequest verifyReq = new CommunityReportDto.VerifyRequest();
        verifyReq.setStatus(CommunityReport.ReportStatus.REJECTED);

        assertThatThrownBy(() -> reportService.verifyReport(reportId, verifyReq, moderatorDetails))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Report has already been processed")
                .extracting(e -> ((SafePathException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("Get nearby reports only returns VERIFIED reports and never exposes reporter identity")
    void getNearby_onlyReturnsVerifiedReports() {
        Point pt = geometryFactory.createPoint(new Coordinate(77.5946, 12.9716));
        CommunityReport verifiedReport = CommunityReport.builder()
                .id(UUID.randomUUID())
                .user(mockUser)
                .reportType(CommunityReport.ReportType.POOR_LIGHTING)
                .description("Light broken on junction")
                .latitude(12.9716)
                .longitude(77.5946)
                .geometry(pt)
                .locationPrecision(CommunityReport.LocationPrecision.EXACT_COORDINATE)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .createdAt(OffsetDateTime.now())
                .build();

        when(reportRepository.findVerifiedNearby(eq(77.5946), eq(12.9716), anyDouble(), anyInt()))
                .thenReturn(List.of(verifiedReport));

        List<CommunityReportDto.Response> results = reportService.getNearby(12.9716, 77.5946, 500, 20);

        assertThat(results).hasSize(1);
        CommunityReportDto.Response resp = results.get(0);
        assertThat(resp.getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
        assertThat(resp.getReportType()).isEqualTo(CommunityReport.ReportType.POOR_LIGHTING);
        // Ensure user identity is not leaked
        assertThat(resp.toString()).doesNotContain("reporter@example.com");
        assertThat(resp.toString()).doesNotContain("Test Reporter");
    }

    @Test
    @DisplayName("Get my reports returns user submissions across all statuses")
    void getMyReports_returnsUserReports() {
        when(userService.getUserByEmail("reporter@example.com")).thenReturn(mockUser);

        CommunityReport r1 = CommunityReport.builder()
                .id(UUID.randomUUID())
                .user(mockUser)
                .reportType(CommunityReport.ReportType.HARASSMENT)
                .status(CommunityReport.ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();

        CommunityReport r2 = CommunityReport.builder()
                .id(UUID.randomUUID())
                .user(mockUser)
                .reportType(CommunityReport.ReportType.POOR_LIGHTING)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .createdAt(OffsetDateTime.now().minusDays(1))
                .build();

        when(reportRepository.findByUserIdOrderByCreatedAtDesc(eq(mockUser.getId()), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(r1, r2)));

        Page<CommunityReportDto.Response> page = reportService.getMyReports(userDetails, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(CommunityReport.ReportStatus.PENDING);
        assertThat(page.getContent().get(1).getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
    }
}

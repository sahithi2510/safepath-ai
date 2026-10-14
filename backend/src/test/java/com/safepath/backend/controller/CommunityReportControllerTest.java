package com.safepath.backend.controller;

import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.entity.CommunityReport;
import com.safepath.backend.service.CommunityReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityReportControllerTest {

    @Mock
    private CommunityReportService reportService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private CommunityReportController reportController;

    @Test
    @DisplayName("POST /api/reports submits report and returns HTTP 201 Created")
    void testSubmitReport_ReturnsCreated() {
        CommunityReportDto.CreateRequest request = new CommunityReportDto.CreateRequest();
        request.setReportType(CommunityReport.ReportType.POOR_LIGHTING);
        request.setDescription("Light out");

        CommunityReportDto.SubmitResponse mockResponse = CommunityReportDto.SubmitResponse.builder()
                .id(UUID.randomUUID())
                .reportType(CommunityReport.ReportType.POOR_LIGHTING)
                .status(CommunityReport.ReportStatus.PENDING)
                .locationPrecision(CommunityReport.LocationPrecision.UNKNOWN)
                .createdAt(OffsetDateTime.now())
                .message("Report received")
                .build();

        when(reportService.submitReport(userDetails, request)).thenReturn(mockResponse);

        ResponseEntity<CommunityReportDto.SubmitResponse> responseEntity =
                reportController.submitReport(userDetails, request);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getStatus()).isEqualTo(CommunityReport.ReportStatus.PENDING);
        verify(reportService).submitReport(userDetails, request);
    }

    @Test
    @DisplayName("GET /api/reports/nearby returns HTTP 200 with verified reports")
    void testGetNearby_ReturnsOk() {
        CommunityReportDto.Response report1 = CommunityReportDto.Response.builder()
                .id(UUID.randomUUID())
                .reportType(CommunityReport.ReportType.HARASSMENT)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .build();

        when(reportService.getNearby(12.9716, 77.5946, 500.0, 20))
                .thenReturn(List.of(report1));

        ResponseEntity<List<CommunityReportDto.Response>> responseEntity =
                reportController.getNearby(12.9716, 77.5946, 500.0, 20);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).hasSize(1);
        verify(reportService).getNearby(12.9716, 77.5946, 500.0, 20);
    }

    @Test
    @DisplayName("GET /api/reports/my returns HTTP 200 with user's reports page")
    void testGetMyReports_ReturnsOk() {
        Page<CommunityReportDto.Response> mockPage = new PageImpl<>(List.of(
                CommunityReportDto.Response.builder()
                        .id(UUID.randomUUID())
                        .status(CommunityReport.ReportStatus.PENDING)
                        .build()
        ));

        when(reportService.getMyReports(eq(userDetails), any(PageRequest.class)))
                .thenReturn(mockPage);

        ResponseEntity<Page<CommunityReportDto.Response>> responseEntity =
                reportController.getMyReports(userDetails, PageRequest.of(0, 10));

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getTotalElements()).isEqualTo(1);
        verify(reportService).getMyReports(eq(userDetails), any(PageRequest.class));
    }

    @Test
    @DisplayName("PUT /api/reports/{id}/verify verifies report and returns HTTP 200")
    void testVerifyReport_ReturnsOk() {
        UUID reportId = UUID.randomUUID();
        CommunityReportDto.VerifyRequest request = new CommunityReportDto.VerifyRequest();
        request.setStatus(CommunityReport.ReportStatus.VERIFIED);
        request.setVerificationNote("Confirmed");

        CommunityReportDto.Response mockResponse = CommunityReportDto.Response.builder()
                .id(reportId)
                .status(CommunityReport.ReportStatus.VERIFIED)
                .build();

        when(reportService.verifyReport(reportId, request, userDetails)).thenReturn(mockResponse);

        ResponseEntity<CommunityReportDto.Response> responseEntity =
                reportController.verifyReport(reportId, request, userDetails);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getStatus()).isEqualTo(CommunityReport.ReportStatus.VERIFIED);
        verify(reportService).verifyReport(reportId, request, userDetails);
    }
}

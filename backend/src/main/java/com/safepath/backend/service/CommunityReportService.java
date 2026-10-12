package com.safepath.backend.service;

import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.entity.CommunityReport;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.CommunityReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Community report service.
 *
 * IMPORTANT: Unverified (PENDING) reports must NOT directly influence safety scoring.
 * Only VERIFIED reports are returned in the nearby endpoint.
 * See docs/PROJECT-CONTRACT.md Section 7.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityReportService {

    private final CommunityReportRepository reportRepository;
    private final UserService userService;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final int MAX_NEARBY_RADIUS_M = 5000;
    private static final int DEFAULT_NEARBY_RADIUS_M = 500;
    private static final int MAX_NEARBY_RESULTS = 50;

    /**
     * Submit a new community report.
     * Sets status to PENDING. User ID is taken from the JWT, not the request body.
     */
    @Transactional
    public CommunityReportDto.SubmitResponse submitReport(
            UserDetails userDetails, CommunityReportDto.CreateRequest request) {

        User user = userService.getUserByEmail(userDetails.getUsername());

        Point point = GEOMETRY_FACTORY.createPoint(
                new Coordinate(request.getLongitude(), request.getLatitude()));

        CommunityReport report = CommunityReport.builder()
                .user(user)
                .reportType(request.getReportType())
                .description(request.getDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .geometry(point)
                .incidentDatetime(request.getIncidentDatetime())
                .status(CommunityReport.ReportStatus.PENDING)
                .build();

        CommunityReport saved = reportRepository.save(report);
        log.info("Community report submitted by {}: {}", user.getEmail(), saved.getId());

        return CommunityReportDto.SubmitResponse.builder()
                .id(saved.getId())
                .reportType(saved.getReportType())
                .status(saved.getStatus())
                .createdAt(saved.getCreatedAt())
                .message("Report received. Thank you. It will be reviewed by our team.")
                .build();
    }

    /**
     * Get nearby VERIFIED reports.
     * Only VERIFIED reports are returned — unverified reports are not surfaced.
     */
    @Transactional(readOnly = true)
    public List<CommunityReportDto.Response> getNearby(
            double lat, double lon, double radiusMeters, int limit) {

        double clampedRadius = Math.min(radiusMeters, MAX_NEARBY_RADIUS_M);
        int clampedLimit = Math.min(limit, MAX_NEARBY_RESULTS);

        return reportRepository
                .findVerifiedNearby(lon, lat, clampedRadius, clampedLimit)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get the current user's own reports (all statuses).
     */
    @Transactional(readOnly = true)
    public Page<CommunityReportDto.Response> getMyReports(
            UserDetails userDetails, Pageable pageable) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return reportRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    /**
     * Verify or reject a report (MODERATOR or ADMIN only).
     * Authorization is enforced at the controller level via @PreAuthorize.
     */
    @Transactional
    public CommunityReportDto.Response verifyReport(
            UUID reportId, CommunityReportDto.VerifyRequest request, UserDetails moderator) {

        CommunityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        if (report.getStatus() != CommunityReport.ReportStatus.PENDING) {
            throw new SafePathException(
                "Report has already been processed (status: " + report.getStatus() + ")",
                HttpStatus.BAD_REQUEST);
        }

        User moderatorUser = userService.getUserByEmail(moderator.getUsername());
        report.setStatus(request.getStatus());
        report.setVerifiedBy(moderatorUser.getId());
        report.setVerifiedAt(OffsetDateTime.now());
        report.setVerificationNote(request.getVerificationNote());

        return toResponse(reportRepository.save(report));
    }

    private CommunityReportDto.Response toResponse(CommunityReport r) {
        return CommunityReportDto.Response.builder()
                .id(r.getId())
                .reportType(r.getReportType())
                .description(r.getDescription())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .status(r.getStatus())
                .incidentDatetime(r.getIncidentDatetime())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

package com.safepath.backend.service;

import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.entity.CommunityReport;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.CommunityReportRepository;
import com.safepath.backend.service.rate.ReportRateLimiterService;
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
 * NON-NEGOTIABLE INTEGRITY RULES:
 * 1. Unverified (PENDING) reports must NOT directly influence safety scoring or ML models.
 * 2. Only VERIFIED reports are returned in public nearby queries.
 * 3. Never invent coordinates or silently geocode ambiguous locations.
 * 4. Never expose a reporter's private user identity in public responses.
 * 5. Abuse control: Sliding-window rate limiting prevents flooding.
 *
 * See docs/PROJECT-CONTRACT.md Section 7 and AGENTS.md Section 9.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityReportService {

    private final CommunityReportRepository reportRepository;
    private final UserService userService;
    private final ReportRateLimiterService rateLimiterService;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), 4326);

    private static final int MAX_NEARBY_RADIUS_M = 5000;
    private static final int DEFAULT_NEARBY_RADIUS_M = 500;
    private static final int MAX_NEARBY_RESULTS = 50;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    /**
     * Submit a new community safety report.
     * Sets status to PENDING. User ID is taken from the authenticated JWT.
     * Enforces rate limiting, optional location validation, and description sanitization.
     */
    @Transactional
    public CommunityReportDto.SubmitResponse submitReport(
            UserDetails userDetails, CommunityReportDto.CreateRequest request) {

        if (userDetails == null) {
            throw new SafePathException("Authentication required to submit report", HttpStatus.UNAUTHORIZED);
        }

        User user = userService.getUserByEmail(userDetails.getUsername());

        // 1. Rate limiting abuse control
        rateLimiterService.checkAndRecord(user.getId());

        // 2. Location validation: latitude and longitude must both be present or both omitted
        boolean hasLat = request.getLatitude() != null;
        boolean hasLon = request.getLongitude() != null;
        if (hasLat != hasLon) {
            throw new SafePathException(
                    "Both latitude and longitude must be provided together, or both omitted.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Double lat = null;
        Double lon = null;
        Point point = null;
        CommunityReport.LocationPrecision precision = CommunityReport.LocationPrecision.UNKNOWN;

        if (hasLat && hasLon) {
            lat = request.getLatitude();
            lon = request.getLongitude();

            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                throw new SafePathException(
                        "Coordinates out of range: latitude [-90, 90], longitude [-180, 180]",
                        HttpStatus.BAD_REQUEST
                );
            }

            point = GEOMETRY_FACTORY.createPoint(new Coordinate(lon, lat));
            precision = CommunityReport.LocationPrecision.EXACT_COORDINATE;
        }

        // 3. Incident time validation
        if (request.getIncidentDatetime() != null && request.getIncidentDatetime().isAfter(OffsetDateTime.now())) {
            throw new SafePathException(
                    "Incident time cannot be in the future",
                    HttpStatus.BAD_REQUEST
            );
        }

        // 4. Description sanitization (untrusted user input)
        String sanitizedDescription = sanitizeDescription(request.getDescription());

        // 5. Build and persist entity
        CommunityReport report = CommunityReport.builder()
                .user(user)
                .reportType(request.getReportType())
                .description(sanitizedDescription)
                .latitude(lat)
                .longitude(lon)
                .geometry(point)
                .locationPrecision(precision)
                .incidentDatetime(request.getIncidentDatetime())
                .status(CommunityReport.ReportStatus.PENDING)
                .build();

        CommunityReport saved = reportRepository.save(report);
        log.info("Community report submitted by user {} with id {} [precision={}]",
                user.getId(), saved.getId(), precision);

        return CommunityReportDto.SubmitResponse.builder()
                .id(saved.getId())
                .reportType(saved.getReportType())
                .status(saved.getStatus())
                .locationPrecision(saved.getLocationPrecision())
                .createdAt(saved.getCreatedAt())
                .message("Report received. Thank you. It has been submitted for verification.")
                .build();
    }

    /**
     * Get nearby VERIFIED reports.
     * Publicly visible. Only VERIFIED reports with valid geometries are returned.
     * Reporter identity is strictly protected.
     */
    @Transactional(readOnly = true)
    public List<CommunityReportDto.Response> getNearby(
            double lat, double lon, double radiusMeters, int limit) {

        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw new SafePathException("Coordinates out of range for nearby query", HttpStatus.BAD_REQUEST);
        }

        double clampedRadius = Math.max(10, Math.min(radiusMeters, MAX_NEARBY_RADIUS_M));
        int clampedLimit = Math.max(1, Math.min(limit, MAX_NEARBY_RESULTS));

        return reportRepository
                .findVerifiedNearby(lon, lat, clampedRadius, clampedLimit)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get the current user's own reports (all statuses: PENDING, VERIFIED, REJECTED).
     */
    @Transactional(readOnly = true)
    public Page<CommunityReportDto.Response> getMyReports(
            UserDetails userDetails, Pageable pageable) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required to view reports", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        return reportRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    /**
     * Verify or reject a report (MODERATOR or ADMIN only).
     * Enforces valid lifecycle transitions: only PENDING -> VERIFIED | REJECTED.
     */
    @Transactional
    public CommunityReportDto.Response verifyReport(
            UUID reportId, CommunityReportDto.VerifyRequest request, UserDetails moderator) {

        if (moderator == null) {
            throw new SafePathException("Authentication required for moderation", HttpStatus.UNAUTHORIZED);
        }

        if (request.getStatus() == CommunityReport.ReportStatus.PENDING) {
            throw new SafePathException("Target status must be VERIFIED or REJECTED", HttpStatus.BAD_REQUEST);
        }

        CommunityReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        if (report.getStatus() != CommunityReport.ReportStatus.PENDING) {
            throw new SafePathException(
                    "Report has already been processed (status: " + report.getStatus() + ")",
                    HttpStatus.BAD_REQUEST
            );
        }

        User moderatorUser = userService.getUserByEmail(moderator.getUsername());
        report.setStatus(request.getStatus());
        report.setVerifiedBy(moderatorUser.getId());
        report.setVerifiedAt(OffsetDateTime.now());
        report.setVerificationNote(sanitizeDescription(request.getVerificationNote()));

        CommunityReport updated = reportRepository.save(report);
        log.info("Report {} transition to {} by moderator {}",
                reportId, updated.getStatus(), moderatorUser.getId());

        return toResponse(updated);
    }

    /**
     * Sanitize user-provided text descriptions by trimming and stripping HTML/script markup.
     */
    private String sanitizeDescription(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new SafePathException("Description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters", HttpStatus.BAD_REQUEST);
        }
        // Basic HTML tag stripping to prevent XSS in clients
        return trimmed.replaceAll("<[^>]*>", "");
    }

    /**
     * Map entity to response DTO without exposing reporter identity.
     */
    private CommunityReportDto.Response toResponse(CommunityReport r) {
        return CommunityReportDto.Response.builder()
                .id(r.getId())
                .reportType(r.getReportType())
                .description(r.getDescription())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .locationPrecision(r.getLocationPrecision())
                .status(r.getStatus())
                .incidentDatetime(r.getIncidentDatetime())
                .createdAt(r.getCreatedAt())
                .build();
    }
}

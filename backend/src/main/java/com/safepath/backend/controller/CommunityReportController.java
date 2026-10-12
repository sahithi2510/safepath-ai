package com.safepath.backend.controller;

import com.safepath.backend.dto.CommunityReportDto;
import com.safepath.backend.service.CommunityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Community report controller.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Community Reports", description = "Submit and view community safety reports")
@SecurityRequirement(name = "bearerAuth")
public class CommunityReportController {

    private final CommunityReportService reportService;

    @Operation(summary = "Submit a safety report")
    @PostMapping
    public ResponseEntity<CommunityReportDto.SubmitResponse> submitReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CommunityReportDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reportService.submitReport(userDetails, request));
    }

    @Operation(summary = "Get nearby VERIFIED reports")
    @GetMapping("/nearby")
    public ResponseEntity<List<CommunityReportDto.Response>> getNearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(defaultValue = "500") @Min(10) @Max(5000) double radiusMeters,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(reportService.getNearby(lat, lon, radiusMeters, limit));
    }

    @Operation(summary = "Get current user's own reports")
    @GetMapping("/my")
    public ResponseEntity<Page<CommunityReportDto.Response>> getMyReports(
            @AuthenticationPrincipal UserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(reportService.getMyReports(userDetails, pageable));
    }

    @Operation(summary = "Verify or reject a report (MODERATOR/ADMIN only)")
    @PutMapping("/{id}/verify")
    @PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
    public ResponseEntity<CommunityReportDto.Response> verifyReport(
            @PathVariable UUID id,
            @Valid @RequestBody CommunityReportDto.VerifyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(reportService.verifyReport(id, request, userDetails));
    }
}

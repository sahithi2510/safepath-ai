package com.safepath.backend.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safepath.backend.dto.EmergencyContactDto;
import com.safepath.backend.entity.AuditLog;
import com.safepath.backend.entity.EmergencyContact;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.service.AuditLogService;
import com.safepath.backend.service.EmergencyContactService;
import com.safepath.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Emergency controller — manages emergency contacts and SOS events.
 *
 * IMPORTANT: The SOS endpoint logs the event and location for safety audit.
 * It does NOT automatically contact or dispatch emergency services.
 * See docs/limitations.md Section 4.3.
 */
@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Emergency", description = "Emergency contacts and SOS")
@SecurityRequirement(name = "bearerAuth")
public class EmergencyController {

    private final EmergencyContactService contactService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Operation(summary = "Get emergency contacts")
    @GetMapping("/contacts")
    public ResponseEntity<List<EmergencyContactDto.Response>> getContacts(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(contactService.getContacts(userDetails));
    }

    @Operation(summary = "Add emergency contact")
    @PostMapping("/contacts")
    public ResponseEntity<EmergencyContactDto.Response> addContact(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EmergencyContactDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contactService.addContact(userDetails, request));
    }

    @Operation(summary = "Delete emergency contact")
    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<Void> deleteContact(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        contactService.deleteContact(userDetails, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Log an SOS event.
     *
     * LIMITATION: This endpoint logs the SOS event for compliance and safety audit purposes only.
     * It does NOT call police, ambulance, or any emergency service.
     * The app provides contact information for emergency services separately.
     * Automated emergency service integration would require official statutory agreements.
     *
     * See docs/limitations.md Section 4.3.
     */
    @Operation(summary = "Log an SOS event (audit only — does not call emergency services)")
    @PostMapping("/sos")
    public ResponseEntity<SosResponse> sos(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) SosRequest request,
            HttpServletRequest httpServletRequest) {

        User user = userService.getUserByEmail(userDetails.getUsername());

        // Validate coordinate bounds if provided
        if (request != null && (request.getLatitude() != null || request.getLongitude() != null)) {
            if (request.getLatitude() == null || request.getLongitude() == null) {
                throw new SafePathException(
                        "Both latitude and longitude must be provided together", HttpStatus.BAD_REQUEST);
            }
            if (request.getLatitude() < -90.0 || request.getLatitude() > 90.0 ||
                request.getLongitude() < -180.0 || request.getLongitude() > 180.0) {
                throw new SafePathException("Coordinates out of range", HttpStatus.BAD_REQUEST);
            }
        }

        List<EmergencyContact> contacts = contactService.getContactsEntity(user);

        // Build audit log details
        Map<String, Object> detailsMap = new HashMap<>();
        if (request != null) {
            if (request.getLatitude() != null && request.getLongitude() != null) {
                detailsMap.put("latitude", request.getLatitude());
                detailsMap.put("longitude", request.getLongitude());
            }
            if (request.getNote() != null && !request.getNote().isBlank()) {
                detailsMap.put("note", request.getNote().trim());
            }
        }
        detailsMap.put("contactCount", contacts.size());
        detailsMap.put("userEmail", user.getEmail());
        detailsMap.put("triggerSource", "MOBILE_SOS_BUTTON");

        String detailsJson = "{}";
        try {
            detailsJson = objectMapper.writeValueAsString(detailsMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize SOS audit details to JSON: {}", e.getMessage());
        }

        String clientIp = resolveClientIp(httpServletRequest);
        AuditLog auditLog = auditLogService.logAction(
                user, "SOS_TRIGGERED", "EMERGENCY_SOS", null, clientIp, detailsJson);

        return ResponseEntity.ok(SosResponse.builder()
                .logged(true)
                .eventId(auditLog.getId())
                .message("SOS event logged. Please contact emergency services directly: National Helpline 112, Police 100, Ambulance 108.")
                .loggedAt(auditLog.getCreatedAt() != null ? auditLog.getCreatedAt() : OffsetDateTime.now())
                .emergencyContactsCount(contacts.size())
                .build());
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "UNKNOWN";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SosRequest {
        private Double latitude;
        private Double longitude;
        private String note;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SosResponse {
        private boolean logged;
        private UUID eventId;
        private String message;
        private OffsetDateTime loggedAt;
        private Integer emergencyContactsCount;
    }
}

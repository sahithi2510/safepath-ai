package com.safepath.backend.controller;

import com.safepath.backend.dto.EmergencyContactDto;
import com.safepath.backend.service.EmergencyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Emergency controller — manages emergency contacts and SOS events.
 *
 * IMPORTANT: The SOS endpoint logs the event and location only.
 * It does NOT automatically contact emergency services.
 * See docs/limitations.md Section 4.3.
 */
@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
@Tag(name = "Emergency", description = "Emergency contacts and SOS")
@SecurityRequirement(name = "bearerAuth")
public class EmergencyController {

    private final EmergencyContactService contactService;

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
     * LIMITATION: This endpoint logs the SOS event for audit purposes only.
     * It does NOT call police, ambulance, or any emergency service.
     * The app provides contact information for emergency services separately.
     * Automated emergency service integration would require official agreements.
     *
     * See docs/limitations.md Section 4.3.
     */
    @Operation(summary = "Log an SOS event (audit only — does not call emergency services)")
    @PostMapping("/sos")
    public ResponseEntity<SosResponse> sos(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SosRequest request) {

        // TODO Milestone 25: persist SOS event to audit_logs
        // TODO Milestone 25: send push notification to emergency contacts (requires additional setup)

        return ResponseEntity.ok(SosResponse.builder()
                .logged(true)
                .message("SOS event logged. Please contact emergency services directly: Police 100, Ambulance 108.")
                .loggedAt(OffsetDateTime.now())
                .build());
    }

    @Data
    public static class SosRequest {
        private Double latitude;
        private Double longitude;
        private String note;
    }

    @Data
    @lombok.Builder
    public static class SosResponse {
        private boolean logged;
        private String message;
        private OffsetDateTime loggedAt;
    }
}

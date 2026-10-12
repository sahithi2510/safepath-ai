package com.safepath.backend.dto;

import com.safepath.backend.entity.EmergencyContact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTOs for emergency contact operations.
 */
public class EmergencyContactDto {

    /** Request to create a new emergency contact. */
    @Data
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 255)
        private String name;

        @NotBlank(message = "Phone number is required")
        @Size(max = 20)
        private String phoneNumber;

        @Size(max = 100)
        private String relationship;

        private boolean primary;
    }

    /** Response DTO for an emergency contact. */
    @Data
    @Builder
    public static class Response {
        private UUID id;
        private String name;
        private String phoneNumber;
        private String relationship;
        private boolean primary;
        private OffsetDateTime createdAt;

        public static Response from(EmergencyContact contact) {
            return Response.builder()
                    .id(contact.getId())
                    .name(contact.getName())
                    .phoneNumber(contact.getPhoneNumber())
                    .relationship(contact.getRelationship())
                    .primary(contact.isPrimary())
                    .createdAt(contact.getCreatedAt())
                    .build();
        }
    }
}

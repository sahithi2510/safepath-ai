package com.safepath.backend.dto;

import com.safepath.backend.entity.EmergencyContact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTOs for emergency contact operations.
 */
public class EmergencyContactDto {

    /** Request to create a new emergency contact. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        private String name;

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        @Pattern(
            regexp = "^[+]?[(]?[0-9]{1,4}[)]?[-\\s./0-9]{5,20}$",
            message = "Invalid phone number format"
        )
        private String phoneNumber;

        @Size(max = 100, message = "Relationship must not exceed 100 characters")
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

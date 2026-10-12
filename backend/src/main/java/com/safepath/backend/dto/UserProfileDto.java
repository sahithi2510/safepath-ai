package com.safepath.backend.dto;

import com.safepath.backend.entity.RoutePreference;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for user profile response. Used for GET /api/users/me
 */
@Data
@Builder
public class UserProfileDto {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private RoutePreference routePreference;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}

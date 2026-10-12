package com.safepath.backend.dto;

import com.safepath.backend.entity.RoutePreference;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO for JWT login response.
 */
@Data
@Builder
public class AuthResponse {

    private String token;
    private String tokenType;
    private long expiresIn;
    private UserSummaryDto user;

    @Data
    @Builder
    public static class UserSummaryDto {
        private UUID id;
        private String email;
        private String fullName;
        private RoutePreference routePreference;
        private OffsetDateTime createdAt;
    }
}

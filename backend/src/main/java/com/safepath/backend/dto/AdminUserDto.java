package com.safepath.backend.dto;

import com.safepath.backend.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTOs for Admin User and Role Management.
 */
public class AdminUserDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private UUID id;
        private String email;
        private String fullName;
        private String phoneNumber;
        private boolean enabled;
        private Set<String> roles;
        private OffsetDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRoleRequest {
        @NotNull(message = "Role name is required")
        private Role.RoleName role;

        @NotNull(message = "Action is required")
        private Action action;

        public enum Action {
            ADD,
            REMOVE
        }
    }
}


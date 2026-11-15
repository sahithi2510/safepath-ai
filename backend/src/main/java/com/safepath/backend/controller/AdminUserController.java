package com.safepath.backend.controller;

import com.safepath.backend.dto.AdminUserDto;
import com.safepath.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for Admin User Management and Role Delegation.
 * Accessible ONLY to users with ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "User role delegation and administrator controls")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    @Operation(summary = "Search and list users with pagination (ADMIN only)")
    @GetMapping
    public ResponseEntity<Page<AdminUserDto.UserSummary>> listUsers(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(userService.searchUsers(search, pageable));
    }

    @Operation(summary = "Update user role (Promote to Moderator / Demote) (ADMIN only)")
    @PutMapping("/{id}/roles")
    public ResponseEntity<AdminUserDto.UserSummary> updateUserRole(
            @PathVariable UUID id,
            @Valid @RequestBody AdminUserDto.UpdateRoleRequest request,
            @AuthenticationPrincipal UserDetails adminDetails) {
        return ResponseEntity.ok(userService.updateUserRole(id, request, adminDetails));
    }
}

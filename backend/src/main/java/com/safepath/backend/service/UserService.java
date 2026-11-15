package com.safepath.backend.service;

import com.safepath.backend.dto.AdminUserDto;
import com.safepath.backend.dto.UpdateProfileRequest;
import com.safepath.backend.dto.UserProfileDto;
import com.safepath.backend.entity.Role;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.RoleRepository;
import com.safepath.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User profile service — handles profile retrieval, updates, and admin role delegation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    /** Get user entity by email (from security context). */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Get profile DTO for the current user. */
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(UserDetails userDetails) {
        User user = getUserByEmail(userDetails.getUsername());
        return toDto(user);
    }

    /** Update profile fields (only non-null fields are changed). */
    @Transactional
    public UserProfileDto updateProfile(UserDetails userDetails, UpdateProfileRequest request) {
        User user = getUserByEmail(userDetails.getUsername());

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getRoutePreference() != null) {
            user.setRoutePreference(request.getRoutePreference());
        }

        return toDto(userRepository.save(user));
    }

    private UserProfileDto toDto(User user) {
        return UserProfileDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .routePreference(user.getRoutePreference())
                .roles(user.getRoles() != null
                        ? user.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet())
                        : java.util.Collections.emptySet())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Search and list registered users (ADMIN only).
     */
    @Transactional(readOnly = true)
    public Page<AdminUserDto.UserSummary> searchUsers(String query, Pageable pageable) {
        Page<User> users;
        if (query != null && !query.trim().isEmpty()) {
            users = userRepository.searchUsers(query.trim(), pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::toAdminSummary);
    }

    /**
     * Update user role: promote to moderator or demote (ADMIN only).
     */
    @Transactional
    public AdminUserDto.UserSummary updateUserRole(
            UUID targetUserId, AdminUserDto.UpdateRoleRequest request, UserDetails adminDetails) {

        if (adminDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }

        User admin = getUserByEmail(adminDetails.getUsername());
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found: " + targetUserId));

        // Security check: Protect primary owner/admin from self-demotion
        if (admin.getId().equals(targetUser.getId())
                && request.getRole() == Role.RoleName.ROLE_ADMIN
                && request.getAction() == AdminUserDto.UpdateRoleRequest.Action.REMOVE) {
            throw new SafePathException("Admin cannot remove their own ADMIN role", HttpStatus.BAD_REQUEST);
        }

        Role role = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRole()));

        if (request.getAction() == AdminUserDto.UpdateRoleRequest.Action.ADD) {
            targetUser.addRole(role);
        } else {
            targetUser.removeRole(request.getRole());
        }

        User saved = userRepository.save(targetUser);
        log.info("Admin {} updated role {} (action: {}) for user {}",
                admin.getEmail(), request.getRole(), request.getAction(), targetUser.getEmail());

        return toAdminSummary(saved);
    }

    private AdminUserDto.UserSummary toAdminSummary(User u) {
        return AdminUserDto.UserSummary.builder()
                .id(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .phoneNumber(u.getPhoneNumber())
                .enabled(u.isEnabled())
                .roles(u.getRoles() != null
                        ? u.getRoles().stream().map(r -> r.getName().name()).collect(Collectors.toSet())
                        : Collections.emptySet())
                .createdAt(u.getCreatedAt())
                .build();
    }
}

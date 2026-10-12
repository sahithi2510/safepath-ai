package com.safepath.backend.service;

import com.safepath.backend.dto.UpdateProfileRequest;
import com.safepath.backend.dto.UserProfileDto;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User profile service — handles profile retrieval and updates.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

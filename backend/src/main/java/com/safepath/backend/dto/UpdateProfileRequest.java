package com.safepath.backend.dto;

import com.safepath.backend.entity.RoutePreference;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating user profile fields.
 * All fields are optional — only provided fields are updated.
 */
@Data
public class UpdateProfileRequest {

    @Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
    private String fullName;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    private RoutePreference routePreference;
}

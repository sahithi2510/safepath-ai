package com.safepath.backend.controller;

import com.safepath.backend.dto.UpdateProfileRequest;
import com.safepath.backend.dto.UserProfileDto;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("getProfile returns HTTP 200 and current user profile with route preference")
    void testGetProfile_ReturnsOk() {
        UserProfileDto mockProfile = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .email("traveler@example.com")
                .fullName("Jane Traveler")
                .phoneNumber("+91-9876543210")
                .routePreference(RoutePreference.STANDARD)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userService.getProfile(userDetails)).thenReturn(mockProfile);

        ResponseEntity<UserProfileDto> response = userController.getProfile(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEmail()).isEqualTo("traveler@example.com");
        assertThat(response.getBody().getRoutePreference()).isEqualTo(RoutePreference.STANDARD);
        verify(userService).getProfile(userDetails);
    }

    @Test
    @DisplayName("updateProfile returns HTTP 200 and updated profile with new route preference")
    void testUpdateProfile_RoutePreference_ReturnsOk() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setRoutePreference(RoutePreference.SOLO_TRAVELLER);

        UserProfileDto mockUpdated = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .email("traveler@example.com")
                .fullName("Jane Traveler")
                .phoneNumber("+91-9876543210")
                .routePreference(RoutePreference.SOLO_TRAVELLER)
                .updatedAt(OffsetDateTime.now())
                .build();

        when(userService.updateProfile(eq(userDetails), eq(request))).thenReturn(mockUpdated);

        ResponseEntity<UserProfileDto> response = userController.updateProfile(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRoutePreference()).isEqualTo(RoutePreference.SOLO_TRAVELLER);
        verify(userService).updateProfile(userDetails, request);
    }

    @Test
    @DisplayName("updateProfile updates name and phone number successfully")
    void testUpdateProfile_NameAndPhone_ReturnsOk() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Jane Updated");
        request.setPhoneNumber("+91-1122334455");

        UserProfileDto mockUpdated = UserProfileDto.builder()
                .id(UUID.randomUUID())
                .email("traveler@example.com")
                .fullName("Jane Updated")
                .phoneNumber("+91-1122334455")
                .routePreference(RoutePreference.STANDARD)
                .updatedAt(OffsetDateTime.now())
                .build();

        when(userService.updateProfile(eq(userDetails), eq(request))).thenReturn(mockUpdated);

        ResponseEntity<UserProfileDto> response = userController.updateProfile(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFullName()).isEqualTo("Jane Updated");
        assertThat(response.getBody().getPhoneNumber()).isEqualTo("+91-1122334455");
        verify(userService).updateProfile(userDetails, request);
    }
}

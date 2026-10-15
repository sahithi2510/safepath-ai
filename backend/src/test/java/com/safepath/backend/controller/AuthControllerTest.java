package com.safepath.backend.controller;

import com.safepath.backend.dto.AuthResponse;
import com.safepath.backend.dto.LoginRequest;
import com.safepath.backend.dto.RegisterRequest;
import com.safepath.backend.dto.UserProfileDto;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.entity.User;
import com.safepath.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    @DisplayName("register creates user and returns HTTP 201 Created with UserProfileDto")
    void testRegister_ReturnsCreated() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Alice Walker");
        request.setEmail("alice@example.com");
        request.setPassword("Secret123");
        request.setPhoneNumber("+919876543210");

        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        User createdUser = User.builder()
                .id(userId)
                .email("alice@example.com")
                .fullName("Alice Walker")
                .phoneNumber("+919876543210")
                .routePreference(RoutePreference.STANDARD)
                .createdAt(now)
                .build();

        when(authService.register(request)).thenReturn(createdUser);

        ResponseEntity<UserProfileDto> response = authController.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(userId);
        assertThat(response.getBody().getEmail()).isEqualTo("alice@example.com");
        assertThat(response.getBody().getFullName()).isEqualTo("Alice Walker");
        assertThat(response.getBody().getRoutePreference()).isEqualTo(RoutePreference.STANDARD);
        assertThat(response.getBody().getCreatedAt()).isEqualTo(now);
        verify(authService).register(request);
    }

    @Test
    @DisplayName("login validates credentials and returns HTTP 200 OK with AuthResponse")
    void testLogin_ReturnsOk() {
        LoginRequest request = new LoginRequest();
        request.setEmail("alice@example.com");
        request.setPassword("Secret123");

        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        AuthResponse mockResponse = AuthResponse.builder()
                .token("jwt-sample-token")
                .tokenType("Bearer")
                .expiresIn(86400)
                .user(AuthResponse.UserSummaryDto.builder()
                        .id(userId)
                        .email("alice@example.com")
                        .fullName("Alice Walker")
                        .routePreference(RoutePreference.STANDARD)
                        .createdAt(now)
                        .build())
                .build();

        when(authService.login(request)).thenReturn(mockResponse);

        ResponseEntity<AuthResponse> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getToken()).isEqualTo("jwt-sample-token");
        assertThat(response.getBody().getTokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().getUser().getEmail()).isEqualTo("alice@example.com");
        verify(authService).login(request);
    }
}

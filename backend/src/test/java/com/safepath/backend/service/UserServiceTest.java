package com.safepath.backend.service;

import com.safepath.backend.dto.UpdateProfileRequest;
import com.safepath.backend.dto.UserProfileDto;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private static final String TEST_EMAIL = "walker@example.com";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .fullName("Demo Walker")
                .phoneNumber("+91-9876543210")
                .routePreference(RoutePreference.STANDARD)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getProfile returns user profile details and current route preference")
    void testGetProfile_Success() {
        when(userDetails.getUsername()).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfile(userDetails);

        assertThat(profile).isNotNull();
        assertThat(profile.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(profile.getFullName()).isEqualTo("Demo Walker");
        assertThat(profile.getPhoneNumber()).isEqualTo("+91-9876543210");
        assertThat(profile.getRoutePreference()).isEqualTo(RoutePreference.STANDARD);
    }

    @Test
    @DisplayName("getProfile throws ResourceNotFoundException when user does not exist")
    void testGetProfile_UserNotFound() {
        when(userDetails.getUsername()).thenReturn("unknown@example.com");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(userDetails))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("updateProfile updates route preference to SOLO_TRAVELLER")
    void testUpdateProfile_RoutePreferenceSoloTraveller() {
        when(userDetails.getUsername()).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setRoutePreference(RoutePreference.SOLO_TRAVELLER);

        UserProfileDto updated = userService.updateProfile(userDetails, request);

        assertThat(updated.getRoutePreference()).isEqualTo(RoutePreference.SOLO_TRAVELLER);
        assertThat(updated.getFullName()).isEqualTo("Demo Walker"); // Unchanged
        assertThat(updated.getPhoneNumber()).isEqualTo("+91-9876543210"); // Unchanged
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("updateProfile updates route preference to TOURIST and ELDERLY")
    void testUpdateProfile_RoutePreferencesTouristAndElderly() {
        when(userDetails.getUsername()).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest touristReq = new UpdateProfileRequest();
        touristReq.setRoutePreference(RoutePreference.TOURIST);
        UserProfileDto touristProfile = userService.updateProfile(userDetails, touristReq);
        assertThat(touristProfile.getRoutePreference()).isEqualTo(RoutePreference.TOURIST);

        UpdateProfileRequest elderlyReq = new UpdateProfileRequest();
        elderlyReq.setRoutePreference(RoutePreference.ELDERLY);
        UserProfileDto elderlyProfile = userService.updateProfile(userDetails, elderlyReq);
        assertThat(elderlyProfile.getRoutePreference()).isEqualTo(RoutePreference.ELDERLY);
    }

    @Test
    @DisplayName("updateProfile updates fullName and phoneNumber while preserving preference")
    void testUpdateProfile_NameAndPhone() {
        when(userDetails.getUsername()).thenReturn(TEST_EMAIL);
        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName("Updated Walker");
        request.setPhoneNumber("+91-1122334455");

        UserProfileDto updated = userService.updateProfile(userDetails, request);

        assertThat(updated.getFullName()).isEqualTo("Updated Walker");
        assertThat(updated.getPhoneNumber()).isEqualTo("+91-1122334455");
        assertThat(updated.getRoutePreference()).isEqualTo(RoutePreference.STANDARD); // Preserved
    }
}

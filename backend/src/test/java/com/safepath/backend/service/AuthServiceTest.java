package com.safepath.backend.service;

import com.safepath.backend.dto.AuthResponse;
import com.safepath.backend.dto.LoginRequest;
import com.safepath.backend.dto.RegisterRequest;
import com.safepath.backend.entity.Role;
import com.safepath.backend.entity.RoutePreference;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.DuplicateResourceException;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.repository.RoleRepository;
import com.safepath.backend.repository.UserRepository;
import com.safepath.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private Role userRole;

    @BeforeEach
    void setUp() {
        userRole = Role.builder()
                .id(1)
                .name(Role.RoleName.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("register successfully encodes password, assigns ROLE_USER and saves user")
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Bob Smith");
        request.setEmail("Bob@Example.COM"); // Test lowercase normalization
        request.setPassword("Secret123");
        request.setPhoneNumber("+919876543210");

        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("Secret123")).thenReturn("hashedSecret123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(OffsetDateTime.now());
            return u;
        });

        User result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("bob@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashedSecret123");
        assertThat(result.getFullName()).isEqualTo("Bob Smith");
        assertThat(result.getRoles()).contains(userRole);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("register throws DuplicateResourceException when email already exists")
    void testRegister_DuplicateEmail_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Secret123");
        request.setFullName("Existing User");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email is already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register throws ResourceNotFoundException when ROLE_USER does not exist")
    void testRegister_MissingRole_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("Secret123");
        request.setFullName("New User");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(roleRepository.findByName(Role.RoleName.ROLE_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Default role ROLE_USER not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login authenticates credentials and returns JWT token and user summary")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("User@Example.com");
        request.setPassword("Secret123");

        Authentication authMock = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authMock);
        when(jwtTokenProvider.generateToken(authMock)).thenReturn("jwt-mock-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(86400000L);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .fullName("Safe User")
                .routePreference(RoutePreference.STANDARD)
                .createdAt(OffsetDateTime.now())
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("jwt-mock-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(86400L);
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");
        assertThat(response.getUser().getFullName()).isEqualTo("Safe User");
    }

    @Test
    @DisplayName("login throws BadCredentialsException when authentication fails")
    void testLogin_BadCredentials_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("WrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Bad credentials");

        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("login throws ResourceNotFoundException if user entity is missing after auth")
    void testLogin_UserNotFoundAfterAuth_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ghost@example.com");
        request.setPassword("Secret123");

        Authentication authMock = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authMock);
        when(jwtTokenProvider.generateToken(authMock)).thenReturn("jwt-token");
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}

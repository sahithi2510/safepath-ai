package com.safepath.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safepath.backend.dto.EmergencyContactDto;
import com.safepath.backend.entity.AuditLog;
import com.safepath.backend.entity.EmergencyContact;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.service.AuditLogService;
import com.safepath.backend.service.EmergencyContactService;
import com.safepath.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyControllerTest {

    @Mock
    private EmergencyContactService contactService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private EmergencyController emergencyController;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Safe User")
                .build();
    }

    @Test
    @DisplayName("GET /api/emergency/contacts returns 200 OK and contact list")
    void testGetContacts_ReturnsOk() {
        EmergencyContactDto.Response responseDto = EmergencyContactDto.Response.builder()
                .id(UUID.randomUUID())
                .name("Emergency Contact")
                .phoneNumber("+919876543210")
                .primary(true)
                .build();

        when(contactService.getContacts(userDetails)).thenReturn(List.of(responseDto));

        ResponseEntity<List<EmergencyContactDto.Response>> response =
                emergencyController.getContacts(userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Emergency Contact");
    }

    @Test
    @DisplayName("POST /api/emergency/contacts returns 201 Created and created contact")
    void testAddContact_ReturnsCreated() {
        EmergencyContactDto.CreateRequest request = EmergencyContactDto.CreateRequest.builder()
                .name("New Contact")
                .phoneNumber("+919876543211")
                .relationship("Friend")
                .primary(true)
                .build();

        EmergencyContactDto.Response responseDto = EmergencyContactDto.Response.builder()
                .id(UUID.randomUUID())
                .name("New Contact")
                .phoneNumber("+919876543211")
                .relationship("Friend")
                .primary(true)
                .build();

        when(contactService.addContact(userDetails, request)).thenReturn(responseDto);

        ResponseEntity<EmergencyContactDto.Response> response =
                emergencyController.addContact(userDetails, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("New Contact");
    }

    @Test
    @DisplayName("DELETE /api/emergency/contacts/{id} returns 204 No Content")
    void testDeleteContact_ReturnsNoContent() {
        UUID contactId = UUID.randomUUID();
        doNothing().when(contactService).deleteContact(userDetails, contactId);

        ResponseEntity<Void> response = emergencyController.deleteContact(userDetails, contactId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(contactService).deleteContact(userDetails, contactId);
    }

    @Test
    @DisplayName("POST /api/emergency/sos with valid coordinates records audit log and returns 200 OK")
    void testSos_ValidCoordinates_RecordsAuditLogAndReturnsOk() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(testUser);

        EmergencyContact contact = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .name("Contact 1")
                .primary(true)
                .build();
        when(contactService.getContactsEntity(testUser)).thenReturn(List.of(contact));

        UUID auditLogId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(auditLogId)
                .user(testUser)
                .action("SOS_TRIGGERED")
                .createdAt(OffsetDateTime.now())
                .build();

        when(auditLogService.logAction(eq(testUser), eq("SOS_TRIGGERED"), eq("EMERGENCY_SOS"), isNull(), anyString(), anyString()))
                .thenReturn(auditLog);

        EmergencyController.SosRequest request = new EmergencyController.SosRequest(12.9716, 77.5946, "Urgent assistance needed");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("192.168.1.100");

        ResponseEntity<EmergencyController.SosResponse> response =
                emergencyController.sos(userDetails, request, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isLogged()).isTrue();
        assertThat(response.getBody().getEventId()).isEqualTo(auditLogId);
        assertThat(response.getBody().getEmergencyContactsCount()).isEqualTo(1);
        assertThat(response.getBody().getMessage()).contains("112");

        verify(auditLogService).logAction(
                eq(testUser),
                eq("SOS_TRIGGERED"),
                eq("EMERGENCY_SOS"),
                isNull(),
                eq("192.168.1.100"),
                argThat(details -> details.contains("12.9716") && details.contains("77.5946") && details.contains("Urgent assistance needed"))
        );
    }

    @Test
    @DisplayName("POST /api/emergency/sos without coordinates records audit log successfully")
    void testSos_WithoutCoordinates_RecordsAuditLog() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(testUser);
        when(contactService.getContactsEntity(testUser)).thenReturn(Collections.emptyList());

        UUID auditLogId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.builder()
                .id(auditLogId)
                .user(testUser)
                .action("SOS_TRIGGERED")
                .createdAt(OffsetDateTime.now())
                .build();

        when(auditLogService.logAction(eq(testUser), eq("SOS_TRIGGERED"), eq("EMERGENCY_SOS"), isNull(), anyString(), anyString()))
                .thenReturn(auditLog);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        ResponseEntity<EmergencyController.SosResponse> response =
                emergencyController.sos(userDetails, null, httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isLogged()).isTrue();
        assertThat(response.getBody().getEmergencyContactsCount()).isEqualTo(0);
        verify(auditLogService).logAction(eq(testUser), eq("SOS_TRIGGERED"), eq("EMERGENCY_SOS"), isNull(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /api/emergency/sos with out-of-range latitude throws 400 Bad Request")
    void testSos_OutOfRangeLatitude_ThrowsBadRequest() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(testUser);

        EmergencyController.SosRequest request = new EmergencyController.SosRequest(120.0, 77.5946, null); // 120 > 90
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        assertThatThrownBy(() -> emergencyController.sos(userDetails, request, httpRequest))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Coordinates out of range")
                .satisfies(e -> assertThat(((SafePathException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(auditLogService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/emergency/sos with mismatched coordinates (lat without lon) throws 400 Bad Request")
    void testSos_MismatchedCoordinates_ThrowsBadRequest() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(testUser);

        EmergencyController.SosRequest request = new EmergencyController.SosRequest(12.9716, null, null);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        assertThatThrownBy(() -> emergencyController.sos(userDetails, request, httpRequest))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Both latitude and longitude must be provided together")
                .satisfies(e -> assertThat(((SafePathException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(auditLogService, never()).logAction(any(), any(), any(), any(), any(), any());
    }
}

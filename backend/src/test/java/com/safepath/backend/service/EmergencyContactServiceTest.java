package com.safepath.backend.service;

import com.safepath.backend.dto.EmergencyContactDto;
import com.safepath.backend.entity.EmergencyContact;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.EmergencyContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyContactServiceTest {

    @Mock
    private EmergencyContactRepository contactRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private EmergencyContactService contactService;

    private User user;
    private User otherUser;
    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        user = User.builder()
                .id(userId)
                .email("user@example.com")
                .fullName("Safe User")
                .build();

        otherUser = User.builder()
                .id(otherUserId)
                .email("attacker@example.com")
                .fullName("Other User")
                .build();
    }

    @Test
    @DisplayName("getContacts returns only authenticated user's emergency contacts ordered by primary")
    void testGetContacts_ReturnsUserContacts() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        EmergencyContact c1 = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Mom")
                .phoneNumber("+919876543210")
                .relationship("Parent")
                .primary(true)
                .createdAt(OffsetDateTime.now())
                .build();

        EmergencyContact c2 = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Brother")
                .phoneNumber("+919876543211")
                .relationship("Sibling")
                .primary(false)
                .createdAt(OffsetDateTime.now())
                .build();

        when(contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId))
                .thenReturn(List.of(c1, c2));

        List<EmergencyContactDto.Response> results = contactService.getContacts(userDetails);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("Mom");
        assertThat(results.get(0).isPrimary()).isTrue();
        assertThat(results.get(1).getName()).isEqualTo("Brother");
        assertThat(results.get(1).isPrimary()).isFalse();
    }

    @Test
    @DisplayName("addContact automatically makes the first contact primary")
    void testAddContact_FirstContactBecomesPrimary() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);
        when(contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId))
                .thenReturn(Collections.emptyList());

        EmergencyContactDto.CreateRequest request = EmergencyContactDto.CreateRequest.builder()
                .name("Alice")
                .phoneNumber("+919876543210")
                .relationship("Friend")
                .primary(false)
                .build();

        EmergencyContact saved = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Alice")
                .phoneNumber("+919876543210")
                .relationship("Friend")
                .primary(true)
                .createdAt(OffsetDateTime.now())
                .build();

        when(contactRepository.save(any(EmergencyContact.class))).thenReturn(saved);

        EmergencyContactDto.Response response = contactService.addContact(userDetails, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Alice");
        assertThat(response.isPrimary()).isTrue();
        verify(contactRepository).save(argThat(EmergencyContact::isPrimary));
    }

    @Test
    @DisplayName("addContact unsets previous primary when new contact is marked primary")
    void testAddContact_NewPrimaryUnsetsPreviousPrimary() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        EmergencyContact existingPrimary = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("Old Primary")
                .phoneNumber("+919876543210")
                .primary(true)
                .build();

        when(contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId))
                .thenReturn(new ArrayList<>(List.of(existingPrimary)));

        EmergencyContactDto.CreateRequest request = EmergencyContactDto.CreateRequest.builder()
                .name("New Primary")
                .phoneNumber("+919876543212")
                .primary(true)
                .build();

        EmergencyContact saved = EmergencyContact.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name("New Primary")
                .phoneNumber("+919876543212")
                .primary(true)
                .build();

        when(contactRepository.save(any(EmergencyContact.class))).thenReturn(saved);

        EmergencyContactDto.Response response = contactService.addContact(userDetails, request);

        assertThat(response.getName()).isEqualTo("New Primary");
        assertThat(existingPrimary.isPrimary()).isFalse();
        verify(contactRepository).save(existingPrimary);
    }

    @Test
    @DisplayName("addContact rejects addition when user already reached max 5 contacts")
    void testAddContact_ExceedsMaxLimit_ThrowsBadRequest() {
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        List<EmergencyContact> fullList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            fullList.add(EmergencyContact.builder().id(UUID.randomUUID()).user(user).build());
        }
        when(contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId)).thenReturn(fullList);

        EmergencyContactDto.CreateRequest request = EmergencyContactDto.CreateRequest.builder()
                .name("Sixth Contact")
                .phoneNumber("+919876543215")
                .build();

        assertThatThrownBy(() -> contactService.addContact(userDetails, request))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Maximum 5 emergency contacts allowed")
                .satisfies(e -> assertThat(((SafePathException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(contactRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteContact successfully removes user's contact")
    void testDeleteContact_Success() {
        UUID contactId = UUID.randomUUID();
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        EmergencyContact contact = EmergencyContact.builder()
                .id(contactId)
                .user(user)
                .name("Contact to delete")
                .phoneNumber("+919876543210")
                .primary(false)
                .build();

        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));

        contactService.deleteContact(userDetails, contactId);

        verify(contactRepository).delete(contact);
    }

    @Test
    @DisplayName("deleteContact throws 403 Forbidden when user attempts to delete another user's contact")
    void testDeleteContact_UnauthorizedUser_ThrowsForbidden() {
        UUID contactId = UUID.randomUUID();
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        EmergencyContact otherContact = EmergencyContact.builder()
                .id(contactId)
                .user(otherUser) // Belongs to different user!
                .name("Victim Contact")
                .phoneNumber("+919876543210")
                .build();

        when(contactRepository.findById(contactId)).thenReturn(Optional.of(otherContact));

        assertThatThrownBy(() -> contactService.deleteContact(userDetails, contactId))
                .isInstanceOf(SafePathException.class)
                .hasMessageContaining("Access denied")
                .satisfies(e -> assertThat(((SafePathException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(contactRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteContact throws 404 when contact does not exist")
    void testDeleteContact_NotFound_ThrowsNotFound() {
        UUID contactId = UUID.randomUUID();
        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);
        when(contactRepository.findById(contactId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contactService.deleteContact(userDetails, contactId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Contact not found");

        verify(contactRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteContact reassigns primary to next contact when primary contact is deleted")
    void testDeleteContact_PrimaryReassignedToNextContact() {
        UUID primaryId = UUID.randomUUID();
        UUID nextId = UUID.randomUUID();

        when(userDetails.getUsername()).thenReturn("user@example.com");
        when(userService.getUserByEmail("user@example.com")).thenReturn(user);

        EmergencyContact primaryContact = EmergencyContact.builder()
                .id(primaryId)
                .user(user)
                .name("Primary Contact")
                .primary(true)
                .build();

        EmergencyContact secondaryContact = EmergencyContact.builder()
                .id(nextId)
                .user(user)
                .name("Secondary Contact")
                .primary(false)
                .build();

        when(contactRepository.findById(primaryId)).thenReturn(Optional.of(primaryContact));
        when(contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(userId))
                .thenReturn(List.of(secondaryContact));

        contactService.deleteContact(userDetails, primaryId);

        verify(contactRepository).delete(primaryContact);
        assertThat(secondaryContact.isPrimary()).isTrue();
        verify(contactRepository).save(secondaryContact);
    }
}

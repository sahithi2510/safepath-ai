package com.safepath.backend.service;

import com.safepath.backend.dto.EmergencyContactDto;
import com.safepath.backend.entity.EmergencyContact;
import com.safepath.backend.entity.User;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.EmergencyContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Emergency contact service.
 * Manages the user's trusted emergency contacts (used in SOS workflow).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyContactService {

    private final EmergencyContactRepository contactRepository;
    private final UserService userService;

    private static final int MAX_CONTACTS = 5;

    /**
     * Get all emergency contacts for the current user.
     */
    @Transactional(readOnly = true)
    public List<EmergencyContactDto.Response> getContacts(UserDetails userDetails) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        return contactRepository
                .findByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId())
                .stream()
                .map(EmergencyContactDto.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * Add a new emergency contact.
     * Limit: max MAX_CONTACTS per user.
     */
    @Transactional
    public EmergencyContactDto.Response addContact(
            UserDetails userDetails, EmergencyContactDto.CreateRequest request) {

        User user = userService.getUserByEmail(userDetails.getUsername());

        if (contactRepository.countByUserId(user.getId()) >= MAX_CONTACTS) {
            throw new SafePathException(
                "Maximum " + MAX_CONTACTS + " emergency contacts allowed",
                HttpStatus.BAD_REQUEST);
        }

        EmergencyContact contact = EmergencyContact.builder()
                .user(user)
                .name(request.getName())
                .phoneNumber(request.getPhoneNumber())
                .relationship(request.getRelationship())
                .primary(request.isPrimary())
                .build();

        return EmergencyContactDto.Response.from(contactRepository.save(contact));
    }

    /**
     * Delete an emergency contact.
     * Ensures the contact belongs to the requesting user.
     */
    @Transactional
    public void deleteContact(UserDetails userDetails, UUID contactId) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        EmergencyContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));

        if (!contact.getUser().getId().equals(user.getId())) {
            throw new SafePathException("Access denied", HttpStatus.FORBIDDEN);
        }

        contactRepository.delete(contact);
        log.info("Emergency contact {} deleted for user {}", contactId, user.getEmail());
    }
}

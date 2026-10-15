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

        List<EmergencyContact> userContacts = contactRepository
                .findByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId());

        if (userContacts.size() >= MAX_CONTACTS) {
            throw new SafePathException(
                "Maximum " + MAX_CONTACTS + " emergency contacts allowed",
                HttpStatus.BAD_REQUEST);
        }

        boolean makePrimary = request.isPrimary() || userContacts.isEmpty();

        if (makePrimary) {
            for (EmergencyContact existing : userContacts) {
                if (existing.isPrimary()) {
                    existing.setPrimary(false);
                    contactRepository.save(existing);
                }
            }
        }

        EmergencyContact contact = EmergencyContact.builder()
                .user(user)
                .name(request.getName().trim())
                .phoneNumber(request.getPhoneNumber().trim())
                .relationship(request.getRelationship() != null ? request.getRelationship().trim() : null)
                .primary(makePrimary)
                .build();

        EmergencyContact saved = contactRepository.save(contact);
        log.info("Emergency contact {} added for user {}", saved.getId(), user.getEmail());
        return EmergencyContactDto.Response.from(saved);
    }

    /**
     * Delete an emergency contact.
     * Ensures the contact belongs to the requesting user.
     * If the deleted contact was primary and others exist, reassigns primary.
     */
    @Transactional
    public void deleteContact(UserDetails userDetails, UUID contactId) {
        User user = userService.getUserByEmail(userDetails.getUsername());
        EmergencyContact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found"));

        if (!contact.getUser().getId().equals(user.getId())) {
            throw new SafePathException("Access denied", HttpStatus.FORBIDDEN);
        }

        boolean wasPrimary = contact.isPrimary();
        contactRepository.delete(contact);
        log.info("Emergency contact {} deleted for user {}", contactId, user.getEmail());

        if (wasPrimary) {
            List<EmergencyContact> remaining = contactRepository
                    .findByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId());
            if (!remaining.isEmpty()) {
                EmergencyContact nextPrimary = remaining.get(0);
                nextPrimary.setPrimary(true);
                contactRepository.save(nextPrimary);
                log.info("Reassigned primary emergency contact to {} for user {}",
                        nextPrimary.getId(), user.getEmail());
            }
        }
    }

    /**
     * Internal lookup for user contacts (used in SOS workflow).
     */
    @Transactional(readOnly = true)
    public List<EmergencyContact> getContactsEntity(User user) {
        return contactRepository.findByUserIdOrderByPrimaryDescCreatedAtAsc(user.getId());
    }
}

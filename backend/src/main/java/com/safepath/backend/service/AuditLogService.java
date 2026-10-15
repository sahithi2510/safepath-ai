package com.safepath.backend.service;

import com.safepath.backend.entity.AuditLog;
import com.safepath.backend.entity.User;
import com.safepath.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for recording immutable system and user audit events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public AuditLog logAction(User user, String action, String entityType, UUID entityId, String ipAddress, String details) {
        AuditLog auditLog = AuditLog.builder()
                .user(user)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .details(details)
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit event recorded: action={}, user={}, id={}", 
                action, user != null ? user.getEmail() : "anonymous", saved.getId());
        return saved;
    }
}

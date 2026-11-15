package com.safepath.backend.service;

import com.safepath.backend.dto.NotificationDto;
import com.safepath.backend.entity.User;
import com.safepath.backend.entity.UserNotification;
import com.safepath.backend.exception.ResourceNotFoundException;
import com.safepath.backend.exception.SafePathException;
import com.safepath.backend.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing user notifications and status alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final UserNotificationRepository notificationRepository;
    private final UserService userService;

    @Transactional
    public UserNotification createNotification(
            User user, String title, String message, String type, UUID referenceId) {
        if (user == null) {
            return null;
        }

        UserNotification notification = UserNotification.builder()
                .user(user)
                .title(title)
                .message(message)
                .notificationType(type != null ? type : "REPORT_STATUS_CHANGED")
                .referenceId(referenceId)
                .read(false)
                .build();

        UserNotification saved = notificationRepository.save(notification);
        log.info("Created notification {} for user {}: {}", saved.getId(), user.getId(), title);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto.Response> getUserNotifications(
            UserDetails userDetails, Pageable pageable) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<NotificationDto.Response> getUnreadNotifications(UserDetails userDetails) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NotificationDto.UnreadCountResponse getUnreadCount(UserDetails userDetails) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        long count = notificationRepository.countByUserIdAndReadFalse(user.getId());
        return NotificationDto.UnreadCountResponse.builder().unreadCount(count).build();
    }

    @Transactional
    public NotificationDto.Response markAsRead(UUID id, UserDetails userDetails) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        UserNotification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new SafePathException("Forbidden: cannot modify another user's notification", HttpStatus.FORBIDDEN);
        }

        notification.setRead(true);
        UserNotification updated = notificationRepository.save(notification);
        return toResponse(updated);
    }

    @Transactional
    public void markAllAsRead(UserDetails userDetails) {
        if (userDetails == null) {
            throw new SafePathException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        User user = userService.getUserByEmail(userDetails.getUsername());
        List<UserNotification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }

    private NotificationDto.Response toResponse(UserNotification n) {
        return NotificationDto.Response.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .notificationType(n.getNotificationType())
                .referenceId(n.getReferenceId())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

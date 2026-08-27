package com.back.notification.service;

import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.repository.NotificationRepository;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notifications;
    private final NotificationSseEmitterRegistry sseRegistry;
    public NotificationService(NotificationRepository notifications, NotificationSseEmitterRegistry sseRegistry) {
        this.notifications = notifications; this.sseRegistry = sseRegistry;
    }

    public SseEmitter subscribe(Long userId) {
        return sseRegistry.register(userId);
    }

    public PageResponse<NotificationResponse> getNotifications(Long userId, int page, int size) {
        return PageResponse.from(notifications.findByReceiverIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(NotificationResponse::from));
    }

    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notifications.countByReceiverIdAndReadFalse(userId));
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));

        if (!notification.getReceiverId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "본인 알림만 읽음 처리할 수 있습니다.");
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notifications.markAllAsRead(userId);
    }

    public record NotificationResponse(Long id, NotificationType type, Long postId, Long commentId, Long actorId,
                                       String actorNickname, String actorProfileImageUrl, boolean read, LocalDateTime createdAt) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getPostId(), n.getCommentId(),
                    n.getActorId(), n.getActorNickname(), n.getActorProfileImageUrl(), n.isRead(), n.getCreatedAt());
        }
    }
    public record UnreadCountResponse(long count) {}
}

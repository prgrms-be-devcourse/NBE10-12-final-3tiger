package com.back.notification.event;

import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.service.NotificationCommandService;
import com.back.notification.service.NotificationService;
import com.back.notification.service.NotificationSettingService;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
    private final NotificationCommandService notificationCommandService;
    private final NotificationSettingService notificationSettingService;
    private final NotificationSseEmitterRegistry sseRegistry;
    public NotificationEventListener(NotificationCommandService notificationCommandService,
                                    NotificationSettingService notificationSettingService,
                                    NotificationSseEmitterRegistry sseRegistry) {
        this.notificationCommandService = notificationCommandService;
        this.notificationSettingService = notificationSettingService;
        this.sseRegistry = sseRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostLikedEvent event) {
        handle(event.receiverId(), event.actorId(), event.actorNickname(), event.actorProfileImageUrl(),
                NotificationType.LIKE, event.postId(), null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentCreatedEvent event) {
        handle(event.receiverId(), event.actorId(), event.actorNickname(), event.actorProfileImageUrl(),
                NotificationType.COMMENT, event.postId(), event.commentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentUpvotedEvent event) {
        handle(event.receiverId(), event.actorId(), event.actorNickname(), event.actorProfileImageUrl(),
                NotificationType.COMMENT_UPVOTE, event.postId(), event.commentId());
    }

    private void handle(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl,
                        NotificationType type, Long postId, Long commentId) {
        if (receiverId.equals(actorId)) return; // 자기 알림 방지

        Notification notification = notificationCommandService.save(receiverId, actorId,
                actorNickname, actorProfileImageUrl, type, postId, commentId);

        // 알림 이력(DB)은 남기되, 수신자가 알림을 꺼둔 경우 실시간 SSE 전송만 건너뛴다.
        if (!notificationSettingService.isEnabled(receiverId)) return;

        sseRegistry.send(receiverId, NotificationService.NotificationResponse.from(notification));
    }
}

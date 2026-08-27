package com.back.notification.event;

import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.service.NotificationCommandService;
import com.back.notification.service.NotificationService;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
    private final NotificationCommandService notificationCommandService;
    private final NotificationSseEmitterRegistry sseRegistry;
    public NotificationEventListener(NotificationCommandService notificationCommandService, NotificationSseEmitterRegistry sseRegistry) {
        this.notificationCommandService = notificationCommandService; this.sseRegistry = sseRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PostLikedEvent event) {
        if (event.receiverId().equals(event.actorId())) return; // 자기 알림 방지

        Notification notification = notificationCommandService.save(event.receiverId(), event.actorId(),
                event.actorNickname(), event.actorProfileImageUrl(), NotificationType.LIKE, event.postId(), null);
        sseRegistry.send(event.receiverId(), NotificationService.NotificationResponse.from(notification));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentCreatedEvent event) {
        if (event.receiverId().equals(event.actorId())) return;

        Notification notification = notificationCommandService.save(event.receiverId(), event.actorId(),
                event.actorNickname(), event.actorProfileImageUrl(), NotificationType.COMMENT, event.postId(), event.commentId());
        sseRegistry.send(event.receiverId(), NotificationService.NotificationResponse.from(notification));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CommentUpvotedEvent event) {
        if (event.receiverId().equals(event.actorId())) return;

        Notification notification = notificationCommandService.save(event.receiverId(), event.actorId(),
                event.actorNickname(), event.actorProfileImageUrl(), NotificationType.COMMENT_UPVOTE, event.postId(), event.commentId());
        sseRegistry.send(event.receiverId(), NotificationService.NotificationResponse.from(notification));
    }
}

package com.back.notification.event;

import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.service.NotificationCommandService;
import com.back.notification.service.NotificationService;
import com.back.notification.service.NotificationSettingService;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import com.back.pushtoken.service.PushSendService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationEventListener {
    private final NotificationCommandService notificationCommandService;
    private final NotificationSettingService notificationSettingService;
    private final NotificationSseEmitterRegistry sseRegistry;
    private final PushSendService pushSendService;
    public NotificationEventListener(NotificationCommandService notificationCommandService,
                                    NotificationSettingService notificationSettingService,
                                    NotificationSseEmitterRegistry sseRegistry,
                                    PushSendService pushSendService) {
        this.notificationCommandService = notificationCommandService;
        this.notificationSettingService = notificationSettingService;
        this.sseRegistry = sseRegistry;
        this.pushSendService = pushSendService;
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

        // 알림 이력(DB)은 남기되, 수신자가 알림을 꺼둔 경우 실시간 SSE 전송과 OS 푸시를 모두 건너뛴다.
        if (!notificationSettingService.isEnabled(receiverId)) return;

        sseRegistry.send(receiverId, NotificationService.NotificationResponse.from(notification));

        // 이 리스너가 AFTER_COMMIT 시점이라 커밋된 알림만 푸시된다 (실제 발송은 PushSendService에서 @Async).
        pushSendService.sendToUser(receiverId, pushTitle(type), pushBody(type, actorNickname));
    }

    private static String pushTitle(NotificationType type) {
        return switch (type) {
            case LIKE -> "새 좋아요";
            case COMMENT -> "새 댓글";
            case COMMENT_UPVOTE -> "새 공감";
        };
    }

    // 프론트 알림 화면(notificationCopy)과 동일한 문구를 사용한다.
    private static String pushBody(NotificationType type, String actorNickname) {
        String action = switch (type) {
            case LIKE -> "회원님의 게시글을 좋아합니다.";
            case COMMENT -> "회원님의 게시글에 댓글을 남겼습니다.";
            case COMMENT_UPVOTE -> "회원님의 댓글에 공감했습니다.";
        };
        return actorNickname + "님이 " + action;
    }
}

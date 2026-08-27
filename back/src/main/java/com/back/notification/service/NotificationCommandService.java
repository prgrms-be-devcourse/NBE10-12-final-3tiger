package com.back.notification.service;

import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// AFTER_COMMIT 시점(원래 트랜잭션의 커넥션은 이미 반납됨)에 별도의 새 트랜잭션으로 저장을 끝내고
// 커넥션을 곧바로 반납한 뒤에야 (느릴 수 있는) SSE 전송이 일어나도록 저장 책임만 분리한 컴포넌트.
// PostLikeWriter와 동일한 REQUIRES_NEW 격리 패턴.
@Service
public class NotificationCommandService {
    private final NotificationRepository notifications;
    public NotificationCommandService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification save(Long receiverId, Long actorId, String actorNickname, String actorProfileImageUrl,
                             NotificationType type, Long postId, Long commentId) {
        return notifications.save(new Notification(receiverId, actorId, actorNickname, actorProfileImageUrl, type, postId, commentId));
    }
}

package com.back.notification.event;

import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.service.NotificationCommandService;
import com.back.notification.service.NotificationService;
import com.back.notification.service.NotificationSettingService;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationCommandService notificationCommandService;
    @Mock
    private NotificationSettingService notificationSettingService;
    @Mock
    private NotificationSseEmitterRegistry sseRegistry;

    @InjectMocks
    private NotificationEventListener listener;

    private Notification newNotification(Long receiverId, NotificationType type, Long postId, Long commentId) {
        Notification notification = new Notification(receiverId, 2L, "액터닉네임", "https://example.com/actor.jpg", type, postId, commentId);
        ReflectionTestUtils.setField(notification, "id", 100L);
        return notification;
    }

    @Test
    @DisplayName("t1: PostLikedEvent 수신 시 알림을 저장하고 SSE로 전송한다")
    void t1() {
        // given
        PostLikedEvent event = new PostLikedEvent(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", 10L);
        Notification saved = newNotification(1L, NotificationType.LIKE, 10L, null);
        given(notificationCommandService.save(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", NotificationType.LIKE, 10L, null))
                .willReturn(saved);
        given(notificationSettingService.isEnabled(1L)).willReturn(true);

        // when
        listener.on(event);

        // then
        ArgumentCaptor<NotificationService.NotificationResponse> captor = ArgumentCaptor.forClass(NotificationService.NotificationResponse.class);
        verify(sseRegistry).send(eq(1L), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.LIKE);
        assertThat(captor.getValue().postId()).isEqualTo(10L);
        assertThat(captor.getValue().commentId()).isNull();
    }

    @Test
    @DisplayName("t2: CommentCreatedEvent 수신 시 알림을 저장하고 SSE로 전송한다")
    void t2() {
        // given
        CommentCreatedEvent event = new CommentCreatedEvent(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", 10L, 20L);
        Notification saved = newNotification(1L, NotificationType.COMMENT, 10L, 20L);
        given(notificationCommandService.save(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", NotificationType.COMMENT, 10L, 20L))
                .willReturn(saved);
        given(notificationSettingService.isEnabled(1L)).willReturn(true);

        // when
        listener.on(event);

        // then
        ArgumentCaptor<NotificationService.NotificationResponse> captor = ArgumentCaptor.forClass(NotificationService.NotificationResponse.class);
        verify(sseRegistry).send(eq(1L), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.COMMENT);
        assertThat(captor.getValue().commentId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("t3: CommentUpvotedEvent 수신 시 알림을 저장하고 SSE로 전송한다")
    void t3() {
        // given
        CommentUpvotedEvent event = new CommentUpvotedEvent(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", 10L, 20L);
        Notification saved = newNotification(1L, NotificationType.COMMENT_UPVOTE, 10L, 20L);
        given(notificationCommandService.save(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", NotificationType.COMMENT_UPVOTE, 10L, 20L))
                .willReturn(saved);
        given(notificationSettingService.isEnabled(1L)).willReturn(true);

        // when
        listener.on(event);

        // then
        ArgumentCaptor<NotificationService.NotificationResponse> captor = ArgumentCaptor.forClass(NotificationService.NotificationResponse.class);
        verify(sseRegistry).send(eq(1L), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.COMMENT_UPVOTE);
    }

    @Test
    @DisplayName("t4: 자기 자신의 좋아요면 알림을 저장하지도, 전송하지도 않는다")
    void t4() {
        // given
        PostLikedEvent event = new PostLikedEvent(1L, 1L, "본인닉네임", null, 10L);

        // when
        listener.on(event);

        // then
        verify(notificationCommandService, never()).save(any(), any(), any(), any(), any(), any(), any());
        verify(sseRegistry, never()).send(any(), any());
    }

    @Test
    @DisplayName("t5: 자기 자신의 댓글 작성이면 알림을 저장하지도, 전송하지도 않는다")
    void t5() {
        // given
        CommentCreatedEvent event = new CommentCreatedEvent(1L, 1L, "본인닉네임", null, 10L, 20L);

        // when
        listener.on(event);

        // then
        verify(notificationCommandService, never()).save(any(), any(), any(), any(), any(), any(), any());
        verify(sseRegistry, never()).send(any(), any());
    }

    @Test
    @DisplayName("t6: 자기 자신의 댓글 공감이면 알림을 저장하지도, 전송하지도 않는다")
    void t6() {
        // given
        CommentUpvotedEvent event = new CommentUpvotedEvent(1L, 1L, "본인닉네임", null, 10L, 20L);

        // when
        listener.on(event);

        // then
        verify(notificationCommandService, never()).save(any(), any(), any(), any(), any(), any(), any());
        verify(sseRegistry, never()).send(any(), any());
    }

    @Test
    @DisplayName("t7: 수신자가 알림을 꺼둔 경우 알림은 저장하되 SSE 전송은 건너뛴다")
    void t7() {
        // given
        PostLikedEvent event = new PostLikedEvent(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", 10L);
        Notification saved = newNotification(1L, NotificationType.LIKE, 10L, null);
        given(notificationCommandService.save(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", NotificationType.LIKE, 10L, null))
                .willReturn(saved);
        given(notificationSettingService.isEnabled(1L)).willReturn(false);

        // when
        listener.on(event);

        // then
        verify(notificationCommandService).save(1L, 2L, "액터닉네임", "https://example.com/actor.jpg", NotificationType.LIKE, 10L, null);
        verify(sseRegistry, never()).send(any(), any());
    }
}

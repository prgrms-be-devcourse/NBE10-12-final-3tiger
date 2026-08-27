package com.back.notification.service;

import com.back.global.error.ApiException;
import com.back.notification.domain.Notification;
import com.back.notification.domain.NotificationType;
import com.back.notification.repository.NotificationRepository;
import com.back.notification.sse.NotificationSseEmitterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSseEmitterRegistry sseRegistry;

    @InjectMocks
    private NotificationService notificationService;

    private Notification newNotification(Long receiverId) {
        return new Notification(receiverId, 2L, "액터닉네임", "https://example.com/actor.jpg",
                NotificationType.LIKE, 10L, null);
    }

    @Test
    @DisplayName("t1: 본인 알림을 읽음 처리하면 isRead가 true가 된다")
    void t1() {
        // given
        Notification notification = newNotification(1L);
        given(notificationRepository.findById(100L)).willReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(100L, 1L);

        // then
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("t2: 본인 알림이 아니면 읽음 처리 시 403 ApiException이 발생하고 읽음 처리되지 않는다")
    void t2() {
        // given
        Notification notification = newNotification(1L);
        given(notificationRepository.findById(100L)).willReturn(Optional.of(notification));

        // when
        ApiException exception = catchThrowableOfType(() -> notificationService.markAsRead(100L, 2L), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getMessage()).isEqualTo("본인 알림만 읽음 처리할 수 있습니다.");
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("t3: 존재하지 않는 알림을 읽음 처리하면 404 ApiException이 발생한다")
    void t3() {
        // given
        given(notificationRepository.findById(999L)).willReturn(Optional.empty());

        // when
        ApiException exception = catchThrowableOfType(() -> notificationService.markAsRead(999L, 1L), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("존재하지 않는 알림입니다.");
    }

    @Test
    @DisplayName("t4: markAllAsRead는 요청한 receiverId로만 리포지토리 업데이트를 위임한다")
    void t4() {
        // when
        notificationService.markAllAsRead(1L);

        // then
        // 다른 유저 알림을 안 건드리는 건 리포지토리 쿼리의 "WHERE n.receiverId = :receiverId" 조건이
        // 실제로 보장하는 부분이라, 리포지토리를 모킹하는 이 단위 테스트로는 서비스가 정확한
        // receiverId를 그대로 위임하는지까지만 검증 가능하다 (쿼리 자체의 스코프 검증은 DB 붙는
        // 통합 테스트가 필요함).
        verify(notificationRepository).markAllAsRead(1L);
    }
}

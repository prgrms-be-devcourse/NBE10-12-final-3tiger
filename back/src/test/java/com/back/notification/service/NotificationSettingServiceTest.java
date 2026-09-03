package com.back.notification.service;

import com.back.notification.domain.NotificationSetting;
import com.back.notification.repository.NotificationSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

    @Mock
    private NotificationSettingRepository settings;

    @InjectMocks
    private NotificationSettingService service;

    @Test
    @DisplayName("t1: 설정 레코드가 없으면 알림은 기본 활성(on)으로 간주한다")
    void t1() {
        // given
        given(settings.findByUserId(1L)).willReturn(Optional.empty());

        // when & then
        assertThat(service.isEnabled(1L)).isTrue();
    }

    @Test
    @DisplayName("t2: 설정 레코드가 off면 isEnabled는 false를 반환한다")
    void t2() {
        // given
        given(settings.findByUserId(1L)).willReturn(Optional.of(new NotificationSetting(1L, false)));

        // when & then
        assertThat(service.isEnabled(1L)).isFalse();
    }

    @Test
    @DisplayName("t3: 설정 레코드가 없으면 updateEnabled가 새 레코드를 생성한다")
    void t3() {
        // given
        given(settings.findByUserId(1L)).willReturn(Optional.empty());
        given(settings.save(any(NotificationSetting.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        service.updateEnabled(1L, false);

        // then
        ArgumentCaptor<NotificationSetting> captor = ArgumentCaptor.forClass(NotificationSetting.class);
        verify(settings).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("t4: 설정 레코드가 있으면 updateEnabled가 기존 레코드를 갱신한다")
    void t4() {
        // given
        NotificationSetting existing = new NotificationSetting(1L, true);
        given(settings.findByUserId(1L)).willReturn(Optional.of(existing));

        // when
        service.updateEnabled(1L, false);

        // then
        assertThat(existing.isEnabled()).isFalse();
        verify(settings, never()).save(any());
    }
}

package com.back.walk.reservation.event;

import com.back.notification.service.NotificationSettingService;
import com.back.pushtoken.service.PushSendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalkReservationReminderEventListenerTest {

    @Mock NotificationSettingService notificationSettingService;
    @Mock PushSendService pushSendService;
    @InjectMocks WalkReservationReminderEventListener listener;

    @Test
    void sendsPushWhenNotificationsAreEnabled() {
        WalkReservationReminderEvent event =
                new WalkReservationReminderEvent(7L, 13L, "서울숲 코스");
        given(notificationSettingService.isEnabled(7L)).willReturn(true);

        listener.on(event);

        verify(pushSendService).sendToUser(
                7L,
                "산책 예약 시간이에요",
                "서울숲 코스 산책을 시작해 보세요!"
        );
    }

    @Test
    void skipsPushWhenNotificationsAreDisabled() {
        WalkReservationReminderEvent event =
                new WalkReservationReminderEvent(7L, 13L, "서울숲 코스");
        given(notificationSettingService.isEnabled(7L)).willReturn(false);

        listener.on(event);

        verifyNoInteractions(pushSendService);
    }
}

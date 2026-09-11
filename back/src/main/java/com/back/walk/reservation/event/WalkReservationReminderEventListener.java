package com.back.walk.reservation.event;

import com.back.notification.service.NotificationSettingService;
import com.back.pushtoken.service.PushSendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WalkReservationReminderEventListener {

    private final NotificationSettingService notificationSettingService;
    private final PushSendService pushSendService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(WalkReservationReminderEvent event) {
        if (!notificationSettingService.isEnabled(event.userId())) {
            return;
        }
        pushSendService.sendToUser(
                event.userId(),
                "산책 예약 시간이에요",
                event.courseName() + " 산책을 시작해 보세요!"
        );
    }
}

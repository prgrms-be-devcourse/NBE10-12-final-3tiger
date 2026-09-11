package com.back.walk.reservation.scheduler;

import com.back.walk.reservation.service.WalkReservationReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WalkReservationReminderScheduler {

    private final WalkReservationReminderService reminderService;

    public WalkReservationReminderScheduler(WalkReservationReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${walk.reservation.reminder.poll-interval-ms:30000}")
    public void sendDueReminders() {
        reminderService.publishDueReminders();
    }
}

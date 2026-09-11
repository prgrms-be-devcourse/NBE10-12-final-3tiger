package com.back.walk.reservation.service;

import com.back.walk.reservation.domain.WalkReservation;
import com.back.walk.reservation.domain.WalkReservationStatus;
import com.back.walk.reservation.event.WalkReservationReminderEvent;
import com.back.walk.reservation.repository.WalkReservationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class WalkReservationReminderService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int BATCH_SIZE = 100;

    private final WalkReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public WalkReservationReminderService(
            WalkReservationRepository reservationRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.reservationRepository = reservationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public int publishDueReminders() {
        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        List<WalkReservation> reservations = reservationRepository
                .findByStatusAndReminderSentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        WalkReservationStatus.SCHEDULED,
                        now,
                        PageRequest.of(0, BATCH_SIZE)
                );

        for (WalkReservation reservation : reservations) {
            reservation.markReminderSent(now);
            eventPublisher.publishEvent(new WalkReservationReminderEvent(
                    reservation.getUser().getId(),
                    reservation.getId(),
                    reservation.getCourse().getName()
            ));
        }
        return reservations.size();
    }
}

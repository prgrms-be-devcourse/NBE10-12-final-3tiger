package com.back.walk.reservation.service;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import com.back.walk.reservation.domain.WalkReservation;
import com.back.walk.reservation.domain.WalkReservationStatus;
import com.back.walk.reservation.event.WalkReservationReminderEvent;
import com.back.walk.reservation.repository.WalkReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WalkReservationReminderServiceTest {

    @Mock WalkReservationRepository reservationRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks WalkReservationReminderService service;

    @Test
    void marksDueReservationAndPublishesEvent() {
        User user = mock(User.class);
        Course course = new Course("서울숲 코스", "11680", 2_500);
        WalkReservation reservation = mock(WalkReservation.class);
        given(user.getId()).willReturn(7L);
        given(reservation.getUser()).willReturn(user);
        given(reservation.getCourse()).willReturn(course);
        given(reservationRepository
                .findByStatusAndReminderSentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        any(), any(LocalDateTime.class), any(PageRequest.class)
                )).willReturn(List.of(reservation));

        int count = service.publishDueReminders();

        assertThat(count).isEqualTo(1);
        verify(reservation).markReminderSent(any(LocalDateTime.class));
        verify(reservation).complete();
        ArgumentCaptor<WalkReservationReminderEvent> eventCaptor =
                ArgumentCaptor.forClass(WalkReservationReminderEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().userId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().courseName()).isEqualTo("서울숲 코스");
        verify(reservationRepository)
                .findByStatusAndReminderSentAtIsNullAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
                        org.mockito.ArgumentMatchers.eq(WalkReservationStatus.SCHEDULED),
                        any(LocalDateTime.class),
                        org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 100))
                );
    }
}

package com.back.walk.reservation.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.exception.BusinessException;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.walk.reservation.domain.WalkReservation;
import com.back.walk.reservation.domain.WalkReservationStatus;
import com.back.walk.reservation.dto.WalkReservationRequest;
import com.back.walk.reservation.dto.WalkReservationResponse;
import com.back.walk.reservation.repository.WalkReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WalkReservationServiceTest {

    @Mock WalkReservationRepository reservationRepository;
    @Mock UserRepository userRepository;
    @Mock CourseRepository courseRepository;
    @InjectMocks WalkReservationService service;

    @Test
    void reservesFutureWalk() {
        User user = User.createLocal("walker@example.com", "hash", "산책러");
        Course course = new Course("서울숲 코스", "11680", 2_500);
        WalkReservationRequest request = new WalkReservationRequest(
                44L,
                OffsetDateTime.parse("2099-09-15T18:30:00+09:00")
        );
        given(reservationRepository.existsByUser_IdAndCourse_IdAndScheduledAtAndStatus(
                1L, 44L, request.scheduledAt().toLocalDateTime(), WalkReservationStatus.SCHEDULED
        )).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(courseRepository.findById(44L)).willReturn(Optional.of(course));
        given(reservationRepository.saveAndFlush(any(WalkReservation.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        WalkReservationResponse response = service.reserve(1L, request);

        assertThat(response.courseName()).isEqualTo("서울숲 코스");
        assertThat(response.scheduledAt()).isEqualTo("2099-09-15T18:30:00");
        assertThat(response.status()).isEqualTo(WalkReservationStatus.SCHEDULED);
        verify(reservationRepository).saveAndFlush(any(WalkReservation.class));
    }

    @Test
    void rejectsPastReservationBeforeDatabaseLookup() {
        WalkReservationRequest request = new WalkReservationRequest(
                44L,
                OffsetDateTime.parse("2020-01-01T09:00:00+09:00")
        );

        assertThatThrownBy(() -> service.reserve(1L, request))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(reservationRepository, userRepository, courseRepository);
    }

    @Test
    void cancelsOnlyCurrentUsersScheduledReservation() {
        Course course = new Course("서울숲 코스", "11680", 2_500);
        WalkReservation reservation = mock(WalkReservation.class);
        given(reservationRepository.findByIdAndUser_Id(13L, 1L))
                .willReturn(Optional.of(reservation));
        given(reservation.getStatus())
                .willReturn(WalkReservationStatus.SCHEDULED, WalkReservationStatus.CANCELED);
        given(reservation.getCourse()).willReturn(course);

        WalkReservationResponse response = service.cancel(1L, 13L);

        verify(reservation).cancel();
        assertThat(response.status()).isEqualTo(WalkReservationStatus.CANCELED);
    }

    @Test
    void returnsScheduledAndCompletedReservationsWithinMonth() {
        WalkReservation scheduled = reservation(WalkReservationStatus.SCHEDULED);
        WalkReservation completed = reservation(WalkReservationStatus.COMPLETED);
        given(reservationRepository
                .findByUser_IdAndStatusInAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        1L,
                        List.of(WalkReservationStatus.SCHEDULED, WalkReservationStatus.COMPLETED),
                        LocalDateTime.of(2026, 9, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 1, 0, 0)
                )).willReturn(List.of(scheduled, completed));

        List<WalkReservationResponse> responses = service.getMonthly(1L, YearMonth.of(2026, 9));

        assertThat(responses).extracting(WalkReservationResponse::status)
                .containsExactly(WalkReservationStatus.SCHEDULED, WalkReservationStatus.COMPLETED);
    }

    private WalkReservation reservation(WalkReservationStatus status) {
        Course course = new Course("서울숲 코스", "11680", 2_500);
        WalkReservation reservation = mock(WalkReservation.class);
        given(reservation.getCourse()).willReturn(course);
        given(reservation.getScheduledAt()).willReturn(LocalDateTime.of(2026, 9, 15, 18, 30));
        given(reservation.getStatus()).willReturn(status);
        return reservation;
    }
}

package com.back.walk.reservation.service;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.global.api.PageResponse;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.walk.reservation.domain.WalkReservation;
import com.back.walk.reservation.domain.WalkReservationStatus;
import com.back.walk.reservation.dto.WalkReservationRequest;
import com.back.walk.reservation.dto.WalkReservationResponse;
import com.back.walk.reservation.repository.WalkReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class WalkReservationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final WalkReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    public WalkReservationService(
            WalkReservationRepository reservationRepository,
            UserRepository userRepository,
            CourseRepository courseRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    @Transactional
    public WalkReservationResponse reserve(Long userId, WalkReservationRequest request) {
        LocalDateTime scheduledAt = request.scheduledAt()
                .atZoneSameInstant(SEOUL_ZONE)
                .toLocalDateTime();
        if (!scheduledAt.isAfter(LocalDateTime.now(SEOUL_ZONE))) {
            throw new BusinessException(ErrorCode.WALK_RESERVATION_TIME_INVALID);
        }
        if (reservationRepository.existsByUser_IdAndCourse_IdAndScheduledAtAndStatus(
                userId, request.courseId(), scheduledAt, WalkReservationStatus.SCHEDULED)) {
            throw new BusinessException(ErrorCode.WALK_RESERVATION_ALREADY_EXISTS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Course course = courseRepository.findById(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        try {
            WalkReservation saved = reservationRepository.saveAndFlush(
                    new WalkReservation(user, course, scheduledAt)
            );
            return toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.WALK_RESERVATION_ALREADY_EXISTS);
        }
    }

    public PageResponse<WalkReservationResponse> getUpcoming(Long userId, int page, int size) {
        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "scheduledAt")
        );
        return PageResponse.from(reservationRepository
                .findByUser_IdAndStatusAndScheduledAtGreaterThanEqual(
                        userId,
                        WalkReservationStatus.SCHEDULED,
                        LocalDateTime.now(SEOUL_ZONE),
                        pageable
                )
                .map(this::toResponse));
    }

    @Transactional
    public WalkReservationResponse cancel(Long userId, Long reservationId) {
        WalkReservation reservation = reservationRepository.findByIdAndUser_Id(reservationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALK_RESERVATION_NOT_FOUND));
        if (reservation.getStatus() != WalkReservationStatus.SCHEDULED) {
            throw new BusinessException(ErrorCode.WALK_RESERVATION_STATUS_INVALID);
        }
        reservation.cancel();
        return toResponse(reservation);
    }

    private WalkReservationResponse toResponse(WalkReservation reservation) {
        Course course = reservation.getCourse();
        return new WalkReservationResponse(
                reservation.getId(),
                course.getId(),
                course.getName(),
                reservation.getScheduledAt(),
                reservation.getStatus()
        );
    }
}

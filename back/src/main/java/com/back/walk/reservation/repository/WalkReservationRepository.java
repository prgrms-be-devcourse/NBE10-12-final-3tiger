package com.back.walk.reservation.repository;

import com.back.walk.reservation.domain.WalkReservation;
import com.back.walk.reservation.domain.WalkReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface WalkReservationRepository extends JpaRepository<WalkReservation, Long> {

    boolean existsByUser_IdAndCourse_IdAndScheduledAtAndStatus(
            Long userId,
            Long courseId,
            LocalDateTime scheduledAt,
            WalkReservationStatus status
    );

    @EntityGraph(attributePaths = "course")
    Page<WalkReservation> findByUser_IdAndStatusAndScheduledAtGreaterThanEqual(
            Long userId,
            WalkReservationStatus status,
            LocalDateTime now,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "course")
    Optional<WalkReservation> findByIdAndUser_Id(Long reservationId, Long userId);
}

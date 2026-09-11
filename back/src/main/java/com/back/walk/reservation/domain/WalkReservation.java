package com.back.walk.reservation.domain;

import com.back.course.domain.Course;
import com.back.global.entity.BaseEntity;
import com.back.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "walk_reservation")
public class WalkReservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalkReservationStatus status;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    protected WalkReservation() {}

    public WalkReservation(User user, Course course, LocalDateTime scheduledAt) {
        this.user = user;
        this.course = course;
        this.scheduledAt = scheduledAt;
        this.status = WalkReservationStatus.SCHEDULED;
    }

    public void cancel() {
        this.status = WalkReservationStatus.CANCELED;
    }

    public void complete() {
        this.status = WalkReservationStatus.COMPLETED;
    }

    public void markReminderSent(LocalDateTime sentAt) {
        this.reminderSentAt = sentAt;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Course getCourse() { return course; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public WalkReservationStatus getStatus() { return status; }
    public LocalDateTime getReminderSentAt() { return reminderSentAt; }
}

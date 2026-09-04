package com.back.hazard.domain;

import com.back.course.domain.Course;
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
@Table(name = "hazard")
public class Hazard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hazard_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "hazard_type", nullable = false, length = 50)
    private String hazardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HazardStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    protected Hazard() {
    }

    public Hazard(Course course, String hazardType) {
        this.course = course;
        this.hazardType = hazardType;
        this.status = HazardStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void updateStatusByReporterCount(long distinctReporterCount, long threshold) {
        if (distinctReporterCount >= threshold) {
            if (status == HazardStatus.PENDING) {
                status = HazardStatus.ACTIVE;
                activatedAt = LocalDateTime.now();
            }
            return;
        }

        status = HazardStatus.PENDING;
        activatedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public String getHazardType() {
        return hazardType;
    }

    public HazardStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }
}

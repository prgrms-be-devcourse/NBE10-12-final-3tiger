package com.back.hazard.domain;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Column(name = "hazard_type", nullable = false, length = 50)
    private String hazardType;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Hazard() {
    }

    public Hazard(
            Course course,
            User reporter,
            String hazardType,
            String severity,
            String content,
            LocalDateTime expiresAt
    ) {
        this.course = course;
        this.reporter = reporter;
        this.hazardType = hazardType;
        this.severity = severity;
        this.content = content;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public User getReporter() {
        return reporter;
    }

    public String getHazardType() {
        return hazardType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getContent() {
        return content;
    }

    public int getUpvoteCount() {
        return upvoteCount;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void increaseUpvote() {
        this.upvoteCount++;
    }
}

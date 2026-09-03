package com.back.hazard.domain;

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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "hazard_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_hazard_report_hazard_reporter",
                columnNames = {"hazard_id", "reporter_user_id"}
        )
)
public class HazardReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hazard_report_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hazard_id", nullable = false)
    private Hazard hazard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected HazardReport() {
    }

    public HazardReport(
            Hazard hazard,
            User reporter,
            String severity,
            String content,
            double latitude,
            double longitude
    ) {
        this.hazard = hazard;
        this.reporter = reporter;
        this.severity = severity;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Hazard getHazard() {
        return hazard;
    }

    public User getReporter() {
        return reporter;
    }

    public String getSeverity() {
        return severity;
    }

    public String getContent() {
        return content;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

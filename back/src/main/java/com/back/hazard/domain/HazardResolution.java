package com.back.hazard.domain;

import com.back.user.domain.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hazard_resolution", uniqueConstraints = @UniqueConstraint(
        name = "uq_hazard_resolution_hazard_user", columnNames = {"hazard_id", "user_id"}))
public class HazardResolution {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "hazard_resolution_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hazard_id", nullable = false)
    private Hazard hazard;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    protected HazardResolution() {}
    public HazardResolution(Hazard hazard, User user) {
        this.hazard = hazard; this.user = user; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Hazard getHazard() { return hazard; }
    public User getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

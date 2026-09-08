package com.back.point.domain;

import com.back.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "point_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_point_history_user_type_reference",
                columnNames = {"user_id", "type", "reference_id"}
        ),
        indexes = {
                @Index(
                        name = "idx_point_history_user_type_created_at",
                        columnList = "user_id,type,created_at"
                ),
                @Index(
                        name = "idx_point_history_type_reference",
                        columnList = "type,reference_id"
                )
        }
)
public class PointHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PointType type;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PointHistory() {
    }

    public PointHistory(
            User user,
            long amount,
            PointType type,
            Long referenceId,
            LocalDateTime createdAt
    ) {
        if (amount == 0) {
            throw new IllegalArgumentException("포인트 변동액은 0일 수 없습니다.");
        }
        this.user = user;
        this.amount = amount;
        this.type = type;
        this.referenceId = referenceId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public long getAmount() {
        return amount;
    }

    public PointType getType() {
        return type;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

package com.back.notification.domain;

import com.back.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 유저별 알림 전체 on/off 설정. 레코드가 없으면 기본 on 으로 간주한다. */
@Entity
@Table(name = "notification_setting", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_setting_user", columnNames = "user_id"
))
public class NotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private boolean enabled;

    protected NotificationSetting() {}

    public NotificationSetting(Long userId, boolean enabled) {
        this.userId = userId;
        this.enabled = enabled;
    }

    public void enable() { this.enabled = true; }

    public void disable() { this.enabled = false; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public boolean isEnabled() { return enabled; }
}

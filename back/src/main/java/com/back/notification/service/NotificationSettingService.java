package com.back.notification.service;

import com.back.notification.domain.NotificationSetting;
import com.back.notification.repository.NotificationSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationSettingService {
    private final NotificationSettingRepository settings;
    public NotificationSettingService(NotificationSettingRepository settings) {
        this.settings = settings;
    }

    /** 레코드가 없으면 기본값(on)으로 간주한다. */
    public boolean isEnabled(Long userId) {
        return settings.findByUserId(userId)
                .map(NotificationSetting::isEnabled)
                .orElse(true);
    }

    @Transactional
    public void updateEnabled(Long userId, boolean enabled) {
        NotificationSetting setting = settings.findByUserId(userId)
                .orElseGet(() -> settings.save(new NotificationSetting(userId, enabled)));

        if (enabled) {
            setting.enable();
        } else {
            setting.disable();
        }
    }

    public record SettingResponse(boolean enabled) {}
}

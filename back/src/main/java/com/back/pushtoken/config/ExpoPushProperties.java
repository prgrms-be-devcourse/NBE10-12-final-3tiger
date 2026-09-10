package com.back.pushtoken.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 저장된 토큰이 ExponentPushToken 형식이라 FCM 대신 Expo 게이트웨이를 경유한다. */
@Component
@ConfigurationProperties(prefix = "app.push.expo")
@Getter
@Setter
public class ExpoPushProperties {

    private String apiUrl = "https://exp.host/--/api/v2/push/send";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
}

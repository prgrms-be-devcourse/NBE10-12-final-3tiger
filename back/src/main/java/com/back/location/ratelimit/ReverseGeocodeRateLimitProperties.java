package com.back.location.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.map.naver.reverse-geocode-rate-limit")
@Getter
@Setter
public class ReverseGeocodeRateLimitProperties {

    private long limit = 20;
    private Duration window = Duration.ofSeconds(60);
}

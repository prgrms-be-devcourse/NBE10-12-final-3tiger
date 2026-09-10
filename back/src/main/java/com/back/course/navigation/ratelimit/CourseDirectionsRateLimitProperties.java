package com.back.course.navigation.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.map.kakao.directions-rate-limit")
@Getter
@Setter
public class CourseDirectionsRateLimitProperties {

    private long limit = 10;
    private Duration window = Duration.ofSeconds(60);
}

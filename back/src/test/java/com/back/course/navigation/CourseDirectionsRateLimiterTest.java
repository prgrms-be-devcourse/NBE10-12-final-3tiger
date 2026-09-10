package com.back.course.navigation;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.course.navigation.ratelimit.CourseDirectionsRateLimitProperties;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseDirectionsRateLimiterTest {

    @Test
    void allowsRequestsUpToBucketCapacity() {
        CourseDirectionsRateLimiter rateLimiter = rateLimiter(10);

        for (int request = 0; request < 10; request++) {
            assertThatCode(() -> rateLimiter.check("USER:7"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsRequestWhenBucketHasNoToken() {
        CourseDirectionsRateLimiter rateLimiter = rateLimiter(2);
        rateLimiter.check("IP:203.0.113.10");
        rateLimiter.check("IP:203.0.113.10");

        assertThatThrownBy(() -> rateLimiter.check("IP:203.0.113.10"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DIRECTIONS_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void managesIndependentBucketForEachClient() {
        CourseDirectionsRateLimiter rateLimiter = rateLimiter(1);
        rateLimiter.check("IP:203.0.113.10");

        assertThatCode(() -> rateLimiter.check("USER:7"))
                .doesNotThrowAnyException();
    }

    private CourseDirectionsRateLimiter rateLimiter(long limit) {
        CourseDirectionsRateLimitProperties properties =
                new CourseDirectionsRateLimitProperties();
        properties.setLimit(limit);
        properties.setWindow(Duration.ofMinutes(1));
        return new CourseDirectionsRateLimiter(properties);
    }
}

package com.back.location;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.location.ratelimit.ReverseGeocodeRateLimitProperties;
import com.back.location.ratelimit.ReverseGeocodeRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReverseGeocodeRateLimiterTest {

    @Test
    void allowsRequestsUpToBucketCapacity() {
        ReverseGeocodeRateLimiter rateLimiter = rateLimiter(20);

        for (int request = 0; request < 20; request++) {
            assertThatCode(() -> rateLimiter.check("IP:127.0.0.1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsRequestWhenBucketHasNoToken() {
        ReverseGeocodeRateLimiter rateLimiter = rateLimiter(2);
        rateLimiter.check("IP:127.0.0.1");
        rateLimiter.check("IP:127.0.0.1");

        assertThatThrownBy(() -> rateLimiter.check("IP:127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REVERSE_GEOCODE_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void managesIndependentBucketForEachClient() {
        ReverseGeocodeRateLimiter rateLimiter = rateLimiter(1);
        rateLimiter.check("IP:127.0.0.1");

        assertThatCode(() -> rateLimiter.check("USER:1"))
                .doesNotThrowAnyException();
    }

    private ReverseGeocodeRateLimiter rateLimiter(long limit) {
        ReverseGeocodeRateLimitProperties properties =
                new ReverseGeocodeRateLimitProperties();
        properties.setLimit(limit);
        properties.setWindow(Duration.ofMinutes(1));
        return new ReverseGeocodeRateLimiter(properties);
    }
}

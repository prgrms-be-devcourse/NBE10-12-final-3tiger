package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceSearchRateLimiterTest {

    @Test
    void allowsRequestsUpToBucketCapacity() {
        PlaceSearchRateLimiter rateLimiter = rateLimiter(30);

        for (int request = 0; request < 30; request++) {
            assertThatCode(() -> rateLimiter.check("IP:127.0.0.1"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsRequestWhenBucketHasNoToken() {
        PlaceSearchRateLimiter rateLimiter = rateLimiter(2);
        rateLimiter.check("IP:127.0.0.1");
        rateLimiter.check("IP:127.0.0.1");

        assertThatThrownBy(() -> rateLimiter.check("IP:127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void managesIndependentBucketForEachClient() {
        PlaceSearchRateLimiter rateLimiter = rateLimiter(1);
        rateLimiter.check("IP:127.0.0.1");

        assertThatCode(() -> rateLimiter.check("USER:1"))
                .doesNotThrowAnyException();
    }

    private PlaceSearchRateLimiter rateLimiter(long limit) {
        PlaceSearchRateLimitProperties properties =
                new PlaceSearchRateLimitProperties();
        properties.setLimit(limit);
        properties.setWindow(Duration.ofMinutes(1));
        return new PlaceSearchRateLimiter(properties);
    }
}

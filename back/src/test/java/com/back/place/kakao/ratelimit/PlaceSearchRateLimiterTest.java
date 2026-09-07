package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("unchecked")
class PlaceSearchRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final PlaceSearchRateLimitProperties properties = properties();
    private final PlaceSearchLocalRateLimiter localRateLimiter =
            new PlaceSearchLocalRateLimiter(properties);
    private final PlaceSearchRateLimiter rateLimiter =
            new PlaceSearchRateLimiter(redisTemplate, properties, localRateLimiter);

    private static PlaceSearchRateLimitProperties properties() {
        PlaceSearchRateLimitProperties properties = new PlaceSearchRateLimitProperties();
        properties.setLimit(30);
        properties.setFallbackLimit(10);
        properties.setWindow(java.time.Duration.ofSeconds(60));
        return properties;
    }

    @Test
    void allowsRequestAtLimitAndUsesExpectedRedisKeyAndWindow() {
        given(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any()
        )).willReturn(30L);

        assertThatCode(() -> rateLimiter.check("IP:127.0.0.1"))
                .doesNotThrowAnyException();

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("RATE_LIMIT:PLACE:IP:127.0.0.1")),
                eq("60")
        );
    }

    @Test
    void rejectsRequestOverLimit() {
        given(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any()
        )).willReturn(31L);

        assertThatThrownBy(() -> rateLimiter.check("IP:127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void usesLocalFallbackWhenRedisReturnsNoCount() {
        given(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any()
        )).willReturn(null);

        assertThatCode(() -> rateLimiter.check("IP:127.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    void usesStricterLocalLimitWhenRedisIsUnavailable() {
        given(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any()
        )).willThrow(new RedisConnectionFailureException("Redis unavailable"));

        for (int request = 0; request < 10; request++) {
            assertThatCode(() -> rateLimiter.check("IP:127.0.0.1"))
                    .doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> rateLimiter.check("IP:127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED));
    }
}

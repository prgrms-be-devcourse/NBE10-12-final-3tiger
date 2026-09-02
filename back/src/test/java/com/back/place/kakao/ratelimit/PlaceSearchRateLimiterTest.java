package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
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
    private final PlaceSearchRateLimiter rateLimiter = new PlaceSearchRateLimiter(redisTemplate);

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
    void failsWhenRedisReturnsNoCount() {
        given(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any()
        )).willReturn(null);

        assertThatThrownBy(() -> rateLimiter.check("IP:127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("장소 검색 요청 횟수를 확인할 수 없습니다.");
    }
}

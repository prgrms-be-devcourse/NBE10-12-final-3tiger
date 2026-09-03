package com.back.course.navigation;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CourseDirectionsRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final CourseDirectionsRateLimiter rateLimiter =
            new CourseDirectionsRateLimiter(redisTemplate);

    @Test
    void allowsTenthRequestAndUsesDirectionsKey() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .willReturn(10L);

        rateLimiter.check("USER:7");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), anyString());
        assertThat(keys.getValue()).containsExactly("RATE_LIMIT:DIRECTIONS:USER:7");
    }

    @Test
    void rejectsRequestOverLimit() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .willReturn(11L);

        assertThatThrownBy(() -> rateLimiter.check("IP:203.0.113.10"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.DIRECTIONS_RATE_LIMIT_EXCEEDED));
    }

    @Test
    void failsClosedWhenRedisReturnsNull() {
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .willReturn(null);

        assertThatThrownBy(() -> rateLimiter.check("USER:7"))
                .isInstanceOf(IllegalStateException.class);
    }
}

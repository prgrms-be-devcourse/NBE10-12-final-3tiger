package com.back.course.navigation.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CourseDirectionsRateLimiter {

    private static final long LIMIT = 10;
    private static final long WINDOW_SECONDS = 60;

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of("""
            local count = redis.call("INCR", KEYS[1])
            if count == 1 then
                redis.call("EXPIRE", KEYS[1], tonumber(ARGV[1]))
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void check(String clientId) {
        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of("RATE_LIMIT:DIRECTIONS:" + clientId),
                String.valueOf(WINDOW_SECONDS)
        );

        if (count == null) {
            throw new IllegalStateException("길찾기 요청 횟수를 확인할 수 없습니다.");
        }
        if (count > LIMIT) {
            throw new BusinessException(ErrorCode.DIRECTIONS_RATE_LIMIT_EXCEEDED);
        }
    }
}

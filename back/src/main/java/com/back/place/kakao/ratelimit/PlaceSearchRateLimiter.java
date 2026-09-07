package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlaceSearchRateLimiter {

    private static final RedisScript<Long> RATE_LIMIT_SCRIPT =
            RedisScript.of("""
                    local count = redis.call("INCR", KEYS[1])
                    
                    if count == 1 then
                        redis.call(
                            "EXPIRE",
                            KEYS[1],
                            tonumber(ARGV[1])
                        )
                    end
                    
                    return count
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final PlaceSearchRateLimitProperties properties;
    private final PlaceSearchLocalRateLimiter localRateLimiter;

    public void check(String clientId) {
        String key = "RATE_LIMIT:PLACE:" + clientId;

        Long count;
        try {
            count = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    List.of(key),
                    String.valueOf(properties.getWindow().toSeconds())
            );
        } catch (DataAccessException exception) {
            localRateLimiter.check(clientId);
            return;
        }

        if (count == null) {
            localRateLimiter.check(clientId);
            return;
        }

        if (count > properties.getLimit()) {
            throw new BusinessException(
                    ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED
            );
        }
    }
}

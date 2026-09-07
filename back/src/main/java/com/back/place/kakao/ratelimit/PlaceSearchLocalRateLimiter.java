package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class PlaceSearchLocalRateLimiter {

    private static final long MAX_CLIENTS = 100_000;

    private final Cache<String, AtomicLong> requestCounts;
    private final long limit;

    public PlaceSearchLocalRateLimiter(
            PlaceSearchRateLimitProperties properties
    ) {
        this.limit = properties.getFallbackLimit();
        this.requestCounts = Caffeine.newBuilder()
                .maximumSize(MAX_CLIENTS)
                .expireAfterWrite(properties.getWindow())
                .build();
    }

    public void check(String clientId) {
        AtomicLong count = requestCounts.get(
                clientId,
                ignored -> new AtomicLong()
        );

        if (count.incrementAndGet() > limit) {
            throw new BusinessException(
                    ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED
            );
        }
    }
}

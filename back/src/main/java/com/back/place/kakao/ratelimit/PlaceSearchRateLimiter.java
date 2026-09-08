package com.back.place.kakao.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class PlaceSearchRateLimiter {

    private static final long MAX_CLIENTS = 100_000;

    private final Cache<String, Bucket> buckets;
    private final long capacity;
    private final Duration refillPeriod;

    public PlaceSearchRateLimiter(
            PlaceSearchRateLimitProperties properties
    ) {
        this.capacity = properties.getLimit();
        this.refillPeriod = properties.getWindow();
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_CLIENTS)
                .expireAfterAccess(refillPeriod.multipliedBy(2))
                .build();
    }

    public void check(String clientId) {
        Bucket bucket = buckets.get(clientId, ignored -> newBucket());

        if (!bucket.tryConsume(1)) {
            throw new BusinessException(
                    ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED
            );
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod))
                .build();
    }
}

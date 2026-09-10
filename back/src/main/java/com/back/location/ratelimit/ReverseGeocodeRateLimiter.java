package com.back.location.ratelimit;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ReverseGeocodeRateLimiter {

    private static final long MAX_CLIENTS = 100_000;

    private final Cache<String, Bucket> buckets;
    private final long capacity;
    private final Duration refillPeriod;

    public ReverseGeocodeRateLimiter(ReverseGeocodeRateLimitProperties properties) {
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
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_RATE_LIMIT_EXCEEDED);
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

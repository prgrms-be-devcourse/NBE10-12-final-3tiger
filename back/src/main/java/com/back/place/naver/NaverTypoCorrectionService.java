package com.back.place.naver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class NaverTypoCorrectionService {

    private static final String NO_CORRECTION = "";

    private final NaverTypoCorrectionClient client;
    private final Cache<String, String> corrections;

    public NaverTypoCorrectionService(NaverTypoCorrectionClient client) {
        this.client = client;
        this.corrections = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofHours(1))
                .build();
    }

    public String correct(String query) {
        return corrections.get(query, key -> {
            String corrected = client.correct(key);
            return corrected == null ? NO_CORRECTION : corrected.trim();
        });
    }
}

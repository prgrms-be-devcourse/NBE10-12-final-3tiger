package com.back.place.kakao.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PlaceSearchRateLimitInterceptor
        implements HandlerInterceptor {

    private final PlaceSearchRateLimiter rateLimiter;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        String clientId = resolveClientId(request);
        rateLimiter.check(clientId);

        return true;
    }

    private String resolveClientId(HttpServletRequest request) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.getPrincipal() instanceof Long userId) {
            return "USER:" + userId;
        }

        return "IP:" + request.getRemoteAddr();
    }
}

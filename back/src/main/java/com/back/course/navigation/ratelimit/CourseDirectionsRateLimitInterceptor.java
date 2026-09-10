package com.back.course.navigation.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class CourseDirectionsRateLimitInterceptor implements HandlerInterceptor {

    private final CourseDirectionsRateLimiter rateLimiter;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        rateLimiter.check(resolveClientId(request));
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

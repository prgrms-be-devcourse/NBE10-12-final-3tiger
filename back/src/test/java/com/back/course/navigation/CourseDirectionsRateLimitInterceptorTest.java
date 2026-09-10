package com.back.course.navigation;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimitInterceptor;
import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CourseDirectionsRateLimitInterceptorTest {

    private final CourseDirectionsRateLimiter rateLimiter =
            mock(CourseDirectionsRateLimiter.class);
    private final CourseDirectionsRateLimitInterceptor interceptor =
            new CourseDirectionsRateLimitInterceptor(rateLimiter);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usesRemoteAddressForAnonymousRequest() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");

        boolean allowed = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()
        );

        assertThat(allowed).isTrue();
        verify(rateLimiter).check("IP:203.0.113.10");
    }

    @Test
    void usesUserIdForAuthenticatedRequest() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, List.of())
        );

        boolean allowed = interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new Object()
        );

        assertThat(allowed).isTrue();
        verify(rateLimiter).check("USER:7");
    }
}

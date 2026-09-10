package com.back.support;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.location.ratelimit.ReverseGeocodeRateLimiter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public abstract class RateLimitWebMvcTestSupport {

    @MockitoBean
    protected CourseDirectionsRateLimiter courseDirectionsRateLimiter;

    @MockitoBean
    protected ReverseGeocodeRateLimiter reverseGeocodeRateLimiter;
}

package com.back.global.config;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimitInterceptor;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.location.ratelimit.ReverseGeocodeRateLimitInterceptor;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserIdResolver resolver;
    private final PlaceSearchRateLimitInterceptor placeSearchRateLimitInterceptor;
    private final CourseDirectionsRateLimitInterceptor courseDirectionsRateLimitInterceptor;
    private final ReverseGeocodeRateLimitInterceptor reverseGeocodeRateLimitInterceptor;

    public WebConfig(
            CurrentUserIdResolver resolver,
            PlaceSearchRateLimitInterceptor placeSearchRateLimitInterceptor,
            CourseDirectionsRateLimitInterceptor courseDirectionsRateLimitInterceptor,
            ReverseGeocodeRateLimitInterceptor reverseGeocodeRateLimitInterceptor
    ) {
        this.resolver = resolver;
        this.placeSearchRateLimitInterceptor = placeSearchRateLimitInterceptor;
        this.courseDirectionsRateLimitInterceptor = courseDirectionsRateLimitInterceptor;
        this.reverseGeocodeRateLimitInterceptor = reverseGeocodeRateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(resolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(placeSearchRateLimitInterceptor)
                .addPathPatterns("/api/v1/places/search");

        registry.addInterceptor(courseDirectionsRateLimitInterceptor)
                .addPathPatterns("/api/v1/courses/*/directions-to-start");

        registry.addInterceptor(reverseGeocodeRateLimitInterceptor)
                .addPathPatterns("/api/v1/locations/reverse-geocode");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(
                "/",
                "/swagger-ui/index.html"
        );
    }
}

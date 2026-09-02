package com.back.global.config;

import com.back.global.auth.CurrentUserIdResolver;
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
    private final PlaceSearchRateLimitInterceptor rateLimitInterceptor;

    public WebConfig(
            CurrentUserIdResolver resolver,
            PlaceSearchRateLimitInterceptor rateLimitInterceptor
    ) {
        this.resolver = resolver;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {
        resolvers.add(resolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/places/search");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController(
                "/",
                "/swagger-ui/index.html"
        );
    }
}

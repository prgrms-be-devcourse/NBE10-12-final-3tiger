package com.back.global.config;

import com.back.global.auth.CurrentUserIdResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CurrentUserIdResolver resolver;
    public WebConfig(CurrentUserIdResolver resolver) { this.resolver = resolver; }
    @Override public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) { resolvers.add(resolver); }
    @Override public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui/index.html");
    }
}

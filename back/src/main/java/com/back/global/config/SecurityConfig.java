package com.back.global.config;

import com.back.global.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(401, "인증이 필요합니다.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/api/v1/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts", "/api/v1/posts/*/comments",
                                "/api/v1/courses/**", "/api/v1/regions/**", "/api/v1/grids/**").permitAll()
                        .requestMatchers("/local-uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/courses/generate", "/api/v1/courses/save").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/signup").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/check-email").permitAll()
                        .anyRequest().authenticated());
        JwtAuthenticationFilter jwtFilter = jwtFilterProvider.getIfAvailable();
        if (jwtFilter != null) http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

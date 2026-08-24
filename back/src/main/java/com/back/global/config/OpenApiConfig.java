package com.back.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI todayWalkOpenApi() {
        return new OpenAPI().info(new Info()
                .title("오늘의산책 API")
                .description("POST 및 BOOKMARK 기능 개발용 API 문서")
                .version("v1"));
    }
}

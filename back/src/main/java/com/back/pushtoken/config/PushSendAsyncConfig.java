package com.back.pushtoken.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 외부 HTTP 발송이 알림 이벤트 스레드를 붙잡지 않도록 분리한 전용 풀. */
@Configuration
@EnableAsync
public class PushSendAsyncConfig {

    @Bean("pushSendExecutor")
    Executor pushSendExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("push-send-");
        executor.initialize();
        return executor;
    }
}

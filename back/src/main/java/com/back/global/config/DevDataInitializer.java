package com.back.global.config;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
@ConditionalOnProperty(name = "app.auth.allow-dev-user", havingValue = "true")
public class DevDataInitializer {
    @Bean
    CommandLineRunner initializeDevData(UserRepository users, CourseRepository courses) {
        return args -> {
            if (!users.existsById(1L)) users.save(new User("임시 산책러"));
            if (courses.count() == 0) courses.save(new Course("서울숲 임시 산책 코스", "11200", 2500));
        };
    }
}

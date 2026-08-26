package com.back.global.config;

import com.back.course.domain.Course;
import com.back.course.repository.CourseRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class TestCourseDataInitializer {

    @Bean
    CommandLineRunner initializeTestCourse(CourseRepository courses) {
        return args -> {
            if (courses.count() == 0) {
                courses.save(new Course("POST 테스트 코스", "11500", 2500));
            }
        };
    }
}

package com.back.course.navigation;

import com.back.course.navigation.repository.CourseNavigationRepository;
import com.back.course.navigation.repository.CourseNavigationView;
import com.back.course.navigation.service.CourseNavigationService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

class CourseNavigationCacheTest {

    @Test
    @DisplayName("동일한 코스의 내비게이션은 최초 한 번만 조회한다")
    void cachesNavigationByCourseId() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            CourseNavigationRepository repository = context.getBean(CourseNavigationRepository.class);
            CourseNavigationService service = context.getBean(CourseNavigationService.class);
            CourseNavigationView view = validView();
            given(repository.findNavigationByCourseId(101L)).willReturn(Optional.of(view));

            var first = service.getNavigation(101L);
            var second = service.getNavigation(101L);

            assertThat(second).isSameAs(first);
            verify(repository, times(1)).findNavigationByCourseId(101L);
        }
    }

    private static CourseNavigationView validView() {
        CourseNavigationView view = mock(CourseNavigationView.class, withSettings().lenient());
        given(view.getCourseId()).willReturn(101L);
        given(view.getName()).willReturn("서울숲 순환 산책로");
        given(view.getDistanceM()).willReturn(2500);
        given(view.getEstimatedMinutes()).willReturn(35);
        given(view.getIsLoop()).willReturn(true);
        given(view.getStartLat()).willReturn(37.544);
        given(view.getStartLng()).willReturn(127.037);
        given(view.getEndLat()).willReturn(37.544);
        given(view.getEndLng()).willReturn(127.037);
        given(view.getPathGeoJson()).willReturn("""
                {"type":"LineString","coordinates":[
                  [127.037,37.544],[127.038,37.545]
                ]}
                """);
        given(view.getCoordinateCount()).willReturn(2);
        given(view.getSrid()).willReturn(4326);
        given(view.getGeometryType()).willReturn("LINESTRING");
        given(view.getPathValid()).willReturn(true);
        given(view.getPathEmpty()).willReturn(false);
        given(view.getCalculatedDistanceM()).willReturn(2500.0);
        given(view.getStartEndDistanceM()).willReturn(0.0);
        return view;
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CourseNavigationRepository repository() {
            return mock(CourseNavigationRepository.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CourseNavigationService courseNavigationService(
                CourseNavigationRepository repository,
                ObjectMapper objectMapper
        ) {
            return new CourseNavigationService(repository, objectMapper);
        }

        @Bean
        CacheManager cacheManager() {
            CaffeineCacheManager manager = new CaffeineCacheManager("courseNavigation");
            manager.setCaffeine(Caffeine.newBuilder()
                    .maximumSize(500)
                    .expireAfterWrite(Duration.ofMinutes(30)));
            return manager;
        }
    }
}

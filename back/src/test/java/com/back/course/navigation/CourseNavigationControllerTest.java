package com.back.course.navigation;

import com.back.course.dto.GeoJsonLineString;
import com.back.course.navigation.controller.CourseNavigationController;
import com.back.course.navigation.dto.CourseNavigationResponse;
import com.back.course.navigation.dto.NavigationPoint;
import com.back.course.navigation.service.CourseNavigationService;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseNavigationController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CourseNavigationControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CourseNavigationService service;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("비로그인 사용자에게 코스 안내 정보를 반환한다")
    void returnsNavigationCourseWithoutAuthentication() throws Exception {
        var response = new CourseNavigationResponse(
                101L, "서울숲 순환 산책로", 2500, 35, true,
                new NavigationPoint(37.544, 127.037),
                new NavigationPoint(37.544, 127.037),
                new GeoJsonLineString(
                        "LineString",
                        List.of(
                                List.of(127.037, 37.544),
                                List.of(127.038, 37.545)
                        )
                )
        );
        given(service.getNavigation(101L)).willReturn(response);

        mvc.perform(get("/api/v1/courses/101/navigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("코스 안내 정보 조회 성공"))
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.isLoop").value(true))
                .andExpect(jsonPath("$.data.startPoint.lat").value(37.544))
                .andExpect(jsonPath("$.data.endPoint.lng").value(127.037))
                .andExpect(jsonPath("$.data.path.type").value("LineString"))
                .andExpect(jsonPath("$.data.path.coordinates[0][0]").value(127.037))
                .andExpect(jsonPath("$.data.path.coordinates[0][1]").value(37.544));
    }

    @Test
    @DisplayName("존재하지 않는 코스는 404를 반환한다")
    void returnsNotFoundForMissingCourse() throws Exception {
        given(service.getNavigation(999L)).willThrow(
                new BusinessException(ErrorCode.COURSE_NOT_FOUND)
        );

        mvc.perform(get("/api/v1/courses/999/navigation"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_404_1"));
    }

    @Test
    @DisplayName("안내할 수 없는 코스는 422를 반환한다")
    void returnsUnprocessableEntityForNonNavigableCourse() throws Exception {
        given(service.getNavigation(102L)).willThrow(
                new BusinessException(ErrorCode.COURSE_NOT_NAVIGABLE)
        );

        mvc.perform(get("/api/v1/courses/102/navigation"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COURSE_422_1"));
    }
}

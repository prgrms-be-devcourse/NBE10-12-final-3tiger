package com.back.course.navigation;

import com.back.course.navigation.controller.CourseStartDirectionsController;
import com.back.course.navigation.dto.CourseStartDirectionsResponse;
import com.back.course.navigation.dto.DirectionsMode;
import com.back.course.navigation.dto.DirectionsStatus;
import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.course.navigation.service.CourseStartDirectionsService;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseStartDirectionsController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CourseStartDirectionsControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CourseStartDirectionsService service;

    @MockitoBean
    private CourseDirectionsRateLimiter directionsRateLimiter;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    void anonymousUserCanRequestSelectedMode() throws Exception {
        given(service.getDirectionsToStart(15L, 37.50, 126.80, DirectionsMode.WALK))
                .willReturn(new CourseStartDirectionsResponse(
                        15L,
                        DirectionsMode.WALK,
                        DirectionsStatus.ROUTE_AVAILABLE,
                        false,
                        50,
                        new CourseStartDirectionsResponse.Destination(
                                "서울식물원 코스 출발점", 37.569, 126.835
                        ),
                        1200,
                        900,
                        "https://map.kakao.com/link/by/walk/test",
                        List.of()
                ));

        mvc.perform(get("/api/v1/courses/15/directions-to-start")
                        .param("latitude", "37.50")
                        .param("longitude", "126.80")
                        .param("mode", "WALK")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.mode").value("WALK"))
                .andExpect(jsonPath("$.data.status").value("ROUTE_AVAILABLE"))
                .andExpect(jsonPath("$.data.destination.name")
                        .value("서울식물원 코스 출발점"));

        verify(directionsRateLimiter).check("IP:203.0.113.10");
    }

    @Test
    void invalidCoordinateReturnsBadRequestBeforeRateLimit() throws Exception {
        mvc.perform(get("/api/v1/courses/15/directions-to-start")
                        .param("latitude", "91")
                        .param("longitude", "126.80")
                        .param("mode", "WALK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service, directionsRateLimiter);
    }

    @Test
    void invalidModeReturnsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/courses/15/directions-to-start")
                        .param("latitude", "37.50")
                        .param("longitude", "126.80")
                        .param("mode", "CAR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service, directionsRateLimiter);
    }

    @Test
    void rateLimitExceededDoesNotCallKakaoService() throws Exception {
        willThrow(new BusinessException(ErrorCode.DIRECTIONS_RATE_LIMIT_EXCEEDED))
                .given(directionsRateLimiter).check(anyString());

        mvc.perform(get("/api/v1/courses/15/directions-to-start")
                        .param("latitude", "37.50")
                        .param("longitude", "126.80")
                        .param("mode", "BICYCLE"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DIRECTIONS_429_1"));

        verifyNoInteractions(service);
    }
}

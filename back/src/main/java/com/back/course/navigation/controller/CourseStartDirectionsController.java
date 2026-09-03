package com.back.course.navigation.controller;

import com.back.course.navigation.dto.CourseStartDirectionsResponse;
import com.back.course.navigation.dto.DirectionsMode;
import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.course.navigation.service.CourseStartDirectionsService;
import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@Validated
@Tag(name = "Course Navigation", description = "산책 코스 출발점 길찾기 API")
public class CourseStartDirectionsController {

    private final CourseStartDirectionsService service;
    private final CourseDirectionsRateLimiter rateLimiter;

    public CourseStartDirectionsController(
            CourseStartDirectionsService service,
            CourseDirectionsRateLimiter rateLimiter
    ) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/{courseId}/directions-to-start")
    @Operation(
            summary = "코스 출발점 길찾기",
            description = """
                    현재 위치에서 코스 출발점까지 선택한 수단의 카카오맵 경로를 조회합니다.
                    mode는 WALK, PUBLIC_TRANSIT, BICYCLE 중 하나이며 요청한 수단의 API만 호출합니다.
                    출발점 50m 이내에서는 외부 길찾기를 호출하지 않고 바로 시작 가능한 상태를 반환합니다.
                    """
    )
    public ApiResponse<CourseStartDirectionsResponse> getDirectionsToStart(
            @PathVariable Long courseId,
            @RequestParam
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam DirectionsMode mode,
            @CurrentUserId(required = false) Long userId,
            HttpServletRequest request
    ) {
        String clientId = userId != null
                ? "USER:" + userId
                : "IP:" + request.getRemoteAddr();
        rateLimiter.check(clientId);

        return ApiResponse.ok(
                "코스 출발점 길찾기 조회 성공",
                service.getDirectionsToStart(courseId, latitude, longitude, mode)
        );
    }
}

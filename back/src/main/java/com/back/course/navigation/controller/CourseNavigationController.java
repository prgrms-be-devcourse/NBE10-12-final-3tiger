package com.back.course.navigation.controller;

import com.back.course.navigation.dto.CourseNavigationResponse;
import com.back.course.navigation.service.CourseNavigationService;
import com.back.global.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Course Navigation", description = "산책 코스 안내 데이터 API")
public class CourseNavigationController {

    private final CourseNavigationService service;

    public CourseNavigationController(CourseNavigationService service) {
        this.service = service;
    }

    @GetMapping("/{courseId}/navigation")
    @Operation(
            summary = "코스 안내 정보 조회",
            description = """
                    앱 내부 산책 안내에 필요한 전체 경로, 출발점, 도착점, 거리,
                    예상 시간과 순환 여부를 반환합니다.
                    path.coordinates는 GeoJSON 표준에 따라 [경도, 위도] 순서입니다.
                    """
    )
    public ApiResponse<CourseNavigationResponse> navigation(@PathVariable Long courseId) {
        return ApiResponse.ok("코스 안내 정보 조회 성공", service.getNavigation(courseId));
    }
}

package com.back.course.controller;

import com.back.course.domain.Persona;
import com.back.course.service.CourseService;
import com.back.global.api.ApiResponse;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserId;
import com.back.global.error.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @GetMapping("/{courseId}")
    ApiResponse<CourseService.CourseDetail> detail(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at
    ) {
        return ApiResponse.ok("코스 상세 조회 성공", service.getDetail(courseId, userId, at));
    }

    @GetMapping
    ApiResponse<PageResponse<CourseService.CourseItem>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer radiusM,
            @RequestParam(required = false) Persona persona,
            @RequestParam(required = false) Integer distanceMinM,
            @RequestParam(required = false) Integer distanceMaxM,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime at,
            @RequestParam(defaultValue = "score") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        boolean hasRegion = regionCode != null && !regionCode.isBlank();
        boolean hasAnyCoord = lat != null || lng != null || radiusM != null;
        boolean hasFullCoord = lat != null && lng != null && radiusM != null;

        if (hasRegion && hasAnyCoord) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "regionCode와 좌표 검색은 함께 사용할 수 없습니다.");
        }
        if (!hasRegion && hasAnyCoord && !hasFullCoord) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "좌표 검색은 lat, lng, radiusM이 모두 필요합니다.");
        }
        if (!hasRegion && !hasFullCoord) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "검색 조건(regionCode 또는 좌표)이 필요합니다.");
        }
        if ("distance".equals(sort) && !hasFullCoord) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "distance 정렬은 좌표 검색에서만 사용할 수 있습니다.");
        }

        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        var query = new CourseService.CourseSearchQuery(
                hasRegion ? regionCode : null,
                hasFullCoord ? lat : null,
                hasFullCoord ? lng : null,
                hasFullCoord ? radiusM : null,
                persona,
                distanceMinM, distanceMaxM,
                isLoop,
                at,
                sort,
                safePage, clampedSize
        );
        return ApiResponse.ok("코스 목록 조회 성공", service.search(query));
    }
}

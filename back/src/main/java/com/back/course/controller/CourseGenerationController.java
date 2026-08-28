package com.back.course.controller;

import com.back.course.dto.GenerateRequest;
import com.back.course.dto.GenerateResponse;
import com.back.course.dto.SaveCourseRequest;
import com.back.course.dto.SaveCourseResponse;
import com.back.course.service.CourseGenerationService;
import com.back.global.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/courses")
public class CourseGenerationController {

    private final CourseGenerationService service;

    public CourseGenerationController(CourseGenerationService service) {
        this.service = service;
    }

    /** 저장 없이 후보 3개 반환 (사용자가 선택 안 하면 데이터 안 남음) */
    @PostMapping("/generate")
    public ApiResponse<GenerateResponse> generate(@Valid @RequestBody GenerateRequest req) {
        return ApiResponse.ok("코스 후보 생성 성공", service.generate(req));
    }

    /** 사용자가 선택한 코스 정식 저장 */
    @PostMapping("/save")
    public ApiResponse<SaveCourseResponse> save(@Valid @RequestBody SaveCourseRequest req) {
        Long courseId = service.save(req);
        return ApiResponse.ok("코스가 저장되었습니다.", new SaveCourseResponse(courseId));
    }
}

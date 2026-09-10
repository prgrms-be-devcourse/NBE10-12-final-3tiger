package com.back.walk.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.walk.dto.WeeklyWalkResponse;
import com.back.walk.service.WeeklyWalkService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/users/me/walks")
public class WeeklyWalkController {

    private final WeeklyWalkService service;

    public WeeklyWalkController(WeeklyWalkService service) {
        this.service = service;
    }

    @GetMapping("/weekly")
    public ApiResponse<WeeklyWalkResponse> getWeeklyWalks(
            @CurrentUserId Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart
    ) {
        return ApiResponse.ok(
                "주간 산책 기록 조회 성공",
                service.getWeeklyWalks(userId, weekStart)
        );
    }
}

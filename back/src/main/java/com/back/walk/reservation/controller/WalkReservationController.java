package com.back.walk.reservation.controller;

import com.back.global.api.ApiResponse;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserId;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.walk.reservation.dto.WalkReservationRequest;
import com.back.walk.reservation.dto.WalkReservationResponse;
import com.back.walk.reservation.service.WalkReservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class WalkReservationController {

    private static final int MAX_PAGE_SIZE = 100;
    private final WalkReservationService service;

    public WalkReservationController(WalkReservationService service) {
        this.service = service;
    }

    @PostMapping("/walk-reservations")
    public ApiResponse<WalkReservationResponse> reserve(
            @CurrentUserId Long userId,
            @Valid @RequestBody WalkReservationRequest request
    ) {
        return ApiResponse.ok("산책이 예약되었습니다.", service.reserve(userId, request));
    }

    @GetMapping("/users/me/walk-reservations")
    public ApiResponse<PageResponse<WalkReservationResponse>> getUpcoming(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        return ApiResponse.ok("내 산책 예약 목록 조회 성공", service.getUpcoming(userId, page, size));
    }

    @DeleteMapping("/walk-reservations/{reservationId}")
    public ApiResponse<WalkReservationResponse> cancel(
            @CurrentUserId Long userId,
            @PathVariable Long reservationId
    ) {
        return ApiResponse.ok("산책 예약이 취소되었습니다.", service.cancel(userId, reservationId));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}

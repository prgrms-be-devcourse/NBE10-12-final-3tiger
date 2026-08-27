package com.back.notification.controller;

import com.back.global.api.*;
import com.back.global.auth.CurrentUserId;
import com.back.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    // SSE 스트림은 ApiResponse 래핑 대상이 아님 (text/event-stream을 직접 반환해야 함)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter subscribe(@CurrentUserId Long userId) {
        return service.subscribe(userId);
    }

    @GetMapping
    @Operation(operationId = "getNotifications")
    ApiResponse<PageResponse<NotificationService.NotificationResponse>> list(@CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok("알림 목록 조회 성공", service.getNotifications(userId, page, size));
    }

    @GetMapping("/unread-count")
    ApiResponse<NotificationService.UnreadCountResponse> unreadCount(@CurrentUserId Long userId) {
        return ApiResponse.ok("안 읽은 알림 개수 조회 성공", service.getUnreadCount(userId));
    }

    @PatchMapping("/{notificationId}/read")
    ApiResponse<Void> markAsRead(@CurrentUserId Long userId, @PathVariable Long notificationId) {
        service.markAsRead(notificationId, userId);
        return ApiResponse.ok("알림을 읽음 처리했습니다.", null);
    }

    @PatchMapping("/read-all")
    ApiResponse<Void> markAllAsRead(@CurrentUserId Long userId) {
        service.markAllAsRead(userId);
        return ApiResponse.ok("모든 알림을 읽음 처리했습니다.", null);
    }
}

package com.back.notification.controller;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.notification.domain.NotificationType;
import com.back.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.back.TestAuthentication.authenticatedAs;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("t1: GET /api/v1/notifications 요청 시 200과 페이징 응답을 반환한다")
    void t1() throws Exception {
        // given
        var response = new NotificationService.NotificationResponse(
                1L, NotificationType.LIKE, 10L, null, 2L, "산책러", "https://example.com/a.jpg", false, LocalDateTime.now());
        given(notificationService.getNotifications(1L, 0, 20))
                .willReturn(PageResponse.from(new PageImpl<>(List.of(response))));

        // when & then
        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("LIKE"))
                .andExpect(jsonPath("$.data.content[0].postId").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("t2: GET /api/v1/notifications/unread-count 요청 시 200과 안읽음 개수를 반환한다")
    void t2() throws Exception {
        // given
        given(notificationService.getUnreadCount(1L)).willReturn(new NotificationService.UnreadCountResponse(3L));

        // when & then
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    @DisplayName("t3: PATCH /api/v1/notifications/{id}/read 요청 시 200을 반환한다")
    void t3() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", 1L).with(authenticatedAs(1L)))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(1L, 1L);
    }

    @Test
    @DisplayName("t4: PATCH /api/v1/notifications/read-all 요청 시 200을 반환한다")
    void t4() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/notifications/read-all").with(authenticatedAs(1L)))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(1L);
    }

    @Test
    @DisplayName("t5: 본인 알림이 아니면 읽음 처리 시 403을 반환한다")
    void t5() throws Exception {
        // given
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "본인 알림만 읽음 처리할 수 있습니다."))
                .when(notificationService).markAsRead(1L, 2L);

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", 1L).with(authenticatedAs(2L)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("t6: GET /api/v1/notifications/subscribe 요청 시 비동기 스트림이 시작된다")
    void t6() throws Exception {
        // given
        given(notificationService.subscribe(1L)).willReturn(new SseEmitter());

        // when & then — asyncDispatch()는 쓰지 않는다 (SSE는 완료되지 않는 스트림이라 무한 대기함)
        mockMvc.perform(get("/api/v1/notifications/subscribe").with(authenticatedAs(1L)))
                .andExpect(request().asyncStarted());
    }
}

package com.back.place.kakao;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.dto.PlaceSearchItem;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimitInterceptor;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KakaoPlaceController.class)
@Import({
        SecurityConfig.class,
        WebConfig.class,
        CurrentUserIdResolver.class,
        GlobalExceptionHandler.class,
        PlaceSearchRateLimitInterceptor.class
})
class KakaoPlaceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KakaoPlaceService service;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter rateLimiter;

    @Test
    void allowsAnonymousSearchAndReturnsSupportedRegionFlag() throws Exception {
        given(service.search("서울식물원")).willReturn(List.of(
                new PlaceSearchItem(
                        "서울식물원",
                        "서울 강서구 마곡동 161",
                        "서울 강서구 마곡동로 161",
                        37.569,
                        126.835,
                        "여행 > 공원",
                        "https://place.map.kakao.com/1",
                        true
                )
        ));

        mvc.perform(get("/api/v1/places/search")
                        .param("query", "서울식물원"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("서울식물원"))
                .andExpect(jsonPath("$.data[0].supportedRegion").value(true));
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mvc.perform(get("/api/v1/places/search")
                        .param("query", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(service);
    }

    @Test
    void returnsTooManyRequestsWithoutCallingServiceWhenRateLimitIsExceeded() throws Exception {
        willThrow(new BusinessException(ErrorCode.PLACE_SEARCH_RATE_LIMIT_EXCEEDED))
                .given(rateLimiter).check(anyString());

        mvc.perform(get("/api/v1/places/search")
                        .param("query", "서울식물원"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PLACE_429_1"));

        verifyNoInteractions(service);
    }
}

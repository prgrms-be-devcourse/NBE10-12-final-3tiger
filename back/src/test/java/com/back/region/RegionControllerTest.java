package com.back.region;

import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.region.controller.RegionController;
import com.back.region.service.RegionService;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegionController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class RegionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RegionService regionService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    @DisplayName("지역 목록 조회 시 중심 좌표와 코스 개수를 반환한다")
    void returnsRegionList() throws Exception {
        given(regionService.list()).willReturn(List.of(
                new RegionService.RegionItem("11500", "강서구", 37.5509, 126.8495, "{\"type\":\"Polygon\",\"coordinates\":[]}", 15),
                new RegionService.RegionItem("11470", "양천구", 37.5169, 126.8664, "{\"type\":\"Polygon\",\"coordinates\":[]}", 15)
        ));

        mvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("지역 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].regionCode").value("11500"))
                .andExpect(jsonPath("$.data[0].name").value("강서구"))
                .andExpect(jsonPath("$.data[0].centerLat").value(37.5509))
                .andExpect(jsonPath("$.data[0].centerLng").value(126.8495))
                .andExpect(jsonPath("$.data[0].courseCount").value(15));
    }

    @Test
    @DisplayName("인증하지 않은 사용자도 지역 목록을 조회할 수 있다")
    void allowsAnonymousRegionList() throws Exception {
        given(regionService.list()).willReturn(List.of(
                new RegionService.RegionItem(
                        "11500",
                        "강서구",
                        37.5509,
                        126.8495,
                        "{\"type\":\"Polygon\",\"coordinates\":[]}",
                        15
                )
        ));

        mvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("지역 목록 조회 성공"))
                                .andExpect(jsonPath("$.data[0].regionCode").value("11500"))
                                .andExpect(jsonPath("$.data[0].name").value("강서구"))
                                .andExpect(jsonPath("$.data[0].centerLat").value(37.5509))
                                .andExpect(jsonPath("$.data[0].centerLng").value(126.8495))
                                .andExpect(jsonPath("$.data[0].courseCount").value(15));
    }
}

package com.back.region;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.region.controller.RegionController;
import com.back.region.service.RegionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RegionController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.auth.allow-dev-user=false"
})
class RegionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RegionService regionService;

    @Test void returnsRegionList() throws Exception {
        given(regionService.list()).willReturn(List.of(
                new RegionService.RegionItem("11500", "강서구", 37.5509, 126.8495, 15),
                new RegionService.RegionItem("11470", "양천구", 37.5169, 126.8664, 15)
        ));

        mvc.perform(get("/api/v1/regions").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("지역 목록 조회 성공"))
                .andExpect(jsonPath("$.data[0].regionCode").value("11500"))
                .andExpect(jsonPath("$.data[0].name").value("강서구"))
                .andExpect(jsonPath("$.data[0].centerLat").value(37.5509))
                .andExpect(jsonPath("$.data[0].centerLng").value(126.8495))
                .andExpect(jsonPath("$.data[0].courseCount").value(15));
    }

    @Test void requiresAuthenticationWhenDevUserDisabled() throws Exception {
        mvc.perform(get("/api/v1/regions"))
                .andExpect(status().isUnauthorized());
    }
}

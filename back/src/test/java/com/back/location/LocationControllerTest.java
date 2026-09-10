package com.back.location;

import com.back.course.navigation.ratelimit.CourseDirectionsRateLimiter;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.global.jwt.JwtProvider;
import com.back.location.controller.LocationController;
import com.back.location.dto.ReverseGeocodeResponse;
import com.back.location.ratelimit.ReverseGeocodeRateLimiter;
import com.back.location.service.LocationService;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class LocationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LocationService locationService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @MockitoBean
    private PlaceSearchRateLimiter placeSearchRateLimiter;

    @MockitoBean
    private ReverseGeocodeRateLimiter reverseGeocodeRateLimiter;

    @MockitoBean
    private CourseDirectionsRateLimiter courseDirectionsRateLimiter;

    @Test
    void allowsAnonymousReverseGeocoding() throws Exception {
        given(locationService.reverseGeocode(37.5667106, 126.827658))
                .willReturn(new ReverseGeocodeResponse(
                        37.5667106,
                        126.827658,
                        "서울특별시 강서구 마곡동로 161",
                        "서울특별시 강서구 마곡동 812",
                        "서울특별시",
                        "강서구",
                        "마곡동",
                        true
                ));

        mvc.perform(get("/api/v1/locations/reverse-geocode")
                        .param("latitude", "37.5667106")
                        .param("longitude", "126.827658"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.district").value("강서구"))
                .andExpect(jsonPath("$.data.supportedRegion").value(true));

        verify(reverseGeocodeRateLimiter).check(anyString());
    }

    @Test
    void rejectsOutOfRangeCoordinates() throws Exception {
        mvc.perform(get("/api/v1/locations/reverse-geocode")
                        .param("latitude", "91")
                        .param("longitude", "126.8"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verifyNoInteractions(locationService);
    }

    @Test
    void rateLimitExceededDoesNotCallLocationService() throws Exception {
        willThrow(new BusinessException(ErrorCode.REVERSE_GEOCODE_RATE_LIMIT_EXCEEDED))
                .given(reverseGeocodeRateLimiter).check(anyString());

        mvc.perform(get("/api/v1/locations/reverse-geocode")
                        .param("latitude", "37.5667106")
                        .param("longitude", "126.827658"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOCATION_429_1"));

        verifyNoInteractions(locationService);
    }
}

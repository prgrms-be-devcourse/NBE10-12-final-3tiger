package com.back.course;

import com.back.course.controller.CourseGenerationController;
import com.back.course.dto.GenerateCandidate;
import com.back.course.dto.GenerateResponse;
import com.back.course.dto.GeoJsonLineString;
import com.back.course.service.CourseGenerationService;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseGenerationController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CourseGenerationControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CourseGenerationService service;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("코스 후보 생성 응답은 ApiResponse 규약으로 래핑되어 반환된다")
    void generateReturnsApiResponseEnvelope() throws Exception {
        GenerateCandidate candidate = new GenerateCandidate(
                new GeoJsonLineString(
                        "LineString",
                        List.of(List.of(126.8496, 37.5474), List.of(126.8507, 37.5483))
                ),
                3050,
                new BigDecimal("0.83"),
                new BigDecimal("1.7"),
                "11500"
        );
        given(service.generate(any())).willReturn(
                new GenerateResponse(List.of(candidate), 3, 1)
        );

        mvc.perform(post("/api/v1/courses/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lat": 37.5509,
                                  "lng": 126.8495,
                                  "distanceM": 3000,
                                  "persona": "dog"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("코스 후보 생성 성공"))
                .andExpect(jsonPath("$.data.candidates").isArray())
                .andExpect(jsonPath("$.data.candidates[0].path.type").value("LineString"))
                .andExpect(jsonPath("$.data.candidates[0].totalM").value(3050))
                .andExpect(jsonPath("$.data.candidates[0].regionCode").value("11500"))
                .andExpect(jsonPath("$.data.requestedCount").value(3))
                .andExpect(jsonPath("$.data.returnedCount").value(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 499, 10001})
    void generateRejectsDistanceOutOfRange(int distance) throws Exception {
        String body = "{\"lat\":37.5509,\"lng\":126.8495,\"distanceM\":" + distance + "}";

        mvc.perform(post("/api/v1/courses/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void generateRejectsMissingCoordinates() throws Exception {
        mvc.perform(post("/api/v1/courses/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceM\":3000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("코스 저장 응답은 ApiResponse 규약으로 courseId를 래핑해 반환한다")
    void saveReturnsCourseIdInEnvelope() throws Exception {
        given(service.save(any())).willReturn(42L);

        mvc.perform(post("/api/v1/courses/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": {
                                    "type": "LineString",
                                    "coordinates": [[126.8496, 37.5474], [126.8507, 37.5483]]
                                  },
                                  "regionCode": "11500"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("코스가 저장되었습니다."))
                .andExpect(jsonPath("$.data.courseId").value(42));
    }

    @Test
    void saveRejectsMissingPath() throws Exception {
        mvc.perform(post("/api/v1/courses/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"regionCode\":\"11500\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void saveRejectsMissingRegionCode() throws Exception {
        mvc.perform(post("/api/v1/courses/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "path": {
                                    "type": "LineString",
                                    "coordinates": [[126.8496, 37.5474]]
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }
}

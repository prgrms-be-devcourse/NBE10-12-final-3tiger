package com.back.course;

import com.back.course.controller.CourseController;
import com.back.course.domain.Persona;
import com.back.course.service.CourseService;
import com.back.global.api.PageResponse;
import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.back.TestAuthentication.authenticatedAs;

@WebMvcTest(CourseController.class)
@Import({CurrentUserIdResolver.class, SecurityConfig.class, GlobalExceptionHandler.class})
class CourseControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean CourseService courseService;

    @Test void returnsCourseDetailWithGeoJsonPath() throws Exception {
        var detail = new CourseService.CourseDetail(
                101L,
                "성수 서울숲 순환",
                new CourseService.GeoJsonLineString(
                        "LineString",
                        List.of(List.of(127.037, 37.544), List.of(127.038, 37.545))
                ),
                2500,
                35,
                12,
                12,
                true,
                "auto_discovered",
                new CourseService.ScoreBars(0.82, 5.4, 0.78, null, 0.73),
                null,
                null,
                List.of(),
                true
        );
        given(courseService.getDetail(101L, 1L, null)).willReturn(detail);

        mvc.perform(get("/api/v1/courses/101").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("코스 상세 조회 성공"))
                .andExpect(jsonPath("$.data.path.type").value("LineString"))
                .andExpect(jsonPath("$.data.path.coordinates[0][0]").value(127.037))
                .andExpect(jsonPath("$.data.path.coordinates[0][1]").value(37.544))
                .andExpect(jsonPath("$.data.isBookmarked").value(true));
    }

    @Test void returnsCourseList() throws Exception {
        var item = new CourseService.CourseItem(
                101L,
                "성수 서울숲 순환",
                2500,
                35,
                true,
                new CourseService.Point(37.544, 127.037),
                new CourseService.Scores(0.82, 5.4, 0.78, null, 0.90, null),
                null,
                List.of()
        );
        var page = new PageResponse<>(List.of(item), 0, 20, 37L);
        given(courseService.search(any())).willReturn(page);

        mvc.perform(get("/api/v1/courses").param("regionCode", "11500").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("코스 목록 조회 성공"))
                .andExpect(jsonPath("$.data.content[0].courseId").value(101))
                .andExpect(jsonPath("$.data.content[0].name").value("성수 서울숲 순환"))
                .andExpect(jsonPath("$.data.content[0].distanceM").value(2500))
                .andExpect(jsonPath("$.data.content[0].estimatedMinutes").value(35))
                .andExpect(jsonPath("$.data.content[0].isLoop").value(true))
                .andExpect(jsonPath("$.data.content[0].startPoint.lat").value(37.544))
                .andExpect(jsonPath("$.data.content[0].startPoint.lng").value(127.037))
                .andExpect(jsonPath("$.data.content[0].scores.flatness").value(0.82))
                .andExpect(jsonPath("$.data.content[0].scores.avgSlopeDegree").value(5.4))
                .andExpect(jsonPath("$.data.content[0].scores.shadeSummer").value(0.78))
                .andExpect(jsonPath("$.data.content[0].scores.wheelchair").value(0.90))
                .andExpect(jsonPath("$.data.content[0].personaBadges").isArray())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(37));
    }

    @Test void acceptsCoordSearch() throws Exception {
        given(courseService.search(any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0L));

        mvc.perform(get("/api/v1/courses")
                        .param("lat", "37.5")
                        .param("lng", "127.0")
                        .param("radiusM", "1000")
                        .param("persona", "dog")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk());
    }

    @Test void rejectsWhenNoSearchCriteria() throws Exception {
        mvc.perform(get("/api/v1/courses").with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value("검색 조건(regionCode 또는 좌표)이 필요합니다."));
    }

    @Test void rejectsWhenBothRegionAndCoord() throws Exception {
        mvc.perform(get("/api/v1/courses")
                        .param("regionCode", "11500")
                        .param("lat", "37.5")
                        .param("lng", "127.0")
                        .param("radiusM", "1000")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("regionCode와 좌표 검색은 함께 사용할 수 없습니다."));
    }

    @Test void rejectsWhenPartialCoord() throws Exception {
        mvc.perform(get("/api/v1/courses")
                        .param("lat", "37.5")
                        .param("lng", "127.0")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("좌표 검색은 lat, lng, radiusM이 모두 필요합니다."));
    }

    @Test void rejectsDistanceSortWithoutCoord() throws Exception {
        mvc.perform(get("/api/v1/courses")
                        .param("regionCode", "11500")
                        .param("sort", "distance")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("distance 정렬은 좌표 검색에서만 사용할 수 있습니다."));
    }

    @Test void requiresAuthenticationWhenDevUserDisabled() throws Exception {
        mvc.perform(get("/api/v1/courses").param("regionCode", "11500"))
                .andExpect(status().isUnauthorized());
    }

    @Test void clampsSizeToHundred() throws Exception {
        given(courseService.search(any())).willReturn(new PageResponse<>(List.of(), 0, 100, 0L));

        mvc.perform(get("/api/v1/courses")
                        .param("regionCode", "11500")
                        .param("size", "500")
                        .with(authenticatedAs(1L)))
                .andExpect(status().isOk());
        org.mockito.ArgumentCaptor<CourseService.CourseSearchQuery> captor =
                org.mockito.ArgumentCaptor.forClass(CourseService.CourseSearchQuery.class);
        org.mockito.Mockito.verify(courseService).search(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(100, captor.getValue().size());
    }
}

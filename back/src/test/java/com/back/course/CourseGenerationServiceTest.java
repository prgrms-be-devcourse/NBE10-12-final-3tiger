package com.back.course;

import com.back.course.domain.Persona;
import com.back.course.dto.GenerateRequest;
import com.back.course.dto.GeoJsonLineString;
import com.back.course.dto.SaveCourseRequest;
import com.back.course.repository.CourseGenerationRepository;
import com.back.course.service.CourseGenerationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

class CourseGenerationServiceTest {

    private final CourseGenerationRepository repo = mock(CourseGenerationRepository.class);
    private final CourseGenerationService service = new CourseGenerationService(repo);

    @Test void generate_forwardsPersonaAsStringToRepository() {
        var at = LocalDateTime.of(2026, 8, 27, 14, 0);
        var req = new GenerateRequest(37.55, 126.844, 3000, null, null, at, Persona.dog);
        given(repo.generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq("dog")))
                .willReturn(Optional.empty());

        service.generate(req);

        verify(repo, atLeastOnce()).generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq("dog"));
    }

    @Test void generate_passesNullPersonaWhenNotProvided() {
        var at = LocalDateTime.of(2026, 8, 27, 14, 0);
        var req = new GenerateRequest(37.55, 126.844, 3000, null, null, at, null);
        given(repo.generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq(null)))
                .willReturn(Optional.empty());

        service.generate(req);

        verify(repo, atLeastOnce()).generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq(null));
    }

    @Test void generate_onewayCallsGenerateOnewayAndReturnsSingleCandidate() {
        var at = LocalDateTime.of(2026, 8, 27, 14, 0);
        var req = new GenerateRequest(37.55, 126.844, null, 37.556, 126.852, at, Persona.walker);
        var path = new GeoJsonLineString("LineString",
                List.of(List.of(126.844, 37.55), List.of(126.852, 37.556)));
        given(repo.generateOnewayOnly(eq(126.844), eq(37.55), eq(126.852), eq(37.556), eq(at), eq("walker")))
                .willReturn(Optional.of(new CourseGenerationRepository.OnewayRow(
                        path, 850, new BigDecimal("0.83"), "11500")));

        var resp = service.generate(req);

        assertThat(resp.candidates()).hasSize(1);
        assertThat(resp.candidates().get(0).errorPct()).isNull();
        assertThat(resp.candidates().get(0).totalM()).isEqualTo(850);
        assertThat(resp.requestedCount()).isEqualTo(1);
        verify(repo, atLeastOnce()).generateOnewayOnly(
                eq(126.844), eq(37.55), eq(126.852), eq(37.556), eq(at), eq("walker"));
    }

    @Test void generate_onewayReturnsEmptyWhenGraphDisconnected() {
        var at = LocalDateTime.of(2026, 8, 27, 14, 0);
        var req = new GenerateRequest(37.55, 126.844, null, 37.556, 126.852, at, null);
        given(repo.generateOnewayOnly(anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(), any()))
                .willReturn(Optional.empty());

        var resp = service.generate(req);

        assertThat(resp.candidates()).isEmpty();
        assertThat(resp.returnedCount()).isZero();
    }

    @Test void generate_loopWithoutDistanceThrows() {
        var req = new GenerateRequest(37.55, 126.844, null, null, null, null, null);
        assertThatThrownBy(() -> service.generate(req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void save_defaultIsLoopWhenNotProvided() {
        var path = new GeoJsonLineString("LineString",
                List.of(List.of(126.844, 37.55), List.of(126.845, 37.551)));
        var req = new SaveCourseRequest(path, "11500", null, null, null);
        given(repo.saveFromPath(eq(path), eq("11500"), anyBoolean(), any(), any())).willReturn(42L);

        service.save(req);

        verify(repo).saveFromPath(eq(path), eq("11500"), eq(true), eq(null), eq(null));
    }

    @Test void save_forwardsOnewayEndPointToRepository() {
        var path = new GeoJsonLineString("LineString",
                List.of(List.of(126.844, 37.55), List.of(126.852, 37.556)));
        var req = new SaveCourseRequest(path, "11500", false, 37.556, 126.852);
        given(repo.saveFromPath(eq(path), eq("11500"), eq(false), eq(126.852), eq(37.556))).willReturn(7L);

        service.save(req);

        verify(repo).saveFromPath(eq(path), eq("11500"), eq(false), eq(126.852), eq(37.556));
    }
}

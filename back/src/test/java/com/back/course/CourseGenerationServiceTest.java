package com.back.course;

import com.back.course.domain.Persona;
import com.back.course.dto.GenerateRequest;
import com.back.course.repository.CourseGenerationRepository;
import com.back.course.service.CourseGenerationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

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
        var req = new GenerateRequest(37.55, 126.844, 3000, at, Persona.dog);
        given(repo.generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq("dog")))
                .willReturn(Optional.empty());

        service.generate(req);

        verify(repo, atLeastOnce()).generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq("dog"));
    }

    @Test void generate_passesNullPersonaWhenNotProvided() {
        var at = LocalDateTime.of(2026, 8, 27, 14, 0);
        var req = new GenerateRequest(37.55, 126.844, 3000, at, null);
        given(repo.generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq(null)))
                .willReturn(Optional.empty());

        service.generate(req);

        verify(repo, atLeastOnce()).generateOnly(eq(126.844), eq(37.55), eq(3000), eq(at), anyInt(), eq(null));
    }
}

package com.back.course.map.service;

import com.back.course.dto.GeoJsonLineString;
import com.back.course.map.render.CourseMapImageRenderer;
import com.back.course.map.storage.CourseMapImageStorage;
import com.back.course.repository.CourseGenerationRepository;
import com.back.map.naver.NaverStaticMapClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CourseMapImageServiceTest {

    private final NaverStaticMapClient client = mock(NaverStaticMapClient.class);
    private final CourseMapImageRenderer renderer = mock(CourseMapImageRenderer.class);
    private final CourseMapImageStorage storage = mock(CourseMapImageStorage.class);
    private final CourseGenerationRepository repository = mock(CourseGenerationRepository.class);
    private final CourseMapImageService service =
            new CourseMapImageService(client, renderer, storage, repository);

    @Test
    void generatesUploadsAndStoresImageUrl() {
        var path = new GeoJsonLineString("LineString", List.of(
                List.of(126.827658, 37.5667106),
                List.of(126.849500, 37.550900)
        ));
        byte[] baseMap = {1};
        byte[] rendered = {2};
        given(client.getMapImage(anyDouble(), anyDouble(), anyInt())).willReturn(baseMap);
        given(renderer.render(any(), any(), any())).willReturn(rendered);
        given(storage.upload(44L, rendered))
                .willReturn("https://cdn.example.com/course-maps/44.png");

        service.generate(44L, path);

        verify(storage).upload(44L, rendered);
        verify(repository).updateMapImageUrl(
                44L,
                "https://cdn.example.com/course-maps/44.png"
        );
    }
}

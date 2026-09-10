package com.back.course.map.service;

import com.back.course.dto.GeoJsonLineString;
import com.back.course.map.event.CourseMapImageRequested;
import com.back.course.map.render.CourseMapImageRenderer;
import com.back.course.map.render.CourseMapViewport;
import com.back.course.map.storage.CourseMapImageStorage;
import com.back.course.repository.CourseGenerationRepository;
import com.back.map.naver.NaverStaticMapClient;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseMapImageService {

    private final NaverStaticMapClient staticMapClient;
    private final CourseMapImageRenderer renderer;
    private final CourseMapImageStorage storage;
    private final CourseGenerationRepository courseRepository;

    @Async("courseMapImageExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void generateAfterCommit(CourseMapImageRequested event) {
        try {
            generate(event.courseId(), event.path());
        } catch (RuntimeException ignored) {
            courseRepository.markMapImageFailed(event.courseId());
        }
    }

    void generate(Long courseId, GeoJsonLineString path) {
        List<List<Double>> coordinates = path.coordinates();
        CourseMapViewport viewport = CourseMapViewport.fit(
                coordinates,
                NaverStaticMapClient.IMAGE_WIDTH,
                NaverStaticMapClient.IMAGE_HEIGHT
        );
        byte[] baseMap = staticMapClient.getMapImage(
                viewport.centerLatitude(),
                viewport.centerLongitude(),
                viewport.level()
        );
        byte[] renderedImage = renderer.render(baseMap, coordinates, viewport);
        String imageUrl = storage.upload(courseId, renderedImage);
        courseRepository.markMapImageCompleted(courseId, imageUrl);
    }
}

package com.back.course.map.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseMapViewportTest {

    @Test
    void fitsEveryCoordinateInsideImageWithPadding() {
        var coordinates = List.of(
                List.of(126.827658, 37.5667106),
                List.of(126.839450, 37.565100),
                List.of(126.849500, 37.550900)
        );

        CourseMapViewport viewport = CourseMapViewport.fit(coordinates, 800, 500);

        assertThat(viewport.level()).isBetween(1, 20);
        for (List<Double> coordinate : coordinates) {
            CourseMapViewport.PixelPoint point = viewport.toPixel(
                    coordinate.get(0), coordinate.get(1), 800, 500
            );
            assertThat(point.x()).isBetween(50.0, 750.0);
            assertThat(point.y()).isBetween(50.0, 450.0);
        }
    }
}

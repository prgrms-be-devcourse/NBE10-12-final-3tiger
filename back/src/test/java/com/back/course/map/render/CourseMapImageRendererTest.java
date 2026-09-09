package com.back.course.map.render;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseMapImageRendererTest {

    private final CourseMapImageRenderer renderer = new CourseMapImageRenderer();

    @Test
    void drawsRouteAndEndpointsOnBaseMap() throws Exception {
        BufferedImage base = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        var graphics = base.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 800, 500);
        graphics.dispose();
        ByteArrayOutputStream baseBytes = new ByteArrayOutputStream();
        ImageIO.write(base, "png", baseBytes);

        var coordinates = List.of(
                List.of(126.827658, 37.5667106),
                List.of(126.839450, 37.565100),
                List.of(126.849500, 37.550900)
        );
        CourseMapViewport viewport = CourseMapViewport.fit(coordinates, 800, 500);

        byte[] rendered = renderer.render(baseBytes.toByteArray(), coordinates, viewport);
        BufferedImage result = ImageIO.read(new ByteArrayInputStream(rendered));

        assertThat(result).isNotNull();
        assertThat(result.getWidth()).isEqualTo(800);
        assertThat(result.getHeight()).isEqualTo(500);
        CourseMapViewport.PixelPoint middle = viewport.toPixel(126.839450, 37.565100, 800, 500);
        assertThat(result.getRGB((int) middle.x(), (int) middle.y())).isNotEqualTo(Color.WHITE.getRGB());
    }
}

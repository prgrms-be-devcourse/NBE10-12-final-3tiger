package com.back.course.map.render;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class CourseMapImageRenderer {

    private static final Color ROUTE_OUTLINE = new Color(255, 255, 255, 220);
    private static final Color ROUTE_COLOR = new Color(37, 99, 235);
    private static final Color START_COLOR = new Color(22, 163, 74);
    private static final Color END_COLOR = new Color(220, 38, 38);

    public byte[] render(byte[] baseMap, List<List<Double>> coordinates, CourseMapViewport viewport) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(baseMap));
            if (source == null) {
                throw new IllegalArgumentException("지도 이미지 형식을 읽을 수 없습니다.");
            }
            BufferedImage result = new BufferedImage(
                    source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D graphics = result.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Path2D route = createRoute(coordinates, viewport, source.getWidth(), source.getHeight());
                graphics.setColor(ROUTE_OUTLINE);
                graphics.setStroke(new BasicStroke(11, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(route);
                graphics.setColor(ROUTE_COLOR);
                graphics.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(route);
                drawEndpoint(graphics, coordinates.get(0), viewport, source, START_COLOR);
                drawEndpoint(graphics, coordinates.get(coordinates.size() - 1), viewport, source, END_COLOR);
            } finally {
                graphics.dispose();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(result, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("코스 지도 이미지를 합성할 수 없습니다.", exception);
        }
    }

    private Path2D createRoute(
            List<List<Double>> coordinates,
            CourseMapViewport viewport,
            int width,
            int height
    ) {
        Path2D path = new Path2D.Double();
        for (int index = 0; index < coordinates.size(); index++) {
            List<Double> coordinate = coordinates.get(index);
            CourseMapViewport.PixelPoint point = viewport.toPixel(
                    coordinate.get(0), coordinate.get(1), width, height
            );
            if (index == 0) {
                path.moveTo(point.x(), point.y());
            } else {
                path.lineTo(point.x(), point.y());
            }
        }
        return path;
    }

    private void drawEndpoint(
            Graphics2D graphics,
            List<Double> coordinate,
            CourseMapViewport viewport,
            BufferedImage image,
            Color color
    ) {
        CourseMapViewport.PixelPoint point = viewport.toPixel(
                coordinate.get(0), coordinate.get(1), image.getWidth(), image.getHeight()
        );
        double radius = 9;
        graphics.setColor(Color.WHITE);
        graphics.fill(new Ellipse2D.Double(point.x() - radius - 3, point.y() - radius - 3,
                (radius + 3) * 2, (radius + 3) * 2));
        graphics.setColor(color);
        graphics.fill(new Ellipse2D.Double(point.x() - radius, point.y() - radius,
                radius * 2, radius * 2));
    }
}

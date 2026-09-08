package com.back.course.map.render;

import java.util.List;

public record CourseMapViewport(double centerLatitude, double centerLongitude, int level) {

    private static final int TILE_SIZE = 256;
    private static final int MAX_LEVEL = 20;
    private static final int MIN_LEVEL = 1;
    private static final int PADDING_PIXELS = 56;

    public static CourseMapViewport fit(
            List<List<Double>> coordinates,
            int imageWidth,
            int imageHeight
    ) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("코스 경로 좌표가 비어 있습니다.");
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (List<Double> coordinate : coordinates) {
            double longitude = coordinate.get(0);
            double latitude = coordinate.get(1);
            double x = normalizedX(longitude);
            double y = normalizedY(latitude);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        double availableWidth = Math.max(1, imageWidth - PADDING_PIXELS * 2.0);
        double availableHeight = Math.max(1, imageHeight - PADDING_PIXELS * 2.0);
        int level = MIN_LEVEL;
        for (int candidate = MAX_LEVEL; candidate >= MIN_LEVEL; candidate--) {
            double worldSize = worldSize(candidate);
            if ((maxX - minX) * worldSize <= availableWidth
                    && (maxY - minY) * worldSize <= availableHeight) {
                level = candidate;
                break;
            }
        }

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        return new CourseMapViewport(latitudeFromY(centerY), longitudeFromX(centerX), level);
    }

    public PixelPoint toPixel(double longitude, double latitude, int imageWidth, int imageHeight) {
        double worldSize = worldSize(level);
        double centerX = normalizedX(centerLongitude) * worldSize;
        double centerY = normalizedY(centerLatitude) * worldSize;
        double x = normalizedX(longitude) * worldSize - centerX + imageWidth / 2.0;
        double y = normalizedY(latitude) * worldSize - centerY + imageHeight / 2.0;
        return new PixelPoint(x, y);
    }

    private static double normalizedX(double longitude) {
        return (longitude + 180.0) / 360.0;
    }

    private static double normalizedY(double latitude) {
        double boundedLatitude = Math.max(-85.05112878, Math.min(85.05112878, latitude));
        double sin = Math.sin(Math.toRadians(boundedLatitude));
        return 0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI);
    }

    private static double longitudeFromX(double x) {
        return x * 360.0 - 180.0;
    }

    private static double latitudeFromY(double y) {
        return Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y))));
    }

    private static double worldSize(int level) {
        return TILE_SIZE * Math.pow(2, level);
    }

    public record PixelPoint(double x, double y) {
    }
}

package com.back.grid.service;

import com.back.grid.dto.Bbox;
import com.back.grid.dto.GridOverlayResponse;
import com.back.grid.repository.GridOverlayProjection;
import com.back.grid.repository.GridRepository;
import java.time.Month;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GridService {
    private final GridRepository gridRepository;

    public GridService(GridRepository gridRepository) {
        this.gridRepository = gridRepository;
    }

    public List<GridOverlayResponse> findOverlays(String bboxValue, int hour, Month month) {
        Bbox bbox = Bbox.parse(bboxValue);

        return gridRepository.findAllByCentroidIn(
                        bbox.minLng(),
                        bbox.minLat(),
                        bbox.maxLng(),
                        bbox.maxLat()
                )
                .stream()
                .map(grid -> toResponse(grid, hour, month))
                .toList();
    }

    private GridOverlayResponse toResponse(GridOverlayProjection grid, int hour, Month month) {
        return new GridOverlayResponse(
                grid.getGridId(),
                grid.getRegionCode(),
                grid.getCentroidLat(),
                grid.getCentroidLng(),
                grid.getFlatness(),
                grid.getShadeSummer(),
                grid.getShadeWinterSun(),
                ShadeScoreResolver.resolve(
                        hour,
                        month,
                        grid.getShadeSummerHourly(),
                        grid.getShadeWinterHourly(),
                        grid.getShadeSummer(),
                        grid.getShadeWinterSun()
                ),
                grid.getTrafficLow(),
                grid.getWheelchair(),
                grid.getSurfaceNatural(),
                grid.getBenchDensity(),
                grid.getRestroomProximity(),
                grid.getWaterFacility()
        );
    }
}

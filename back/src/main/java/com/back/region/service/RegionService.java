package com.back.region.service;

import com.back.region.repository.RegionRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class RegionService {

    private final RegionRepository regions;

    public RegionService(RegionRepository regions) {
        this.regions = regions;
    }

    @Cacheable("regions")
    public List<RegionItem> list() {
        return regions.findAllListViews().stream()
                .map(v -> new RegionItem(
                        v.getRegionCode(),
                        v.getName(),
                        v.getCenterLat(),
                        v.getCenterLng(),
                        v.getCourseCount()))
                .toList();
    }

    public record RegionItem(
            String regionCode,
            String name,
            double centerLat,
            double centerLng,
            int courseCount
    ) {}
}

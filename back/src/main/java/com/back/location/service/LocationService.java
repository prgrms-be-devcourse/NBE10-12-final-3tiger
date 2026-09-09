package com.back.location.service;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.location.dto.ReverseGeocodeResponse;
import com.back.map.naver.NaverMapClient;
import com.back.map.naver.dto.NaverReverseGeocodeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final Set<SupportedRegion> SUPPORTED_REGIONS = Set.of(
            new SupportedRegion("서울", "강서구"),
            new SupportedRegion("서울", "양천구")
    );

    private final NaverMapClient naverMapClient;

    public ReverseGeocodeResponse reverseGeocode(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);

        NaverReverseGeocodeResponse response = naverMapClient.reverseGeocode(latitude, longitude);
        List<NaverReverseGeocodeResponse.Result> results = response.results();
        if (results == null || results.isEmpty()) {
            throw new BusinessException(ErrorCode.LOCATION_ADDRESS_NOT_FOUND);
        }

        var roadResult = findResult(results, "roadaddr");
        var jibunResult = findResult(results, "addr");
        var regionResult = roadResult != null ? roadResult : jibunResult;

        String city = areaName(regionResult, 1);
        String district = areaName(regionResult, 2);
        String neighborhood = areaName(regionResult, 3);

        return new ReverseGeocodeResponse(
                latitude,
                longitude,
                buildAddress(roadResult),
                buildAddress(jibunResult),
                city,
                district,
                neighborhood,
                isSupportedRegion(city, district)
        );
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            throw new BusinessException(ErrorCode.INVALID_COORDINATE);
        }
    }

    private NaverReverseGeocodeResponse.Result findResult(
            List<NaverReverseGeocodeResponse.Result> results,
            String name
    ) {
        return results.stream()
                .filter(result -> name.equals(result.name()))
                .findFirst()
                .orElse(null);
    }

    private String buildAddress(NaverReverseGeocodeResponse.Result result) {
        if (result == null) {
            return null;
        }

        NaverReverseGeocodeResponse.Land land = result.land();
        return joinNonBlank(
                areaName(result, 1),
                areaName(result, 2),
                areaName(result, 3),
                areaName(result, 4),
                land == null ? null : land.name(),
                landNumber(land)
        );
    }

    private String areaName(NaverReverseGeocodeResponse.Result result, int depth) {
        if (result == null || result.region() == null) {
            return null;
        }
        NaverReverseGeocodeResponse.Area area = switch (depth) {
            case 1 -> result.region().area1();
            case 2 -> result.region().area2();
            case 3 -> result.region().area3();
            case 4 -> result.region().area4();
            default -> null;
        };
        return area == null ? null : area.name();
    }

    private String landNumber(NaverReverseGeocodeResponse.Land land) {
        if (land == null || !StringUtils.hasText(land.number1())) {
            return null;
        }
        return StringUtils.hasText(land.number2())
                ? land.number1() + "-" + land.number2()
                : land.number1();
    }

    private String joinNonBlank(String... parts) {
        return String.join(" ", java.util.Arrays.stream(parts)
                .filter(StringUtils::hasText)
                .toList());
    }

    private boolean isSupportedRegion(String city, String district) {
        return SUPPORTED_REGIONS.contains(
                new SupportedRegion(normalizeCity(city), district)
        );
    }

    private String normalizeCity(String city) {
        return "서울특별시".equals(city) ? "서울" : city;
    }

    private record SupportedRegion(String city, String district) {}
}

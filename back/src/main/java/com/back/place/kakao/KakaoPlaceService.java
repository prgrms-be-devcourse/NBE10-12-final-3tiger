package com.back.place.kakao;

import com.back.place.kakao.dto.PlaceSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class KakaoPlaceService {

    private static final Set<SupportedRegion> SUPPORTED_REGIONS = Set.of(
            new SupportedRegion("서울", "강서구"),
            new SupportedRegion("서울", "양천구")
    );

    private final KakaoPlaceClient client;

    public List<PlaceSearchItem> search(String query) {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("검색어를 입력해 주세요.");
        }

        var response = client.search(query.trim(), 15);

        if (response == null || response.documents() == null) {
            return List.of();
        }

        return response.documents().stream()
                .map(item -> new PlaceSearchItem(
                        item.placeName(),
                        item.addressName(),
                        item.roadAddressName(),
                        parseCoordinate(item.y()),
                        parseCoordinate(item.x()),
                        item.categoryName(),
                        item.placeUrl(),
                        isSupportedRegion(item.addressName(), item.roadAddressName())
                ))
                .toList();
    }

    private boolean isSupportedRegion(String address, String roadAddress) {
        return isSupportedAddress(address) || isSupportedAddress(roadAddress);
    }

    private boolean isSupportedAddress(String address) {
        if (!StringUtils.hasText(address)) {
            return false;
        }

        String[] parts = address.trim().split("\\s+");
        if (parts.length < 2) {
            return false;
        }

        SupportedRegion region = new SupportedRegion(normalizeCity(parts[0]), parts[1]);
        return SUPPORTED_REGIONS.contains(region);
    }

    private String normalizeCity(String city) {
        return switch (city) {
            case "서울특별시" -> "서울";
            default -> city;
        };
    }

    private double parseCoordinate(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            throw new IllegalStateException("카카오 좌표 응답이 올바르지 않습니다.");
        }
    }

    private record SupportedRegion(String city, String district) {}
}

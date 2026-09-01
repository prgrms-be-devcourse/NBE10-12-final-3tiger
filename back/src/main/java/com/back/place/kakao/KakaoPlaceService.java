package com.back.place.kakao;

import com.back.place.kakao.dto.PlaceSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KakaoPlaceService {

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
                        item.placeUrl()
                ))
                .toList();
    }

    private double parseCoordinate(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            throw new IllegalStateException("카카오 좌표 응답이 올바르지 않습니다.");
        }
    }
}

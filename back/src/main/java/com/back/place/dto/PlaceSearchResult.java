package com.back.place.dto;

import com.back.place.kakao.dto.PlaceSearchItem;

import java.util.List;

public record PlaceSearchResult(
        String originalQuery,
        String correctedQuery,
        boolean correctionApplied,
        List<PlaceSearchItem> items
) {
}

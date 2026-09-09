package com.back.place.service;

import com.back.place.dto.PlaceSearchResult;
import com.back.place.kakao.KakaoPlaceService;
import com.back.place.kakao.dto.PlaceSearchItem;
import com.back.place.naver.NaverTypoCorrectionException;
import com.back.place.naver.NaverTypoCorrectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final KakaoPlaceService kakaoPlaceService;
    private final NaverTypoCorrectionService typoCorrectionService;

    public PlaceSearchResult search(String query) {
        String originalQuery = query.trim();
        List<PlaceSearchItem> originalItems = kakaoPlaceService.search(originalQuery);
        if (!originalItems.isEmpty()) {
            return result(originalQuery, null, false, originalItems);
        }

        String correctedQuery;
        try {
            correctedQuery = typoCorrectionService.correct(originalQuery);
        } catch (NaverTypoCorrectionException exception) {
            return result(originalQuery, null, false, originalItems);
        }

        if (!StringUtils.hasText(correctedQuery)
                || originalQuery.equalsIgnoreCase(correctedQuery.trim())) {
            return result(originalQuery, null, false, originalItems);
        }

        correctedQuery = correctedQuery.trim();
        List<PlaceSearchItem> correctedItems = kakaoPlaceService.search(correctedQuery);
        return result(originalQuery, correctedQuery, true, correctedItems);
    }

    private PlaceSearchResult result(
            String originalQuery,
            String correctedQuery,
            boolean correctionApplied,
            List<PlaceSearchItem> items
    ) {
        return new PlaceSearchResult(originalQuery, correctedQuery, correctionApplied, items);
    }
}

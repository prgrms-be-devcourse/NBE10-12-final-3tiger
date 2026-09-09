package com.back.place.service;

import com.back.place.kakao.KakaoPlaceService;
import com.back.place.kakao.dto.PlaceSearchItem;
import com.back.place.naver.NaverTypoCorrectionException;
import com.back.place.naver.NaverTypoCorrectionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PlaceSearchServiceTest {

    private final KakaoPlaceService kakao = mock(KakaoPlaceService.class);
    private final NaverTypoCorrectionService typoCorrection = mock(NaverTypoCorrectionService.class);
    private final PlaceSearchService service = new PlaceSearchService(kakao, typoCorrection);

    @Test
    void doesNotCallTypoCorrectionWhenOriginalSearchHasResults() {
        PlaceSearchItem item = place("서울식물원");
        given(kakao.search("서울식물원")).willReturn(List.of(item));

        var result = service.search("  서울식물원  ");

        assertThat(result.originalQuery()).isEqualTo("서울식물원");
        assertThat(result.correctedQuery()).isNull();
        assertThat(result.correctionApplied()).isFalse();
        assertThat(result.items()).containsExactly(item);
        verify(typoCorrection, never()).correct("서울식물원");
    }

    @Test
    void retriesKakaoOnceWithCorrectedQueryWhenOriginalResultIsEmpty() {
        PlaceSearchItem correctedItem = place("서울식물원");
        given(kakao.search("tjdnftlranfdnjs")).willReturn(List.of());
        given(typoCorrection.correct("tjdnftlranfdnjs")).willReturn("서울식물원");
        given(kakao.search("서울식물원")).willReturn(List.of(correctedItem));

        var result = service.search("tjdnftlranfdnjs");

        assertThat(result.correctedQuery()).isEqualTo("서울식물원");
        assertThat(result.correctionApplied()).isTrue();
        assertThat(result.items()).containsExactly(correctedItem);
        verify(kakao).search("서울식물원");
    }

    @Test
    void returnsOriginalEmptyResultWhenThereIsNoCorrection() {
        given(kakao.search("없는장소")).willReturn(List.of());
        given(typoCorrection.correct("없는장소")).willReturn("");

        var result = service.search("없는장소");

        assertThat(result.correctionApplied()).isFalse();
        assertThat(result.items()).isEmpty();
        verify(kakao).search("없는장소");
    }

    @Test
    void ignoresNaverFailureBecauseTypoCorrectionIsOptional() {
        given(kakao.search("없는장소")).willReturn(List.of());
        given(typoCorrection.correct("없는장소"))
                .willThrow(new NaverTypoCorrectionException("failed"));

        var result = service.search("없는장소");

        assertThat(result.correctionApplied()).isFalse();
        assertThat(result.items()).isEmpty();
    }

    private PlaceSearchItem place(String name) {
        return new PlaceSearchItem(
                name,
                "서울 강서구 마곡동",
                "서울 강서구 마곡동로 161",
                37.5667,
                126.8276,
                "여행 > 공원",
                "https://place.map.kakao.com/1",
                true
        );
    }
}

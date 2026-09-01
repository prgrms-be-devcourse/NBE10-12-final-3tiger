package com.back.place.kakao;

import com.back.place.kakao.dto.KakaoPlaceSearchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class KakaoPlaceServiceTest {

    private final KakaoPlaceClient client = mock(KakaoPlaceClient.class);
    private final KakaoPlaceService service = new KakaoPlaceService(client);

    @Test
    void marksGangseoAndYangcheonAsSupportedRegions() {
        given(client.search("공원", 15)).willReturn(response(
                document("강서구 공원", "서울 강서구 마곡동", "서울 강서구 마곡중앙로 1"),
                document("양천구 공원", "서울특별시 양천구 신정동", "")
        ));

        var result = service.search("공원");

        assertThat(result)
                .extracting(item -> item.supportedRegion())
                .containsExactly(true, true);
    }

    @Test
    void doesNotConfuseBusanGangseoWithSeoulGangseo() {
        given(client.search("강서구 공원", 15)).willReturn(response(
                document("부산 공원", "부산 강서구 명지동", "부산 강서구 명지국제1로 1")
        ));

        var result = service.search("강서구 공원");

        assertThat(result).singleElement()
                .satisfies(item -> assertThat(item.supportedRegion()).isFalse());
    }

    private KakaoPlaceSearchResponse response(KakaoPlaceSearchResponse.Document... documents) {
        return new KakaoPlaceSearchResponse(
                List.of(documents),
                new KakaoPlaceSearchResponse.Meta(documents.length)
        );
    }

    private KakaoPlaceSearchResponse.Document document(
            String name,
            String address,
            String roadAddress
    ) {
        return new KakaoPlaceSearchResponse.Document(
                name,
                address,
                roadAddress,
                "126.8495",
                "37.5509",
                "여행 > 공원",
                "https://place.map.kakao.com/1"
        );
    }
}

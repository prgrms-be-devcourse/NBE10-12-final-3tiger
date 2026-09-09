package com.back.location;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.location.service.LocationService;
import com.back.map.naver.NaverMapClient;
import com.back.map.naver.dto.NaverReverseGeocodeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LocationServiceTest {

    private final NaverMapClient client = mock(NaverMapClient.class);
    private final LocationService service = new LocationService(client);

    @Test
    void normalizesRoadAndJibunAddressesAndMarksSupportedRegion() {
        given(client.reverseGeocode(37.5667106, 126.827658))
                .willReturn(response(
                        result("roadaddr", "서울특별시", "강서구", "마곡동", "마곡동로", "161", ""),
                        result("addr", "서울특별시", "강서구", "마곡동", "", "812", "")
                ));

        var result = service.reverseGeocode(37.5667106, 126.827658);

        assertThat(result.roadAddress()).isEqualTo("서울특별시 강서구 마곡동 마곡동로 161");
        assertThat(result.jibunAddress()).isEqualTo("서울특별시 강서구 마곡동 812");
        assertThat(result.city()).isEqualTo("서울특별시");
        assertThat(result.district()).isEqualTo("강서구");
        assertThat(result.neighborhood()).isEqualTo("마곡동");
        assertThat(result.supportedRegion()).isTrue();
    }

    @Test
    void marksOutsideRegionAsUnsupported() {
        given(client.reverseGeocode(35.1796, 129.0756))
                .willReturn(response(result(
                        "addr", "부산광역시", "부산진구", "부전동", "", "1", ""
                )));

        assertThat(service.reverseGeocode(35.1796, 129.0756).supportedRegion()).isFalse();
    }

    @Test
    void rejectsInvalidCoordinatesBeforeCallingNaver() {
        assertThatThrownBy(() -> service.reverseGeocode(91, 126.8))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_COORDINATE));

        verifyNoInteractions(client);
    }

    @Test
    void returnsNotFoundWhenNaverHasNoAddress() {
        given(client.reverseGeocode(37.5, 126.8))
                .willReturn(new NaverReverseGeocodeResponse(
                        new NaverReverseGeocodeResponse.Status(0, "ok", "done"),
                        List.of()
                ));

        assertThatThrownBy(() -> service.reverseGeocode(37.5, 126.8))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.LOCATION_ADDRESS_NOT_FOUND));
    }

    private NaverReverseGeocodeResponse response(NaverReverseGeocodeResponse.Result... results) {
        return new NaverReverseGeocodeResponse(
                new NaverReverseGeocodeResponse.Status(0, "ok", "done"),
                List.of(results)
        );
    }

    private NaverReverseGeocodeResponse.Result result(
            String name,
            String city,
            String district,
            String neighborhood,
            String landName,
            String number1,
            String number2
    ) {
        return new NaverReverseGeocodeResponse.Result(
                name,
                new NaverReverseGeocodeResponse.Region(
                        new NaverReverseGeocodeResponse.Area("kr"),
                        new NaverReverseGeocodeResponse.Area(city),
                        new NaverReverseGeocodeResponse.Area(district),
                        new NaverReverseGeocodeResponse.Area(neighborhood),
                        new NaverReverseGeocodeResponse.Area("")
                ),
                new NaverReverseGeocodeResponse.Land("", number1, number2, landName)
        );
    }
}

package com.back.grid.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.back.global.error.ApiException;
import org.junit.jupiter.api.Test;

class BboxTest {
    @Test
    void parsesMinAndMaxCoordinates() {
        Bbox bbox = Bbox.parse("126.80,37.50,126.81,37.51");

        assertThat(bbox).isEqualTo(new Bbox(126.80, 37.50, 126.81, 37.51));
    }

    @Test
    void rejectsMissingOrMalformedBbox() {
        assertThatThrownBy(() -> Bbox.parse(null))
                .isInstanceOf(ApiException.class)
                .hasMessage("bbox는 필수입니다.");
        assertThatThrownBy(() -> Bbox.parse("126.80,37.50,126.90"))
                .isInstanceOf(ApiException.class)
                .hasMessage("bbox는 minLng,minLat,maxLng,maxLat 형식이어야 합니다.");
    }

    @Test
    void rejectsReversedCoordinates() {
        assertThatThrownBy(() -> Bbox.parse("126.90,37.60,126.80,37.50"))
                .isInstanceOf(ApiException.class)
                .hasMessage("bbox 최소 좌표는 최대 좌표보다 작아야 합니다.");
    }

    @Test
    void acceptsBboxAtMaximumSpan() {
        Bbox bbox = Bbox.parse("126.80,37.50,126.82,37.52");

        assertThat(bbox).isEqualTo(new Bbox(126.80, 37.50, 126.82, 37.52));
    }

    @Test
    void rejectsBboxLargerThanMaximumSpan() {
        assertThatThrownBy(() -> Bbox.parse("126.80,37.50,126.821,37.52"))
                .isInstanceOf(ApiException.class)
                .hasMessage("bbox 조회 범위는 위도와 경도 각각 0.02도 이하여야 합니다.");
    }
}

package com.back.grid.service;

import com.back.grid.dto.GridOverlayResponse;
import com.back.grid.repository.GridOverlayProjection;
import com.back.grid.repository.GridRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GridServiceTest {

    private static final String VALID_BBOX = "127.000000,37.500000,127.010000,37.510000";

    @Mock
    private GridRepository gridRepository;

    @InjectMocks
    private GridService gridService;

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static GridOverlayProjection projectionOf(BigDecimal[] summerHourly, BigDecimal[] winterHourly,
                                                      BigDecimal summerScalar, BigDecimal winterScalar) {
        GridOverlayProjection p = mock(GridOverlayProjection.class);
        given(p.getGridId()).willReturn(1L);
        given(p.getRegionCode()).willReturn("11500");
        given(p.getCentroidLat()).willReturn(37.5);
        given(p.getCentroidLng()).willReturn(127.0);
        doReturn(summerHourly).when(p).getShadeSummerHourly();
        doReturn(winterHourly).when(p).getShadeWinterHourly();
        given(p.getShadeSummer()).willReturn(summerScalar);
        given(p.getShadeWinterSun()).willReturn(winterScalar);
        return p;
    }

    @Test
    @DisplayName("여름 hour=14는 summer hourly 배열 인덱스 3을 shadeNow에 담아 응답한다")
    void summer14PicksSummerIndex3() {
        BigDecimal[] summer = {bd("0.80"), bd("0.70"), bd("0.60"), bd("0.50"), bd("0.40"), bd("0.30"), bd("0.20")};
        GridOverlayProjection projection = projectionOf(summer, null, bd("0.55"), bd("0.44"));
        given(gridRepository.findAllByCentroidIn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(projection));

        List<GridOverlayResponse> result = gridService.findOverlays(VALID_BBOX, 14, Month.JULY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).shadeNow()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("겨울 hour=12는 winter hourly 배열 인덱스 2를 shadeNow에 담아 응답한다")
    void winter12PicksWinterIndex2() {
        BigDecimal[] winter = {bd("0.15"), bd("0.25"), bd("0.35"), bd("0.45"), bd("0.55"), bd("0.65")};
        GridOverlayProjection projection = projectionOf(null, winter, bd("0.55"), bd("0.44"));
        given(gridRepository.findAllByCentroidIn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(projection));

        List<GridOverlayResponse> result = gridService.findOverlays(VALID_BBOX, 12, Month.JANUARY);

        assertThat(result.get(0).shadeNow()).isEqualByComparingTo("0.35");
    }

    @Test
    @DisplayName("hourly 배열이 없으면 계절에 맞는 scalar로 fallback한다")
    void fallsBackToScalarWhenHourlyIsNull() {
        GridOverlayProjection projection = projectionOf(null, null, bd("0.55"), bd("0.44"));
        given(gridRepository.findAllByCentroidIn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(projection));

        List<GridOverlayResponse> summerResult = gridService.findOverlays(VALID_BBOX, 14, Month.JULY);
        List<GridOverlayResponse> winterResult = gridService.findOverlays(VALID_BBOX, 12, Month.JANUARY);

        assertThat(summerResult.get(0).shadeNow()).isEqualByComparingTo("0.55");
        assertThat(winterResult.get(0).shadeNow()).isEqualByComparingTo("0.44");
    }

    @Test
    @DisplayName("응답에 기존 스칼라 필드도 그대로 포함된다")
    void keepsLegacyScalarFieldsInResponse() {
        GridOverlayProjection projection = projectionOf(null, null, bd("0.55"), bd("0.44"));
        given(gridRepository.findAllByCentroidIn(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .willReturn(List.of(projection));

        GridOverlayResponse response = gridService.findOverlays(VALID_BBOX, 14, Month.JULY).get(0);

        assertThat(response.shadeSummer()).isEqualByComparingTo("0.55");
        assertThat(response.shadeWinterSun()).isEqualByComparingTo("0.44");
    }
}

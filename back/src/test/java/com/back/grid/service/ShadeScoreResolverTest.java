package com.back.grid.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class ShadeScoreResolverTest {

    private static final BigDecimal[] SUMMER = {
            bd("0.80"), bd("0.70"), bd("0.60"), bd("0.50"), bd("0.40"), bd("0.30"), bd("0.20")
    };
    private static final BigDecimal[] WINTER = {
            bd("0.15"), bd("0.25"), bd("0.35"), bd("0.45"), bd("0.55"), bd("0.65")
    };
    private static final BigDecimal SUMMER_SCALAR = bd("0.99");
    private static final BigDecimal WINTER_SCALAR = bd("0.11");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @ParameterizedTest(name = "여름 {0}시 → summer 인덱스 {1} 값")
    @CsvSource({
            "8, 0.80", "10, 0.70", "12, 0.60", "14, 0.50",
            "16, 0.40", "18, 0.30", "20, 0.20"
    })
    void summerHourMapsToSummerArrayIndex(int hour, String expected) {
        BigDecimal result = ShadeScoreResolver.resolve(hour, Month.JULY, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @ParameterizedTest(name = "겨울 {0}시 → winter 인덱스 값")
    @CsvSource({
            "8, 0.15", "10, 0.25", "12, 0.35",
            "14, 0.45", "16, 0.55", "18, 0.65"
    })
    void winterHourMapsToWinterArrayIndex(int hour, String expected) {
        BigDecimal result = ShadeScoreResolver.resolve(hour, Month.JANUARY, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("여름 08시 이전은 summer 배열 첫 원소로 clamp")
    void summerBeforeStartClampsToFirst() {
        BigDecimal result = ShadeScoreResolver.resolve(5, Month.AUGUST, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.80");
    }

    @Test
    @DisplayName("여름 20시 이후는 summer 배열 마지막 원소로 clamp")
    void summerAfterEndClampsToLast() {
        BigDecimal result = ShadeScoreResolver.resolve(23, Month.AUGUST, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.20");
    }

    @Test
    @DisplayName("겨울 18시 이후는 winter 배열 마지막 원소로 clamp")
    void winterAfterEndClampsToLast() {
        BigDecimal result = ShadeScoreResolver.resolve(21, Month.DECEMBER, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.65");
    }

    @ParameterizedTest(name = "{0}월은 여름으로 판단")
    @EnumSource(value = Month.class, names = {"JUNE", "JULY", "AUGUST"})
    void junJulAugAreSummer(Month month) {
        BigDecimal result = ShadeScoreResolver.resolve(14, month, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.50");
    }

    @ParameterizedTest(name = "{0}월은 겨울(비여름)으로 판단")
    @EnumSource(value = Month.class, names = {"JANUARY", "MARCH", "MAY", "SEPTEMBER", "NOVEMBER"})
    void nonSummerMonthsAreWinter(Month month) {
        BigDecimal result = ShadeScoreResolver.resolve(12, month, SUMMER, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.35");
    }

    @Test
    @DisplayName("여름인데 summerHourly가 null이면 summer scalar로 fallback")
    void summerHourlyNullFallsBackToSummerScalar() {
        BigDecimal result = ShadeScoreResolver.resolve(14, Month.JULY, null, WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.99");
    }

    @Test
    @DisplayName("겨울인데 winterHourly가 null이면 winter scalar로 fallback")
    void winterHourlyNullFallsBackToWinterScalar() {
        BigDecimal result = ShadeScoreResolver.resolve(12, Month.JANUARY, SUMMER, null, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.11");
    }

    @Test
    @DisplayName("여름인데 summerHourly와 summer scalar가 모두 null이면 null 반환")
    void summerAllNullReturnsNull() {
        BigDecimal result = ShadeScoreResolver.resolve(14, Month.JULY, null, WINTER, null, WINTER_SCALAR);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("빈 배열(길이 0)이면 scalar로 fallback")
    void emptyHourlyArrayFallsBackToScalar() {
        BigDecimal result = ShadeScoreResolver.resolve(14, Month.JULY, new BigDecimal[0], WINTER, SUMMER_SCALAR, WINTER_SCALAR);
        assertThat(result).isEqualByComparingTo("0.99");
    }
}

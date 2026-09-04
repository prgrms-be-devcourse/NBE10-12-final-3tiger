package com.back.grid.service;

import java.math.BigDecimal;
import java.time.Month;

public final class ShadeScoreResolver {

    private static final int HOUR_STEP = 2;
    private static final int HOUR_START = 8;

    private ShadeScoreResolver() {}

    public static BigDecimal resolve(
            int hour,
            Month month,
            BigDecimal[] summerHourly,
            BigDecimal[] winterHourly,
            BigDecimal summerScalar,
            BigDecimal winterScalar
    ) {
        boolean summer = isSummer(month);
        BigDecimal[] hourly = summer ? summerHourly : winterHourly;
        BigDecimal scalar = summer ? summerScalar : winterScalar;

        if (hourly == null || hourly.length == 0) {
            return scalar;
        }
        int index = clamp((hour - HOUR_START) / HOUR_STEP, 0, hourly.length - 1);
        return hourly[index];
    }

    private static boolean isSummer(Month month) {
        int m = month.getValue();
        return m >= 6 && m <= 8;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

package com.back.global.resilience;

import com.back.global.exception.BusinessException;
import com.back.global.exception.ErrorCode;
import com.back.place.naver.NaverTypoCorrectionException;

import java.util.Set;
import java.util.function.Predicate;

public class ExternalApiFailurePredicate implements Predicate<Throwable> {

    private static final Set<ErrorCode> EXTERNAL_FAILURES = Set.of(
            ErrorCode.SOCIAL_SERVER_ERROR,
            ErrorCode.KAKAO_PLACE_SEARCH_FAILED,
            ErrorCode.KAKAO_DIRECTIONS_FAILED,
            ErrorCode.KAKAO_DIRECTIONS_QUOTA_EXCEEDED,
            ErrorCode.NAVER_REVERSE_GEOCODING_FAILED
    );

    @Override
    public boolean test(Throwable throwable) {
        if (throwable instanceof BusinessException exception) {
            return EXTERNAL_FAILURES.contains(exception.getErrorCode());
        }

        return throwable instanceof NaverTypoCorrectionException
                || throwable instanceof IllegalStateException;
    }
}

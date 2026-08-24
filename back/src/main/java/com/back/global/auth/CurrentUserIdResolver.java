package com.back.global.auth;

import com.back.global.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserIdResolver implements HandlerMethodArgumentResolver {
    private final boolean allowDevUser;
    private final Long devUserId;

    public CurrentUserIdResolver(@Value("${app.auth.allow-dev-user:false}") boolean allowDevUser,
                                 @Value("${app.auth.dev-user-id:1}") Long devUserId) {
        this.allowDevUser = allowDevUser;
        this.devUserId = devUserId;
    }

    @Override public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class) && parameter.getParameterType() == Long.class;
    }

    @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
        String value = request.getHeader("X-User-Id");
        if (value == null && allowDevUser) return devUserId;
        if (value == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
        try { return Long.valueOf(value); }
        catch (NumberFormatException e) { throw new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 사용자 ID입니다."); }
    }
}

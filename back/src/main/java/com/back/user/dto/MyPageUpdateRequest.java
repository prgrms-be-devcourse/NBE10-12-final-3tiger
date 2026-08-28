package com.back.user.dto;

import com.back.course.domain.Persona;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MyPageUpdateRequest(
        @Size(max = 50)
        @Pattern(regexp = "^(?!\\s*$).+", message = "닉네임은 공백일 수 없습니다.")
        String nickname,

        Persona primaryPersona,

        List<@NotNull Persona> personaTags
) {
}

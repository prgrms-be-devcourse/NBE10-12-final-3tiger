package com.back.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserMemoRequest(
        @NotNull @Size(max = 10) List<@NotBlank @Size(max = 20) String> tags,
        @Size(max = 1000) String memo
) {
}

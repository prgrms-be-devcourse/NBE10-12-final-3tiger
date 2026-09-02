package com.back.user.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UserMemoResponse(Long targetUserId, List<String> tags, String memo, LocalDateTime updatedAt) {
}

package com.back.hazard.domain;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HazardTest {

    @Test
    @DisplayName("공감은 공감 수만 1 증가시키고 만료 및 생성 시각을 변경하지 않는다")
    void increasesOnlyUpvoteCount() {
        LocalDateTime expiresAt = LocalDateTime.of(2027, 3, 31, 0, 0);
        Hazard hazard = new Hazard(
                new Course("테스트 코스", "11500", 3000),
                User.createLocal("reporter@test.com", "hash", "신고자"),
                "빙판",
                "상",
                "그늘진 구간 결빙 주의",
                expiresAt
        );
        LocalDateTime createdAt = hazard.getCreatedAt();

        hazard.increaseUpvote();

        assertThat(hazard.getUpvoteCount()).isEqualTo(1);
        assertThat(hazard.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(hazard.getCreatedAt()).isEqualTo(createdAt);
    }
}

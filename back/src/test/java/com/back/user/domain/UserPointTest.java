package com.back.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserPointTest {

    @Test
    @DisplayName("양수 포인트만 잔액에 적립한다")
    void addsPositivePoints() {
        User user = User.createLocal("point-user@test.com", "hash", "사용자");

        user.addPoints(10L);
        user.addPoints(100L);

        assertThat(user.getPointBalance()).isEqualTo(110L);
    }

    @Test
    @DisplayName("0 이하 포인트 적립은 거부한다")
    void rejectsNonPositivePoints() {
        User user = User.createLocal("invalid-point@test.com", "hash", "사용자");

        assertThatThrownBy(() -> user.addPoints(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.addPoints(-10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPointBalance()).isZero();
    }
}

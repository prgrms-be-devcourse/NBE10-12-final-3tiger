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

    @Test
    @DisplayName("보유 포인트 범위 안에서 포인트를 차감한다")
    void spendsPointsWithinBalance() {
        User user = User.createLocal("spend@test.com", "hash", "사용자");
        user.addPoints(100L);

        user.spendPoints(70L);

        assertThat(user.getPointBalance()).isEqualTo(30L);
    }

    @Test
    @DisplayName("0 이하 금액은 차감할 수 없다")
    void rejectsNonPositiveSpendAmount() {
        User user = User.createLocal("invalid-spend@test.com", "hash", "사용자");
        user.addPoints(100L);

        assertThatThrownBy(() -> user.spendPoints(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.spendPoints(-10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(user.getPointBalance()).isEqualTo(100L);
    }

    @Test
    @DisplayName("보유 포인트보다 큰 금액은 차감할 수 없다")
    void rejectsSpendOverBalance() {
        User user = User.createLocal("over-spend@test.com", "hash", "사용자");
        user.addPoints(50L);

        assertThatThrownBy(() -> user.spendPoints(60L))
                .isInstanceOf(IllegalStateException.class);
        assertThat(user.getPointBalance()).isEqualTo(50L);
    }
}

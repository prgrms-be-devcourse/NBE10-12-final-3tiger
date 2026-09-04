package com.back.hazard.domain;

import com.back.course.domain.Course;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HazardTest {

    @Test
    @DisplayName("새 위험 후보는 PENDING 상태로 생성된다")
    void createsPendingHazard() {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        assertThat(hazard.getActivatedAt()).isNull();
        assertThat(hazard.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 신고자가 임계치에 도달하면 ACTIVE로 전환한다")
    void activatesAtReporterThreshold() {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");

        hazard.updateStatusByReporterCount(2, 3);
        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);

        hazard.updateStatusByReporterCount(3, 3);
        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(hazard.getActivatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ACTIVE 위험은 신고자가 임계치 미만으로 줄면 PENDING으로 돌아간다")
    void revertsActiveHazardToPending() {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");
        hazard.updateStatusByReporterCount(3, 3);

        hazard.updateStatusByReporterCount(2, 3);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.PENDING);
        assertThat(hazard.getActivatedAt()).isNull();
    }

    @Test
    @DisplayName("ACTIVE 위험은 신고자가 세 명 이상 남으면 활성화 시각을 유지한다")
    void keepsActiveHazardAtReporterThreshold() {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");
        hazard.updateStatusByReporterCount(4, 3);
        var activatedAt = hazard.getActivatedAt();

        hazard.updateStatusByReporterCount(3, 3);

        assertThat(hazard.getStatus()).isEqualTo(HazardStatus.ACTIVE);
        assertThat(hazard.getActivatedAt()).isEqualTo(activatedAt);
    }
}

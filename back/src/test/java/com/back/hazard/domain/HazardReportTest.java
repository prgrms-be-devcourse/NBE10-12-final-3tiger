package com.back.hazard.domain;

import com.back.course.domain.Course;
import com.back.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HazardReportTest {

    @Test
    @DisplayName("개별 신고는 신고자와 원본 GPS 위치를 보존한다")
    void keepsReporterAndOriginalGps() {
        Hazard hazard = new Hazard(new Course("테스트 코스", "11500", 3000), "빙판");
        User reporter = User.createLocal("reporter@test.com", "hash", "신고자");

        HazardReport report = new HazardReport(
                hazard, reporter, "상", "그늘진 구간 결빙 주의", 37.5219, 126.8575);

        assertThat(report.getHazard()).isSameAs(hazard);
        assertThat(report.getReporter()).isSameAs(reporter);
        assertThat(report.getSeverity()).isEqualTo("상");
        assertThat(report.getContent()).isEqualTo("그늘진 구간 결빙 주의");
        assertThat(report.getLatitude()).isEqualTo(37.5219);
        assertThat(report.getLongitude()).isEqualTo(126.8575);
        assertThat(report.getCreatedAt()).isNotNull();
    }
}

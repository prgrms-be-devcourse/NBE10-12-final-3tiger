package com.back.hazard.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=none")
@ActiveProfiles("test")
@Sql("/hazard/walk-edge-candidate-fixture.sql")
class WalkEdgeCandidateRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGIS = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5")
                    .asCompatibleSubstituteFor("postgres")
    ).withCreateContainerCmdModifier(command -> command.withPlatform("linux/amd64"));

    @Autowired
    private WalkEdgeCandidateRepository repository;

    @Test
    @DisplayName("4326 GPS를 5179로 변환해 거리(m)가 가까운 edge 후보를 반환한다")
    void findsNearestEdgesWithMeterDistance() {
        var candidates = repository.findNearest(37.5001, 126.8005, 2);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.getFirst().edgeId()).isEqualTo(101L);
        assertThat(candidates.getFirst().source()).isEqualTo(1001L);
        assertThat(candidates.getFirst().target()).isEqualTo(1002L);
        assertThat(candidates.getFirst().distanceM()).isBetween(10.0, 12.5);
        assertThat(candidates.get(1).distanceM()).isGreaterThan(candidates.getFirst().distanceM());
    }

    @Test
    @DisplayName("신고 위치가 달라지면 가장 가까운 edge와 거리가 달라진다")
    void changesCandidatesWhenGpsChanges() {
        var nearFirst = repository.findNearest(37.5001, 126.8005, 1);
        var nearSecond = repository.findNearest(37.5009, 126.8005, 1);

        assertThat(nearFirst.getFirst().edgeId()).isEqualTo(101L);
        assertThat(nearSecond.getFirst().edgeId()).isEqualTo(102L);
        assertThat(nearFirst.getFirst().distanceM()).isNotEqualTo(nearSecond.getFirst().distanceM());
    }

    @Test
    @DisplayName("같은 도로 구간에 가까운 두 GPS는 현재 데이터에서 같은 edge를 선택한다")
    void selectsSameEdgeForNearbyPointsOnSameRoad() {
        var first = repository.findNearest(37.5001, 126.8004, 1);
        var second = repository.findNearest(37.5001, 126.8006, 1);

        assertThat(first.getFirst().edgeId()).isEqualTo(101L);
        assertThat(second.getFirst().edgeId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("두 GPS가 shared vertex 5m 이내인지 5179 좌표계의 meter 거리로 확인한다")
    void checksBothGpsDistancesFromSharedVertex() {
        boolean withinFiveMeters = repository.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1002L),
                37.5000, 126.80098,
                37.50002, 126.8010,
                5.0
        );
        boolean onePointOutside = repository.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1002L),
                37.5000, 126.80090,
                37.50002, 126.8010,
                5.0
        );
        boolean otherPointOutside = repository.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1002L),
                37.5000, 126.80098,
                37.50010, 126.8010,
                5.0
        );
        boolean bothPointsOutside = repository.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1002L),
                37.5000, 126.80090,
                37.50010, 126.8010,
                5.0
        );

        assertThat(withinFiveMeters).isTrue();
        assertThat(onePointOutside).isFalse();
        assertThat(otherPointOutside).isFalse();
        assertThat(bothPointsOutside).isFalse();
    }

    @Test
    @DisplayName("공유 vertex 후보가 둘이면 어느 하나가 두 GPS 모두 5m 이내일 때 참이다")
    void checksAnyOfMultipleSharedVertices() {
        boolean result = repository.areBothPointsWithinDistanceOfAnyVertex(
                List.of(1001L, 1002L),
                37.5000, 126.80098,
                37.50002, 126.8010,
                5.0
        );

        assertThat(result).isTrue();
    }
}

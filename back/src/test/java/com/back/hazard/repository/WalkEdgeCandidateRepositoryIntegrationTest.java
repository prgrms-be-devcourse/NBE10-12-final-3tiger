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
}

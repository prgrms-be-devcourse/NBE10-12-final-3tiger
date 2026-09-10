package com.back.walk.service;

import com.back.global.exception.BusinessException;
import com.back.walk.dto.WeeklyWalkResponse;
import com.back.walk.repository.WeeklyWalkQueryRepository;
import com.back.walk.repository.WeeklyWalkQueryRepository.DailyWalkAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WeeklyWalkServiceTest {

    @Mock
    private WeeklyWalkQueryRepository repository;

    @InjectMocks
    private WeeklyWalkService service;

    @Test
    void returnsSevenDaysIncludingDatesWithoutWalks() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        given(repository.findDailyAggregates(
                1L,
                LocalDateTime.of(2026, 9, 7, 0, 0),
                LocalDateTime.of(2026, 9, 14, 0, 0)
        )).willReturn(List.of(
                new DailyWalkAggregate(monday, 2, 3_000, 40),
                new DailyWalkAggregate(monday.plusDays(2), 1, 1_500, 20)
        ));

        WeeklyWalkResponse response = service.getWeeklyWalks(1L, monday);

        assertThat(response.weekStart()).isEqualTo(monday);
        assertThat(response.weekEnd()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(response.totalWalkCount()).isEqualTo(3);
        assertThat(response.totalDistanceMeters()).isEqualTo(4_500);
        assertThat(response.totalMinutes()).isEqualTo(60);
        assertThat(response.dailyRecords()).hasSize(7);
        assertThat(response.dailyRecords().get(1))
                .extracting("date", "walkCount", "distanceMeters", "minutes")
                .containsExactly(LocalDate.of(2026, 9, 8), 0L, 0L, 0L);
        assertThat(response.dailyRecords().get(2).walkCount()).isEqualTo(1);

        verify(repository).findDailyAggregates(
                1L,
                LocalDateTime.of(2026, 9, 7, 0, 0),
                LocalDateTime.of(2026, 9, 14, 0, 0)
        );
    }

    @Test
    void rejectsWeekStartThatIsNotMonday() {
        LocalDate tuesday = LocalDate.of(2026, 9, 8);

        assertThatThrownBy(() -> service.getWeeklyWalks(1L, tuesday))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(repository);
    }
}

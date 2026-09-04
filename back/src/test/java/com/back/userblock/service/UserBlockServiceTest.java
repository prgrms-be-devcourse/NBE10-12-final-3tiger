package com.back.userblock.service;

import com.back.global.error.ApiException;
import com.back.user.repository.UserRepository;
import com.back.userblock.domain.UserBlock;
import com.back.userblock.repository.UserBlockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock private UserBlockRepository userBlocks;
    @Mock private UserRepository users;

    @InjectMocks private UserBlockService service;

    @Test
    @DisplayName("t1: 차단 성공 시 user_block을 저장하고 blocked=true를 반환한다")
    void t1() {
        // given
        given(users.existsById(2L)).willReturn(true);
        given(userBlocks.existsByBlocker_IdAndBlocked_Id(1L, 2L)).willReturn(false);

        // when
        UserBlockService.BlockResult result = service.block(1L, 2L);

        // then
        assertThat(result.blockedUserId()).isEqualTo(2L);
        assertThat(result.blocked()).isTrue();
        verify(userBlocks).save(any(UserBlock.class));
    }

    @Test
    @DisplayName("t2: 자기 자신을 차단하면 400 ApiException이 발생한다")
    void t2() {
        // when
        ApiException exception = catchThrowableOfType(() -> service.block(1L, 1L), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userBlocks, never()).save(any());
    }

    @Test
    @DisplayName("t3: 이미 차단한 사용자를 다시 차단하면 저장 없이 blocked=true를 반환한다(멱등)")
    void t3() {
        // given
        given(users.existsById(2L)).willReturn(true);
        given(userBlocks.existsByBlocker_IdAndBlocked_Id(1L, 2L)).willReturn(true);

        // when
        UserBlockService.BlockResult result = service.block(1L, 2L);

        // then
        assertThat(result.blocked()).isTrue();
        verify(userBlocks, never()).save(any());
    }

    @Test
    @DisplayName("t4: 존재하지 않는 사용자를 차단하면 404 ApiException이 발생한다")
    void t4() {
        // given
        given(users.existsById(999L)).willReturn(false);

        // when
        ApiException exception = catchThrowableOfType(() -> service.block(1L, 999L), ApiException.class);

        // then
        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(userBlocks, never()).save(any());
    }

    @Test
    @DisplayName("t5: 차단 해제 시 user_block을 삭제하고 blocked=false를 반환한다(멱등)")
    void t5() {
        // when
        UserBlockService.BlockResult result = service.unblock(1L, 2L);

        // then
        assertThat(result.blocked()).isFalse();
        verify(userBlocks).deleteByBlocker_IdAndBlocked_Id(1L, 2L);
    }

    @Test
    @DisplayName("t6: relatedUserIds는 차단 관계 상대 id 목록을 집합으로 반환한다")
    void t6() {
        // given
        given(userBlocks.findRelatedUserIds(1L)).willReturn(List.of(2L, 3L, 2L));

        // when & then
        assertThat(service.relatedUserIds(1L)).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("t7: isBlocked는 어느 방향이든 차단 레코드가 있으면 true를 반환한다")
    void t7() {
        // given
        given(userBlocks.existsByBlocker_IdAndBlocked_Id(1L, 2L)).willReturn(false);
        given(userBlocks.existsByBlocker_IdAndBlocked_Id(2L, 1L)).willReturn(true);

        // when & then
        assertThat(service.isBlocked(1L, 2L)).isTrue();
    }
}

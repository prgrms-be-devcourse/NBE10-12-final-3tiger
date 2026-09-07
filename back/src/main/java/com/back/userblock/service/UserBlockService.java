package com.back.userblock.service;

import com.back.global.api.PageResponse;
import com.back.global.error.ApiException;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import com.back.userblock.domain.UserBlock;
import com.back.userblock.repository.UserBlockRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserBlockService {

    // 빈 in 절을 만들지 않기 위한 sentinel (user id 는 항상 양수)
    private static final long NO_SUCH_USER_ID = -1L;

    private final UserBlockRepository userBlocks;
    private final UserBlockWriter userBlockWriter;
    private final UserRepository users;

    public UserBlockService(UserBlockRepository userBlocks, UserBlockWriter userBlockWriter, UserRepository users) {
        this.userBlocks = userBlocks;
        this.userBlockWriter = userBlockWriter;
        this.users = users;
    }

    /** blockerId 가 blockedId 를 차단한다. 이미 차단한 상태면 에러 없이 현재 상태를 반환한다(멱등). */
    @Transactional
    public BlockResult block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다.");
        }
        if (!users.existsById(blockedId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다.");
        }

        if (!userBlocks.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)) {
            try {
                userBlockWriter.trySave(new UserBlock(users.getReferenceById(blockerId), users.getReferenceById(blockedId)));
            } catch (DataIntegrityViolationException e) {
                // uk_user_block_blocker_blocked 위반 = 동시 중복 차단 → 멱등 처리
            }
        }
        return new BlockResult(blockedId, true);
    }

    /** 차단을 해제한다. 차단한 적이 없어도 에러 없이 현재 상태를 반환한다(멱등). */
    @Transactional
    public BlockResult unblock(Long blockerId, Long blockedId) {
        userBlocks.deleteByBlocker_IdAndBlocked_Id(blockerId, blockedId);
        return new BlockResult(blockedId, false);
    }

    public PageResponse<BlockedUser> myBlocks(Long userId, int page, int size) {
        return PageResponse.from(userBlocks.findByBlocker_IdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toBlockedUser));
    }

    /** userId 와 상호 차단 관계에 있는 사용자 id 집합. 피드/댓글 목록에서 서로 숨기는 데 쓴다. */
    public Set<Long> relatedUserIds(Long userId) {
        return Set.copyOf(userBlocks.findRelatedUserIds(userId));
    }

    /** 두 사용자가 (방향 무관) 차단 관계인지 여부. */
    public boolean isBlocked(Long userIdA, Long userIdB) {
        return userBlocks.existsByBlocker_IdAndBlocked_Id(userIdA, userIdB)
                || userBlocks.existsByBlocker_IdAndBlocked_Id(userIdB, userIdA);
    }

    // 비로그인이면 차단 관계가 없다. 결과가 비면 sentinel 을 넣어 빈 in 절을 피한다.
    public Collection<Long> excludedUserIds(Long userId) {
        if (userId == null) {
            return List.of(NO_SUCH_USER_ID);
        }
        Set<Long> blocked = relatedUserIds(userId);
        if (blocked.isEmpty()) {
            return List.of(NO_SUCH_USER_ID);
        }
        return blocked;
    }

    private BlockedUser toBlockedUser(UserBlock block) {
        User blocked = block.getBlocked();
        return new BlockedUser(blocked.getId(), blocked.getNickname(), blocked.getProfileImageUrl(),
                block.getCreatedAt());
    }

    public record BlockResult(Long blockedUserId, boolean blocked) {}
    public record BlockedUser(Long userId, String nickname, String profileImageUrl, LocalDateTime blockedAt) {}
}

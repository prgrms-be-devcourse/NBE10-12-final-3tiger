package com.back.userblock.service;

import com.back.userblock.domain.UserBlock;
import com.back.userblock.repository.UserBlockRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserBlockWriter {
    private final UserBlockRepository userBlocks;
    public UserBlockWriter(UserBlockRepository userBlocks) {
        this.userBlocks = userBlocks;
    }

    // 별도(REQUIRES_NEW) 트랜잭션으로 격리: saveAndFlush가 유니크 제약 위반으로 실패해도
    // 호출한 쪽의 바깥 트랜잭션까지 rollback-only로 오염되지 않도록 함
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void trySave(UserBlock userBlock) {
        userBlocks.saveAndFlush(userBlock);
    }
}

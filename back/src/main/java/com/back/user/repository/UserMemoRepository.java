package com.back.user.repository;

import com.back.user.domain.UserMemo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMemoRepository extends JpaRepository<UserMemo, Long> {
    Optional<UserMemo> findByOwner_IdAndTarget_Id(Long ownerUserId, Long targetUserId);
    long deleteByOwner_IdAndTarget_Id(Long ownerUserId, Long targetUserId);
}

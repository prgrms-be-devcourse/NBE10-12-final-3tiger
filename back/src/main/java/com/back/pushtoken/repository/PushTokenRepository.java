package com.back.pushtoken.repository;

import com.back.pushtoken.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    /** upsert(등록) 및 삭제(해제) 기준. token 컬럼이 유니크하므로 최대 1건. */
    Optional<PushToken> findByToken(String token);

    long deleteByToken(String token);
}

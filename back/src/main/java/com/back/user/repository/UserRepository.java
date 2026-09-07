package com.back.user.repository;

import com.back.user.domain.Provider;
import com.back.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id and user.deletedAt is null")
    Optional<User> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

    Optional<User> findByProviderAndProviderUidAndDeletedAtIsNull(Provider provider, String providerUid);
}

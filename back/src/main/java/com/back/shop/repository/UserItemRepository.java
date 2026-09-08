package com.back.shop.repository;

import com.back.shop.domain.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    boolean existsByUser_IdAndShopItem_Id(Long userId, Long shopItemId);

    Optional<UserItem> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = "shopItem")
    List<UserItem> findByUser_Id(Long userId);

    @EntityGraph(attributePaths = "shopItem")
    List<UserItem> findByUser_IdOrderByPurchasedAtDesc(Long userId);

    @EntityGraph(attributePaths = "shopItem")
    List<UserItem> findByUser_IdAndEquippedTrue(Long userId);

    @EntityGraph(attributePaths = {"user", "shopItem"})
    List<UserItem> findByUser_IdInAndEquippedTrue(Collection<Long> userIds);
}

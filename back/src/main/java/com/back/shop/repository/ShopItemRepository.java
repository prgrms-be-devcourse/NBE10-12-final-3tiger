package com.back.shop.repository;

import com.back.shop.domain.ShopItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShopItemRepository extends JpaRepository<ShopItem, Long> {

    Optional<ShopItem> findByIdAndActiveTrue(Long id);

    List<ShopItem> findAllByActiveTrueOrderByTypeAscIdAsc();
}

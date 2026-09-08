package com.back.shop.dto;

import com.back.shop.domain.ShopItemType;

import java.time.LocalDateTime;

public record ShopPurchaseResponse(
        Long itemId,
        ShopItemType type,
        String code,
        long remainingPointBalance,
        LocalDateTime purchasedAt
) {
}

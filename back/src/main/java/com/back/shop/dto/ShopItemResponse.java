package com.back.shop.dto;

import com.back.shop.domain.ShopItemType;

public record ShopItemResponse(
        Long itemId,
        ShopItemType type,
        String code,
        String name,
        String description,
        long price,
        boolean owned,
        boolean equipped
) {
}

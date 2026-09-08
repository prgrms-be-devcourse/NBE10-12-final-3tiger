package com.back.shop.controller;

import com.back.global.api.ApiResponse;
import com.back.global.auth.CurrentUserId;
import com.back.shop.dto.MyItemResponse;
import com.back.shop.dto.ShopItemResponse;
import com.back.shop.dto.ShopPurchaseResponse;
import com.back.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Shop", description = "꾸미기 상점 API")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/shop/items")
    @Operation(summary = "상점 상품 목록 조회")
    ApiResponse<List<ShopItemResponse>> getShopItems(@CurrentUserId Long userId) {
        return ApiResponse.ok("상점 상품 조회 성공", shopService.getShopItems(userId));
    }

    @PostMapping("/shop/items/{itemId}/purchase")
    @Operation(summary = "꾸미기 아이템 구매")
    ApiResponse<ShopPurchaseResponse> purchase(
            @CurrentUserId Long userId,
            @PathVariable Long itemId
    ) {
        return ApiResponse.ok("꾸미기 아이템을 구매했습니다.", shopService.purchase(userId, itemId));
    }

    @GetMapping("/users/me/items")
    @Operation(summary = "내 보유 아이템 조회")
    ApiResponse<List<MyItemResponse>> getMyItems(@CurrentUserId Long userId) {
        return ApiResponse.ok("보유 아이템 조회 성공", shopService.getMyItems(userId));
    }

    @PatchMapping("/users/me/items/{userItemId}/equip")
    @Operation(summary = "꾸미기 아이템 장착")
    ApiResponse<MyItemResponse> equip(
            @CurrentUserId Long userId,
            @PathVariable Long userItemId
    ) {
        return ApiResponse.ok("꾸미기 아이템을 장착했습니다.", shopService.equip(userId, userItemId));
    }

    @PatchMapping("/users/me/items/{userItemId}/unequip")
    @Operation(summary = "꾸미기 아이템 장착 해제")
    ApiResponse<MyItemResponse> unequip(
            @CurrentUserId Long userId,
            @PathVariable Long userItemId
    ) {
        return ApiResponse.ok("꾸미기 아이템 장착을 해제했습니다.", shopService.unequip(userId, userItemId));
    }
}

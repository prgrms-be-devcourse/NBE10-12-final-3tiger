package com.back.shop.controller;

import com.back.global.auth.CurrentUserIdResolver;
import com.back.global.config.SecurityConfig;
import com.back.global.config.WebConfig;
import com.back.global.error.ApiException;
import com.back.global.exception.GlobalExceptionHandler;
import com.back.global.jwt.JwtProvider;
import com.back.place.kakao.ratelimit.PlaceSearchRateLimiter;
import com.back.shop.domain.ShopItemType;
import com.back.shop.dto.MyItemResponse;
import com.back.shop.dto.ShopItemResponse;
import com.back.shop.dto.ShopPurchaseResponse;
import com.back.shop.service.ShopService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.back.TestAuthentication.authenticatedAs;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShopController.class)
@Import({SecurityConfig.class, WebConfig.class, CurrentUserIdResolver.class, GlobalExceptionHandler.class})
class ShopControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ShopService shopService;
    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean PlaceSearchRateLimiter placeSearchRateLimiter;

    @Test
    void getsShopItems() throws Exception {
        given(shopService.getShopItems(1L)).willReturn(List.of(
                new ShopItemResponse(10L, ShopItemType.PROFILE_BORDER, "GOLD", "골드 프로필 테두리",
                        "골드색 프로필 테두리", 100L, true, false)
        ));

        mvc.perform(get("/api/v1/shop/items").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data[0].type").value("PROFILE_BORDER"))
                .andExpect(jsonPath("$.data[0].owned").value(true))
                .andExpect(jsonPath("$.data[0].equipped").value(false));
    }

    @Test
    void purchasesItem() throws Exception {
        LocalDateTime purchasedAt = LocalDateTime.of(2026, 9, 7, 21, 0);
        given(shopService.purchase(1L, 10L)).willReturn(
                new ShopPurchaseResponse(10L, ShopItemType.PROFILE_BORDER, "GOLD", 50L, purchasedAt));

        mvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 10L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("꾸미기 아이템을 구매했습니다."))
                .andExpect(jsonPath("$.data.remainingPointBalance").value(50L));

        verify(shopService).purchase(1L, 10L);
    }

    @Test
    void returnsPurchaseConflict() throws Exception {
        given(shopService.purchase(1L, 10L))
                .willThrow(new ApiException(HttpStatus.CONFLICT, "포인트가 부족합니다."));

        mvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 10L).with(authenticatedAs(1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("포인트가 부족합니다."));
    }

    @Test
    void returnsDuplicatePurchaseConflict() throws Exception {
        given(shopService.purchase(1L, 10L))
                .willThrow(new ApiException(HttpStatus.CONFLICT, "이미 보유한 상품입니다."));

        mvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 10L).with(authenticatedAs(1L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 보유한 상품입니다."));
    }

    @Test
    void getsMyItems() throws Exception {
        given(shopService.getMyItems(1L)).willReturn(List.of(
                new MyItemResponse(20L, 10L, ShopItemType.PROFILE_BADGE, "STAR", "별 뱃지", true,
                        LocalDateTime.of(2026, 9, 7, 21, 0))
        ));

        mvc.perform(get("/api/v1/users/me/items").with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userItemId").value(20L))
                .andExpect(jsonPath("$.data[0].equipped").value(true));
    }

    @Test
    void equipsAndUnequipsItem() throws Exception {
        MyItemResponse equipped = new MyItemResponse(20L, 10L, ShopItemType.PROFILE_BADGE,
                "STAR", "별 뱃지", true, LocalDateTime.of(2026, 9, 7, 21, 0));
        MyItemResponse unequipped = new MyItemResponse(20L, 10L, ShopItemType.PROFILE_BADGE,
                "STAR", "별 뱃지", false, LocalDateTime.of(2026, 9, 7, 21, 0));
        given(shopService.equip(1L, 20L)).willReturn(equipped);
        given(shopService.unequip(1L, 20L)).willReturn(unequipped);

        mvc.perform(patch("/api/v1/users/me/items/{userItemId}/equip", 20L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped").value(true));
        mvc.perform(patch("/api/v1/users/me/items/{userItemId}/unequip", 20L).with(authenticatedAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.equipped").value(false));
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/v1/shop/items")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/shop/items/10/purchase")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/items")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/me/items/20/equip")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/me/items/20/unequip")).andExpect(status().isUnauthorized());
        verifyNoInteractions(shopService);
    }
}

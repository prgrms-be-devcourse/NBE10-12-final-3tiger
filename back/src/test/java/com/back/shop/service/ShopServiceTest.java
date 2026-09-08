package com.back.shop.service;

import com.back.global.error.ApiException;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.shop.domain.ShopItem;
import com.back.shop.domain.ShopItemType;
import com.back.shop.domain.UserItem;
import com.back.shop.dto.MyItemResponse;
import com.back.shop.dto.ShopItemResponse;
import com.back.shop.dto.ShopPurchaseResponse;
import com.back.shop.repository.ShopItemRepository;
import com.back.shop.repository.UserItemRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock ShopItemRepository shopItemRepository;
    @Mock UserItemRepository userItemRepository;
    @Mock UserRepository userRepository;
    @Mock PointHistoryRepository pointHistoryRepository;
    @Mock EntityManager entityManager;

    private ShopService shopService;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(
                shopItemRepository,
                userItemRepository,
                userRepository,
                pointHistoryRepository,
                entityManager,
                Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneId.of("Asia/Seoul"))
        );
    }

    @Test
    @DisplayName("상점 목록은 로그인 사용자의 보유 및 장착 상태를 포함한다")
    void listsActiveItemsWithOwnership() {
        User user = user(1L, 200L);
        ShopItem gold = item(10L, ShopItemType.PROFILE_BORDER, "GOLD", 100L, true);
        ShopItem blue = item(11L, ShopItemType.PROFILE_BORDER, "BLUE", 100L, true);
        UserItem owned = ownedItem(20L, user, gold, true);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(shopItemRepository.findAllByActiveTrueOrderByTypeAscIdAsc()).willReturn(List.of(gold, blue));
        given(userItemRepository.findByUser_Id(1L)).willReturn(List.of(owned));

        List<ShopItemResponse> result = shopService.getShopItems(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).owned()).isTrue();
        assertThat(result.get(0).equipped()).isTrue();
        assertThat(result.get(1).owned()).isFalse();
    }

    @Test
    @DisplayName("상품 구매는 포인트를 차감하고 구매 이력과 보유 아이템을 저장한다")
    void purchasesItem() {
        User user = user(1L, 150L);
        ShopItem item = item(10L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        given(shopItemRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(item));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).willReturn(false);
        given(userItemRepository.save(any(UserItem.class))).willAnswer(invocation -> {
            UserItem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 20L);
            return saved;
        });

        ShopPurchaseResponse result = shopService.purchase(1L, 10L);

        assertThat(result.remainingPointBalance()).isEqualTo(100L);
        assertThat(result.code()).isEqualTo("STAR");
        assertThat(user.getPointBalance()).isEqualTo(100L);
        verify(entityManager).refresh(user);
        ArgumentCaptor<PointHistory> history = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).save(history.capture());
        assertThat(history.getValue().getAmount()).isEqualTo(-50L);
        assertThat(history.getValue().getType()).isEqualTo(PointType.SHOP_ITEM_PURCHASE);
        assertThat(history.getValue().getReferenceId()).isEqualTo(10L);
        verify(userItemRepository).save(any(UserItem.class));
    }

    @Test
    @DisplayName("포인트가 부족하면 구매 관련 데이터를 저장하지 않는다")
    void rejectsInsufficientPoints() {
        User user = user(1L, 40L);
        ShopItem item = item(10L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        given(shopItemRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(item));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).willReturn(false);

        assertApiException(() -> shopService.purchase(1L, 10L), HttpStatus.CONFLICT, "포인트가 부족합니다.");

        assertThat(user.getPointBalance()).isEqualTo(40L);
        verify(pointHistoryRepository, never()).save(any());
        verify(userItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 보유한 상품은 잠금 획득 후 중복 구매를 차단한다")
    void rejectsDuplicatePurchase() {
        User user = user(1L, 100L);
        ShopItem item = item(10L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        given(shopItemRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(item));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.existsByUser_IdAndShopItem_Id(1L, 10L)).willReturn(true);

        assertApiException(() -> shopService.purchase(1L, 10L), HttpStatus.CONFLICT, "이미 보유한 상품입니다.");

        verify(entityManager).refresh(user);
        assertThat(user.getPointBalance()).isEqualTo(100L);
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않거나 비활성 상품은 구매할 수 없다")
    void rejectsUnavailableItem() {
        given(shopItemRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.empty());

        assertApiException(() -> shopService.purchase(1L, 10L), HttpStatus.NOT_FOUND, "상점 상품을 찾을 수 없습니다.");
        verify(userRepository, never()).findByIdAndDeletedAtIsNullForUpdate(any());
    }

    @Test
    @DisplayName("탈퇴 사용자는 구매할 수 없다")
    void rejectsInactiveUser() {
        ShopItem item = item(10L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        given(shopItemRepository.findByIdAndActiveTrue(10L)).willReturn(Optional.of(item));
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.empty());

        assertApiException(() -> shopService.purchase(1L, 10L), HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다.");
    }

    @Test
    @DisplayName("내 보유 아이템을 구매 최신순으로 반환한다")
    void listsMyItems() {
        User user = user(1L, 100L);
        ShopItem item = item(10L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        UserItem owned = ownedItem(20L, user, item, false);
        given(userRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(user));
        given(userItemRepository.findByUser_IdOrderByPurchasedAtDesc(1L)).willReturn(List.of(owned));

        List<MyItemResponse> result = shopService.getMyItems(1L);

        assertThat(result).singleElement().satisfies(response -> {
            assertThat(response.userItemId()).isEqualTo(20L);
            assertThat(response.code()).isEqualTo("STAR");
            assertThat(response.equipped()).isFalse();
        });
    }

    @Test
    @DisplayName("아이템 장착 시 같은 타입의 기존 장착만 해제한다")
    void equipsOneItemPerType() {
        User user = user(1L, 100L);
        ShopItem gold = item(10L, ShopItemType.PROFILE_BORDER, "GOLD", 100L, true);
        ShopItem blue = item(11L, ShopItemType.PROFILE_BORDER, "BLUE", 100L, true);
        ShopItem badge = item(12L, ShopItemType.PROFILE_BADGE, "STAR", 50L, true);
        UserItem goldOwned = ownedItem(20L, user, gold, true);
        UserItem blueOwned = ownedItem(21L, user, blue, false);
        UserItem badgeOwned = ownedItem(22L, user, badge, true);
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.findByIdAndUser_Id(21L, 1L)).willReturn(Optional.of(blueOwned));
        given(userItemRepository.findByUser_IdAndEquippedTrue(1L)).willReturn(List.of(goldOwned, badgeOwned));

        MyItemResponse result = shopService.equip(1L, 21L);

        assertThat(result.equipped()).isTrue();
        assertThat(goldOwned.isEquipped()).isFalse();
        assertThat(blueOwned.isEquipped()).isTrue();
        assertThat(badgeOwned.isEquipped()).isTrue();
        verify(entityManager).refresh(user);
    }

    @Test
    @DisplayName("보유 아이템 장착을 해제한다")
    void unequipsItem() {
        User user = user(1L, 100L);
        UserItem owned = ownedItem(20L, user,
                item(10L, ShopItemType.POST_BORDER, "PINK", 100L, true), true);
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.findByIdAndUser_Id(20L, 1L)).willReturn(Optional.of(owned));

        MyItemResponse result = shopService.unequip(1L, 20L);

        assertThat(result.equipped()).isFalse();
        assertThat(owned.isEquipped()).isFalse();
    }

    @Test
    @DisplayName("다른 사용자의 아이템은 장착할 수 없다")
    void rejectsForeignItemEquip() {
        User user = user(1L, 100L);
        given(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).willReturn(Optional.of(user));
        given(userItemRepository.findByIdAndUser_Id(20L, 1L)).willReturn(Optional.empty());

        assertApiException(() -> shopService.equip(1L, 20L), HttpStatus.NOT_FOUND, "보유한 아이템을 찾을 수 없습니다.");
    }

    private User user(Long id, long points) {
        User user = User.createLocal("user" + id + "@test.com", "hash", "사용자");
        ReflectionTestUtils.setField(user, "id", id);
        if (points > 0) user.addPoints(points);
        return user;
    }

    private ShopItem item(Long id, ShopItemType type, String code, long price, boolean active) {
        ShopItem item = new ShopItem(type, code, code + " 상품", "설명", price, active);
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private UserItem ownedItem(Long id, User user, ShopItem item, boolean equipped) {
        UserItem userItem = new UserItem(user, item, Instant.parse("2026-09-07T12:00:00Z")
                .atZone(ZoneId.of("Asia/Seoul")).toLocalDateTime());
        ReflectionTestUtils.setField(userItem, "id", id);
        if (equipped) userItem.equip();
        return userItem;
    }

    private void assertApiException(Runnable action, HttpStatus status, String message) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(status);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }
}

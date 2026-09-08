package com.back.shop.repository;

import com.back.global.config.JpaConfig;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.shop.domain.ShopItem;
import com.back.shop.domain.ShopItemType;
import com.back.shop.domain.UserItem;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.datasource.url="
        + "jdbc:h2:mem:shop;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@ActiveProfiles("test")
class ShopRepositoryTest {

    @Autowired ShopItemRepository shopItemRepository;
    @Autowired UserItemRepository userItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired PointHistoryRepository pointHistoryRepository;

    @Test
    @DisplayName("상점 상품의 type과 code 조합은 중복될 수 없다")
    void enforcesUniqueTypeAndCode() {
        shopItemRepository.saveAndFlush(item(ShopItemType.PROFILE_BORDER, "GOLD", true));

        assertThatThrownBy(() -> shopItemRepository.saveAndFlush(
                item(ShopItemType.PROFILE_BORDER, "GOLD", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 사용자는 같은 상품을 두 번 보유할 수 없다")
    void enforcesUniqueUserItem() {
        User user = userRepository.save(User.createLocal("unique-shop@test.com", "hash", "사용자"));
        ShopItem item = shopItemRepository.save(item(ShopItemType.PROFILE_BADGE, "STAR", true));
        userItemRepository.saveAndFlush(new UserItem(user, item, LocalDateTime.now()));

        assertThatThrownBy(() -> userItemRepository.saveAndFlush(
                new UserItem(user, item, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("활성 상품과 내 보유 아이템을 조회한다")
    void findsActiveAndOwnedItems() {
        User user = userRepository.save(User.createLocal("owned-shop@test.com", "hash", "사용자"));
        ShopItem active = shopItemRepository.save(item(ShopItemType.PROFILE_BADGE, "STAR", true));
        shopItemRepository.save(item(ShopItemType.PROFILE_BADGE, "LEAF", false));
        userItemRepository.save(new UserItem(user, active, LocalDateTime.now()));

        assertThat(shopItemRepository.findAllByActiveTrueOrderByTypeAscIdAsc())
                .extracting(ShopItem::getCode)
                .containsExactly("STAR");
        assertThat(userItemRepository.findByUser_Id(user.getId()))
                .extracting(owned -> owned.getShopItem().getCode())
                .containsExactly("STAR");
    }

    @Test
    @DisplayName("상점 구매 포인트 이력은 음수 금액으로 저장한다")
    void savesNegativePurchaseHistory() {
        User user = userRepository.save(User.createLocal("history-shop@test.com", "hash", "사용자"));

        PointHistory saved = pointHistoryRepository.saveAndFlush(new PointHistory(
                user, -100L, PointType.SHOP_ITEM_PURCHASE, 10L, LocalDateTime.now()));

        assertThat(saved.getAmount()).isEqualTo(-100L);
        assertThat(saved.getType()).isEqualTo(PointType.SHOP_ITEM_PURCHASE);
    }

    private ShopItem item(ShopItemType type, String code, boolean active) {
        return new ShopItem(type, code, code + " 상품", "설명", 100L, active);
    }
}

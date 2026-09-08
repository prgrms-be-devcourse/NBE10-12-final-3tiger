package com.back.shop.service;

import com.back.global.error.ApiException;
import com.back.point.domain.PointHistory;
import com.back.point.domain.PointType;
import com.back.point.repository.PointHistoryRepository;
import com.back.point.service.PointRewardService;
import com.back.shop.domain.ShopItem;
import com.back.shop.domain.ShopItemType;
import com.back.shop.domain.UserItem;
import com.back.shop.repository.ShopItemRepository;
import com.back.shop.repository.UserItemRepository;
import com.back.user.domain.User;
import com.back.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@ActiveProfiles("test")
class ShopConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserRepository userRepository;
    @Autowired ShopItemRepository shopItemRepository;
    @Autowired UserItemRepository userItemRepository;
    @Autowired PointHistoryRepository pointHistoryRepository;
    @Autowired ShopService shopService;
    @Autowired PointRewardService pointRewardService;

    @Test
    @DisplayName("잔액 100P로 70P 상품 두 개를 동시에 구매해도 하나만 성공한다")
    void preventsConcurrentOverspending() throws Exception {
        TransactionTemplate tx = tx();
        Long userId = createUser(tx, 100L);
        Long firstItemId = createItem(tx, "OVER_A", 70L, ShopItemType.PROFILE_BORDER);
        Long secondItemId = createItem(tx, "OVER_B", 70L, ShopItemType.POST_BORDER);

        AtomicInteger successes = runConcurrently(
                () -> shopService.purchase(userId, firstItemId),
                () -> shopService.purchase(userId, secondItemId)
        );

        assertThat(successes.get()).isEqualTo(1);
        tx.executeWithoutResult(status -> {
            assertThat(userRepository.findById(userId).orElseThrow().getPointBalance()).isEqualTo(30L);
            assertThat(userItemRepository.findByUser_Id(userId)).hasSize(1);
            assertThat(purchaseHistories(userId)).hasSize(1);
        });
    }

    @Test
    @DisplayName("동일 상품을 동시에 구매해도 한 번만 차감하고 한 건만 보유한다")
    void preventsConcurrentDuplicatePurchase() throws Exception {
        TransactionTemplate tx = tx();
        Long userId = createUser(tx, 200L);
        Long itemId = createItem(tx, "DUPLICATE", 100L, ShopItemType.PROFILE_BADGE);

        AtomicInteger successes = runConcurrently(
                () -> shopService.purchase(userId, itemId),
                () -> shopService.purchase(userId, itemId)
        );

        assertThat(successes.get()).isEqualTo(1);
        tx.executeWithoutResult(status -> {
            assertThat(userRepository.findById(userId).orElseThrow().getPointBalance()).isEqualTo(100L);
            assertThat(userItemRepository.findByUser_Id(userId)).hasSize(1);
            assertThat(purchaseHistories(userId)).hasSize(1);
        });
    }

    @Test
    @DisplayName("같은 사용자에게 적립과 구매가 동시에 발생해도 두 변동이 모두 반영된다")
    void keepsConcurrentRewardAndPurchase() throws Exception {
        TransactionTemplate tx = tx();
        Long userId = createUser(tx, 100L);
        Long itemId = createItem(tx, "REWARD_RACE", 70L, ShopItemType.POST_BORDER);
        long reportReferenceId = Math.abs(UUID.randomUUID().getMostSignificantBits());

        AtomicInteger successes = runConcurrently(
                () -> shopService.purchase(userId, itemId),
                () -> pointRewardService.rewardHazardReport(userId, reportReferenceId)
        );

        assertThat(successes.get()).isEqualTo(2);
        tx.executeWithoutResult(status ->
                assertThat(userRepository.findById(userId).orElseThrow().getPointBalance()).isEqualTo(40L));
    }

    @Test
    @DisplayName("같은 타입 아이템 동시 장착 후에는 하나만 장착 상태다")
    void keepsOneEquippedItemPerType() throws Exception {
        TransactionTemplate tx = tx();
        Long userId = createUser(tx, 0L);
        Long firstItemId = createItem(tx, "EQUIP_A", 100L, ShopItemType.PROFILE_BORDER);
        Long secondItemId = createItem(tx, "EQUIP_B", 100L, ShopItemType.PROFILE_BORDER);
        List<Long> userItemIds = tx.execute(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            ShopItem first = shopItemRepository.findById(firstItemId).orElseThrow();
            ShopItem second = shopItemRepository.findById(secondItemId).orElseThrow();
            return List.of(
                    userItemRepository.save(new UserItem(user, first, LocalDateTime.now())).getId(),
                    userItemRepository.save(new UserItem(user, second, LocalDateTime.now())).getId()
            );
        });

        AtomicInteger successes = runConcurrently(
                () -> shopService.equip(userId, userItemIds.get(0)),
                () -> shopService.equip(userId, userItemIds.get(1))
        );

        assertThat(successes.get()).isEqualTo(2);
        tx.executeWithoutResult(status ->
                assertThat(userItemRepository.findByUser_IdAndEquippedTrue(userId)).hasSize(1));
    }

    @Test
    @DisplayName("구매 이력 저장이 실패하면 잔액 차감과 UserItem 저장도 롤백한다")
    void rollsBackWholePurchaseWhenHistoryFails() {
        TransactionTemplate tx = tx();
        Long userId = createUser(tx, 100L);
        Long itemId = createItem(tx, "ROLLBACK", 50L, ShopItemType.PROFILE_BADGE);
        tx.executeWithoutResult(status -> {
            User user = userRepository.findById(userId).orElseThrow();
            pointHistoryRepository.save(new PointHistory(
                    user, -50L, PointType.SHOP_ITEM_PURCHASE, itemId, LocalDateTime.now()));
        });

        boolean failed = false;
        try {
            shopService.purchase(userId, itemId);
        } catch (DataIntegrityViolationException exception) {
            failed = true;
        }

        assertThat(failed).isTrue();
        tx.executeWithoutResult(status -> {
            assertThat(userRepository.findById(userId).orElseThrow().getPointBalance()).isEqualTo(100L);
            assertThat(userItemRepository.findByUser_Id(userId)).isEmpty();
            assertThat(purchaseHistories(userId)).hasSize(1);
        });
    }

    private AtomicInteger runConcurrently(ThrowingAction first, ThrowingAction second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstFuture = executor.submit(() -> runAction(first, ready, start, successes));
            var secondFuture = executor.submit(() -> runAction(second, ready, start, successes));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            firstFuture.get(15, TimeUnit.SECONDS);
            secondFuture.get(15, TimeUnit.SECONDS);
        }
        return successes;
    }

    private void runAction(
            ThrowingAction action,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes
    ) {
        ready.countDown();
        await(start);
        try {
            action.run();
            successes.incrementAndGet();
        } catch (ApiException | DataIntegrityViolationException ignored) {
            // Expected losing request in overspend/duplicate scenarios.
        }
    }

    private Long createUser(TransactionTemplate tx, long points) {
        return tx.execute(status -> {
            User user = User.createLocal(unique("user") + "@test.com", "hash", "사용자");
            if (points > 0) user.addPoints(points);
            return userRepository.saveAndFlush(user).getId();
        });
    }

    private Long createItem(TransactionTemplate tx, String code, long price, ShopItemType type) {
        return tx.execute(status -> shopItemRepository.saveAndFlush(new ShopItem(
                type, unique(code), code + " 상품", "설명", price, true)).getId());
    }

    private List<PointHistory> purchaseHistories(Long userId) {
        return pointHistoryRepository.findAll().stream()
                .filter(history -> history.getUser().getId().equals(userId))
                .filter(history -> history.getType() == PointType.SHOP_ITEM_PURCHASE)
                .toList();
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run();
    }
}

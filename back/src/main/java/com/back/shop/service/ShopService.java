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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ShopService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ShopItemRepository shopItemRepository;
    private final UserItemRepository userItemRepository;
    private final UserRepository userRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @Autowired
    public ShopService(
            ShopItemRepository shopItemRepository,
            UserItemRepository userItemRepository,
            UserRepository userRepository,
            PointHistoryRepository pointHistoryRepository,
            EntityManager entityManager
    ) {
        this(shopItemRepository, userItemRepository, userRepository,
                pointHistoryRepository, entityManager, Clock.system(KST));
    }

    ShopService(
            ShopItemRepository shopItemRepository,
            UserItemRepository userItemRepository,
            UserRepository userRepository,
            PointHistoryRepository pointHistoryRepository,
            EntityManager entityManager,
            Clock clock
    ) {
        this.shopItemRepository = shopItemRepository;
        this.userItemRepository = userItemRepository;
        this.userRepository = userRepository;
        this.pointHistoryRepository = pointHistoryRepository;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    public List<ShopItemResponse> getShopItems(Long userId) {
        findActiveUser(userId);
        Map<Long, UserItem> ownedByItemId = userItemRepository.findByUser_Id(userId).stream()
                .collect(Collectors.toMap(item -> item.getShopItem().getId(), Function.identity()));

        return shopItemRepository.findAllByActiveTrueOrderByTypeAscIdAsc().stream()
                .map(item -> {
                    UserItem owned = ownedByItemId.get(item.getId());
                    return toShopItemResponse(item, owned);
                })
                .toList();
    }

    @Transactional
    public ShopPurchaseResponse purchase(Long userId, Long itemId) {
        ShopItem item = shopItemRepository.findByIdAndActiveTrue(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "상점 상품을 찾을 수 없습니다."));
        User user = findActiveUserForUpdate(userId);

        if (userItemRepository.existsByUser_IdAndShopItem_Id(userId, itemId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 보유한 상품입니다.");
        }
        if (user.getPointBalance() < item.getPrice()) {
            throw new ApiException(HttpStatus.CONFLICT, "포인트가 부족합니다.");
        }

        user.spendPoints(item.getPrice());
        LocalDateTime purchasedAt = LocalDateTime.now(clock);
        pointHistoryRepository.save(new PointHistory(
                user,
                -item.getPrice(),
                PointType.SHOP_ITEM_PURCHASE,
                item.getId(),
                purchasedAt
        ));
        UserItem userItem = userItemRepository.save(new UserItem(user, item, purchasedAt));

        return new ShopPurchaseResponse(
                item.getId(),
                item.getType(),
                item.getCode(),
                user.getPointBalance(),
                userItem.getPurchasedAt()
        );
    }

    public List<MyItemResponse> getMyItems(Long userId) {
        findActiveUser(userId);
        return userItemRepository.findByUser_IdOrderByPurchasedAtDesc(userId).stream()
                .map(this::toMyItemResponse)
                .toList();
    }

    @Transactional
    public MyItemResponse equip(Long userId, Long userItemId) {
        findActiveUserForUpdate(userId);
        UserItem target = findOwnedItem(userId, userItemId);
        ShopItemType targetType = target.getShopItem().getType();

        userItemRepository.findByUser_IdAndEquippedTrue(userId).stream()
                .filter(owned -> owned.getShopItem().getType() == targetType)
                .forEach(UserItem::unequip);
        target.equip();

        return toMyItemResponse(target);
    }

    @Transactional
    public MyItemResponse unequip(Long userId, Long userItemId) {
        findActiveUserForUpdate(userId);
        UserItem target = findOwnedItem(userId, userItemId);
        target.unequip();
        return toMyItemResponse(target);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
    }

    private User findActiveUserForUpdate(Long userId) {
        Optional<User> user = userRepository.findByIdAndDeletedAtIsNullForUpdate(userId);
        user.ifPresent(entityManager::refresh);
        return user.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "존재하지 않는 사용자입니다."));
    }

    private UserItem findOwnedItem(Long userId, Long userItemId) {
        return userItemRepository.findByIdAndUser_Id(userItemId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "보유한 아이템을 찾을 수 없습니다."));
    }

    private ShopItemResponse toShopItemResponse(ShopItem item, UserItem owned) {
        return new ShopItemResponse(
                item.getId(),
                item.getType(),
                item.getCode(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                owned != null,
                owned != null && owned.isEquipped()
        );
    }

    private MyItemResponse toMyItemResponse(UserItem userItem) {
        ShopItem item = userItem.getShopItem();
        return new MyItemResponse(
                userItem.getId(),
                item.getId(),
                item.getType(),
                item.getCode(),
                item.getName(),
                userItem.isEquipped(),
                userItem.getPurchasedAt()
        );
    }
}

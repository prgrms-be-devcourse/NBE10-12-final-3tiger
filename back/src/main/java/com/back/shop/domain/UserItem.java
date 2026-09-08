package com.back.shop.domain;

import com.back.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_item_user_shop_item",
                columnNames = {"user_id", "shop_item_id"}
        )
)
public class UserItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_item_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_item_id", nullable = false)
    private ShopItem shopItem;

    @Column(nullable = false)
    private boolean equipped;

    @Column(name = "purchased_at", nullable = false, updatable = false)
    private LocalDateTime purchasedAt;

    protected UserItem() {
    }

    public UserItem(User user, ShopItem shopItem, LocalDateTime purchasedAt) {
        this.user = user;
        this.shopItem = shopItem;
        this.purchasedAt = purchasedAt;
        this.equipped = false;
    }

    public void equip() {
        this.equipped = true;
    }

    public void unequip() {
        this.equipped = false;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ShopItem getShopItem() {
        return shopItem;
    }

    public boolean isEquipped() {
        return equipped;
    }

    public LocalDateTime getPurchasedAt() {
        return purchasedAt;
    }
}

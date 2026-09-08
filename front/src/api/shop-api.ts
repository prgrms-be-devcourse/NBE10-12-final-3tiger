import { apiRequest } from "./client";
import type { MyItem, ShopItem, ShopPurchase } from "@/types/domain";

export const getShopItems = () =>
  apiRequest<ShopItem[]>({ url: "/api/v1/shop/items" });

export const purchaseShopItem = (itemId: number) =>
  apiRequest<ShopPurchase>({
    url: `/api/v1/shop/items/${itemId}/purchase`,
    method: "POST",
  });

export const getMyItems = () =>
  apiRequest<MyItem[]>({ url: "/api/v1/users/me/items" });

export const equipMyItem = (userItemId: number) =>
  apiRequest<MyItem>({
    url: `/api/v1/users/me/items/${userItemId}/equip`,
    method: "PATCH",
  });

export const unequipMyItem = (userItemId: number) =>
  apiRequest<MyItem>({
    url: `/api/v1/users/me/items/${userItemId}/unequip`,
    method: "PATCH",
  });

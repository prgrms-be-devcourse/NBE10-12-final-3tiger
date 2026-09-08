import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { router } from "expo-router";
import { Alert, ScrollView, View } from "react-native";
import { useEffect } from "react";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  equipMyItem,
  getMyItems,
  getShopItems,
  purchaseShopItem,
  unequipMyItem,
} from "@/api/shop-api";
import { getMyProfile } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import {
  badgeAppearance,
  cosmeticColor,
  postBorderStyle,
  profileBorderStyle,
} from "@/components/shop/cosmetics";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { ShopItem, UserProfile } from "@/types/domain";

function ItemPreview({ item }: { item: ShopItem }) {
  const badge = badgeAppearance(item.code);
  if (item.type === "PROFILE_BADGE") {
    return (
      <View className="h-14 w-14 items-center justify-center rounded-full bg-[#F2F8F2] dark:bg-[#2A312C]">
        <Ionicons
          name={badge?.icon ?? "ribbon"}
          size={30}
          color={badge?.color ?? "#087A3F"}
        />
      </View>
    );
  }
  if (item.type === "POST_BORDER") {
    return (
      <View
        className="h-14 w-14 items-center justify-center rounded-xl bg-[#F8FAF8] dark:bg-[#242B26]"
        style={postBorderStyle(item.code)}
      >
        <Ionicons
          name="image-outline"
          size={24}
          color={cosmeticColor(item.code)}
        />
      </View>
    );
  }
  return (
    <View
      className="h-14 w-14 items-center justify-center rounded-full bg-[#F8FAF8] dark:bg-[#242B26]"
      style={profileBorderStyle(item.code)}
    >
      <Ionicons name="person" size={25} color={cosmeticColor(item.code)} />
    </View>
  );
}

export default function ShopScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const queryClient = useQueryClient();
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);
  const shopQuery = useQuery({
    queryKey: ["shop-items"],
    queryFn: getShopItems,
    enabled: isAuthenticated,
  });
  const myItemsQuery = useQuery({
    queryKey: ["my-items"],
    queryFn: getMyItems,
    enabled: isAuthenticated,
  });
  const profileQuery = useQuery<UserProfile>({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });

  const refreshCosmetics = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["shop-items"] }),
      queryClient.invalidateQueries({ queryKey: ["my-items"] }),
      queryClient.invalidateQueries({ queryKey: ["posts"] }),
    ]);
  };
  const purchaseMutation = useMutation({
    mutationFn: purchaseShopItem,
    onSuccess: async (result) => {
      queryClient.setQueryData<UserProfile>(["my-profile"], (profile) =>
        profile
          ? { ...profile, pointBalance: result.remainingPointBalance }
          : profile,
      );
      await refreshCosmetics();
      Alert.alert("구매 완료", "꾸미기 아이템을 구매했습니다.");
    },
    onError: (error: Error) => Alert.alert("구매할 수 없어요", error.message),
  });
  const equipMutation = useMutation({
    mutationFn: ({
      userItemId,
      equipped,
    }: {
      userItemId: number;
      equipped: boolean;
    }) => (equipped ? unequipMyItem(userItemId) : equipMyItem(userItemId)),
    onSuccess: refreshCosmetics,
    onError: (error: Error) => Alert.alert("적용할 수 없어요", error.message),
  });

  if (!isAuthenticated) {
    return null;
  }
  if (shopQuery.isPending || myItemsQuery.isPending) {
    return <LoadingState label="꾸미기 상품을 불러오는 중이에요" />;
  }
  if (shopQuery.isError || myItemsQuery.isError) {
    const error = shopQuery.error ?? myItemsQuery.error;
    return (
      <ErrorState
        message={error?.message}
        onRetry={() => {
          void shopQuery.refetch();
          void myItemsQuery.refetch();
        }}
      />
    );
  }

  const ownedByItemId = new Map(
    (myItemsQuery.data ?? []).map((item) => [item.itemId, item]),
  );
  const pending = purchaseMutation.isPending || equipMutation.isPending;

  return (
    <SafeAreaView
      className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-14 flex-row items-center border-b border-[#E5EBE5] bg-white px-3 dark:border-[#343D36] dark:bg-[#1B211D]">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          className="h-11 w-11"
          onPress={() => router.back()}
        >
          <Ionicons
            name="arrow-back"
            size={22}
            color={isDark ? "#F1F5F2" : "#223128"}
          />
        </Button>
        <Text className="ml-2 text-lg font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
          꾸미기 상점
        </Text>
        <View className="ml-auto flex-row items-center gap-1 rounded-full bg-[#E9FBEF] px-3 py-1.5 dark:bg-[#24382B]">
          <Ionicons name="leaf" size={15} color="#087A3F" />
          <Text className="text-sm font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
            {profileQuery.data?.pointBalance ?? 0} P
          </Text>
        </View>
      </View>
      <ScrollView contentContainerClassName="gap-3 p-4 pb-10">
        {(shopQuery.data ?? []).map((item) => {
          const owned = ownedByItemId.get(item.itemId);
          const equipped = owned?.equipped ?? item.equipped;
          return (
            <View
              key={item.itemId}
              className="flex-row items-center gap-3 rounded-xl bg-white p-4 dark:bg-[#1B211D]"
            >
              <ItemPreview item={item} />
              <View className="flex-1">
                <Text className="text-[15px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
                  {item.name}
                </Text>
                <Text className="mt-1 text-xs leading-[17px] text-slate-500 dark:text-[#AAB5AD]">
                  {item.description}
                </Text>
                <Text className="mt-2 text-sm font-black text-[#087A3F] dark:text-[#86EFAC]">
                  {item.price} P
                </Text>
              </View>
              {!owned ? (
                <Button
                  size="sm"
                  className="h-10 rounded-lg bg-[#087A3F] px-3"
                  disabled={pending}
                  onPress={() => purchaseMutation.mutate(item.itemId)}
                >
                  <Text className="text-xs font-extrabold text-white">
                    구매하기
                  </Text>
                </Button>
              ) : (
                <View className="items-end gap-1.5">
                  <Text className="text-[11px] font-bold text-slate-500 dark:text-[#AAB5AD]">
                    {equipped ? "장착중" : "보유중"}
                  </Text>
                  <Button
                    variant={equipped ? "secondary" : "default"}
                    size="sm"
                    className={`h-9 rounded-lg px-3 ${equipped ? "bg-[#E9FBEF] dark:bg-[#24382B]" : "bg-[#087A3F]"}`}
                    disabled={pending}
                    onPress={() =>
                      equipMutation.mutate({
                        userItemId: owned.userItemId,
                        equipped,
                      })
                    }
                  >
                    <Text
                      className={`text-xs font-extrabold ${equipped ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-white"}`}
                    >
                      {equipped ? "해제" : "장착하기"}
                    </Text>
                  </Button>
                </View>
              )}
            </View>
          );
        })}
      </ScrollView>
    </SafeAreaView>
  );
}

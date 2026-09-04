import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery } from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, FlatList, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getBlockedUsers } from "@/api/user-block-api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { BlockUserDialog } from "@/components/user/block-user-dialog";
import { BLOCKED_USERS_KEY } from "@/hooks/use-blocked-users";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { BlockedUser } from "@/types/domain";

function formatDate(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleDateString("ko-KR");
}

export default function BlockedUsersScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [target, setTarget] = useState<BlockedUser | null>(null);

  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);

  const blocksQuery = useInfiniteQuery({
    queryKey: BLOCKED_USERS_KEY,
    queryFn: ({ pageParam }) => getBlockedUsers({ page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: isAuthenticated,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const blocked = blocksQuery.data?.pages.flatMap((page) => page.content) ?? [];

  return (
    <SafeAreaView
      className="flex-1 bg-[#F6F9F6] dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-14 flex-row items-center border-b border-[#E5EBE5] bg-white px-3 dark:border-[#343D36] dark:bg-[#1B211D]">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          className="h-11 w-11 rounded-full"
          onPress={() => router.back()}
        >
          <Ionicons
            name="arrow-back"
            size={23}
            color={isDark ? "#F1F5F2" : "#191C1D"}
          />
        </Button>
        <Text className="flex-1 text-center text-lg font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
          차단한 사용자
        </Text>
        <View className="w-11" />
      </View>

      {blocksQuery.isPending ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : blocksQuery.isError ? (
        <ErrorState
          message={blocksQuery.error.message}
          onRetry={() => void blocksQuery.refetch()}
        />
      ) : (
        <FlatList
          data={blocked}
          keyExtractor={(item) => String(item.userId)}
          contentContainerClassName="grow pb-8"
          showsVerticalScrollIndicator={false}
          ItemSeparatorComponent={() => <Separator className="bg-[#E8EDE8]" />}
          ListEmptyComponent={
            <EmptyState
              title="차단한 사용자가 없어요"
              description="차단하면 서로의 게시물과 댓글이 숨겨져요."
            />
          }
          renderItem={({ item }) => (
            <View className="flex-row items-center gap-3 bg-white px-5 py-3.5 dark:bg-[#1B211D]">
              <Avatar alt={`${item.nickname} 프로필`} className="h-11 w-11">
                <AvatarImage
                  source={
                    item.profileImageUrl
                      ? { uri: item.profileImageUrl }
                      : DEFAULT_PROFILE_IMAGE
                  }
                />
                <AvatarFallback className="bg-[#E9F5EC]" />
              </Avatar>
              <View className="flex-1">
                <Text className="text-sm font-bold text-[#191C1D] dark:text-[#F1F5F2]">
                  {item.nickname}
                </Text>
                <Text className="mt-0.5 text-[11px] text-slate-500 dark:text-[#AAB5AD]">
                  {formatDate(item.blockedAt)} 차단
                </Text>
              </View>
              <Button
                variant="outline"
                size="sm"
                className="h-9 rounded-lg px-3"
                onPress={() => setTarget(item)}
              >
                <Text className="text-xs font-bold text-[#33443A] dark:text-[#D4DDD6]">
                  차단 해제
                </Text>
              </Button>
            </View>
          )}
          onEndReached={() => {
            if (blocksQuery.hasNextPage && !blocksQuery.isFetchingNextPage)
              void blocksQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            blocksQuery.isFetchingNextPage ? (
              <ActivityIndicator color="#087A3F" className="my-4" />
            ) : null
          }
        />
      )}

      {target && (
        <BlockUserDialog
          open
          userId={target.userId}
          nickname={target.nickname}
          blocked
          onClose={() => setTarget(null)}
        />
      )}
    </SafeAreaView>
  );
}

import { Ionicons } from "@expo/vector-icons";
import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect } from "react";
import { ActivityIndicator, FlatList, Pressable, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  getNotifications,
  readAllNotifications,
  readNotification,
} from "@/api/notification-api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { NotificationItem } from "@/types/notification";

const notificationCopy = {
  LIKE: {
    icon: "heart" as const,
    color: "#16A34A",
    background: "bg-[#E9FBEF]",
    message: "회원님의 게시글을 좋아합니다.",
  },
  COMMENT: {
    icon: "chatbubble" as const,
    color: "#2563EB",
    background: "bg-[#EAF2FF]",
    message: "회원님의 게시글에 댓글을 남겼습니다.",
  },
  COMMENT_UPVOTE: {
    icon: "heart-circle" as const,
    color: "#D97706",
    background: "bg-[#FFF4DE]",
    message: "회원님의 댓글에 공감했습니다.",
  },
};

function formatNotificationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const minutes = Math.max(
    0,
    Math.floor((Date.now() - date.getTime()) / 60_000),
  );
  if (minutes < 1) return "방금 전";
  if (minutes < 60) return minutes + "분 전";
  if (minutes < 1_440) return Math.floor(minutes / 60) + "시간 전";
  return date.toLocaleDateString("ko-KR");
}

export default function NotificationsScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);

  const notificationsQuery = useInfiniteQuery({
    queryKey: ["notifications"],
    queryFn: ({ pageParam }) => getNotifications({ page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: isAuthenticated,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const notifications =
    notificationsQuery.data?.pages.flatMap((page) => page.content) ?? [];

  const markReadMutation = useMutation({ mutationFn: readNotification });
  const readAllMutation = useMutation({
    mutationFn: readAllNotifications,
    onMutate: () => {
      queryClient.setQueriesData<{
        pages: Array<{ content: NotificationItem[] }>;
        pageParams: unknown[];
      }>({ queryKey: ["notifications"] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            content: page.content.map((item) => ({ ...item, read: true })),
          })),
        };
      });
      queryClient.setQueryData(["notification-unread-count"], { count: 0 });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["notifications"] });
      void queryClient.invalidateQueries({
        queryKey: ["notification-unread-count"],
      });
    },
  });

  const openNotification = async (item: NotificationItem) => {
    if (!item.read) {
      queryClient.setQueriesData<{
        pages: Array<{ content: NotificationItem[] }>;
        pageParams: unknown[];
      }>({ queryKey: ["notifications"] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            content: page.content.map((notification) =>
              notification.id === item.id
                ? { ...notification, read: true }
                : notification,
            ),
          })),
        };
      });
      queryClient.setQueryData<{ count: number }>(
        ["notification-unread-count"],
        (previous) => ({ count: Math.max(0, (previous?.count ?? 1) - 1) }),
      );
      try {
        await markReadMutation.mutateAsync(item.id);
      } catch {
        void queryClient.invalidateQueries({ queryKey: ["notifications"] });
        void queryClient.invalidateQueries({
          queryKey: ["notification-unread-count"],
        });
        return;
      }
    }

    router.replace({
      pathname: "/(tabs)/community",
      params: {
        notificationId: String(item.id),
        postId: String(item.postId),
        openComments: item.type === "LIKE" ? "0" : "1",
      },
    } as never);
  };

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
          알림
        </Text>
        <Button
          variant="ghost"
          size="sm"
          className="h-11 min-w-11 px-1"
          disabled={
            !notifications.some((item) => !item.read) ||
            readAllMutation.isPending
          }
          onPress={() => readAllMutation.mutate()}
        >
          <Text className="text-xs font-bold text-[#087A3F]">모두 읽음</Text>
        </Button>
      </View>

      {notificationsQuery.isPending ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : notificationsQuery.isError ? (
        <ErrorState
          message={notificationsQuery.error.message}
          onRetry={() => void notificationsQuery.refetch()}
        />
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => String(item.id)}
          contentContainerClassName="grow pb-8"
          showsVerticalScrollIndicator={false}
          ItemSeparatorComponent={() => <Separator className="bg-[#E8EDE8]" />}
          ListEmptyComponent={
            <EmptyState
              title="새로운 알림이 없어요"
              description="좋아요와 댓글 소식을 이곳에서 알려드릴게요."
            />
          }
          renderItem={({ item }) => {
            const copy = notificationCopy[item.type];
            return (
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={item.actorNickname + "님의 알림"}
                className={cn(
                  "min-h-[92px] flex-row items-center gap-3 px-5 py-4",
                  item.read
                    ? "bg-white dark:bg-[#1B211D]"
                    : "bg-[#F0FAF3] dark:bg-[#203026]",
                )}
                onPress={() => void openNotification(item)}
              >
                <View>
                  <Avatar
                    alt={item.actorNickname + " 프로필"}
                    className="h-12 w-12 border border-[#E0E8E1]"
                  >
                    <AvatarImage
                      source={
                        item.actorProfileImageUrl
                          ? { uri: item.actorProfileImageUrl }
                          : DEFAULT_PROFILE_IMAGE
                      }
                    />
                    <AvatarFallback className="bg-[#E9F5EC]" />
                  </Avatar>
                  <View
                    className={cn(
                      "absolute -bottom-1 -right-1 h-6 w-6 items-center justify-center rounded-full border-2 border-white dark:border-[#1B211D]",
                      copy.background,
                    )}
                  >
                    <Ionicons name={copy.icon} size={13} color={copy.color} />
                  </View>
                </View>
                <View className="flex-1">
                  <Text className="text-sm leading-5 text-[#28352C] dark:text-[#D4DDD6]">
                    <Text className="font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
                      {item.actorNickname}님이{" "}
                    </Text>
                    {copy.message}
                  </Text>
                  <Text className="mt-1 text-[11px] text-[#738078] dark:text-[#AAB5AD]">
                    {formatNotificationTime(item.createdAt)}
                  </Text>
                </View>
                {!item.read && (
                  <View
                    accessibilityLabel="읽지 않음"
                    className="h-2.5 w-2.5 rounded-full bg-[#22C55E]"
                  />
                )}
              </Pressable>
            );
          }}
          onEndReached={() => {
            if (
              notificationsQuery.hasNextPage &&
              !notificationsQuery.isFetchingNextPage
            )
              void notificationsQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            notificationsQuery.isFetchingNextPage ? (
              <ActivityIndicator color="#087A3F" className="my-4" />
            ) : null
          }
        />
      )}
    </SafeAreaView>
  );
}

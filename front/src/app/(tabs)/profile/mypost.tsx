import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseDetail } from "@/api/course-api";
import { getMyPosts } from "@/api/post-api";
import { getMyProfile } from "@/api/user-api";
import { Button } from "@/components/ui/button";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { Post } from "@/types/domain";

function PostDetailSheet({
  post,
  onClose,
}: {
  post: Post | null;
  onClose: () => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(translateY, windowHeight, onClose);
  useEffect(() => {
    if (!post) return;
    translateY.setValue(windowHeight);
    Animated.timing(translateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [post, translateY, windowHeight]);
  const courseQuery = useQuery({
    queryKey: ["course", post?.courseId],
    queryFn: () => getCourseDetail(post!.courseId),
    enabled: post !== null,
  });
  return (
    <Modal
      visible={!!post}
      transparent
      animationType="none"
      onRequestClose={dismissSheet}
    >
      <View className="flex-1 justify-end">
        <Pressable
          className="absolute inset-0 bg-black/40"
          onPress={dismissSheet}
        />
        <Animated.View
          className="h-[76%] rounded-t-[30px] bg-white pt-2.5 dark:bg-[#1B211D]"
          style={{ transform: [{ translateY }] }}
        >
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={translateY}
            dismissDistance={windowHeight}
          />
          {post && (
            <ScrollView
              showsVerticalScrollIndicator
              contentContainerClassName="px-5 pb-8"
            >
              {post.photoUrl ? (
                <Image
                  source={{ uri: post.photoUrl }}
                  className="h-64 w-full rounded-[21px] bg-slate-200"
                  resizeMode="cover"
                />
              ) : (
                <View className="h-52 items-center justify-center rounded-[21px] bg-muted">
                  <Ionicons name="image-outline" size={36} color="#94A09A" />
                </View>
              )}
              <View className="mt-4 flex-row items-start">
                <View className="flex-1 pr-3">
                  <Text className="text-[11px] font-extrabold text-[#087A3F]">
                    나의 산책 기록
                  </Text>
                  <Text className="mt-1 text-[22px] font-black text-[#17251B] dark:text-[#F1F5F2]">
                    {courseQuery.data?.name ?? "산책 기록"}
                  </Text>
                </View>
              </View>
              <View className="mt-4 flex-row rounded-2xl bg-[#F2F8F2] py-3 dark:bg-[#242B26]">
                <View className="flex-1 items-center border-r border-[#DDE7DE]">
                  <Ionicons name="calendar-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047] dark:text-[#AAB5AD]">
                    {new Date(post.walkedAt).toLocaleDateString("ko-KR")}
                  </Text>
                </View>
                <View className="flex-1 items-center">
                  <Ionicons name="time-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047] dark:text-[#AAB5AD]">
                    {courseQuery.data?.estimatedMinutes ?? "-"}분
                  </Text>
                </View>
              </View>
              <Text className="mt-5 text-[15px] leading-6 text-[#2D3931] dark:text-[#D4DDD6]">
                {post.content}
              </Text>
              <Separator className="my-5 bg-[#E6EBE7]" />
              <View className="flex-row items-center gap-6">
                <View className="flex-row items-center gap-1.5">
                  <Ionicons name="heart" size={22} color="#22C55E" />
                  <Text className="text-sm font-bold text-[#405047] dark:text-[#AAB5AD]">
                    {post.likeCount}
                  </Text>
                </View>
                <View className="flex-row items-center gap-1.5">
                  <Ionicons
                    name="chatbubble-outline"
                    size={21}
                    color="#526056"
                  />
                  <Text className="text-sm font-bold text-[#405047] dark:text-[#AAB5AD]">
                    {post.commentCount ?? 0}
                  </Text>
                </View>
              </View>
            </ScrollView>
          )}
        </Animated.View>
      </View>
    </Modal>
  );
}

export default function MyPostScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [selectedPostId, setSelectedPostId] = useState<number | null>(null);
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const postsQuery = useInfiniteQuery({
    queryKey: ["my-posts"],
    queryFn: ({ pageParam }) => getMyPosts({ page: pageParam, size: 30 }),
    initialPageParam: 0,
    enabled: isAuthenticated,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const posts = postsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const selected =
    selectedPostId === null
      ? null
      : (posts.find((post) => post.postId === selectedPostId) ?? null);
  return (
    <SafeAreaView className="flex-1 bg-white dark:bg-[#111411]" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-5 dark:border-[#343D36] dark:bg-[#1B211D]">
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
        <Text className="text-lg text-[#006E2F] dark:text-[#F1F5F2]">
          나의 게시글
        </Text>
        <View className="w-11" />
      </View>
      <View className="h-[100px] flex-row items-center gap-[15px] px-5">
        <Image
          source={
            profileQuery.data?.profileImageUrl
              ? { uri: profileQuery.data.profileImageUrl }
              : DEFAULT_PROFILE_IMAGE
          }
          className="h-16 w-16 rounded-full border-2 border-[#22C55E]"
        />
        <View>
          <Text className="text-xl font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
            나의 산책 기록
          </Text>
          <Text className="mt-1 text-sm text-slate-600 dark:text-[#AAB5AD]">
            총 {postsQuery.data?.pages[0]?.totalElements ?? 0}개의 기록
          </Text>
        </View>
      </View>
      {postsQuery.isPending ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : postsQuery.isError ? (
        <ErrorState
          message={postsQuery.error.message}
          onRetry={() => void postsQuery.refetch()}
        />
      ) : (
        <FlatList
          data={posts}
          numColumns={3}
          keyExtractor={(item) => String(item.postId)}
          columnWrapperClassName="gap-[3px]"
          contentContainerClassName="grow gap-[3px] px-[3px] pb-6"
          ListEmptyComponent={<EmptyState title="작성한 게시글이 없어요" />}
          renderItem={({ item }) => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="게시글 상세 보기"
              className="aspect-square flex-1 overflow-hidden bg-slate-200"
              onPress={() => setSelectedPostId(item.postId)}
            >
              {item.photoUrl ? (
                <Image
                  source={{ uri: item.photoUrl }}
                  className="h-full w-full"
                  resizeMode="cover"
                />
              ) : (
                <View className="flex-1 items-center justify-center">
                  <Ionicons name="image-outline" size={28} color="#94A09A" />
                </View>
              )}
            </Pressable>
          )}
          onEndReached={() => {
            if (postsQuery.hasNextPage && !postsQuery.isFetchingNextPage)
              void postsQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            postsQuery.isFetchingNextPage ? (
              <ActivityIndicator color="#087A3F" className="my-4" />
            ) : null
          }
        />
      )}
      <PostDetailSheet
        post={selected}
        onClose={() => setSelectedPostId(null)}
      />
    </SafeAreaView>
  );
}

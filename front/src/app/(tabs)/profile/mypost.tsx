import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseDetail } from "@/api/course-api";
import { getMyPosts } from "@/api/post-api";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";
import type { Post } from "@/types/domain";

function PostDetailSheet({
  post,
  onClose,
}: {
  post: Post | null;
  onClose: () => void;
}) {
  const courseQuery = useQuery({
    queryKey: ["course", post?.courseId],
    queryFn: () => getCourseDetail(post!.courseId),
    enabled: post !== null,
  });
  return (
    <Modal
      visible={!!post}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <Pressable className="flex-1 justify-end bg-black/40" onPress={onClose}>
        <Pressable
          className="max-h-[88%] rounded-t-[30px] bg-white pt-2.5"
          onPress={(event) => event.stopPropagation()}
        >
          <View className="mb-3 h-[5px] w-[42px] self-center rounded-full bg-slate-300" />
          {post && (
            <ScrollView
              showsVerticalScrollIndicator={false}
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
                  <Text className="mt-1 text-[22px] font-black text-[#17251B]">
                    {courseQuery.data?.name ?? "산책 기록"}
                  </Text>
                </View>
                <Button
                  variant="secondary"
                  size="icon"
                  accessibilityLabel="상세 닫기"
                  className="h-11 w-11 rounded-full"
                  onPress={onClose}
                >
                  <Ionicons name="close" size={22} color="#526056" />
                </Button>
              </View>
              <View className="mt-4 flex-row rounded-2xl bg-[#F2F8F2] py-3">
                <View className="flex-1 items-center border-r border-[#DDE7DE]">
                  <Ionicons name="calendar-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047]">
                    {new Date(post.walkedAt).toLocaleDateString("ko-KR")}
                  </Text>
                </View>
                <View className="flex-1 items-center">
                  <Ionicons name="time-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047]">
                    {courseQuery.data?.estimatedMinutes ?? "-"}분
                  </Text>
                </View>
              </View>
              <Text className="mt-5 text-[15px] leading-6 text-[#2D3931]">
                {post.content}
              </Text>
              <Separator className="my-5 bg-[#E6EBE7]" />
              <View className="flex-row items-center gap-6">
                <View className="flex-row items-center gap-1.5">
                  <Ionicons name="heart" size={22} color="#22C55E" />
                  <Text className="text-sm font-bold text-[#405047]">
                    {post.likeCount}
                  </Text>
                </View>
                <View className="flex-row items-center gap-1.5">
                  <Ionicons
                    name="chatbubble-outline"
                    size={21}
                    color="#526056"
                  />
                  <Text className="text-sm font-bold text-[#405047]">
                    {post.commentCount ?? 0}
                  </Text>
                </View>
              </View>
            </ScrollView>
          )}
        </Pressable>
      </Pressable>
    </Modal>
  );
}

export default function MyPostScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [selected, setSelected] = useState<Post | null>(null);
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);
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
  return (
    <SafeAreaView className="flex-1 bg-white" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-5">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          className="h-11 w-11"
          onPress={() => router.back()}
        >
          <Ionicons name="arrow-back" size={22} color="#223128" />
        </Button>
        <Text className="text-2xl font-black text-[#006E2F]">나의 게시글</Text>
        <View className="w-11" />
      </View>
      <View className="h-[100px] flex-row items-center gap-[15px] px-5">
        <View className="h-16 w-16 items-center justify-center rounded-full border-2 border-[#22C55E] bg-[#E8F7EC]">
          <Ionicons name="person" size={30} color="#006E2F" />
        </View>
        <View>
          <Text className="text-xl font-extrabold text-[#191C1D]">
            나의 산책 기록
          </Text>
          <Text className="mt-1 text-sm text-slate-600">
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
              onPress={() => setSelected(item)}
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
      <PostDetailSheet post={selected} onClose={() => setSelected(null)} />
    </SafeAreaView>
  );
}

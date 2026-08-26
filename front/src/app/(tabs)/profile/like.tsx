import { Ionicons } from "@expo/vector-icons";
import {
  useInfiniteQuery,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, FlatList, Image, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getMyLikedPosts, likePost, unlikePost } from "@/api/post-api";
import { CourseCommentSheet } from "@/components/comments/course-comment-sheet";
import { PostActions } from "@/components/feed/post-actions";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";
import type { Post } from "@/types/domain";

function LikedPostCard({
  item,
  onComments,
}: {
  item: Post;
  onComments: () => void;
}) {
  const queryClient = useQueryClient();
  const [liked, setLiked] = useState(true);
  const [likeCount, setLikeCount] = useState(item.likeCount);
  const mutation = useMutation({
    mutationFn: () => (liked ? unlikePost(item.postId) : likePost(item.postId)),
    onMutate: () => {
      setLiked((value) => !value);
      setLikeCount((count) => count + (liked ? -1 : 1));
    },
    onError: () => {
      setLiked((value) => !value);
      setLikeCount((count) => count + (liked ? 1 : -1));
    },
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
  });
  return (
    <View className="bg-white">
      <View className="min-h-[60px] flex-row items-center gap-3 px-5 py-2">
        <Avatar
          alt={`${item.nickname ?? "사용자"} 프로필`}
          className="h-10 w-10"
        >
          <AvatarFallback className="bg-secondary">
            <Text className="font-black text-primary">
              {(item.nickname ?? "산").slice(0, 1)}
            </Text>
          </AvatarFallback>
        </Avatar>
        <View className="flex-1">
          <Text className="text-sm font-black text-slate-900">
            {item.nickname ?? "산책러"}
          </Text>
          <Text className="mt-0.5 text-xs text-[#3D4A3D]">
            {new Date(item.likedAt ?? item.walkedAt).toLocaleDateString(
              "ko-KR",
            )}
          </Text>
        </View>
        <Ionicons name="ellipsis-vertical" size={21} color="#768179" />
      </View>
      {item.photoUrl ? (
        <Image
          source={{ uri: item.photoUrl }}
          className="h-80 w-full bg-slate-200"
          resizeMode="cover"
        />
      ) : (
        <View className="h-52 items-center justify-center bg-muted">
          <Ionicons name="image-outline" size={34} color="#94A09A" />
        </View>
      )}
      <PostActions
        liked={liked}
        likeCount={likeCount}
        commentCount={item.commentCount ?? 0}
        onToggleLike={() => {
          if (!mutation.isPending) mutation.mutate();
        }}
        onOpenComments={onComments}
      />
      <View className="px-5 pb-5">
        <Text className="text-[15px] leading-[22px] text-slate-900">
          <Text className="font-black">{item.nickname ?? "산책러"} </Text>
          {item.caption}
        </Text>
      </View>
    </View>
  );
}

export default function LikedPostsScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [commentCourseId, setCommentCourseId] = useState<number | null>(null);
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);
  const likedQuery = useInfiniteQuery({
    queryKey: ["liked-posts"],
    queryFn: ({ pageParam }) => getMyLikedPosts({ page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: isAuthenticated,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const posts = likedQuery.data?.pages.flatMap((page) => page.content) ?? [];
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
        <Text className="text-2xl font-black text-[#006E2F]">좋아요한 글</Text>
        <View className="w-11" />
      </View>
      {likedQuery.isPending ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : likedQuery.isError ? (
        <ErrorState
          message={likedQuery.error.message}
          onRetry={() => void likedQuery.refetch()}
        />
      ) : (
        <FlatList
          data={posts}
          keyExtractor={(item) => String(item.postId)}
          renderItem={({ item }) => (
            <LikedPostCard
              item={item}
              onComments={() => setCommentCourseId(item.courseId)}
            />
          )}
          contentContainerClassName="grow pb-6"
          ListEmptyComponent={<EmptyState title="좋아요한 글이 없어요" />}
          onEndReached={() => {
            if (likedQuery.hasNextPage && !likedQuery.isFetchingNextPage)
              void likedQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            likedQuery.isFetchingNextPage ? (
              <ActivityIndicator color="#087A3F" className="my-4" />
            ) : null
          }
        />
      )}
      <CourseCommentSheet
        courseId={commentCourseId}
        onClose={() => setCommentCourseId(null)}
      />
    </SafeAreaView>
  );
}

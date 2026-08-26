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

import { bookmarkCourse, unbookmarkCourse } from "@/api/course-api";
import { getMyLikedPosts, likePost, unlikePost } from "@/api/post-api";
import { PostCommentSheet } from "@/components/comments/post-comment-sheet";
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
  const [liked, setLiked] = useState(item.isLiked ?? true);
  const [likeCount, setLikeCount] = useState(item.likeCount);
  const [bookmarked, setBookmarked] = useState(item.isBookmarked ?? false);
  const [expanded, setExpanded] = useState(false);
  const mutation = useMutation({
    mutationFn: ({ desiredLiked }: { desiredLiked: boolean }) =>
      desiredLiked ? likePost(item.postId) : unlikePost(item.postId),
    onMutate: ({ desiredLiked }: { desiredLiked: boolean }) => {
      const previous = { liked, likeCount };
      setLiked(desiredLiked);
      setLikeCount((count) => count + (desiredLiked ? 1 : -1));
      return previous;
    },
    onError: (_error, _variables, context) => {
      if (!context) return;
      setLiked(context.liked);
      setLikeCount(context.likeCount);
    },
    onSuccess: (result) => {
      setLiked(result.isLiked);
      setLikeCount(result.likeCount);
      if (!result.isLiked) {
        queryClient.setQueriesData<{
          pages: Array<{ content: Post[] }>;
          pageParams: unknown[];
        }>({ queryKey: ["liked-posts"] }, (data) => {
          if (!data) return data;
          return {
            ...data,
            pages: data.pages.map((page) => ({
              ...page,
              content: page.content.filter(
                (post) => post.postId !== item.postId,
              ),
            })),
          };
        });
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({
        queryKey: ["liked-posts"],
        refetchType: "all",
      });
      void queryClient.invalidateQueries({
        queryKey: ["posts"],
        refetchType: "all",
      });
    },
  });
  const bookmarkMutation = useMutation({
    mutationFn: ({ desiredBookmarked }: { desiredBookmarked: boolean }) =>
      desiredBookmarked
        ? bookmarkCourse(item.courseId)
        : unbookmarkCourse(item.courseId),
    onMutate: ({ desiredBookmarked }: { desiredBookmarked: boolean }) => {
      const previous = bookmarked;
      setBookmarked(desiredBookmarked);
      return previous;
    },
    onError: (_error, _variables, previous) => {
      if (previous !== undefined) setBookmarked(previous);
    },
    onSuccess: (result) => setBookmarked(result.isBookmarked),
    onSettled: () =>
      void queryClient.invalidateQueries({
        queryKey: ["bookmarks"],
        refetchType: "all",
      }),
  });
  return (
    <View className="bg-white">
      <View className="min-h-[52px] flex-row items-center gap-2 px-3 py-2">
        <Avatar
          alt={`${item.nickname ?? "사용자"} 프로필`}
          className="h-8 w-8 border border-[#E4EAE5]"
        >
          <AvatarFallback className="bg-[#E9F5EC]">
            <Text className="text-xs font-bold text-[#087A3F]">
              {(item.nickname ?? "산").slice(0, 1)}
            </Text>
          </AvatarFallback>
        </Avatar>
        <View className="flex-1">
          <Text className="text-[13px] font-semibold leading-4 text-[#191C1D]">
            {item.nickname ?? "산책러"}
          </Text>
          <Text className="text-[10px] text-[#6B756D]">
            {new Date(item.likedAt ?? item.walkedAt).toLocaleDateString(
              "ko-KR",
            )}
          </Text>
        </View>
        <Ionicons name="ellipsis-vertical" size={18} color="#526056" />
      </View>
      {item.photoUrl ? (
        <Image
          source={{ uri: item.photoUrl }}
          className="h-64 w-full bg-slate-200"
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
          if (!mutation.isPending)
            mutation.mutate({ desiredLiked: !liked });
        }}
        onOpenComments={onComments}
        bookmarked={bookmarked}
        onToggleBookmark={() => {
          if (!bookmarkMutation.isPending)
            bookmarkMutation.mutate({ desiredBookmarked: !bookmarked });
        }}
      />
      <View className="px-3 pb-4">
        <View className="flex-row items-end">
          <Text className="flex-1 text-[13px] leading-5 text-[#252A26]" numberOfLines={expanded ? undefined : 1}>
            <Text className="text-[13px] font-bold leading-5 text-[#191C1D]">{item.nickname ?? "산책러"} </Text>
            {item.content}
          </Text>
          {!expanded && (
            <Button variant="link" size="sm" className="ml-1 h-5 px-0" onPress={() => setExpanded(true)}>
              <Text className="text-[11px] text-slate-500">더 보기</Text>
            </Button>
          )}
        </View>
        <Text className="mt-1 text-[10px] text-[#758078]">
          댓글 {item.commentCount ?? 0}개 모두 보기
        </Text>
      </View>
    </View>
  );
}

export default function LikedPostsScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [commentPostId, setCommentPostId] = useState<number | null>(null);
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
        <Text className="text-lg text-[#006E2F]">좋아요한 글</Text>
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
              onComments={() => setCommentPostId(item.postId)}
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
      <PostCommentSheet
        postId={commentPostId}
        onClose={() => setCommentPostId(null)}
      />
    </SafeAreaView>
  );
}

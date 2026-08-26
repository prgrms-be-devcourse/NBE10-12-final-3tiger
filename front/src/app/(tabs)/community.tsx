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
import { getPosts, likePost, unlikePost } from "@/api/post-api";
import { CourseCommentSheet } from "@/components/comments/course-comment-sheet";
import { PostActions } from "@/components/feed/post-actions";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import type { Post as PostType } from "@/types/domain";

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const minutes = Math.max(
    0,
    Math.floor((Date.now() - date.getTime()) / 60_000),
  );
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1_440) return `${Math.floor(minutes / 60)}시간 전`;
  return date.toLocaleDateString("ko-KR");
}

function IconButton({
  label,
  icon,
  onPress,
}: {
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
  onPress?: () => void;
}) {
  return (
    <Button
      variant="ghost"
      size="icon"
      accessibilityLabel={label}
      className="h-11 w-11 rounded-full active:bg-[#E9F5EC]"
      onPress={onPress}
    >
      <Ionicons name={icon} size={23} color="#087A3F" />
    </Button>
  );
}

function FeedPost({
  item,
  onOpenComments,
}: {
  item: PostType;
  onOpenComments: () => void;
}) {
  const queryClient = useQueryClient();
  const [liked, setLiked] = useState(item.isLiked ?? false);
  const [likeCount, setLikeCount] = useState(item.likeCount);
  const [bookmarked, setBookmarked] = useState(item.isBookmarked ?? false);
  const [expanded, setExpanded] = useState(false);
  useEffect(() => {
    setLiked(item.isLiked ?? false);
    setLikeCount(item.likeCount);
    setBookmarked(item.isBookmarked ?? false);
  }, [item]);

  const likeMutation = useMutation({
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
      void queryClient.invalidateQueries({ queryKey: ["posts"] }),
  });
  const bookmarkMutation = useMutation({
    mutationFn: () =>
      bookmarked
        ? unbookmarkCourse(item.courseId)
        : bookmarkCourse(item.courseId),
    onMutate: () => setBookmarked((value) => !value),
    onError: () => setBookmarked((value) => !value),
    onSettled: () =>
      void queryClient.invalidateQueries({ queryKey: ["bookmarks"] }),
  });

  return (
    <View className="w-full bg-white">
      <View className="min-h-[52px] flex-row items-center gap-2 px-3 py-2">
        <Avatar
          alt={`${item.nickname ?? "사용자"} 프로필`}
          className="h-8 w-8 border border-[#E4EAE5]"
        >
          {item.profileImageUrl && (
            <AvatarImage source={{ uri: item.profileImageUrl }} />
          )}
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
            {formatTime(item.walkedAt)}
          </Text>
        </View>
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="게시글 메뉴"
          className="h-8 w-8 rounded-full"
        >
          <Ionicons name="ellipsis-vertical" size={18} color="#087A3F" />
        </Button>
      </View>
      {item.photoUrl ? (
        <Image
          source={{ uri: item.photoUrl }}
          className="h-64 w-full bg-[#E6E8E9]"
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
          if (!likeMutation.isPending) likeMutation.mutate();
        }}
        onOpenComments={onOpenComments}
        bookmarked={bookmarked}
        onToggleBookmark={() => {
          if (!bookmarkMutation.isPending) bookmarkMutation.mutate();
        }}
      />
      <View className="flex-row items-end px-3 pb-4">
        <Text
          className="flex-1 text-[13px] leading-5 text-[#252A26]"
          numberOfLines={expanded ? undefined : 1}
        >
          <Text className="text-[13px] font-bold leading-5 text-[#191C1D]">
            {item.nickname ?? "산책러"}{" "}
          </Text>
          {item.caption}
        </Text>
        {!expanded && (
          <Button
            variant="link"
            size="sm"
            className="ml-1 h-5 px-0"
            onPress={() => setExpanded(true)}
          >
            <Text className="text-[11px] text-slate-500">더 보기</Text>
          </Button>
        )}
      </View>
    </View>
  );
}

export default function CommunityScreen() {
  const [commentCourseId, setCommentCourseId] = useState<number | null>(null);
  const postsQuery = useInfiniteQuery({
    queryKey: ["posts", "latest"],
    queryFn: ({ pageParam }) =>
      getPosts({ page: pageParam, size: 20, sort: "latest" }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const posts = postsQuery.data?.pages.flatMap((page) => page.content) ?? [];

  return (
    <SafeAreaView className="flex-1 bg-white" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-3">
        <View
          pointerEvents="none"
          className="absolute inset-x-0 h-14 items-center justify-center"
        >
          <Image
            source={require("../../../assets/title.png")}
            className="h-[35px] w-[132px]"
            resizeMode="contain"
          />
        </View>
        <IconButton
          label="게시글 작성"
          icon="add"
          onPress={() => router.push("/review/write" as never)}
        />
        <View className="ml-auto flex-row">
          <IconButton label="피드 검색" icon="search" />
          <IconButton label="알림" icon="notifications-outline" />
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
          keyExtractor={(item) => String(item.postId)}
          renderItem={({ item }) => (
            <FeedPost
              item={item}
              onOpenComments={() => setCommentCourseId(item.courseId)}
            />
          )}
          showsVerticalScrollIndicator={false}
          contentContainerClassName="grow pb-6"
          ListEmptyComponent={<EmptyState title="아직 공유된 산책이 없어요" />}
          onEndReached={() => {
            if (postsQuery.hasNextPage && !postsQuery.isFetchingNextPage)
              void postsQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.6}
          ListFooterComponent={
            postsQuery.isFetchingNextPage ? (
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

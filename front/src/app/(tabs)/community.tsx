import { Ionicons } from "@expo/vector-icons";
import { BlurView } from "expo-blur";
import * as Haptics from "expo-haptics";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  FlatList,
  Image,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { bookmarkCourse, unbookmarkCourse } from "@/api/course-api";
import { getUnreadNotificationCount } from "@/api/notification-api";
import { getPosts, likePost, unlikePost } from "@/api/post-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { PostCommentSheet } from "@/components/comments/post-comment-sheet";
import { PostActions } from "@/components/feed/post-actions";
import { PostMenuSheet } from "@/components/feed/post-menu-sheet";
import { PersonalUserMemoSheet } from "@/components/user/personal-user-memo-sheet";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { PostFeedItem } from "@/types/domain";

const HEADER_BAR_HEIGHT = 56;
const SORT_BAR_HEIGHT = 44;

const POST_SORTS: Array<{ key: "latest" | "popularity"; label: string }> = [
  { key: "latest", label: "최신순" },
  { key: "popularity", label: "좋아요순" },
];

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
  canDelete,
  onOpenComments,
  onRequireLogin,
}: {
  item: PostFeedItem;
  canDelete: boolean;
  onOpenComments: () => void;
  onRequireLogin: () => void;
}) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const queryClient = useQueryClient();
  const [liked, setLiked] = useState(item.isLiked);
  const [likeCount, setLikeCount] = useState(item.likeCount);
  const [bookmarked, setBookmarked] = useState(item.isBookmarked);
  const [expanded, setExpanded] = useState(false);
  const [contentLineCount, setContentLineCount] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const [memoOpen, setMemoOpen] = useState(false);
  useEffect(() => {
    setLiked(item.isLiked);
    setLikeCount(item.likeCount);
    setBookmarked(item.isBookmarked);
  }, [item.isBookmarked, item.isLiked, item.likeCount]);
  useEffect(() => {
    setExpanded(false);
    setContentLineCount(0);
  }, [item.postId]);
  const updateMyPostLike = (isLiked: boolean, nextLikeCount: number) => {
    queryClient.setQueriesData<{
      pages: Array<{ content: PostFeedItem[] }>;
      pageParams: unknown[];
    }>({ queryKey: ["my-posts"] }, (data) => {
      if (!data) return data;
      return {
        ...data,
        pages: data.pages.map((page) => ({
          ...page,
          content: page.content.map((post) =>
            post.postId === item.postId
              ? { ...post, isLiked, likeCount: nextLikeCount }
              : post,
          ),
        })),
      };
    });
  };

  const likeMutation = useMutation({
    mutationFn: ({ desiredLiked }: { desiredLiked: boolean }) =>
      desiredLiked ? likePost(item.postId) : unlikePost(item.postId),
    onMutate: ({ desiredLiked }: { desiredLiked: boolean }) => {
      const previous = { liked, likeCount };
      const nextLikeCount = Math.max(0, likeCount + (desiredLiked ? 1 : -1));
      setLiked(desiredLiked);
      setLikeCount(nextLikeCount);
      updateMyPostLike(desiredLiked, nextLikeCount);
      return previous;
    },
    onError: (_error, _variables, context) => {
      if (!context) return;
      setLiked(context.liked);
      setLikeCount(context.likeCount);
      updateMyPostLike(context.liked, context.likeCount);
    },
    onSuccess: (result) => {
      setLiked(result.isLiked);
      setLikeCount(result.likeCount);
      updateMyPostLike(result.isLiked, result.likeCount);
      if (!result.isLiked) {
        queryClient.setQueriesData<{
          pages: Array<{ content: PostFeedItem[] }>;
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
      void queryClient.invalidateQueries({ queryKey: ["posts"] });
      void queryClient.invalidateQueries({ queryKey: ["my-posts"] });
      void queryClient.invalidateQueries({
        queryKey: ["liked-posts"],
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
    onSuccess: (result) => {
      setBookmarked(result.isBookmarked);
      queryClient.setQueriesData<{
        pages: Array<{ content: PostFeedItem[] }>;
        pageParams: unknown[];
      }>({ queryKey: ["posts"] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            content: page.content.map((post) =>
              post.courseId === item.courseId
                ? { ...post, isBookmarked: result.isBookmarked }
                : post,
            ),
          })),
        };
      });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["bookmarks"] });
      void queryClient.invalidateQueries({
        queryKey: ["liked-posts"],
        refetchType: "all",
      });
    },
  });

  return (
    <View className="w-full bg-white dark:bg-[#1B211D]">
      <View className="min-h-[52px] flex-row items-center gap-2 px-3 py-2">
        <Avatar
          alt={`${item.nickname ?? "사용자"} 프로필`}
          className="h-8 w-8 border border-[#E4EAE5]"
        >
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
          <Text className="text-[13px] font-semibold leading-4 text-[#191C1D] dark:text-[#F1F5F2]">
            {item.nickname ?? "산책러"}
          </Text>
          <Text className="text-[10px] text-[#6B756D] dark:text-[#AAB5AD]">
            {formatTime(item.walkedAt)}
          </Text>
        </View>
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="게시글 메뉴"
          className="h-8 w-8 rounded-full"
          onPress={() => setMenuOpen(true)}
        >
          <Ionicons name="ellipsis-vertical" size={18} color="#087A3F" />
        </Button>
        {!item.isMine ? (
          <Button
            variant="ghost"
            size="icon"
            accessibilityLabel={`${item.nickname ?? "사용자"}님 개인 메모`}
            className="h-8 w-8 rounded-full"
            onPress={() => {
              if (!isAuthenticated) {
                onRequireLogin();
                return;
              }
              setMemoOpen(true);
            }}
          >
            <Ionicons name="pricetag-outline" size={17} color="#087A3F" />
          </Button>
        ) : null}
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
          if (!isAuthenticated) {
            onRequireLogin();
            return;
          }
          if (!likeMutation.isPending) {
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
            likeMutation.mutate({ desiredLiked: !liked });
          }
        }}
        onOpenComments={onOpenComments}
        bookmarked={bookmarked}
        onToggleBookmark={() => {
          if (!isAuthenticated) {
            onRequireLogin();
            return;
          }
          if (!bookmarkMutation.isPending) {
            void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
            bookmarkMutation.mutate({ desiredBookmarked: !bookmarked });
          }
        }}
      />
      <View className="relative px-3 pb-4">
        <Text className="mb-1 text-sm font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
          {item.title}
        </Text>
        <Text
          accessible={false}
          importantForAccessibility="no-hide-descendants"
          className="absolute left-3 right-3 top-0 opacity-0 text-[13px] leading-5"
          onTextLayout={(event) =>
            setContentLineCount(event.nativeEvent.lines.length)
          }
        >
          <Text className="text-[13px] font-bold leading-5">
            {item.nickname ?? "산책러"}{" "}
          </Text>
          {item.content}
        </Text>
        <View className="flex-row items-end">
          <Text
            className="flex-1 text-[13px] leading-5 text-[#252A26] dark:text-[#D4DDD6]"
            numberOfLines={expanded ? undefined : 2}
          >
            <Text className="text-[13px] font-bold leading-5 text-[#191C1D] dark:text-[#F1F5F2]">
              {item.nickname ?? "산책러"}{" "}
            </Text>
            {item.content}
          </Text>
          {!expanded && contentLineCount > 2 && (
            <Button
              variant="link"
              size="sm"
              className="ml-1 h-5 px-0"
              onPress={() => setExpanded(true)}
            >
              <Text className="text-[11px] text-slate-500 dark:text-[#AAB5AD]">
                더 보기
              </Text>
            </Button>
          )}
        </View>
        <Text className="mt-1 text-[10px] text-[#758078] dark:text-[#AAB5AD]">
          댓글 {item.commentCount ?? 0}개 모두 보기
        </Text>
      </View>

      <PostMenuSheet
        postId={item.postId}
        open={menuOpen}
        canDelete={canDelete}
        onClose={() => setMenuOpen(false)}
      />
      {!item.isMine ? (
        <PersonalUserMemoSheet
          open={memoOpen}
          targetUserId={item.userId}
          nickname={item.nickname ?? "산책러"}
          onClose={() => setMemoOpen(false)}
        />
      ) : null}
    </View>
  );
}

export default function CommunityScreen() {
  const { notificationId, postId, openComments } = useLocalSearchParams<{
    notificationId?: string;
    postId?: string;
    openComments?: string;
  }>();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const insets = useSafeAreaInsets();
  const headerBarHeight = insets.top + HEADER_BAR_HEIGHT;
  const headerHeight = headerBarHeight + SORT_BAR_HEIGHT;
  const listRef = useRef<FlatList<PostFeedItem>>(null);
  const handledNotificationId = useRef<string | null>(null);
  const headerTranslateY = useRef(new Animated.Value(0)).current;
  const headerVisibleRef = useRef(true);
  const lastScrollOffsetRef = useRef(0);
  const [commentPostId, setCommentPostId] = useState<number | null>(null);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [sort, setSort] = useState<"latest" | "popularity">("latest");
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchInput, setSearchInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const submitSearch = () => setKeyword(searchInput.trim());
  const closeSearch = () => {
    setSearchInput("");
    setKeyword("");
    setSearchOpen(false);
  };
  const postsQuery = useInfiniteQuery({
    queryKey: ["posts", sort, keyword],
    queryFn: ({ pageParam }) =>
      getPosts({
        page: pageParam,
        size: 20,
        sort,
        keyword: keyword || undefined,
      }),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const posts = useMemo(
    () => postsQuery.data?.pages.flatMap((page) => page.content) ?? [],
    [postsQuery.data?.pages],
  );
  const unreadQuery = useQuery({
    queryKey: ["notification-unread-count"],
    queryFn: getUnreadNotificationCount,
    enabled: isAuthenticated,
    staleTime: 15_000,
  });
  const refreshPosts = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    const startedAt = Date.now();
    try {
      await postsQuery.refetch();
    } finally {
      const remaining = Math.max(0, 1_000 - (Date.now() - startedAt));
      if (remaining > 0)
        await new Promise((resolve) => setTimeout(resolve, remaining));
      setIsRefreshing(false);
    }
  };
  const setHeaderVisible = (visible: boolean) => {
    if (headerVisibleRef.current === visible) return;
    headerVisibleRef.current = visible;
    Animated.timing(headerTranslateY, {
      toValue: visible ? 0 : -headerHeight,
      duration: visible ? 210 : 180,
      useNativeDriver: true,
    }).start();
  };
  const handleFeedScroll = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const offset = event.nativeEvent.contentOffset.y;
    const delta = offset - lastScrollOffsetRef.current;
    const velocity = event.nativeEvent.velocity?.y ?? 0;

    if (offset <= 2) setHeaderVisible(true);
    else if (delta > 5 && velocity >= -0.05) setHeaderVisible(false);
    else if (delta < -7 || velocity < -0.35) setHeaderVisible(true);

    lastScrollOffsetRef.current = Math.max(0, offset);
  };

  useEffect(() => {
    if (
      !notificationId ||
      handledNotificationId.current === notificationId ||
      !postId ||
      posts.length === 0
    )
      return;
    handledNotificationId.current = notificationId;
    const targetPostId = Number(postId);
    const index = posts.findIndex((item) => item.postId === targetPostId);
    if (index >= 0) {
      requestAnimationFrame(() => {
        listRef.current?.scrollToIndex({ index, animated: true });
      });
    }
    if (openComments === "1") setCommentPostId(targetPostId);
  }, [notificationId, openComments, postId, posts]);

  return (
    <View className="flex-1 bg-white dark:bg-[#111411]">
      <Animated.View
        pointerEvents="box-none"
        className="absolute inset-x-0 top-0 z-20"
        style={{
          height: headerHeight,
          transform: [{ translateY: headerTranslateY }],
        }}
      >
        <BlurView
          pointerEvents="none"
          intensity={42}
          tint={isDark ? "systemThinMaterialDark" : "systemThinMaterialLight"}
          experimentalBlurMethod="dimezisBlurView"
          blurReductionFactor={3}
          style={StyleSheet.absoluteFillObject}
        />
        <View
          className="flex-row items-center justify-between px-3"
          style={{ height: headerBarHeight, paddingTop: insets.top }}
        >
          {searchOpen ? (
            <TextInput
              autoFocus
              value={searchInput}
              onChangeText={setSearchInput}
              onSubmitEditing={submitSearch}
              returnKeyType="search"
              placeholder="코스 이름으로 검색"
              placeholderTextColor={isDark ? "#7F8B82" : "#7A857D"}
              className="absolute left-14 right-36 h-9 rounded-full bg-white px-4 text-sm text-[#191C1D] dark:bg-[#1B211D] dark:text-[#F1F5F2]"
              style={{ top: insets.top + 10 }}
            />
          ) : (
            <View
              pointerEvents="none"
              className="absolute inset-x-0 h-14 items-center justify-center"
              style={{ top: insets.top }}
            >
              <Image
                source={require("../../../assets/title-transparent.png")}
                className="h-[35px] w-[132px] dark:hidden"
                resizeMode="contain"
              />
              <Image
                source={require("../../../assets/title-transparent.png")}
                className="hidden h-[35px] w-[132px] dark:flex"
                resizeMode="contain"
              />
            </View>
          )}
          <IconButton
            label="게시글 작성"
            icon="add"
            onPress={() => router.push("/review/write" as never)}
          />
          <View className="ml-auto flex-row">
            <IconButton
              label={searchOpen ? "검색 실행" : "피드 검색"}
              icon="search"
              onPress={() => {
                if (searchOpen) submitSearch();
                else setSearchOpen(true);
              }}
            />
            {searchOpen && (
              <IconButton label="검색 닫기" icon="close" onPress={closeSearch} />
            )}
            <View>
              <IconButton
                label="알림"
                icon="notifications-outline"
                onPress={() => {
                  if (!isAuthenticated) {
                    setLoginRequiredOpen(true);
                    return;
                  }
                  router.push("/notifications" as never);
                }}
              />
              {(unreadQuery.data?.count ?? 0) > 0 && (
                <View className="absolute right-0.5 top-0.5 min-w-[18px] items-center justify-center rounded-full bg-[#EF4444] px-1 py-0.5">
                  <Text className="text-[9px] font-black leading-3 text-white">
                    {(unreadQuery.data?.count ?? 0) > 99
                      ? "99+"
                      : unreadQuery.data?.count}
                  </Text>
                </View>
              )}
            </View>
          </View>
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          className="flex-none"
          style={{ height: SORT_BAR_HEIGHT }}
          contentContainerClassName="items-center gap-2 px-3"
        >
          {POST_SORTS.map((option) => {
            const active = sort === option.key;
            return (
              <Pressable
                key={option.key}
                className={`h-9 justify-center rounded-full px-3 ${active ? "bg-[#087A3F]" : "bg-white dark:bg-[#1B211D]"}`}
                onPress={() => setSort(option.key)}
              >
                <Text
                  className={`text-xs font-extrabold ${active ? "text-white" : "text-[#536158] dark:text-[#AAB5AD]"}`}
                >
                  {option.label}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      </Animated.View>
      {postsQuery.isPending ? (
        <View className="flex-1 items-center justify-center gap-3 bg-white px-6 py-12 dark:bg-[#111411]">
          <ActivityIndicator color="#087A3F" />
          <Text className="text-sm text-slate-500 dark:text-[#AAB5AD]">
            피드를 불러오는 중이에요
          </Text>
        </View>
      ) : postsQuery.isError ? (
        <ErrorState
          message={postsQuery.error.message}
          onRetry={() => void postsQuery.refetch()}
          appearance="light"
          className="bg-white dark:bg-[#111411]"
        />
      ) : (
        <View className="flex-1">
          <FlatList
            ref={listRef}
            data={posts}
            keyExtractor={(item) => String(item.postId)}
            renderItem={({ item }) => (
              <FeedPost
                item={item}
                canDelete={item.isMine}
                onOpenComments={() => setCommentPostId(item.postId)}
                onRequireLogin={() => setLoginRequiredOpen(true)}
              />
            )}
            showsVerticalScrollIndicator={false}
            refreshControl={
              <RefreshControl
                refreshing={isRefreshing}
                onRefresh={() => void refreshPosts()}
                tintColor="transparent"
                colors={["transparent"]}
              />
            }
            onScroll={handleFeedScroll}
            scrollEventThrottle={16}
            ListHeaderComponent={<View style={{ height: headerHeight }} />}
            contentContainerClassName="grow pb-6"
            ListEmptyComponent={<EmptyState title="아직 공유된 산책이 없어요" />}
            onEndReached={() => {
              if (postsQuery.hasNextPage && !postsQuery.isFetchingNextPage)
                void postsQuery.fetchNextPage();
            }}
            onEndReachedThreshold={0.6}
            onScrollToIndexFailed={({ index }) => {
              listRef.current?.scrollToOffset({
                offset: Math.max(0, index * 380),
                animated: true,
              });
            }}
            ListFooterComponent={
              postsQuery.isFetchingNextPage ? (
                <ActivityIndicator color="#087A3F" className="my-4" />
              ) : null
            }
          />
          {isRefreshing && (
            <View
              pointerEvents="none"
              className="absolute inset-x-0 items-center"
              style={{ top: headerHeight + 10 }}
            >
              <ActivityIndicator color={isDark ? "#AAB5AD" : "#087A3F"} />
            </View>
          )}
        </View>
      )}
      <PostCommentSheet
        postId={commentPostId}
        onClose={() => setCommentPostId(null)}
      />
      <LoginRequiredModal
        visible={loginRequiredOpen}
        onClose={() => setLoginRequiredOpen(false)}
      />
    </View>
  );
}

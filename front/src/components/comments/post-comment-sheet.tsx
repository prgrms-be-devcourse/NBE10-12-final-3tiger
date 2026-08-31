import { Ionicons } from "@expo/vector-icons";
import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  Easing,
  TextInput,
  View,
  useWindowDimensions,
} from "react-native";
import { useRef } from "react";

import {
  addPostComment,
  getPostComments,
  toggleCommentUpvote,
} from "@/api/post-api";
import { getMyProfile } from "@/api/user-api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { LIKED_COLOR } from "@/components/feed/post-actions";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { PostComment } from "@/types/domain";

function CommentRow({ item }: { item: PostComment }) {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const [upvoted, setUpvoted] = useState(item.upvoted ?? false);
  const [upvoteCount, setUpvoteCount] = useState(item.upvoteCount);
  const [profileImageReady, setProfileImageReady] = useState(false);
  useEffect(() => {
    setUpvoted(item.upvoted ?? false);
    setUpvoteCount(item.upvoteCount);
  }, [item.upvoted, item.upvoteCount]);
  useEffect(() => {
    setProfileImageReady(false);
  }, [item.profileImageUrl]);
  const scale = useRef(new Animated.Value(1)).current;
  const burst = useRef(new Animated.Value(0)).current;
  const mutation = useMutation({
    mutationFn: () => toggleCommentUpvote(item.commentId),
    onSuccess: (result) => {
      setUpvoted(result.upvoted);
      setUpvoteCount(result.upvoteCount);
      // FlatList는 화면 밖의 행을 언마운트할 수 있으므로, 행 내부 state만
      // 바꾸지 않고 댓글 목록 캐시에도 결과를 기록해 스크롤 후 복원한다.
      queryClient.setQueriesData<{
        pages: Array<{ content: PostComment[] }>;
        pageParams: unknown[];
      }>({ queryKey: ["post-comments"] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            content: page.content.map((comment) =>
              comment.commentId === item.commentId
                ? {
                    ...comment,
                    upvoted: result.upvoted,
                    upvoteCount: result.upvoteCount,
                  }
                : comment,
            ),
          })),
        };
      });
    },
  });
  const handleUpvote = () => {
    if (!isAuthenticated || mutation.isPending) return;
    scale.stopAnimation();
    burst.stopAnimation();
    scale.setValue(1);
    burst.setValue(0);
    Animated.parallel([
      Animated.sequence([
        Animated.timing(scale, {
          toValue: 0.78,
          duration: 80,
          useNativeDriver: true,
        }),
        Animated.spring(scale, {
          toValue: upvoted ? 1 : 1.3,
          speed: 30,
          bounciness: 9,
          useNativeDriver: true,
        }),
        Animated.spring(scale, {
          toValue: 1,
          speed: 24,
          bounciness: 4,
          useNativeDriver: true,
        }),
      ]),
      ...(!upvoted
        ? [
            Animated.timing(burst, {
              toValue: 1,
              duration: 360,
              easing: Easing.out(Easing.cubic),
              useNativeDriver: true,
            }),
          ]
        : []),
    ]).start();
    mutation.mutate();
  };

  return (
    <View
      className={`w-full flex-row items-start gap-3 px-5 ${profileImageReady ? "opacity-100" : "opacity-0"}`}
    >
      <Avatar alt={`${item.nickname} 프로필`} className="h-10 w-10">
        <AvatarImage
          source={
            item.profileImageUrl
              ? { uri: item.profileImageUrl }
              : DEFAULT_PROFILE_IMAGE
          }
          onLoad={() => setProfileImageReady(true)}
          onError={() => setProfileImageReady(true)}
        />
        <AvatarFallback className="bg-[#E9F5EC]" />
      </Avatar>
      <View className="flex-1 pt-0.5">
        <View className="flex-row items-center gap-1.5">
          <Text className="text-xs font-bold text-[#191C1D] dark:text-[#F1F5F2]">
            {item.nickname}
          </Text>
          <Text className="text-[10px] text-[#6B756D] dark:text-[#AAB5AD]">
            {new Date(item.createdAt).toLocaleDateString("ko-KR")}
          </Text>
        </View>
        <Text className="mt-0.5 text-xs leading-[18px] text-[#34443A] dark:text-[#D4DDD6]">
          {item.content}
        </Text>
      </View>
      <Button
        variant="ghost"
        accessibilityLabel={upvoted ? "댓글 공감 취소" : "댓글 공감"}
        className="-mr-2 h-12 w-10 flex-col gap-0 rounded-full px-0 py-1"
        disabled={!isAuthenticated || mutation.isPending}
        onPress={handleUpvote}
      >
        <View className="h-[22px] w-[22px] items-center justify-center">
          <Animated.View
            pointerEvents="none"
            className="absolute h-[22px] w-[22px] rounded-full border border-[#22C55E]"
            style={{
              opacity: burst.interpolate({
                inputRange: [0, 0.15, 1],
                outputRange: [0, 0.45, 0],
              }),
              transform: [
                {
                  scale: burst.interpolate({
                    inputRange: [0, 1],
                    outputRange: [0.5, 1.75],
                  }),
                },
              ],
            }}
          />
          <Animated.View style={{ transform: [{ scale }] }}>
            <Ionicons
              name={upvoted ? "heart" : "heart-outline"}
              size={21}
              color={upvoted ? LIKED_COLOR : isDark ? "#AAB5AD" : "#64748B"}
            />
          </Animated.View>
        </View>
        <Text
          className={`text-[11px] font-bold ${upvoted ? "text-[#22C55E]" : "text-[#64748B] dark:text-[#AAB5AD]"}`}
        >
          {upvoteCount}
        </Text>
      </Button>
    </View>
  );
}

export function PostCommentSheet({
  postId,
  onClose,
}: {
  postId: number | string | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [content, setContent] = useState("");
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, onClose);
  useEffect(() => {
    if (postId !== null) {
      sheetTranslateY.setValue(windowHeight);
      Animated.timing(sheetTranslateY, {
        toValue: 0,
        duration: 280,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }).start();
    }
  }, [postId, sheetTranslateY, windowHeight]);
  const numericPostId = postId === null ? null : Number(postId);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: postId !== null && isAuthenticated,
    staleTime: 5 * 60 * 1000,
  });
  const commentsQuery = useInfiniteQuery({
    queryKey: ["post-comments", numericPostId],
    queryFn: ({ pageParam }) =>
      getPostComments(numericPostId!, { page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: numericPostId !== null && Number.isFinite(numericPostId),
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
  });
  const comments =
    commentsQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const createMutation = useMutation({
    mutationFn: () => addPostComment(numericPostId!, content.trim()),
    onSuccess: async () => {
      setContent("");
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["post-comments", numericPostId],
        }),
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
      ]);
    },
  });
  const canSubmit =
    isAuthenticated &&
    numericPostId !== null &&
    Number.isFinite(numericPostId) &&
    Boolean(content.trim()) &&
    !createMutation.isPending;
  const submit = () => {
    if (canSubmit) createMutation.mutate();
  };

  return (
    <Modal
      visible={postId !== null}
      transparent
      animationType="none"
      onRequestClose={dismissSheet}
    >
      <View className="flex-1 justify-end">
        <Pressable
          className="absolute inset-0 bg-black/40"
          onPress={dismissSheet}
        />
        <KeyboardAvoidingView
          className="h-[78%]"
          behavior={Platform.OS === "ios" ? "padding" : undefined}
          keyboardVerticalOffset={Platform.OS === "ios" ? 12 : 0}
        >
          <Animated.View
            className="flex-1 rounded-t-[30px] bg-[#FCFDFC] pt-2.5 dark:bg-[#171C18]"
            style={{ transform: [{ translateY: sheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={onClose}
              translateY={sheetTranslateY}
              dismissDistance={windowHeight}
            />
            <View className="h-10 items-center justify-center px-5">
              <Text className="text-[17px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
                댓글
              </Text>
            </View>
            {commentsQuery.isPending ? (
              <View className="flex-1 items-center justify-center">
                <ActivityIndicator color="#087A3F" />
              </View>
            ) : commentsQuery.isError ? (
              <View className="flex-1">
                <ErrorState
                  message={commentsQuery.error.message}
                  onRetry={() => void commentsQuery.refetch()}
                />
              </View>
            ) : (
              <FlatList
                className="flex-1"
                data={comments}
                keyExtractor={(item) => String(item.commentId)}
                contentContainerClassName="grow gap-3 py-4 pb-8"
                showsVerticalScrollIndicator
                scrollEnabled
                nestedScrollEnabled
                keyboardShouldPersistTaps="handled"
                ListEmptyComponent={
                  <View className="flex-1 items-center justify-center px-6 py-12">
                    <Ionicons name="leaf-outline" size={36} color="#94A09A" />
                    <Text className="mt-3 font-bold text-[#191C1D] dark:text-[#F1F5F2]">
                      아직 댓글이 없어요
                    </Text>
                    <Text className="mt-1 text-center text-sm text-[#6B756D] dark:text-[#AAB5AD]">
                      첫 댓글을 남겨보세요.
                    </Text>
                  </View>
                }
                renderItem={({ item }) => <CommentRow item={item} />}
                onEndReached={() => {
                  if (
                    commentsQuery.hasNextPage &&
                    !commentsQuery.isFetchingNextPage
                  )
                    void commentsQuery.fetchNextPage();
                }}
                onEndReachedThreshold={0.5}
                ListFooterComponent={
                  commentsQuery.isFetchingNextPage ? (
                    <ActivityIndicator color="#087A3F" className="my-4" />
                  ) : null
                }
              />
            )}
            <Separator className="bg-[#E5EBE5]" />
            <View className="flex-row items-start gap-3 bg-white px-4 pb-4 pt-3 dark:bg-[#1B211D]">
              <Avatar
                alt={`${profileQuery.data?.nickname ?? "내"} 프로필`}
                className="h-11 w-11 border border-[#DDE7DE]"
              >
                <AvatarImage
                  source={
                    profileQuery.data?.profileImageUrl
                      ? { uri: profileQuery.data.profileImageUrl }
                      : DEFAULT_PROFILE_IMAGE
                  }
                />
                <AvatarFallback className="bg-[#E9F5EC]" />
              </Avatar>
              <View className="min-h-11 flex-1 flex-row items-end rounded-[22px] border border-[#DCE5DE] bg-[#F8FAF8] pl-4 pr-1.5 py-1.5 dark:border-[#343D36] dark:bg-[#242B26]">
                <TextInput
                  value={content}
                  onChangeText={setContent}
                  editable={isAuthenticated && !createMutation.isPending}
                  className="max-h-24 min-h-8 flex-1 py-1 text-[14px] text-[#191C1D] dark:text-[#F1F5F2]"
                  multiline
                  maxLength={1000}
                  placeholder={
                    isAuthenticated
                      ? "댓글을 입력하세요"
                      : "로그인 후 댓글을 작성할 수 있어요"
                  }
                  placeholderTextColor="#7A857D"
                  textAlignVertical="center"
                />
                <Button
                  size="icon"
                  accessibilityLabel="댓글 작성"
                  className="h-9 w-9 rounded-full"
                  disabled={!canSubmit}
                  onPress={submit}
                >
                  {createMutation.isPending ? (
                    <ActivityIndicator size="small" color="white" />
                  ) : (
                    <Ionicons name="send" size={17} color="white" />
                  )}
                </Button>
              </View>
            </View>
            {createMutation.isError ? (
              <Text className="px-5 pb-3 text-xs text-destructive">
                {createMutation.error.message}
              </Text>
            ) : null}
          </Animated.View>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
}

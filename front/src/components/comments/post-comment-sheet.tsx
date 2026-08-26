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
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { LIKED_COLOR } from "@/components/feed/post-actions";
import { useAuthStore } from "@/stores/auth-store";
import type { PostComment } from "@/types/domain";

function CommentRow({ item }: { item: PostComment }) {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [upvoted, setUpvoted] = useState(false);
  const [upvoteCount, setUpvoteCount] = useState(item.upvoteCount);
  const scale = useRef(new Animated.Value(1)).current;
  const burst = useRef(new Animated.Value(0)).current;
  const mutation = useMutation({
    mutationFn: () => toggleCommentUpvote(item.commentId),
    onSuccess: (result) => {
      setUpvoted(result.upvoted);
      setUpvoteCount(result.upvoteCount);
      void queryClient.invalidateQueries({ queryKey: ["post-comments"] });
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
    <View className="w-full flex-row items-start gap-3 px-5">
      <Avatar alt={`${item.nickname} 프로필`} className="h-10 w-10">
        <AvatarFallback className="bg-secondary">
          <Text className="font-black text-primary">
            {item.nickname.slice(0, 1)}
          </Text>
        </AvatarFallback>
      </Avatar>
      <View className="flex-1 pt-0.5">
        <View className="flex-row items-center gap-1.5">
          <Text className="text-xs font-bold text-foreground">
            {item.nickname}
          </Text>
          <Text className="text-[10px] text-muted-foreground">
            {new Date(item.createdAt).toLocaleDateString("ko-KR")}
          </Text>
        </View>
        <Text className="mt-0.5 text-xs leading-[18px] text-[#34443A]">
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
              color={upvoted ? LIKED_COLOR : "#64748B"}
            />
          </Animated.View>
        </View>
        <Text
          className={`text-[11px] font-bold ${upvoted ? "text-[#22C55E]" : "text-[#64748B]"}`}
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
        <Pressable className="absolute inset-0 bg-black/40" onPress={dismissSheet} />
        <KeyboardAvoidingView
          className="h-[78%]"
          behavior={Platform.OS === "ios" ? "padding" : undefined}
          keyboardVerticalOffset={Platform.OS === "ios" ? 12 : 0}
        >
          <Animated.View
            className="flex-1 rounded-t-[30px] bg-background pt-2.5"
            style={{ transform: [{ translateY: sheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={onClose}
              translateY={sheetTranslateY}
              dismissDistance={windowHeight}
            />
            <View className="h-10 items-center justify-center px-5">
              <Text className="text-[17px] font-black text-foreground">
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
                  <EmptyState
                    title="아직 댓글이 없어요"
                    description="첫 댓글을 남겨보세요."
                  />
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
            <Separator />
            <View className="flex-row items-start gap-3 bg-white px-4 pb-4 pt-3">
              <Avatar
                alt={`${profileQuery.data?.nickname ?? "내"} 프로필`}
                className="h-11 w-11 border border-[#DDE7DE]"
              >
                {profileQuery.data?.profileImageUrl ? (
                  <AvatarImage
                    source={{ uri: profileQuery.data.profileImageUrl }}
                  />
                ) : null}
                <AvatarFallback className="bg-[#E9F5EC]">
                  <Text className="font-black text-[#087A3F]">
                    {(profileQuery.data?.nickname ?? "나").slice(0, 1)}
                  </Text>
                </AvatarFallback>
              </Avatar>
              <View className="min-h-11 flex-1 flex-row items-end rounded-[22px] border border-[#DCE5DE] bg-[#F8FAF8] pl-4 pr-1.5 py-1.5">
                <TextInput
                  value={content}
                  onChangeText={setContent}
                  editable={isAuthenticated && !createMutation.isPending}
                  className="max-h-24 min-h-8 flex-1 py-1 text-[14px] text-[#191C1D]"
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

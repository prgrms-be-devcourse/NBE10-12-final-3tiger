import { Ionicons } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
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
  ScrollView,
  Easing,
  TextInput,
  View,
  useWindowDimensions,
} from "react-native";
import { useRef } from "react";

import {
  addCommentReply,
  addPostComment,
  deleteComment,
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

const COMMENT_SORTS: Array<{ key: "latest" | "upvote"; label: string }> = [
  { key: "latest", label: "최신순" },
  { key: "upvote", label: "공감순" },
];

function CommentRow({
  item,
  postId,
  currentUserId,
  isReply = false,
}: {
  item: PostComment;
  postId: number;
  currentUserId?: number;
  isReply?: boolean;
}) {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const canDelete = currentUserId != null && currentUserId === item.userId;
  const replies = item.replies ?? [];
  const [upvoted, setUpvoted] = useState(item.isUpvoted);
  const [upvoteCount, setUpvoteCount] = useState(item.upvoteCount);
  const [profileImageReady, setProfileImageReady] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuPosition, setMenuPosition] = useState({ left: 20, top: 0 });
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [replyOpen, setReplyOpen] = useState(false);
  const [replyContent, setReplyContent] = useState("");
  const [repliesExpanded, setRepliesExpanded] = useState(
    () => (item.replies?.length ?? 0) < 3,
  );
  const commentRef = useRef<View>(null);
  useEffect(() => {
    setUpvoted(item.isUpvoted);
    setUpvoteCount(item.upvoteCount);
  }, [item.isUpvoted, item.upvoteCount]);
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
        const patchUpvote = (comment: PostComment) =>
          comment.commentId === item.commentId
            ? {
                ...comment,
                isUpvoted: result.upvoted,
                upvoteCount: result.upvoteCount,
              }
            : comment;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            // 원댓글과 답글(중첩) 모두 반영
            content: page.content.map((comment) => ({
              ...patchUpvote(comment),
              replies: (comment.replies ?? []).map(patchUpvote),
            })),
          })),
        };
      });
    },
  });
  const deleteMutation = useMutation({
    mutationFn: () => deleteComment(item.commentId),
    onSuccess: async () => {
      setDeleteConfirmOpen(false);
      queryClient.setQueriesData<{
        pages: Array<{ content: PostComment[] }>;
        pageParams: unknown[];
      }>({ queryKey: ["post-comments", postId] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            // 원댓글이면 목록에서 제거, 답글이면 부모의 replies 에서 제거
            content: page.content
              .filter((comment) => comment.commentId !== item.commentId)
              .map((comment) => ({
                ...comment,
                replies: (comment.replies ?? []).filter(
                  (reply) => reply.commentId !== item.commentId,
                ),
              })),
          })),
        };
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["post-comments", postId] }),
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
      ]);
    },
  });
  const replyMutation = useMutation({
    mutationFn: () => addCommentReply(item.commentId, replyContent.trim()),
    onSuccess: async () => {
      setReplyContent("");
      setReplyOpen(false);
      setRepliesExpanded(true);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["post-comments", postId] }),
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
      ]);
    },
  });
  const canSubmitReply =
    isAuthenticated && Boolean(replyContent.trim()) && !replyMutation.isPending;
  const submitReply = () => {
    if (canSubmitReply) replyMutation.mutate();
  };
  const handleUpvote = () => {
    if (!isAuthenticated || mutation.isPending) return;
    void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
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
      className={`w-full ${profileImageReady ? "opacity-100" : "opacity-0"}`}
    >
      <Pressable
        ref={commentRef}
        accessibilityLabel={`${item.nickname}님의 댓글, 길게 눌러 메뉴 열기`}
        delayLongPress={450}
        onLongPress={
          item.isDeleted
            ? undefined
            : () => {
                void Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                commentRef.current?.measureInWindow((x, y, width, height) => {
                  setMenuPosition({ left: x + 20, top: y + height + 6 });
                  setMenuOpen(true);
                });
              }
        }
        className="w-full flex-row items-start gap-3 bg-[#FCFDFC] px-5 dark:bg-[#171C18]"
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
          <Text
            className={`mt-0.5 text-xs leading-[18px] ${
              item.isDeleted
                ? "italic text-[#9AA79F] dark:text-[#6E7A72]"
                : "text-[#34443A] dark:text-[#D4DDD6]"
            }`}
          >
            {item.content}
          </Text>
        </View>
        {!item.isDeleted && (
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
        )}
      </Pressable>

      {!isReply && !item.isDeleted && isAuthenticated && (
        <Pressable
          accessibilityLabel="답글 달기"
          className="mt-1 self-start pl-[68px] pr-5 py-1"
          onPress={() => setReplyOpen((prev) => !prev)}
        >
          <Text className="text-[11px] font-bold text-[#6B756D] dark:text-[#AAB5AD]">
            답글 달기
          </Text>
        </Pressable>
      )}

      {!isReply && replyOpen && (
        <View className="mt-1.5 flex-row items-end gap-2 pl-[68px] pr-5">
          <View className="min-h-9 flex-1 flex-row items-end rounded-[18px] border border-[#DCE5DE] bg-[#F8FAF8] py-1 pl-3.5 pr-1 dark:border-[#343D36] dark:bg-[#242B26]">
            <TextInput
              value={replyContent}
              onChangeText={setReplyContent}
              editable={!replyMutation.isPending}
              autoFocus
              className="max-h-20 min-h-7 flex-1 py-1 text-[13px] text-[#191C1D] dark:text-[#F1F5F2]"
              multiline
              maxLength={1000}
              placeholder={`${item.nickname}님에게 답글 남기기`}
              placeholderTextColor="#7A857D"
              textAlignVertical="center"
            />
            <Button
              size="icon"
              accessibilityLabel="답글 작성"
              className="h-8 w-8 rounded-full"
              disabled={!canSubmitReply}
              onPress={submitReply}
            >
              {replyMutation.isPending ? (
                <ActivityIndicator size="small" color="white" />
              ) : (
                <Ionicons name="send" size={15} color="white" />
              )}
            </Button>
          </View>
        </View>
      )}
      {!isReply && replyMutation.isError ? (
        <Text className="mt-1 pl-[68px] pr-5 text-[11px] text-destructive">
          {replyMutation.error.message}
        </Text>
      ) : null}

      {!isReply && replies.length > 0 && (
        <View className="mt-2 gap-3">
          {replies.length >= 3 && (
            <Pressable
              accessibilityLabel={repliesExpanded ? "답글 숨기기" : "답글 보기"}
              className="self-start pl-[68px] pr-5 py-1"
              onPress={() => setRepliesExpanded((prev) => !prev)}
            >
              <Text className="text-[11px] font-bold text-[#087A3F] dark:text-[#4ADE80]">
                {repliesExpanded
                  ? "답글 숨기기"
                  : `답글 ${replies.length}개 보기`}
              </Text>
            </Pressable>
          )}
          {repliesExpanded &&
            replies.map((reply) => (
              <View key={reply.commentId} className="pl-8">
                <CommentRow
                  item={reply}
                  postId={postId}
                  currentUserId={currentUserId}
                  isReply
                />
              </View>
            ))}
        </View>
      )}

      <Modal
        visible={menuOpen}
        transparent
        animationType="fade"
        onRequestClose={() => setMenuOpen(false)}
      >
        <View className="flex-1">
          <Pressable
            accessibilityLabel="댓글 메뉴 닫기"
            className="absolute inset-0"
            onPress={() => setMenuOpen(false)}
          />
          <View
            accessibilityRole="menu"
            className="absolute w-[148px] overflow-hidden rounded-xl border border-[#DDE5DA] bg-white shadow-lg dark:border-[#343D36] dark:bg-[#242B26]"
            style={menuPosition}
          >
            <Button
              variant="ghost"
              accessibilityLabel="댓글 신고"
              className="h-12 justify-start rounded-none px-4"
              onPress={() => {}}
            >
              <Ionicons name="flag-outline" size={18} color="#526056" />
              <Text className="text-sm font-bold text-[#33443A] dark:text-[#D4DDD6]">
                신고
              </Text>
            </Button>
            {canDelete && (
              <>
                <View className="mx-3 h-px bg-[#EEF1EE] dark:bg-[#343D36]" />
                <Button
                  variant="ghost"
                  accessibilityLabel="댓글 삭제"
                  className="h-12 justify-start rounded-none px-4"
                  onPress={() => {
                    setMenuOpen(false);
                    setDeleteConfirmOpen(true);
                  }}
                >
                  <Ionicons name="trash-outline" size={18} color="#DC2626" />
                  <Text className="text-sm font-bold text-[#DC2626]">삭제</Text>
                </Button>
              </>
            )}
          </View>
        </View>
      </Modal>

      <Modal
        visible={deleteConfirmOpen}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!deleteMutation.isPending) setDeleteConfirmOpen(false);
        }}
      >
        <View className="flex-1 items-center justify-center bg-black/40 px-6">
          <View className="w-full max-w-[360px] rounded-2xl bg-white p-5 shadow-lg dark:bg-[#1B211D]">
            <View className="mb-4 h-11 w-11 items-center justify-center rounded-full bg-[#FEECEC]">
              <Ionicons name="trash-outline" size={22} color="#DC2626" />
            </View>
            <Text className="text-lg font-extrabold text-[#17251B] dark:text-[#F1F5F2]">
              댓글을 삭제할까요?
            </Text>
            <Text className="mt-2 text-sm leading-5 text-[#667168] dark:text-[#AAB5AD]">
              삭제한 댓글은 다시 복구할 수 없습니다.
            </Text>
            {deleteMutation.isError && (
              <Text className="mt-3 text-sm text-[#DC2626]">
                {deleteMutation.error.message}
              </Text>
            )}
            <View className="mt-6 flex-row gap-2.5">
              <Button
                variant="secondary"
                className="h-12 flex-1 rounded-xl bg-[#EEF2EF] dark:bg-[#2A312C]"
                disabled={deleteMutation.isPending}
                onPress={() => setDeleteConfirmOpen(false)}
              >
                <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                  취소
                </Text>
              </Button>
              <Button
                variant="destructive"
                className="h-12 flex-1 rounded-xl bg-[#DC2626]"
                disabled={deleteMutation.isPending}
                onPress={() => deleteMutation.mutate()}
              >
                {deleteMutation.isPending && (
                  <ActivityIndicator size="small" color="white" />
                )}
                <Text className="font-bold text-white">
                  {deleteMutation.isPending ? "삭제 중" : "삭제"}
                </Text>
              </Button>
            </View>
          </View>
        </View>
      </Modal>
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
  const [sort, setSort] = useState<"latest" | "upvote">("latest");
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
    queryKey: ["post-comments", numericPostId, sort],
    queryFn: ({ pageParam }) =>
      getPostComments(numericPostId!, { page: pageParam, size: 20, sort }),
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
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              className="flex-none"
              contentContainerClassName="gap-2 px-5 pb-2 pt-1"
            >
              {COMMENT_SORTS.map((option) => {
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
                renderItem={({ item }) => (
                  <CommentRow
                    item={item}
                    postId={numericPostId!}
                    currentUserId={profileQuery.data?.userId}
                  />
                )}
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

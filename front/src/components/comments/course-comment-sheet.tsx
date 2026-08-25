import { Ionicons } from "@expo/vector-icons";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Text } from "@/components/ui/text";
import { LIKED_COLOR } from "@/components/feed/post-actions";
import { useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  FlatList,
  Modal,
  Pressable,
  useWindowDimensions,
  View,
} from "react-native";
import { useRef } from "react";

type CommentItem = {
  id: string;
  nickname: string;
  content: string;
  createdAt: string;
  upvoteCount: number;
};

const COMMENT_TEXTS = [
  ["초록산책", "오후에도 나무 그늘이 이어져서 편하게 걸었어요."],
  ["해피보호자", "강아지와 걷기 좋고 중간중간 쉴 곳도 충분해요."],
  ["느린걸음", "경사가 완만해서 부모님과 함께 다녀오기 좋았습니다."],
  ["유모차마실", "노면이 고르고 폭이 넓어서 유모차도 무리 없었어요."],
  ["아침산책", "이른 시간에는 한적하고 새소리도 잘 들려요."],
  ["걷기좋은날", "화장실 위치를 미리 확인하면 더 편하게 이용할 수 있어요."],
  ["서울숲친구", "주말에는 사람이 많아서 오전 방문을 추천합니다."],
  ["두발네발", "반려견 음수대를 이용할 수 있어 좋았어요."],
  ["초록바람", "바람이 잘 통하고 풍경이 예쁜 코스예요."],
  ["산책초보", "길 안내가 잘 되어 있어서 처음 가도 어렵지 않았습니다."],
  ["건강한하루", "벤치가 자주 보여 천천히 쉬어가며 걸었어요."],
  ["매일한바퀴", "노을 질 때 풍경이 특히 아름다워요."],
] as const;

function createComments(courseId: string): CommentItem[] {
  return COMMENT_TEXTS.map(([nickname, content], index) => ({
    id: `${courseId}-${index + 1}`,
    nickname,
    content,
    createdAt: index < 2 ? `${index + 1}시간 전` : `${index}일 전`,
    upvoteCount: (Number(courseId) + index * 3) % 12,
  }));
}

export function CourseCommentSheet({
  courseId,
  onClose,
}: {
  courseId: string | null;
  onClose: () => void;
}) {
  const allComments = useMemo(
    () => (courseId ? createComments(courseId) : []),
    [courseId],
  );
  const [visibleCount, setVisibleCount] = useState(8);
  const [loadingMore, setLoadingMore] = useState(false);
  const [upvoted, setUpvoted] = useState<Set<string>>(new Set());
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, onClose);

  useEffect(() => {
    setVisibleCount(8);
    setUpvoted(new Set());
    if (courseId) {
      sheetTranslateY.setValue(windowHeight);
      Animated.timing(sheetTranslateY, {
        toValue: 0,
        duration: 280,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }).start();
    }
  }, [courseId, sheetTranslateY, windowHeight]);

  const loadMore = () => {
    if (loadingMore || visibleCount >= allComments.length) return;
    setLoadingMore(true);
    setTimeout(() => {
      setVisibleCount((count) => Math.min(count + 4, allComments.length));
      setLoadingMore(false);
    }, 300);
  };

  const toggleUpvote = (id: string) =>
    setUpvoted((current) => {
      const next = new Set(current);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });

  return (
    <Modal
      visible={courseId !== null}
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
          className="h-[78%] rounded-t-[30px] bg-background pt-2.5"
          style={{ transform: [{ translateY: sheetTranslateY }] }}
        >
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={sheetTranslateY}
            dismissDistance={windowHeight}
          />
          <View className="h-10 items-center justify-center px-5">
            <View
              pointerEvents="none"
              className="absolute inset-x-0 items-center"
            >
              <Text className="text-[17px] font-black text-foreground">
                댓글
              </Text>
            </View>
          </View>
          <FlatList
            className="flex-1"
            data={allComments.slice(0, visibleCount)}
            keyExtractor={(item) => item.id}
            contentContainerClassName="gap-7 py-5 pb-10"
            showsVerticalScrollIndicator
            scrollEnabled
            nestedScrollEnabled
            onEndReached={loadMore}
            onEndReachedThreshold={0.35}
            renderItem={({ item }) => {
              const active = upvoted.has(item.id);
              return (
                <View className="w-full flex-row items-start gap-3 px-5">
                  <Avatar alt={`${item.nickname} 프로필`} className="h-10 w-10">
                    <AvatarFallback className="bg-secondary">
                      <Text className="font-black text-primary">
                        {item.nickname.slice(0, 1)}
                      </Text>
                    </AvatarFallback>
                  </Avatar>
                  <View className="flex-1">
                    <View className="flex-row items-center gap-2">
                      <Text className="font-extrabold">{item.nickname}</Text>
                      <Text variant="muted" className="text-xs">
                        {item.createdAt}
                      </Text>
                    </View>
                    <Text className="mt-1.5 text-[14px] leading-[21px] text-[#34443A]">
                      {item.content}
                    </Text>
                  </View>
                  <Button
                    variant="ghost"
                    className="-mr-2 h-12 w-10 flex-col gap-0 rounded-full px-0 py-1"
                    accessibilityLabel={active ? "댓글 공감 취소" : "댓글 공감"}
                    onPress={() => toggleUpvote(item.id)}
                  >
                    <Ionicons
                      name={active ? "heart" : "heart-outline"}
                      size={21}
                      color={active ? LIKED_COLOR : "#64748B"}
                    />
                    <Text
                      className={`text-[11px] font-bold ${active ? "text-[#22C55E]" : "text-[#64748B]"}`}
                    >
                      {item.upvoteCount + (active ? 1 : 0)}
                    </Text>
                  </Button>
                </View>
              );
            }}
            ListFooterComponent={
              loadingMore ? (
                <ActivityIndicator className="my-4" color="#087A3F" />
              ) : visibleCount >= allComments.length ? (
                <Text variant="muted" className="py-4 text-center">
                  모든 댓글을 확인했어요
                </Text>
              ) : null
            }
          />
        </Animated.View>
      </View>
    </Modal>
  );
}

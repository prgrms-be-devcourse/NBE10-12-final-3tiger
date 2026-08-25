import { Ionicons } from "@expo/vector-icons";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useRef } from "react";
import { Animated, Easing, View } from "react-native";

export const LIKED_COLOR = "#22C55E";
const IDLE_COLOR = "#526056";

type PostActionsProps = {
  liked: boolean;
  likeCount: number;
  commentCount: number;
  onToggleLike: () => void;
  onOpenComments?: () => void;
  bookmarked?: boolean;
  onToggleBookmark?: () => void;
};

export function PostActions({
  liked,
  likeCount,
  commentCount,
  onToggleLike,
  onOpenComments,
  bookmarked,
  onToggleBookmark,
}: PostActionsProps) {
  const heartScale = useRef(new Animated.Value(1)).current;
  const burstProgress = useRef(new Animated.Value(0)).current;

  const handleToggleLike = () => {
    heartScale.stopAnimation();
    burstProgress.stopAnimation();
    heartScale.setValue(1);
    burstProgress.setValue(0);

    const heartAnimation = liked
      ? Animated.sequence([
          Animated.timing(heartScale, {
            toValue: 0.82,
            duration: 90,
            easing: Easing.out(Easing.quad),
            useNativeDriver: true,
          }),
          Animated.spring(heartScale, {
            toValue: 1,
            speed: 28,
            bounciness: 5,
            useNativeDriver: true,
          }),
        ])
      : Animated.sequence([
          Animated.timing(heartScale, {
            toValue: 0.76,
            duration: 80,
            easing: Easing.out(Easing.quad),
            useNativeDriver: true,
          }),
          Animated.spring(heartScale, {
            toValue: 1.32,
            speed: 32,
            bounciness: 10,
            useNativeDriver: true,
          }),
          Animated.spring(heartScale, {
            toValue: 1,
            speed: 24,
            bounciness: 4,
            useNativeDriver: true,
          }),
        ]);

    const animations = [heartAnimation];
    if (!liked) {
      animations.push(
        Animated.timing(burstProgress, {
          toValue: 1,
          duration: 360,
          easing: Easing.out(Easing.cubic),
          useNativeDriver: true,
        }),
      );
    }
    Animated.parallel(animations).start();
    onToggleLike();
  };

  return (
    <View className="min-h-10 flex-row items-center justify-between px-2">
      <View className="flex-row items-center">
        <Button
          variant="ghost"
          accessibilityLabel={liked ? "좋아요 취소" : "좋아요"}
          className="h-10 flex-row gap-1 rounded-full px-1 active:bg-[#E9F5EC]"
          onPress={handleToggleLike}
        >
          <View className="h-6 w-6 items-center justify-center">
            <Animated.View
              pointerEvents="none"
              className="absolute h-6 w-6 rounded-full border-2 border-[#22C55E]"
              style={{
                opacity: burstProgress.interpolate({
                  inputRange: [0, 0.15, 1],
                  outputRange: [0, 0.45, 0],
                }),
                transform: [
                  {
                    scale: burstProgress.interpolate({
                      inputRange: [0, 1],
                      outputRange: [0.45, 1.75],
                    }),
                  },
                ],
              }}
            />
            <Animated.View style={{ transform: [{ scale: heartScale }] }}>
              <Ionicons
                name={liked ? "heart" : "heart-outline"}
                size={21}
                color={liked ? LIKED_COLOR : IDLE_COLOR}
              />
            </Animated.View>
          </View>
          <Text
            className={`text-xs font-semibold ${liked ? "text-[#22C55E]" : "text-[#526056]"}`}
          >
            {likeCount}
          </Text>
        </Button>
        <Button
          variant="ghost"
          accessibilityLabel="댓글 보기"
          className="h-10 flex-row gap-1 rounded-full px-1 active:bg-[#F1F4F1]"
          onPress={onOpenComments}
        >
          <Ionicons name="chatbubble-outline" size={20} color={IDLE_COLOR} />
          <Text className="text-xs font-semibold text-[#526056]">
            {commentCount}
          </Text>
        </Button>
      </View>
      {onToggleBookmark && (
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel={bookmarked ? "북마크 해제" : "북마크"}
          className="h-10 w-10 rounded-full active:bg-[#E9F5EC]"
          onPress={onToggleBookmark}
        >
          <Ionicons
            name={bookmarked ? "bookmark" : "bookmark-outline"}
            size={21}
            color={bookmarked ? "#087A3F" : IDLE_COLOR}
          />
        </Button>
      )}
    </View>
  );
}

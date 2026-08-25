import { Ionicons } from "@expo/vector-icons";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { View } from "react-native";

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
  return (
    <View className="min-h-[60px] flex-row items-center justify-between px-4">
      <View className="flex-row items-center gap-1">
        <Button
          variant="ghost"
          accessibilityLabel={liked ? "좋아요 취소" : "좋아요"}
          className="h-11 flex-row gap-1.5 rounded-full px-3 active:bg-[#E9F5EC]"
          onPress={onToggleLike}
        >
          <Ionicons
            name={liked ? "heart" : "heart-outline"}
            size={24}
            color={liked ? LIKED_COLOR : IDLE_COLOR}
          />
          <Text
            className={`text-sm font-bold ${liked ? "text-[#22C55E]" : "text-[#526056]"}`}
          >
            {likeCount}
          </Text>
        </Button>
        <Button
          variant="ghost"
          accessibilityLabel="댓글 보기"
          className="h-11 flex-row gap-1.5 rounded-full px-3 active:bg-[#F1F4F1]"
          onPress={onOpenComments}
        >
          <Ionicons name="chatbubble-outline" size={22} color={IDLE_COLOR} />
          <Text className="text-sm font-bold text-[#526056]">
            {commentCount}
          </Text>
        </Button>
      </View>
      {onToggleBookmark && (
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel={bookmarked ? "북마크 해제" : "북마크"}
          className="h-11 w-11 rounded-full active:bg-[#E9F5EC]"
          onPress={onToggleBookmark}
        >
          <Ionicons
            name={bookmarked ? "bookmark" : "bookmark-outline"}
            size={24}
            color={bookmarked ? "#087A3F" : IDLE_COLOR}
          />
        </Button>
      )}
    </View>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { PostActions } from "@/components/feed/post-actions";
import { CourseCommentSheet } from "@/components/comments/course-comment-sheet";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { router } from "expo-router";
import { useState } from "react";
import { FlatList, Image, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const POSTS = [
  {
    id: "1",
    courseId: "101",
    courseName: "성수 서울숲 순환",
    user: "건강한하루",
    time: "2시간 전",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuAe99syyGw6NkbqaoAYXLZYhf3xOBnas6sZ0e07MMtdnHhoD7cSENUqzi8_XLPxG0-5DgF0gLSsUzck5sT1s6T8JREtOzC_q23ZCsXnqAKA5tYraLHH0ghXfoZw101rwAkOxd_PE9I6-4yTKjs_jqz0p42HBjdrB5Zx61hZS9nqQrn8wynTre1O2jum5qy3q3-_gMZ_5-L_vVlDtV9CAA-hoWMrveAUXCRRNht08ceVKtJm-89UDeyWYw",
    text: "오늘 아침 공원 산책길이 너무 좋았어요. 날씨도 화창하고 걷기 딱 좋은 온도네요. 다들 좋은 하루 보내세요! 🌿",
    likes: 24,
    comments: 5,
  },
  {
    id: "2",
    courseId: "102",
    courseName: "한강공원 뚝섬길",
    user: "유모차라이더",
    time: "어제",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuDm5gtCiLACjWV3nZmzE6oU_w2PAFpfar_lfnhS7eomevejN0O9Jqem8RVhcz4IHxpwlvWDPEBGmqkhj4jJycX1SNEIZaemSjJbh9N16u_mO-2Uq_RvnapuqbNCj0kYTbDVtQ-tMqs-0gIB2D-RBWmr_VOjxZtm7gH1PkMYToO7z98mfNf8S94p5OgiIyKItFhZWJNX-zX8v_wzDkt_WxJF7AZuxDov68WBF98RUiLY-_kFX-TUKRiafg",
    text: "이 길은 경사도 완만하고 바닥이 평평해서 유모차 끌고 가기 정말 좋아요! 추천합니다 👍",
    likes: 128,
    comments: 12,
  },
];

function Card({
  item,
  onOpenComments,
}: {
  item: (typeof POSTS)[number];
  onOpenComments: () => void;
}) {
  const [liked, setLiked] = useState(true);
  const [bookmarked, setBookmarked] = useState(false);

  return (
    <View className="bg-white">
      <View className="min-h-[72px] flex-row items-center gap-3 px-5 py-3">
        <Avatar
          alt={`${item.user} 프로필`}
          className="h-11 w-11 border border-[#E4EAE5]"
        >
          <AvatarFallback className="bg-[#E9F5EC]">
            <Text className="font-extrabold text-[#087A3F]">
              {item.user.slice(0, 1)}
            </Text>
          </AvatarFallback>
        </Avatar>
        <View className="flex-1">
          <Text className="text-[16px] font-extrabold text-[#191C1D]">
            {item.user}
          </Text>
          <Text className="mt-1 text-xs text-[#6B756D]">{item.time}</Text>
        </View>
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="게시글 메뉴"
          className="h-11 w-11 rounded-full"
        >
          <Ionicons name="ellipsis-vertical" size={23} color="#526056" />
        </Button>
      </View>

      <Image
        source={{ uri: item.image }}
        className="h-80 w-full bg-slate-200"
        resizeMode="cover"
      />

      <PostActions
        liked={liked}
        likeCount={item.likes - (liked ? 0 : 1)}
        commentCount={item.comments}
        onToggleLike={() => setLiked((value) => !value)}
        onOpenComments={onOpenComments}
        bookmarked={bookmarked}
        onToggleBookmark={() => setBookmarked((value) => !value)}
      />

      <View className="px-5 pb-5">
        <Text className="text-[15px] leading-[24px] text-[#252A26]">
          <Text className="text-[16px] font-extrabold leading-[24px] text-[#191C1D]">
            {item.user}{" "}
          </Text>
          {item.text}
        </Text>
        <Text className="mt-2 text-[13px] text-[#758078]">
          댓글 {item.comments}개 모두 보기
        </Text>
      </View>
      <Separator className="bg-[#E8ECE8]" />
    </View>
  );
}

export default function LikedPostsScreen() {
  const [commentCourse, setCommentCourse] = useState<
    (typeof POSTS)[number] | null
  >(null);
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
      <FlatList
        data={POSTS}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <Card item={item} onOpenComments={() => setCommentCourse(item)} />
        )}
        contentContainerClassName="pb-6"
        ItemSeparatorComponent={() => <View className="h-3 bg-[#F3F6F3]" />}
      />
      <CourseCommentSheet
        courseId={commentCourse?.courseId ?? null}
        onClose={() => setCommentCourse(null)}
      />
    </SafeAreaView>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { CourseCommentSheet } from "@/components/comments/course-comment-sheet";
import { PostActions } from "@/components/feed/post-actions";
import { Button } from "@/components/ui/button";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";
import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  Animated,
  Easing,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

type MyPost = {
  id: string;
  courseId: string;
  image: string;
  content: string;
  course: string;
  walkedAt: string;
  duration: string;
  likes: number;
  comments: number;
};

const POSTS: MyPost[] = [
  {
    id: "1",
    courseId: "101",
    image: "https://images.unsplash.com/photo-1558788353-f76d92427f16?w=800",
    content:
      "해피와 서울숲을 천천히 걸었어요. 그늘이 많고 길이 평탄해서 편안한 산책이었습니다.",
    course: "성수 서울숲 순환",
    walkedAt: "2026년 8월 24일",
    duration: "42분",
    likes: 34,
    comments: 6,
  },
  {
    id: "2",
    courseId: "102",
    image: "https://images.unsplash.com/photo-1472396961693-142e6e269027?w=800",
    content: "바람이 선선한 아침에 만난 초록 풍경을 기록해 봅니다.",
    course: "한강공원 뚝섬길",
    walkedAt: "2026년 8월 21일",
    duration: "55분",
    likes: 21,
    comments: 3,
  },
  {
    id: "3",
    courseId: "103",
    image: "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?w=800",
    content:
      "노을이 내려앉은 산책길. 오늘도 무리하지 않고 기분 좋게 걸었습니다.",
    course: "연남동 경의선 숲길",
    walkedAt: "2026년 8월 18일",
    duration: "31분",
    likes: 48,
    comments: 9,
  },
  {
    id: "4",
    courseId: "104",
    image: "https://images.unsplash.com/photo-1519331379826-f10be5486c6f?w=800",
    content: "공원 벤치에서 잠시 쉬며 여유로운 오후를 보냈어요.",
    course: "보라매공원 둘레길",
    walkedAt: "2026년 8월 14일",
    duration: "60분",
    likes: 17,
    comments: 2,
  },
  {
    id: "5",
    courseId: "105",
    image: "https://images.unsplash.com/photo-1473448912268-2022ce9509d8?w=800",
    content: "나무 사이로 이어지는 조용한 길이 무척 마음에 들었습니다.",
    course: "북서울꿈의숲 산책로",
    walkedAt: "2026년 8월 10일",
    duration: "47분",
    likes: 29,
    comments: 4,
  },
  {
    id: "6",
    courseId: "106",
    image: "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=800",
    content: "맑은 하늘과 넓은 길 덕분에 기분 좋게 하루를 시작했어요.",
    course: "올림픽공원 순환길",
    walkedAt: "2026년 8월 7일",
    duration: "50분",
    likes: 41,
    comments: 8,
  },
];

function PostDetailSheet({
  post,
  onClose,
  onOpenComments,
}: {
  post: MyPost | null;
  onClose: () => void;
  onOpenComments: (post: MyPost) => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const [liked, setLiked] = useState(true);
  const dismissSheet = () =>
    dismissBottomSheet(translateY, windowHeight, onClose);

  useEffect(() => {
    if (!post) return;
    setLiked(true);
    translateY.setValue(windowHeight);
    Animated.timing(translateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [post, translateY, windowHeight]);

  return (
    <Modal
      visible={!!post}
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
          className="h-[88%] rounded-t-[30px] bg-white pt-2.5"
          style={{ transform: [{ translateY }] }}
        >
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={translateY}
            dismissDistance={windowHeight}
          />
          {post && (
            <ScrollView
              className="flex-1"
              nestedScrollEnabled
              showsVerticalScrollIndicator
              contentContainerClassName="px-5 pb-8"
            >
              <Image
                source={{ uri: post.image }}
                className="h-64 w-full rounded-[21px] bg-slate-200"
                resizeMode="cover"
              />

              <View className="mt-4 flex-row items-start">
                <View className="flex-1 pr-3">
                  <Text className="text-[11px] font-extrabold text-[#087A3F]">
                    나의 산책 기록
                  </Text>
                  <Text className="mt-1 text-[22px] font-black text-[#17251B]">
                    {post.course}
                  </Text>
                </View>
              </View>

              <View className="mt-4 flex-row rounded-2xl bg-[#F2F8F2] py-3">
                <View className="flex-1 items-center border-r border-[#DDE7DE]">
                  <Ionicons name="calendar-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047]">
                    {post.walkedAt}
                  </Text>
                </View>
                <View className="flex-1 items-center">
                  <Ionicons name="time-outline" size={18} color="#087A3F" />
                  <Text className="mt-1 text-xs font-bold text-[#405047]">
                    {post.duration}
                  </Text>
                </View>
              </View>

              <Text className="mt-5 text-[15px] leading-6 text-[#2D3931]">
                {post.content}
              </Text>
              <Separator className="my-5 bg-[#E6EBE7]" />
              <View className="-mx-4">
                <PostActions
                  liked={liked}
                  likeCount={post.likes - (liked ? 0 : 1)}
                  commentCount={post.comments}
                  onToggleLike={() => setLiked((value) => !value)}
                  onOpenComments={() => onOpenComments(post)}
                />
              </View>
            </ScrollView>
          )}
        </Animated.View>
      </View>
    </Modal>
  );
}

export default function MyPostScreen() {
  const [selected, setSelected] = useState<MyPost | null>(null);
  const [commentCourse, setCommentCourse] = useState<MyPost | null>(null);
  const openComments = (post: MyPost) => {
    setSelected(null);
    setTimeout(() => setCommentCourse(post), 250);
  };

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
        <Text className="text-2xl font-black text-[#006E2F]">나의 게시글</Text>
        <View className="w-11" />
      </View>

      <View className="h-[100px] flex-row items-center gap-[15px] px-5">
        <View className="h-16 w-16 items-center justify-center rounded-full border-2 border-[#22C55E] bg-[#E8F7EC]">
          <Ionicons name="person" size={30} color="#006E2F" />
        </View>
        <View>
          <Text className="text-xl font-extrabold text-[#191C1D]">
            산책러버
          </Text>
          <Text className="mt-1 text-sm text-slate-600">
            총 24개의 산책 기록
          </Text>
        </View>
      </View>

      <FlatList
        data={POSTS}
        numColumns={3}
        keyExtractor={(item) => item.id}
        columnWrapperClassName="gap-[3px]"
        contentContainerClassName="gap-[3px] px-[3px] pb-6"
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${item.course} 게시글 상세 보기`}
            className="aspect-square flex-1 overflow-hidden bg-slate-200"
            onPress={() => {
              setSelected(item);
            }}
          >
            <Image
              source={{ uri: item.image }}
              className="h-full w-full"
              resizeMode="cover"
            />
          </Pressable>
        )}
        ListFooterComponent={
          <View className="mt-[3px] h-[110px] items-center justify-center gap-1 bg-slate-100">
            <Ionicons name="images-outline" size={30} color="#94A09A" />
            <Text className="text-xs font-bold text-[#76827B]">더 보기</Text>
          </View>
        }
      />

      <PostDetailSheet
        post={selected}
        onClose={() => setSelected(null)}
        onOpenComments={openComments}
      />
      <CourseCommentSheet
        courseId={commentCourse?.courseId ?? null}
        onClose={() => setCommentCourse(null)}
      />
    </SafeAreaView>
  );
}

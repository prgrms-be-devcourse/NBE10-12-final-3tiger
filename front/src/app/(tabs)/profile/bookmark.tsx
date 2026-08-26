import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { router, usePathname } from "expo-router";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  Pressable,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseDetail, getMyBookmarks } from "@/api/course-api";
import { CourseCommentSheet } from "@/components/comments/course-comment-sheet";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";
import type { Course } from "@/types/domain";

const FALLBACK_IMAGE =
  "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1000";

export default function ProfileBookmarkScreen() {
  const pathname = usePathname();
  const isTabRoot = pathname === "/bookmark";
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [commentCourseId, setCommentCourseId] = useState<number | null>(null);
  useEffect(() => {
    if (!isAuthenticated) router.replace("/(auth)/login" as never);
  }, [isAuthenticated]);

  const bookmarksQuery = useInfiniteQuery({
    queryKey: ["bookmarks"],
    queryFn: ({ pageParam }) => getMyBookmarks({ page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: isAuthenticated,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const detailQuery = useQuery({
    queryKey: ["course", selectedId],
    queryFn: () => getCourseDetail(selectedId!),
    enabled: selectedId !== null,
  });
  const courses =
    bookmarksQuery.data?.pages.flatMap((page) => page.content) ?? [];
  const selected = detailQuery.data;
  const openCourseDetails = (courseId: number) => {
    setSelectedId(null);
    setTimeout(() => router.push(`/course/${courseId}` as never), 300);
  };

  return (
    <SafeAreaView className="flex-1 bg-[#F8FAFB]" edges={["top"]}>
      {!isTabRoot && (
        <View className="h-14 flex-row items-center justify-between bg-white px-5">
          <Button
            variant="ghost"
            size="icon"
            accessibilityLabel="뒤로 가기"
            className="h-11 w-11"
            onPress={() => router.back()}
          >
            <Ionicons name="arrow-back" size={24} color="#191C1D" />
          </Button>
          <Text className="text-2xl font-black text-[#006E2F]">
            저장한 코스
          </Text>
          <View className="w-11" />
        </View>
      )}
      {bookmarksQuery.isPending ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : bookmarksQuery.isError ? (
        <ErrorState
          message={bookmarksQuery.error.message}
          onRetry={() => void bookmarksQuery.refetch()}
        />
      ) : (
        <FlatList
          data={courses}
          keyExtractor={(item) => String(item.courseId)}
          contentContainerClassName="grow gap-4 p-5 pb-[30px]"
          ListHeaderComponent={
            <Text className="mb-0.5 text-sm text-slate-600">
              총 {bookmarksQuery.data?.pages[0]?.totalElements ?? 0}개의 코스
            </Text>
          }
          ListEmptyComponent={
            <EmptyState
              title="저장한 코스가 없어요"
              description="마음에 드는 코스를 저장해 보세요."
            />
          }
          renderItem={({ item }) => (
            <CourseCard
              item={item}
              onPress={() => setSelectedId(item.courseId)}
            />
          )}
          onEndReached={() => {
            if (
              bookmarksQuery.hasNextPage &&
              !bookmarksQuery.isFetchingNextPage
            )
              void bookmarksQuery.fetchNextPage();
          }}
          onEndReachedThreshold={0.5}
          ListFooterComponent={
            bookmarksQuery.isFetchingNextPage ? (
              <ActivityIndicator color="#087A3F" className="m-4" />
            ) : null
          }
        />
      )}
      <Modal
        visible={selectedId !== null}
        transparent
        animationType="slide"
        onRequestClose={() => setSelectedId(null)}
      >
        <Pressable
          className="flex-1 justify-end bg-black/40"
          onPress={() => setSelectedId(null)}
        >
          <Pressable
            className="rounded-t-[28px] bg-white p-5 pt-2.5"
            onPress={(event) => event.stopPropagation()}
          >
            <View className="mb-[15px] h-[5px] w-[42px] self-center rounded-full bg-slate-300" />
            {detailQuery.isPending ? (
              <ActivityIndicator color="#087A3F" className="my-16" />
            ) : detailQuery.isError ? (
              <ErrorState
                message={detailQuery.error.message}
                onRetry={() => void detailQuery.refetch()}
              />
            ) : selected ? (
              <>
                <Image
                  source={{ uri: selected.imageUrl || FALLBACK_IMAGE }}
                  className="h-[170px] w-full rounded-xl"
                />
                <View className="mt-4 flex-row items-center">
                  <View className="flex-1">
                    <Text className="text-[11px] font-extrabold text-[#006E2F]">
                      저장한 코스 상세
                    </Text>
                    <Text className="mt-1 text-[22px] font-black text-[#191C1D]">
                      {selected.name}
                    </Text>
                  </View>
                  <Button
                    variant="secondary"
                    size="icon"
                    accessibilityLabel="닫기"
                    className="rounded-full"
                    onPress={() => setSelectedId(null)}
                  >
                    <Ionicons name="close" size={22} color="#475569" />
                  </Button>
                </View>
                <View className="mt-3 flex-row items-center gap-1.5">
                  <Ionicons
                    name="git-branch-outline"
                    size={18}
                    color="#006E2F"
                  />
                  <Text>{(selected.distanceM / 1000).toFixed(1)}km</Text>
                  <Ionicons name="timer-outline" size={18} color="#006E2F" />
                  <Text>{selected.estimatedMinutes ?? "-"}분</Text>
                </View>
                <Text className="mt-[13px] text-sm leading-[21px] text-slate-600">
                  {selected.summary ??
                    selected.personaBadges?.join(" · ") ??
                    "코스 상세 정보를 확인해 보세요."}
                </Text>
                <View className="mt-4 flex-row gap-2">
                  <Button
                    variant="outline"
                    className="h-14 flex-1 rounded-xl"
                    onPress={() => {
                      setSelectedId(null);
                      setCommentCourseId(selected.courseId);
                    }}
                  >
                    <Text>댓글 보기</Text>
                  </Button>
                  <Button
                    className="h-14 flex-[2] rounded-xl"
                    onPress={() => openCourseDetails(selected.courseId)}
                  >
                    <Text className="font-black text-primary-foreground">
                      코스 자세히 보기
                    </Text>
                  </Button>
                </View>
              </>
            ) : null}
          </Pressable>
        </Pressable>
      </Modal>
      <CourseCommentSheet
        courseId={commentCourseId}
        onClose={() => setCommentCourseId(null)}
      />
    </SafeAreaView>
  );
}

function CourseCard({ item, onPress }: { item: Course; onPress: () => void }) {
  return (
    <Pressable className="rounded-xl bg-white p-4 shadow-sm" onPress={onPress}>
      <View>
        <Image
          source={{ uri: item.imageUrl || FALLBACK_IMAGE }}
          className="h-32 w-full rounded-lg"
          resizeMode="cover"
        />
        <View className="absolute right-[9px] top-[9px] h-[34px] w-[34px] items-center justify-center rounded-full bg-white/85">
          <Ionicons name="bookmark" size={21} color="#006E2F" />
        </View>
      </View>
      <View className="mt-[11px] flex-row items-center gap-2">
        <Text className="flex-1 text-lg font-extrabold text-[#191C1D]">
          {item.name}
        </Text>
        {item.personaBadges?.[0] && (
          <View className="rounded-full bg-secondary px-[9px] py-1">
            <Text className="text-[11px] font-extrabold text-primary">
              {item.personaBadges[0]}
            </Text>
          </View>
        )}
      </View>
      <View className="mt-[9px] flex-row items-center gap-1">
        <Ionicons name="git-branch-outline" size={17} color="#475569" />
        <Text className="mr-[9px] text-[13px] text-slate-600">
          {(item.distanceM / 1000).toFixed(1)}km
        </Text>
        {item.estimatedMinutes !== undefined && (
          <>
            <Ionicons name="timer-outline" size={17} color="#475569" />
            <Text className="text-[13px] text-slate-600">
              {item.estimatedMinutes}분
            </Text>
          </>
        )}
      </View>
    </Pressable>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery } from "@tanstack/react-query";
import {
  ActivityIndicator,
  FlatList,
  Modal,
  Pressable,
  View,
} from "react-native";

import { getComments } from "@/api/course-api";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Text } from "@/components/ui/text";

export function CourseCommentSheet({
  courseId,
  onClose,
}: {
  courseId: number | string | null;
  onClose: () => void;
}) {
  const numericCourseId = courseId === null ? null : Number(courseId);
  const commentsQuery = useInfiniteQuery({
    queryKey: ["course-comments", numericCourseId],
    queryFn: ({ pageParam }) =>
      getComments(numericCourseId!, { page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: numericCourseId !== null && Number.isFinite(numericCourseId),
    getNextPageParam: (lastPage) => {
      const loaded = (lastPage.page + 1) * lastPage.size;
      return loaded < lastPage.totalElements ? lastPage.page + 1 : undefined;
    },
  });
  const comments =
    commentsQuery.data?.pages.flatMap((page) => page.content) ?? [];

  return (
    <Modal
      visible={courseId !== null}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <Pressable className="flex-1 justify-end bg-black/40" onPress={onClose}>
        <Pressable
          className="h-[72%] rounded-t-[30px] bg-background pt-2.5"
          onPress={(event) => event.stopPropagation()}
        >
          <View className="mb-3 h-[5px] w-[42px] self-center rounded-full bg-slate-300" />
          <View className="flex-row items-center justify-between px-5 pb-3">
            <Text className="text-xl font-black text-foreground">댓글</Text>
            <Pressable
              accessibilityLabel="댓글 닫기"
              className="size-10 items-center justify-center rounded-full bg-muted"
              onPress={onClose}
            >
              <Ionicons name="close" size={22} color="#526056" />
            </Pressable>
          </View>
          <Separator />
          {commentsQuery.isPending ? (
            <View className="flex-1 items-center justify-center">
              <ActivityIndicator color="#087A3F" />
            </View>
          ) : commentsQuery.isError ? (
            <ErrorState
              message={commentsQuery.error.message}
              onRetry={() => void commentsQuery.refetch()}
            />
          ) : (
            <FlatList
              data={comments}
              keyExtractor={(item) => String(item.commentId)}
              contentContainerClassName="grow px-5 pb-8"
              ItemSeparatorComponent={() => (
                <Separator className="bg-[#EEF1EE]" />
              )}
              ListEmptyComponent={
                <EmptyState
                  title="아직 댓글이 없어요"
                  description="첫 댓글을 남겨보세요."
                />
              }
              renderItem={({ item }) => (
                <View className="py-4">
                  <View className="flex-row items-center justify-between">
                    <Text className="font-extrabold text-foreground">
                      {item.nickname}
                    </Text>
                    <Text className="text-xs text-muted-foreground">
                      {new Date(item.createdAt).toLocaleDateString("ko-KR")}
                    </Text>
                  </View>
                  <Text className="mt-2 text-sm leading-5 text-foreground">
                    {item.content}
                  </Text>
                  <View className="mt-2 flex-row items-center gap-1">
                    <Ionicons
                      name="thumbs-up-outline"
                      size={15}
                      color="#758078"
                    />
                    <Text className="text-xs text-muted-foreground">
                      {item.upvoteCount}
                    </Text>
                  </View>
                </View>
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
        </Pressable>
      </Pressable>
    </Modal>
  );
}

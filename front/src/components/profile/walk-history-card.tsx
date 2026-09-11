import { Ionicons } from "@expo/vector-icons";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  FlatList,
  Image,
  Modal,
  Pressable,
  Text,
  useWindowDimensions,
  View,
} from "react-native";

import { getMyWalks } from "@/api/walk-api";
import { getCourseDetail } from "@/api/course-api";
import { Button } from "@/components/ui/button";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import type { WalkRecord } from "@/types/domain";

const PERSONA_LABELS: Record<string, string> = {
  walker: "일반",
  dog: "반려견",
  senior: "시니어",
  stroller: "유모차",
};

const formatDistance = (meters: number) =>
  `${(meters / 1_000).toFixed(meters % 1_000 === 0 ? 0 : 1)} km`;

const formatDuration = (seconds: number | null) => {
  if (seconds == null) return "시간 미기록";
  const minutes = Math.max(1, Math.round(seconds / 60));
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder ? `${hours}시간 ${remainder}분` : `${hours}시간`;
};

const formatDate = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  }).format(date);
};

const formatCompactDate = (value: string) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const year = `${date.getFullYear() % 100}`.padStart(2, "0");
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  const weekday = ["일", "월", "화", "수", "목", "금", "토"][date.getDay()];
  return `${year}.${month}.${day} ${weekday}`;
};

function Stars({ rating }: { rating: number }) {
  return (
    <View className="flex-row gap-0.5" accessibilityLabel={`별점 ${rating}점`}>
      {Array.from({ length: 5 }, (_, index) => (
        <Ionicons
          key={index}
          name={index < rating ? "star" : "star-outline"}
          size={15}
          color={index < rating ? "#F59E0B" : "#94A3B8"}
        />
      ))}
    </View>
  );
}

function WalkRecordRow({
  record,
  persona,
  last,
  compact = false,
}: {
  record: WalkRecord;
  persona: string;
  last: boolean;
  compact?: boolean;
}) {
  return (
    <View className="flex-row px-1">
      <View className="w-9 items-center">
        <View className="z-10 h-8 w-8 items-center justify-center rounded-full bg-[#E9FBEF] dark:bg-[#24382B]">
          <Ionicons name="walk" size={17} color="#087A3F" />
        </View>
        {!last && (
          <View className="min-h-6 w-0.5 flex-1 bg-[#CFE3D2] dark:bg-[#38513F]" />
        )}
      </View>
      <View className={`ml-2 flex-1 ${last ? "pb-0" : "pb-4"}`}>
        {compact ? (
          <View className="min-h-12 justify-center pb-1">
            <Text
              numberOfLines={1}
              className="text-[15px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]"
            >
              {record.courseName}
            </Text>
            <Text className="mt-1 text-[11px] font-medium text-[#6B756D] dark:text-[#AAB5AD]">
              {formatCompactDate(record.walkedAt)}
            </Text>
          </View>
        ) : (
          <>
            <Text className="text-[11px] font-bold text-[#6B756D] dark:text-[#AAB5AD]">
              {formatDate(record.walkedAt)}
            </Text>
            <View className="mt-1.5 rounded-xl border border-[#E2EAE2] bg-[#F9FBF9] p-3 dark:border-[#343D36] dark:bg-[#242B26]">
              <Text
                numberOfLines={1}
                className="text-[15px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]"
              >
                {record.courseName}
              </Text>
              <View className="mt-2 flex-row flex-wrap items-center gap-1.5">
                <View className="rounded-full bg-[#E9FBEF] px-2 py-1 dark:bg-[#24382B]">
                  <Text className="text-[10px] font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
                    {PERSONA_LABELS[persona] ?? "일반"}
                  </Text>
                </View>
                <View className="flex-row items-center gap-1">
                  <Ionicons name="map-outline" size={13} color="#64748B" />
                  <Text className="text-[11px] font-bold text-slate-600 dark:text-[#AAB5AD]">
                    {formatDistance(record.distanceMeters)}
                  </Text>
                </View>
                <View className="flex-row items-center gap-1">
                  <Ionicons name="time-outline" size={13} color="#64748B" />
                  <Text className="text-[11px] font-bold text-slate-600 dark:text-[#AAB5AD]">
                    {formatDuration(record.durationSeconds)}
                  </Text>
                </View>
              </View>
              {record.rating != null && (
                <View className="mt-3 flex-row items-center justify-between rounded-lg bg-white px-3 py-2.5 dark:bg-[#1B211D]">
                  <View>
                    <Text className="text-[10px] font-semibold text-slate-500 dark:text-[#AAB5AD]">
                      내가 남긴 코스 별점
                    </Text>
                    <Text className="mt-0.5 text-xs font-extrabold text-[#26372D] dark:text-[#F1F5F2]">
                      {record.rating}.0
                    </Text>
                  </View>
                  <Stars rating={record.rating} />
                </View>
              )}
            </View>
          </>
        )}
      </View>
    </View>
  );
}

function WalkHistorySheet({
  open,
  persona,
  onClose,
}: {
  open: boolean;
  persona: string;
  onClose: () => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(translateY, windowHeight, onClose);
  useEffect(() => {
    if (!open) return;
    translateY.setValue(windowHeight);
    Animated.timing(translateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [open, translateY, windowHeight]);
  const historyQuery = useInfiniteQuery({
    queryKey: ["my-walks", "all"],
    queryFn: ({ pageParam }) => getMyWalks({ page: pageParam, size: 20 }),
    initialPageParam: 0,
    enabled: open,
    getNextPageParam: (lastPage) =>
      (lastPage.page + 1) * lastPage.size < lastPage.totalElements
        ? lastPage.page + 1
        : undefined,
  });
  const records =
    historyQuery.data?.pages.flatMap((page) => page.content) ?? [];

  return (
    <Modal
      visible={open}
      transparent
      animationType="none"
      onRequestClose={dismissSheet}
    >
      <View className="flex-1 justify-end">
        <Pressable
          accessibilityLabel="산책 기록 닫기"
          className="absolute inset-0 bg-black/40"
          onPress={dismissSheet}
        />
        <Animated.View
          className="h-[78%] rounded-t-[30px] bg-[#FCFDFC] pt-2.5 dark:bg-[#171C18]"
          style={{ transform: [{ translateY }] }}
        >
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={translateY}
            dismissDistance={windowHeight}
          />
          <View className="px-5 pb-3">
            <Text className="text-[20px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
              나의 산책 기록
            </Text>
            <Text className="mt-1 text-xs text-slate-500 dark:text-[#AAB5AD]">
              완료한 코스와 내가 남긴 별점을 확인해요
            </Text>
          </View>
          {historyQuery.isPending ? (
            <View className="flex-1 items-center justify-center gap-2">
              <ActivityIndicator color="#087A3F" />
              <Text className="text-xs text-slate-500 dark:text-[#AAB5AD]">
                산책 기록을 불러오는 중이에요
              </Text>
            </View>
          ) : historyQuery.isError ? (
            <View className="flex-1 items-center justify-center gap-3 px-6">
              <Ionicons
                name="cloud-offline-outline"
                size={30}
                color="#64748B"
              />
              <Text className="text-sm font-bold text-[#526056] dark:text-[#D4DDD6]">
                산책 기록을 불러올 수 없습니다
              </Text>
              <Button
                variant="secondary"
                className="h-10 rounded-full bg-[#E9FBEF] px-5 dark:bg-[#24382B]"
                onPress={() => void historyQuery.refetch()}
              >
                <Text className="text-xs font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
                  다시 시도
                </Text>
              </Button>
            </View>
          ) : (
            <FlatList
              data={records}
              keyExtractor={(record) => String(record.walkId)}
              contentContainerClassName="grow px-5 pb-10 pt-2"
              showsVerticalScrollIndicator
              renderItem={({ item, index }) => (
                <WalkRecordRow
                  record={item}
                  persona={persona}
                  last={index === records.length - 1}
                />
              )}
              ListEmptyComponent={
                <View className="flex-1 items-center justify-center py-20">
                  <Ionicons
                    name="footsteps-outline"
                    size={36}
                    color="#94A09A"
                  />
                  <Text className="mt-3 font-bold text-[#191C1D] dark:text-[#F1F5F2]">
                    아직 완료한 산책이 없어요
                  </Text>
                </View>
              }
              onEndReached={() => {
                if (
                  historyQuery.hasNextPage &&
                  !historyQuery.isFetchingNextPage
                )
                  void historyQuery.fetchNextPage();
              }}
              onEndReachedThreshold={0.5}
              ListFooterComponent={
                historyQuery.isFetchingNextPage ? (
                  <ActivityIndicator color="#087A3F" className="my-4" />
                ) : null
              }
            />
          )}
        </Animated.View>
      </View>
    </Modal>
  );
}

export function WalkHistoryCard({ persona }: { persona: string }) {
  const [sheetOpen, setSheetOpen] = useState(false);
  const recentQuery = useQuery({
    queryKey: ["my-walks", "recent"],
    queryFn: () => getMyWalks({ page: 0, size: 5 }),
  });
  const records = recentQuery.data?.content ?? [];
  const latestCourseId = records[0]?.courseId;
  const latestCourseQuery = useQuery({
    queryKey: ["course", latestCourseId],
    queryFn: () => getCourseDetail(latestCourseId!),
    enabled: latestCourseId != null,
  });
  const latestCourseImage =
    latestCourseQuery.data?.imageUrl ?? latestCourseQuery.data?.mapImageUrl;

  return (
    <>
      <View className="bg-white p-4 dark:bg-[#1B211D]">
        <View>
          <Text className="text-[17px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
            나의 산책 기록
          </Text>
        </View>

        {latestCourseImage ? (
          <Image
            source={{ uri: latestCourseImage }}
            accessibilityLabel={`${records[0]?.courseName ?? "최근 산책 코스"} 이미지`}
            resizeMode="cover"
            className="mt-4 w-full rounded-lg"
            style={{ aspectRatio: 8 / 3 }}
          />
        ) : null}

        {recentQuery.isPending ? (
          <View className="h-28 items-center justify-center">
            <ActivityIndicator color="#087A3F" />
          </View>
        ) : recentQuery.isError ? (
          <View className="items-center gap-2 py-7">
            <Text className="text-xs font-bold text-slate-600 dark:text-[#D4DDD6]">
              산책 기록을 불러올 수 없습니다
            </Text>
            <Pressable onPress={() => void recentQuery.refetch()}>
              <Text className="text-xs font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
                다시 시도
              </Text>
            </Pressable>
          </View>
        ) : records.length === 0 ? (
          <View className="items-center py-7">
            <Ionicons name="walk-outline" size={30} color="#94A09A" />
            <Text className="mt-2 text-xs font-bold text-slate-500 dark:text-[#AAB5AD]">
              산책을 완료하면 여기에 기록돼요
            </Text>
          </View>
        ) : (
          <View className="mt-4">
            {records.map((record, index) => (
              <WalkRecordRow
                key={record.walkId}
                record={record}
                persona={persona}
                last={index === records.length - 1}
                compact
              />
            ))}
          </View>
        )}
        {(recentQuery.data?.totalElements ?? 0) > 0 && (
          <Button
            className="mt-4 h-11 w-full rounded-lg bg-[#EEF6EB] dark:bg-[#2A312C]"
            onPress={() => setSheetOpen(true)}
          >
            <Text className="text-sm font-extrabold text-[#191C1D] dark:text-white">
              전체 보기
            </Text>
          </Button>
        )}
      </View>
      <WalkHistorySheet
        open={sheetOpen}
        persona={persona}
        onClose={() => setSheetOpen(false)}
      />
    </>
  );
}

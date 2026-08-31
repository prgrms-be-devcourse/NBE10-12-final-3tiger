import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import { router, useLocalSearchParams } from "expo-router";
import { useMemo } from "react";
import { ScrollView, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseDetail } from "@/api/course-api";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import { useThemeStore } from "@/stores/theme-store";

export default function CourseDetailScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const { id } = useLocalSearchParams<{ id: string }>();
  const courseId = Number(id);
  const detailQuery = useQuery({
    queryKey: ["course", courseId],
    queryFn: () => getCourseDetail(courseId),
    enabled: Number.isFinite(courseId),
  });
  const stats = useMemo(() => {
    const detail = detailQuery.data;
    return detail
      ? [
          [`${(detail.distanceM / 1000).toFixed(1)}km`, "거리"],
          [`${detail.estimatedMinutes ?? "-"}분`, "예상 시간"],
          [
            detail.scoreBars?.shade == null
              ? "-"
              : `${Math.round(detail.scoreBars.shade * 100)}%`,
            "그늘",
          ],
        ]
      : [];
  }, [detailQuery.data]);
  if (detailQuery.isPending)
    return <LoadingState label="코스 상세를 불러오는 중이에요" />;
  if (detailQuery.isError)
    return (
      <SafeAreaView className="flex-1">
        <ErrorState
          message={detailQuery.error.message}
          onRetry={() => void detailQuery.refetch()}
        />
      </SafeAreaView>
    );
  const detail = detailQuery.data;
  return (
    <SafeAreaView
      className="flex-1 bg-[#F4F8F4] dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-14 flex-row items-center justify-between bg-white px-5 dark:bg-[#1B211D]">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          onPress={() => router.back()}
        >
          <Ionicons
            name="arrow-back"
            size={23}
            color={isDark ? "#F1F5F2" : "#203126"}
          />
        </Button>
        <Text className="text-xl font-black text-[#087A3F]">코스 상세</Text>
        <View className="w-10" />
      </View>
      <ScrollView contentContainerClassName="p-5 pb-10">
        <View className="rounded-3xl bg-white p-5 dark:bg-[#1B211D]">
          <Text className="text-[11px] font-black text-[#087A3F]">
            추천 산책 코스
          </Text>
          <Text className="mt-2 text-2xl font-black text-[#18271D] dark:text-[#F1F5F2]">
            {detail.name}
          </Text>
          <Text className="mt-2 text-sm leading-6 text-[#6B756D] dark:text-[#AAB5AD]">
            {detail.summary ??
              detail.personaBadges?.join(" · ") ??
              "코스 환경과 상세 점수를 확인해 보세요."}
          </Text>
          <View className="mt-5 flex-row rounded-2xl bg-[#E9FBEF] py-4 dark:bg-[#24382B]">
            {stats.map(([value, label]) => (
              <View key={label} className="flex-1 items-center">
                <Text className="text-base font-black text-[#087A3F]">
                  {value}
                </Text>
                <Text className="mt-1 text-xs text-[#5F6B62] dark:text-[#AAB5AD]">
                  {label}
                </Text>
              </View>
            ))}
          </View>
          {detail.personaBadges && (
            <View className="mt-5 flex-row flex-wrap gap-2">
              {detail.personaBadges.map((badge) => (
                <View
                  key={badge}
                  className="rounded-full bg-[#E9FBEF] px-3 py-2 dark:bg-[#24382B]"
                >
                  <Text className="text-xs font-bold text-[#087A3F]">
                    {badge}
                  </Text>
                </View>
              ))}
            </View>
          )}
          <Button className="mt-6 h-14 rounded-2xl bg-[#087A3F] active:bg-[#066C38]">
            <Ionicons name="navigate" size={20} color="white" />
            <Text className="font-black text-white">안내 시작</Text>
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

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

export default function CourseDetailScreen() {
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
          [`${Math.round((detail.scores?.shadeSummer ?? 0) * 100)}%`, "그늘"],
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
    <SafeAreaView className="flex-1 bg-[#F4F8F4]" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between bg-white px-5">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          onPress={() => router.back()}
        >
          <Ionicons name="arrow-back" size={23} color="#203126" />
        </Button>
        <Text className="text-xl font-black text-[#087A3F]">코스 상세</Text>
        <View className="w-10" />
      </View>
      <ScrollView contentContainerClassName="p-5 pb-10">
        <View className="rounded-3xl bg-white p-5">
          <Text className="text-[11px] font-black text-primary">
            추천 산책 코스
          </Text>
          <Text className="mt-2 text-2xl font-black text-foreground">
            {detail.name}
          </Text>
          <Text className="mt-2 text-sm leading-6 text-muted-foreground">
            {detail.summary ??
              detail.personaBadges?.join(" · ") ??
              "코스 환경과 상세 점수를 확인해 보세요."}
          </Text>
          <View className="mt-5 flex-row rounded-2xl bg-secondary py-4">
            {stats.map(([value, label]) => (
              <View key={label} className="flex-1 items-center">
                <Text className="text-base font-black text-primary">
                  {value}
                </Text>
                <Text className="mt-1 text-xs text-muted-foreground">
                  {label}
                </Text>
              </View>
            ))}
          </View>
          {detail.personaBadges && (
            <View className="mt-5 flex-row flex-wrap gap-2">
              {detail.personaBadges.map((badge) => (
                <View key={badge} className="rounded-full bg-accent px-3 py-2">
                  <Text className="text-xs font-bold text-accent-foreground">
                    {badge}
                  </Text>
                </View>
              ))}
            </View>
          )}
          <Button className="mt-6 h-14 rounded-2xl">
            <Ionicons name="navigate" size={20} color="white" />
            <Text className="font-black text-primary-foreground">
              안내 시작
            </Text>
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

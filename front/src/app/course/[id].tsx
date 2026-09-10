import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import { router, useLocalSearchParams } from "expo-router";
import { useMemo } from "react";
import { Image, Platform, ScrollView, View } from "react-native";
import MapView, { PROVIDER_GOOGLE, Polyline, type Region } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseDetail } from "@/api/course-api";
import { resolveApiHostUrl } from "@/api/client";
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
    staleTime: 0,
    refetchOnMount: "always",
    refetchInterval: (query) => {
      const detail = query.state.data;
      if (!detail || detail.mapImageUrl || query.state.dataUpdateCount >= 8) {
        return false;
      }
      return 1_500;
    },
  });
  const pathCoords = useMemo(() => {
    const path = detailQuery.data?.path;
    if (!path) return [];
    const raw = Array.isArray(path) ? path : (path.coordinates ?? []);
    return raw
      .filter((p): p is [number, number] => Array.isArray(p) && p.length >= 2)
      .map(([lng, lat]) => ({ latitude: lat, longitude: lng }));
  }, [detailQuery.data?.path]);

  const mapRegion: Region | null = useMemo(() => {
    if (pathCoords.length === 0) return null;
    const lats = pathCoords.map((p) => p.latitude);
    const lngs = pathCoords.map((p) => p.longitude);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);
    return {
      latitude: (minLat + maxLat) / 2,
      longitude: (minLng + maxLng) / 2,
      latitudeDelta: Math.max((maxLat - minLat) * 1.4, 0.005),
      longitudeDelta: Math.max((maxLng - minLng) * 1.4, 0.005),
    };
  }, [pathCoords]);

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
          {detail.mapImageUrl ? (
            <Image
              source={{ uri: resolveApiHostUrl(detail.mapImageUrl) }}
              className="mb-5 h-[190px] w-full rounded-2xl bg-[#E5EBE5] dark:bg-[#303632]"
              resizeMode="cover"
              accessibilityLabel={`${detail.name} 코스 지도`}
            />
          ) : mapRegion ? (
            <View className="mb-5 h-[190px] w-full overflow-hidden rounded-2xl bg-[#E5EBE5] dark:bg-[#303632]">
              <MapView
                provider={Platform.OS === "android" ? PROVIDER_GOOGLE : undefined}
                style={{ flex: 1 }}
                initialRegion={mapRegion}
                scrollEnabled={false}
                zoomEnabled={false}
                pitchEnabled={false}
                rotateEnabled={false}
                toolbarEnabled={false}
              >
                <Polyline
                  coordinates={pathCoords}
                  strokeColor="#087A3F"
                  strokeWidth={5}
                />
              </MapView>
            </View>
          ) : null}
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
          {(() => {
            const bars = detail.scoreBars;
            const personaScore = detail.scoreWalker ?? detail.scoreSenior ?? detail.scoreStroller ?? detail.scoreDog;
            // 실측 raw score 분포가 낮은 편(대부분 0.2~0.5) → 1.5배 boost로 UX 자연스럽게. 100 상한.
            const toPointsBoosted = (v?: number | null) =>
              v == null ? "-" : `${Math.min(100, Math.round(v * 150))}점`;
            const toPoints = (v?: number | null) =>
              v == null ? "-" : `${Math.round(v * 100)}점`;
            const toPercent = (v?: number | null) =>
              v == null ? "-" : `${Math.round(v * 100)}%`;
            const toCategory = (v?: number | null) =>
              v == null ? "-" : v >= 0.7 ? "많음" : v >= 0.3 ? "보통" : "적음";
            const metrics: Array<[string, string]> = [
              ["추천 점수", toPointsBoosted(personaScore)],
              ["그늘", toPercent(bars?.shade)],
              ["평탄도", toPoints(bars?.flatness)],
              ["자연 노면", toPoints(bars?.surfaceNatural)],
              ["벤치", toCategory(bars?.benchDensity)],
              ["화장실", toCategory(bars?.restroomProximity)],
              ["음수대", toCategory(bars?.waterFacility)],
              ["포장 품질", toPoints(bars?.pavementQuality)],
            ];
            return (
              <View className="mt-4 flex-row flex-wrap">
                {metrics.map(([label, value]) => (
                  <View key={label} className="w-1/2 p-1">
                    <View className="flex-row items-center justify-between rounded-xl bg-[#F4F8F4] px-3 py-2.5 dark:bg-[#242B26]">
                      <Text className="text-xs text-[#5F6B62] dark:text-[#AAB5AD]">
                        {label}
                      </Text>
                      <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
                        {value}
                      </Text>
                    </View>
                  </View>
                ))}
              </View>
            );
          })()}
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
          <Button
            className="mt-6 h-14 rounded-2xl bg-[#087A3F] active:bg-[#066C38]"
            onPress={() =>
              router.push({
                pathname: "/course/navigation",
                params: { id: String(courseId) },
              })
            }
          >
            <Ionicons name="navigate" size={20} color="white" />
            <Text className="font-black text-white">안내 시작</Text>
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

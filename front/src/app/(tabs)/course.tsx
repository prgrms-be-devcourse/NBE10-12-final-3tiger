import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router } from "expo-router";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  Pressable,
  ScrollView,
  StyleSheet,
  useWindowDimensions,
  View,
} from "react-native";
import MapView, { Marker, Polyline } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  bookmarkCourse,
  getCourseDetail,
  getCourses,
  unbookmarkCourse,
} from "@/api/course-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { useAuthStore } from "@/stores/auth-store";

const DEFAULT_COORDS = { latitude: 37.5462, longitude: 127.0372 };

const PERSONA_FILTERS: Array<{ key: string | null; label: string }> = [
  { key: null, label: "전체" },
  { key: "walker", label: "일반" },
  { key: "dog", label: "반려견" },
  { key: "senior", label: "시니어" },
  { key: "stroller", label: "유모차" },
];

export default function CourseScreen() {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [coords, setCoords] = useState(DEFAULT_COORDS);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showDetails, setShowDetails] = useState(true);
  const [persona, setPersona] = useState<string | null>(null);
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissDetails = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, () =>
      setShowDetails(false),
    );
  useEffect(() => {
    void Location.getLastKnownPositionAsync().then((position) => {
      if (position)
        setCoords({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        });
    });
  }, []);
  const coursesQuery = useQuery({
    queryKey: ["courses", coords, persona],
    queryFn: () =>
      getCourses({
        lat: coords.latitude,
        lng: coords.longitude,
        radiusM: 5000,
        sort: "score",
        page: 0,
        size: 10,
        persona: persona ?? undefined,
      }),
  });
  const courses = coursesQuery.data?.content ?? [];
  useEffect(() => {
    setSelectedId(courses[0]?.courseId ?? null);
  }, [courses]);
  useEffect(() => {
    if (!showDetails) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [showDetails, sheetTranslateY, windowHeight]);
  const detailQuery = useQuery({
    queryKey: ["course", selectedId],
    queryFn: () => getCourseDetail(selectedId!),
    enabled: selectedId !== null,
  });
  const detail = detailQuery.data;
  const [isBookmarked, setIsBookmarked] = useState(false);
  useEffect(() => {
    setIsBookmarked(detail?.isBookmarked ?? false);
  }, [detail?.isBookmarked, selectedId]);
  const bookmarkMutation = useMutation({
    mutationFn: () =>
      isBookmarked
        ? unbookmarkCourse(selectedId!)
        : bookmarkCourse(selectedId!),
    onMutate: () => setIsBookmarked((value) => !value),
    onError: () => setIsBookmarked((value) => !value),
    onSuccess: (result) => setIsBookmarked(result.isBookmarked),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["course", selectedId] });
      void queryClient.invalidateQueries({ queryKey: ["bookmarks"] });
    },
  });
  const route = useMemo(() => {
    const path = detail?.path;
    const values = Array.isArray(path) ? path : path?.coordinates;
    return (
      values?.map(([lng, lat]) => ({ latitude: lat, longitude: lng })) ?? []
    );
  }, [detail]);

  if (coursesQuery.isError)
    return (
      <SafeAreaView className="flex-1">
        <ErrorState
          message={coursesQuery.error.message}
          onRetry={() => void coursesQuery.refetch()}
        />
      </SafeAreaView>
    );
  return (
    <View className="flex-1 bg-[#E8F0E5]">
      <MapView
        style={StyleSheet.absoluteFill}
        onPress={dismissDetails}
        region={{ ...coords, latitudeDelta: 0.014, longitudeDelta: 0.012 }}
      >
        {route.length > 1 && (
          <Polyline coordinates={route} strokeColor="#087A3F" strokeWidth={7} />
        )}
        {courses.map(
          (course) =>
            course.startPoint && (
              <Marker
                key={course.courseId}
                coordinate={{
                  latitude: course.startPoint.lat,
                  longitude: course.startPoint.lng,
                }}
                onPress={() => setSelectedId(course.courseId)}
                pinColor={
                  course.courseId === selectedId ? "#087A3F" : "#94A09A"
                }
              />
            ),
        )}
      </MapView>
      <SafeAreaView
        edges={["top"]}
        className="px-[18px]"
        pointerEvents="box-none"
      >
        <View className="mt-1 flex-row items-center justify-between">
          <Button
            variant="secondary"
            size="icon"
            accessibilityLabel="뒤로 가기"
            className="h-12 w-12 rounded-[17px] bg-white"
            onPress={() => router.back()}
          >
            <Ionicons name="arrow-back" size={23} color="#203126" />
          </Button>
          <Text className="rounded-2xl bg-white px-[18px] py-[13px] text-lg font-black text-[#1A2B20]">
            추천 코스
          </Text>
          <View className="h-12 w-12" />
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 pt-3"
        >
          {PERSONA_FILTERS.map((filter) => {
            const active = persona === filter.key;
            return (
              <Pressable
                key={filter.label}
                className={`h-9 justify-center rounded-full px-3 ${active ? "bg-[#087A3F]" : "bg-white"}`}
                onPress={() => setPersona(filter.key)}
              >
                <Text
                  className={`text-xs font-extrabold ${active ? "text-white" : "text-[#536158]"}`}
                >
                  {filter.label}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 pt-2"
        >
          {courses.map((course) => (
            <Pressable
              key={course.courseId}
              className={`h-10 justify-center rounded-full px-[15px] ${course.courseId === selectedId ? "bg-[#087A3F]" : "bg-white"}`}
              onPress={() => setSelectedId(course.courseId)}
            >
              <Text
                className={`text-[13px] font-extrabold ${course.courseId === selectedId ? "text-white" : "text-[#536158]"}`}
              >
                {course.name}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
      </SafeAreaView>
      {showDetails && (
        <Animated.View
          className="absolute inset-x-0 bottom-0 h-[68%] rounded-t-[30px] bg-white px-5 pb-[22px] pt-2.5 shadow-2xl"
          style={{ transform: [{ translateY: sheetTranslateY }] }}
        >
          <BottomSheetHandle
            onDismiss={() => setShowDetails(false)}
            translateY={sheetTranslateY}
            dismissDistance={windowHeight}
          />
          <ScrollView
            className="flex-1"
            contentContainerClassName="px-0 pb-2"
            nestedScrollEnabled
            showsVerticalScrollIndicator
          >
            {coursesQuery.isPending || detailQuery.isPending ? (
              <ActivityIndicator color="#087A3F" className="my-12" />
            ) : detail ? (
              <>
                <View className="flex-row items-center">
                  <View className="flex-1">
                    <Text className="text-[11px] font-black text-[#087A3F]">
                      현재 위치 추천 코스
                    </Text>
                    <Text className="mt-1 text-[22px] font-black text-[#18271D]">
                      {detail.name}
                    </Text>
                  </View>
                  <Button
                    variant="secondary"
                    size="icon"
                    accessibilityLabel="코스 저장"
                    className="rounded-2xl"
                    disabled={bookmarkMutation.isPending}
                    onPress={() => {
                      if (!isAuthenticated) {
                        setLoginRequiredOpen(true);
                        return;
                      }
                      if (selectedId !== null && !bookmarkMutation.isPending)
                        bookmarkMutation.mutate();
                    }}
                  >
                    <Ionicons
                      name={isBookmarked ? "bookmark" : "bookmark-outline"}
                      size={23}
                      color="#087A3F"
                    />
                  </Button>
                </View>
                <Text className="mt-1.5 text-[13px] text-[#78837B]">
                  {(detail.distanceM / 1000).toFixed(1)}km · 약{" "}
                  {detail.estimatedMinutes ?? "-"}분{" "}
                  {detail.isLoop ? "· 순환 코스" : ""}
                </Text>
                <View className="mt-[15px] flex-row rounded-[18px] bg-[#F2F8F2] py-3">
                  {[
                    [
                      `${Math.round((detail.scores?.shadeSummer ?? 0) * 100)}%`,
                      "그늘",
                    ],
                    [`${detail.scores?.avgSlopeDegree ?? "-"}°`, "평균 경사"],
                    [detail.scores?.surfaceType ?? "-", "노면"],
                  ].map(([value, label]) => (
                    <View key={label} className="flex-1 items-center">
                      <Text className="text-sm font-black text-[#25352B]">
                        {value}
                      </Text>
                      <Text className="mt-0.5 text-[10px] text-slate-500">
                        {label}
                      </Text>
                    </View>
                  ))}
                </View>
                <Button
                  className="mt-[15px] h-14 rounded-[18px]"
                  onPress={() =>
                    router.push(`/course/${detail.courseId}` as never)
                  }
                >
                  <Ionicons
                    name="information-circle-outline"
                    size={20}
                    color="white"
                  />
                  <Text className="text-base font-black text-white">
                    코스 상세 보기
                  </Text>
                </Button>
              </>
            ) : (
              <Text className="py-12 text-center text-muted-foreground">
                주변 추천 코스가 없어요.
              </Text>
            )}
          </ScrollView>
        </Animated.View>
      )}
      {!showDetails && (
        <SafeAreaView
          edges={["bottom"]}
          className="absolute inset-x-0 bottom-5 items-center"
          pointerEvents="box-none"
        >
          <Button
            className="h-14 flex-row gap-2 rounded-full px-6 shadow-lg"
            onPress={() => setShowDetails(true)}
          >
            <Ionicons name="chevron-up" size={20} color="white" />
            <Text className="text-[15px] font-black text-white">
              코스 정보 보기
            </Text>
          </Button>
        </SafeAreaView>
      )}
      <LoginRequiredModal
        visible={loginRequiredOpen}
        onClose={() => setLoginRequiredOpen(false)}
      />
    </View>
  );
}

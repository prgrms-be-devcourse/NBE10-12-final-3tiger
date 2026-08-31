import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useLocalSearchParams } from "expo-router";
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
import { getMyProfile } from "@/api/user-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { Course } from "@/types/domain";

const DEFAULT_COORDS = { latitude: 37.5462, longitude: 127.0372 };

const PERSONA_FILTERS: Array<{ key: string | null; label: string }> = [
  { key: null, label: "전체" },
  { key: "walker", label: "일반" },
  { key: "dog", label: "반려견" },
  { key: "senior", label: "시니어" },
  { key: "stroller", label: "유모차" },
];

const getCourseCenter = (course: Course) => {
  const path = course.path;
  const coordinates = Array.isArray(path) ? path : path?.coordinates;
  const validCoordinates = coordinates?.filter(
    ([longitude, latitude]) =>
      Number.isFinite(latitude) && Number.isFinite(longitude),
  );

  if (validCoordinates?.length) {
    const latitudes = validCoordinates.map(([, latitude]) => latitude);
    const longitudes = validCoordinates.map(([longitude]) => longitude);
    return {
      latitude: (Math.min(...latitudes) + Math.max(...latitudes)) / 2,
      longitude: (Math.min(...longitudes) + Math.max(...longitudes)) / 2,
    };
  }

  return course.startPoint
    ? { latitude: course.startPoint.lat, longitude: course.startPoint.lng }
    : null;
};

export default function CourseScreen() {
  const { regionCode, regionName, lat, lng } = useLocalSearchParams<{
    regionCode?: string;
    regionName?: string;
    lat?: string;
    lng?: string;
  }>();
  const serviceCoords = useMemo(() => {
    const latitude = Number(lat);
    const longitude = Number(lng);
    if (
      !regionCode ||
      !Number.isFinite(latitude) ||
      !Number.isFinite(longitude)
    )
      return null;
    return { latitude, longitude };
  }, [lat, lng, regionCode]);
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [coords, setCoords] = useState(serviceCoords ?? DEFAULT_COORDS);
  const [mapCenter, setMapCenter] = useState(serviceCoords ?? DEFAULT_COORDS);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showDetails, setShowDetails] = useState(true);
  const [persona, setPersona] = useState<string | null | undefined>(undefined);
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissDetails = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, () =>
      setShowDetails(false),
    );
  useEffect(() => {
    if (serviceCoords) {
      setCoords(serviceCoords);
      return;
    }

    const loadLastLocation = async () => {
      try {
        const permission = await Location.getForegroundPermissionsAsync();
        if (permission.status !== Location.PermissionStatus.GRANTED) return;

        const position = await Location.getLastKnownPositionAsync();
        if (position) {
          setCoords({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
          });
        }
      } catch {
        // Keep the default coordinates when the saved location is unavailable.
      }
    };

    void loadLastLocation();
  }, [serviceCoords]);
  useEffect(() => {
    setMapCenter(coords);
  }, [coords]);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const preferredPersona = isAuthenticated
    ? (profileQuery.data?.primaryPersona ?? null)
    : null;
  const effectivePersona = persona === undefined ? preferredPersona : persona;
  const coursesQuery = useQuery({
    queryKey: ["courses", regionCode, coords, effectivePersona],
    queryFn: () =>
      getCourses({
        ...(regionCode
          ? { regionCode }
          : {
              lat: coords.latitude,
              lng: coords.longitude,
              radiusM: 5000,
            }),
        sort: "score",
        page: 0,
        size: 10,
        persona: effectivePersona ?? undefined,
      }),
    enabled: !isAuthenticated || !profileQuery.isPending,
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
  const personaScore = detail
    ? effectivePersona === "walker"
      ? detail.scoreWalker
      : effectivePersona === "senior"
        ? detail.scoreSenior
        : effectivePersona === "stroller"
          ? detail.scoreStroller
          : effectivePersona === "dog"
            ? detail.scoreDog
            : null
    : null;
  const personaScoreLabel =
    PERSONA_FILTERS.find((filter) => filter.key === effectivePersona)?.label ??
    "페르소나";
  const [isBookmarked, setIsBookmarked] = useState(false);
  const [bookmarkError, setBookmarkError] = useState<string | null>(null);
  useEffect(() => {
    setIsBookmarked(detail?.isBookmarked ?? false);
    setBookmarkError(null);
  }, [detail?.isBookmarked, selectedId]);
  const bookmarkMutation = useMutation({
    mutationFn: ({
      courseId,
      desiredBookmarked,
    }: {
      courseId: number;
      desiredBookmarked: boolean;
    }) =>
      desiredBookmarked ? bookmarkCourse(courseId) : unbookmarkCourse(courseId),
    onMutate: ({ desiredBookmarked }) => {
      const previous = isBookmarked;
      setBookmarkError(null);
      setIsBookmarked(desiredBookmarked);
      return previous;
    },
    onError: (error: Error, _variables, previous) => {
      if (previous !== undefined) setIsBookmarked(previous);
      setBookmarkError(error.message);
    },
    onSuccess: (result, { courseId }) => {
      setIsBookmarked(result.isBookmarked);
      queryClient.setQueryData<Course>(["course", courseId], (current) =>
        current ? { ...current, isBookmarked: result.isBookmarked } : current,
      );
    },
    onSettled: (_result, _error, { courseId }) => {
      void queryClient.invalidateQueries({ queryKey: ["course", courseId] });
      void queryClient.invalidateQueries({
        queryKey: ["bookmarks"],
        refetchType: "all",
      });
    },
  });
  const route = useMemo(() => {
    const path = detail?.path;
    const values = Array.isArray(path) ? path : path?.coordinates;
    return (
      values?.map(([lng, lat]) => ({ latitude: lat, longitude: lng })) ?? []
    );
  }, [detail]);

  useEffect(() => {
    if (!detail) return;
    const center = getCourseCenter(detail);
    if (center) setMapCenter(center);
  }, [detail]);

  const selectCourse = (course: Course) => {
    setSelectedId(course.courseId);
    const center = getCourseCenter(course);
    if (center) setMapCenter(center);
  };

  if (coursesQuery.isError)
    return (
      <SafeAreaView
        className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]"
        edges={["top"]}
      >
        <ErrorState
          message={coursesQuery.error.message}
          onRetry={() => void coursesQuery.refetch()}
          appearance="light"
          className="bg-[#F2F7F2] dark:bg-[#111411]"
        />
      </SafeAreaView>
    );
  return (
    <View className="flex-1 bg-[#E8F0E5] dark:bg-[#111411]">
      <MapView
        style={StyleSheet.absoluteFill}
        onPress={dismissDetails}
        region={{ ...mapCenter, latitudeDelta: 0.014, longitudeDelta: 0.012 }}
        userInterfaceStyle={isDark ? "dark" : "light"}
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
                onPress={() => selectCourse(course)}
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
            className="h-12 w-12 rounded-[17px] bg-white dark:bg-[#1B211D]"
            onPress={() => router.back()}
          >
            <Ionicons
              name="arrow-back"
              size={23}
              color={isDark ? "#F1F5F2" : "#203126"}
            />
          </Button>
          <Text className="rounded-2xl bg-white px-[18px] py-[13px] text-lg font-black text-[#1A2B20] dark:bg-[#1B211D] dark:text-[#F1F5F2]">
            {regionName ? `${regionName} 추천 코스` : "추천 코스"}
          </Text>
          <View className="h-12 w-12" />
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 pt-3"
        >
          {PERSONA_FILTERS.map((filter) => {
            const active = effectivePersona === filter.key;
            return (
              <Pressable
                key={filter.label}
                className={`h-9 justify-center rounded-full px-3 ${active ? "bg-[#087A3F]" : "bg-white dark:bg-[#1B211D]"}`}
                onPress={() => setPersona(filter.key)}
              >
                <Text
                  className={`text-xs font-extrabold ${active ? "text-white" : "text-[#536158] dark:text-[#AAB5AD]"}`}
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
              className={`h-10 justify-center rounded-full px-[15px] ${course.courseId === selectedId ? "bg-[#087A3F]" : "bg-white dark:bg-[#1B211D]"}`}
              onPress={() => selectCourse(course)}
            >
              <Text
                className={`text-[13px] font-extrabold ${course.courseId === selectedId ? "text-white" : "text-[#536158] dark:text-[#AAB5AD]"}`}
              >
                {course.name}
              </Text>
            </Pressable>
          ))}
        </ScrollView>
      </SafeAreaView>
      {showDetails && (
        <Animated.View
          className="absolute inset-x-0 bottom-0 h-[36%] rounded-t-[30px] bg-white px-5 pb-[22px] pt-2.5 shadow-2xl dark:bg-[#1B211D]"
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
                    <Text className="mt-1 text-[22px] font-black text-[#18271D] dark:text-[#F1F5F2]">
                      {detail.name}
                    </Text>
                  </View>
                  <Button
                    variant="secondary"
                    size="icon"
                    accessibilityLabel={
                      isBookmarked ? "코스 저장 해제" : "코스 저장"
                    }
                    className={`rounded-2xl ${isBookmarked ? "bg-[#087A3F] active:bg-[#066C38]" : "bg-[#E9FBEF] active:bg-[#D8F3E0]"}`}
                    disabled={bookmarkMutation.isPending}
                    onPress={() => {
                      if (!isAuthenticated) {
                        setLoginRequiredOpen(true);
                        return;
                      }
                      if (selectedId !== null && !bookmarkMutation.isPending)
                        bookmarkMutation.mutate({
                          courseId: selectedId,
                          desiredBookmarked: !isBookmarked,
                        });
                    }}
                  >
                    <Ionicons
                      name={isBookmarked ? "bookmark" : "bookmark-outline"}
                      size={23}
                      color={isBookmarked ? "white" : "#087A3F"}
                    />
                  </Button>
                </View>
                <Text className="mt-1.5 text-[13px] text-[#78837B] dark:text-[#AAB5AD]">
                  {(detail.distanceM / 1000).toFixed(1)}km · 약{" "}
                  {detail.estimatedMinutes ?? "-"}분{" "}
                  {detail.isLoop ? "· 순환 코스" : ""}
                </Text>
                {bookmarkError && (
                  <Text
                    accessibilityLiveRegion="polite"
                    className="mt-2 text-xs font-bold text-[#B91C1C]"
                  >
                    {bookmarkError}
                  </Text>
                )}
                <View className="mt-[15px] flex-row rounded-[18px] bg-[#F2F8F2] py-3 dark:bg-[#242B26]">
                  {[
                    [
                      detail.scoreBars?.shade == null
                        ? "-"
                        : `${Math.round(detail.scoreBars.shade * 100)}%`,
                      "그늘",
                    ],
                    [
                      detail.scoreBars?.avgSlopeDegree == null
                        ? "-"
                        : `${detail.scoreBars.avgSlopeDegree.toFixed(2)}°`,
                      "평균 경사",
                    ],
                    [
                      personaScore == null
                        ? "-"
                        : `${Math.round(personaScore * 100)}점`,
                      `${personaScoreLabel} 점수`,
                    ],
                  ].map(([value, label]) => (
                    <View key={label} className="flex-1 items-center">
                      <Text className="text-sm font-black text-[#25352B] dark:text-[#F1F5F2]">
                        {value}
                      </Text>
                      <Text className="mt-0.5 text-[10px] text-slate-500 dark:text-[#AAB5AD]">
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

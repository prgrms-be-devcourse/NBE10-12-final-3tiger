import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Animated,
  Easing,
  Pressable,
  ScrollView,
  StyleSheet,
  useWindowDimensions,
  View,
} from "react-native";
import MapView, {
  Callout,
  Circle,
  Marker,
  type LatLng,
  type LongPressEvent,
} from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  bookmarkCourse,
  getCourseDetail,
  getCourses,
  unbookmarkCourse,
} from "@/api/course-api";
import { getGridOverlays } from "@/api/grid-api";
import {
  confirmHazard,
  createHazard,
  deleteMyHazardReport,
  getActiveHazards,
  resolveHazard,
} from "@/api/hazard-api";
import { getMyProfile } from "@/api/user-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { HazardDeleteConfirmModal } from "@/components/hazard/hazard-delete-confirm-modal";
import { HazardReportSheet } from "@/components/hazard/hazard-report-sheet";
import { Button } from "@/components/ui/button";
import { CourseRouteOverlay } from "@/components/map/course-route-overlay";
import { ErrorState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type {
  Course,
  GridOverlay,
  Hazard,
  HazardCreateRequest,
} from "@/types/domain";

const DEFAULT_COORDS = { latitude: 37.5462, longitude: 127.0372 };
const COURSE_MAP_VIEW = {
  latitudeDelta: 0.014,
  longitudeDelta: 0.012,
} as const;

const getRouteMapRegion = (
  route: LatLng[],
  distanceM: number | undefined,
  fallback: LatLng,
) => {
  if (route.length < 2) return { ...fallback, ...COURSE_MAP_VIEW };

  const bounds = route.reduce(
    (result, point) => ({
      minLatitude: Math.min(result.minLatitude, point.latitude),
      maxLatitude: Math.max(result.maxLatitude, point.latitude),
      minLongitude: Math.min(result.minLongitude, point.longitude),
      maxLongitude: Math.max(result.maxLongitude, point.longitude),
    }),
    {
      minLatitude: route[0].latitude,
      maxLatitude: route[0].latitude,
      minLongitude: route[0].longitude,
      maxLongitude: route[0].longitude,
    },
  );
  const distance = distanceM ?? 0;
  const minimumLatitudeDelta =
    distance <= 1000
      ? 0.006
      : distance <= 3000
        ? 0.012
        : distance <= 5000
          ? 0.02
          : 0.035;
  const padding = distance <= 1000 ? 1.18 : distance <= 3000 ? 1.14 : 1.1;

  return {
    latitude: (bounds.minLatitude + bounds.maxLatitude) / 2,
    longitude: (bounds.minLongitude + bounds.maxLongitude) / 2,
    latitudeDelta: Math.max(
      (bounds.maxLatitude - bounds.minLatitude) * padding,
      minimumLatitudeDelta,
    ),
    longitudeDelta: Math.max(
      (bounds.maxLongitude - bounds.minLongitude) * padding,
      minimumLatitudeDelta * 0.86,
    ),
  };
};

type GridLayer = "shade" | "flatness" | "amenity";

const GRID_LAYERS: Array<{
  key: GridLayer | null;
  label: string;
  icon: React.ComponentProps<typeof Ionicons>["name"];
}> = [
  { key: null, label: "끄기", icon: "eye-off-outline" },
  { key: "shade", label: "그늘", icon: "leaf-outline" },
  { key: "flatness", label: "평탄도", icon: "trail-sign-outline" },
  { key: "amenity", label: "편의시설", icon: "water-outline" },
];

const toGridBbox = (center: typeof DEFAULT_COORDS) =>
  [
    center.longitude - COURSE_MAP_VIEW.longitudeDelta / 2,
    center.latitude - COURSE_MAP_VIEW.latitudeDelta / 2,
    center.longitude + COURSE_MAP_VIEW.longitudeDelta / 2,
    center.latitude + COURSE_MAP_VIEW.latitudeDelta / 2,
  ]
    .map((value) => value.toFixed(6))
    .join(",");

const average = (values: Array<number | null>) => {
  const available = values.filter((value): value is number => value !== null);
  return available.length
    ? available.reduce((sum, value) => sum + value, 0) / available.length
    : null;
};

const getLayerScore = (grid: GridOverlay, layer: GridLayer) => {
  if (layer === "flatness") return grid.flatness;
  if (layer === "shade") return grid.shadeNow;
  return average([
    grid.benchDensity,
    grid.restroomProximity,
    grid.waterFacility,
  ]);
};

const getScoreColor = (score: number) => {
  if (score >= 0.67) return "rgba(34, 197, 94, 0.38)";
  if (score >= 0.34) return "rgba(234, 179, 8, 0.34)";
  return "rgba(249, 115, 22, 0.32)";
};

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
  const mapRef = useRef<MapView>(null);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [coords, setCoords] = useState(serviceCoords ?? DEFAULT_COORDS);
  const [mapCenter, setMapCenter] = useState(serviceCoords ?? DEFAULT_COORDS);
  const [mapHeading, setMapHeading] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showDetails, setShowDetails] = useState(true);
  const [persona, setPersona] = useState<string | null | undefined>(undefined);
  const [gridLayer, setGridLayer] = useState<GridLayer | null>(null);
  const [hazardCoordinate, setHazardCoordinate] = useState<LatLng | null>(null);
  const [hazardSheetOpen, setHazardSheetOpen] = useState(false);
  const [selectedHazard, setSelectedHazard] = useState<Hazard | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [hazardNotice, setHazardNotice] = useState<string | null>(null);
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
  const gridBbox = useMemo(() => toGridBbox(mapCenter), [mapCenter]);
  const currentHour = useMemo(() => new Date().getHours(), []);
  const gridsQuery = useQuery({
    queryKey: ["grid-overlays", gridBbox, currentHour],
    queryFn: () => getGridOverlays(gridBbox, currentHour),
    enabled: gridLayer !== null,
    staleTime: 60_000,
  });
  useEffect(() => {
    setSelectedId((current) =>
      current !== null && courses.some((course) => course.courseId === current)
        ? current
        : (courses[0]?.courseId ?? null),
    );
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
  const hazardsQuery = useQuery({
    queryKey: ["hazards", selectedId],
    queryFn: () => getActiveHazards(selectedId!),
    enabled: selectedId !== null,
  });
  useFocusEffect(
    useCallback(() => {
      if (selectedId !== null) {
        void queryClient.refetchQueries({
          queryKey: ["hazards", selectedId],
          exact: true,
        });
      }
    }, [queryClient, selectedId]),
  );
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
  const hazardCreateMutation = useMutation({
    mutationFn: ({
      courseId,
      request,
    }: {
      courseId: number;
      request: HazardCreateRequest;
    }) => createHazard(courseId, request),
    onSuccess: async (_result, { courseId }) => {
      setHazardSheetOpen(false);
      setHazardCoordinate(null);
      setShowDetails(true);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["hazards", courseId] }),
        queryClient.invalidateQueries({ queryKey: ["my-profile"] }),
      ]);
      setHazardNotice(
        "위험 신고가 접수되었습니다. 3명의 신고가 모이면 지도에 표시됩니다.",
      );
    },
  });
  const hazardConfirmationMutation = useMutation({
    mutationFn: ({ hazardId }: { courseId: number; hazardId: number }) =>
      confirmHazard(hazardId),
    onSuccess: (result, { courseId, hazardId }) => {
      queryClient.setQueryData<Hazard[]>(
        ["hazards", courseId],
        (current) =>
          current?.map((hazard) =>
            hazard.hazardId === hazardId
              ? { ...hazard, confirmationCount: result.confirmationCount }
              : hazard,
          ) ?? current,
      );
      void queryClient.invalidateQueries({
        queryKey: ["hazards", courseId],
      });
      setSelectedHazard((current) =>
        current?.hazardId === hazardId
          ? { ...current, confirmationCount: result.confirmationCount }
          : current,
      );
      setHazardNotice("아직 존재하는 위험으로 확인했습니다.");
    },
    onError: (error: Error) => {
      Alert.alert("위험을 확인할 수 없어요", error.message);
    },
  });
  const hazardResolutionMutation = useMutation({
    mutationFn: ({ hazardId }: { courseId: number; hazardId: number }) =>
      resolveHazard(hazardId),
    onSuccess: async (result, { courseId }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["hazards", courseId] }),
        queryClient.invalidateQueries({ queryKey: ["my-profile"] }),
      ]);
      setSelectedHazard(null);
      setHazardNotice(
        result.resolved
          ? "위험이 해결된 것으로 확인되어 지도에서 제거되었습니다."
          : `해결 확인이 반영되었습니다. (${result.resolutionCount}/3)`,
      );
    },
    onError: (error: Error) => Alert.alert("해결 확인 실패", error.message),
  });
  const hazardDeleteMutation = useMutation({
    mutationFn: ({ hazardId }: { courseId: number; hazardId: number }) =>
      deleteMyHazardReport(hazardId),
    onSuccess: async (_result, { courseId }) => {
      setDeleteConfirmOpen(false);
      setSelectedHazard(null);
      await queryClient.invalidateQueries({ queryKey: ["hazards", courseId] });
      setHazardNotice("내 위험 신고를 취소했습니다.");
    },
    onError: (error: Error) => Alert.alert("신고 취소 실패", error.message),
  });
  const route = useMemo(() => {
    const path = detail?.path;
    const values = Array.isArray(path) ? path : path?.coordinates;
    return (
      values?.map(([lng, lat]) => ({ latitude: lat, longitude: lng })) ?? []
    );
  }, [detail]);
  const courseMapRegion = useMemo(
    () => getRouteMapRegion(route, detail?.distanceM, mapCenter),
    [detail?.distanceM, mapCenter, route],
  );

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

  const handleMapLongPress = (event: LongPressEvent) => {
    if (selectedId === null) {
      Alert.alert("코스를 선택해 주세요", "신고할 코스를 먼저 선택해 주세요.");
      return;
    }
    hazardCreateMutation.reset();
    setHazardCoordinate(event.nativeEvent.coordinate);
    setSelectedHazard(null);
    setHazardSheetOpen(false);
    setShowDetails(false);
  };

  const clearHazardSelection = () => {
    if (hazardCreateMutation.isPending) return;
    hazardCreateMutation.reset();
    setHazardSheetOpen(false);
    setHazardCoordinate(null);
    setShowDetails(true);
  };

  const openHazardReportSheet = () => {
    if (!isAuthenticated) {
      setLoginRequiredOpen(true);
      return;
    }
    if (selectedId === null || hazardCoordinate === null) return;
    hazardCreateMutation.reset();
    setHazardSheetOpen(true);
  };

  const submitHazardReport = (request: HazardCreateRequest) => {
    if (selectedId === null || hazardCreateMutation.isPending) return;
    hazardCreateMutation.mutate({ courseId: selectedId, request });
  };

  const handleHazardConfirmation = (hazardId: number) => {
    if (!isAuthenticated) {
      setLoginRequiredOpen(true);
      return;
    }
    if (selectedId === null || hazardConfirmationMutation.isPending) return;
    hazardConfirmationMutation.mutate({ courseId: selectedId, hazardId });
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
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        onPress={dismissDetails}
        onLongPress={handleMapLongPress}
        region={courseMapRegion}
        userInterfaceStyle={isDark ? "dark" : "light"}
        onRegionChangeComplete={() => {
          void mapRef.current
            ?.getCamera()
            .then((camera) => setMapHeading(camera.heading ?? 0))
            .catch(() => undefined);
        }}
      >
        {gridLayer !== null &&
          gridsQuery.data?.map((grid) => {
            const score = getLayerScore(grid, gridLayer);
            return score == null ? null : (
              <Circle
                key={grid.gridId}
                center={{
                  latitude: grid.centroidLat,
                  longitude: grid.centroidLng,
                }}
                radius={42}
                fillColor={getScoreColor(score)}
                strokeColor={getScoreColor(Math.min(1, score + 0.12))}
                strokeWidth={1}
                zIndex={1}
              />
            );
          })}
        {route.length > 1 && (
          <CourseRouteOverlay
            key={`course-route-${selectedId ?? "none"}`}
            coordinates={route}
            mapHeading={mapHeading}
          />
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
        {hazardsQuery.data?.map((hazard) => (
          <Marker
            key={`hazard-${hazard.hazardId}`}
            coordinate={{
              latitude: hazard.latitude,
              longitude: hazard.longitude,
            }}
            anchor={{ x: 0.5, y: 0.5 }}
            zIndex={4}
            onPress={() => {
              setSelectedHazard(hazard);
              setDeleteConfirmOpen(false);
              setHazardCoordinate(null);
              setShowDetails(false);
            }}
          >
            <View
              style={[
                styles.hazardMarker,
                hazard.status === "PENDING" && styles.pendingHazardMarker,
              ]}
            >
              <Ionicons
                name={hazard.status === "PENDING" ? "time" : "warning"}
                size={18}
                color="white"
              />
            </View>
            <Callout
              tooltip
              accessibilityLabel={`${hazard.hazardType} 위험 정보`}
            >
              <View
                style={[
                  styles.hazardCallout,
                  { backgroundColor: isDark ? "#1B211D" : "#FFFFFF" },
                ]}
              >
                <Text
                  className={`text-[11px] font-black ${
                    hazard.status === "PENDING"
                      ? "text-[#D97706] dark:text-[#FCD34D]"
                      : "text-[#DC2626] dark:text-[#FCA5A5]"
                  }`}
                >
                  {hazard.status === "PENDING" ? "검증 대기 중" : "활성 위험"}
                </Text>
                <Text className="mt-1 text-base font-black text-[#191C1D] dark:text-[#F1F5F2]">
                  {hazard.hazardType}
                </Text>
                <Text className="mt-1 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                  {hazard.status === "PENDING"
                    ? `신고 ${hazard.reportCount}/3명`
                    : `위험 확인 ${hazard.confirmationCount}회`}
                </Text>
                <Text className="mt-2 text-[11px] font-bold text-[#087A3F]">
                  아래 카드에서 상태를 알려주세요
                </Text>
              </View>
            </Callout>
          </Marker>
        ))}
        {hazardCoordinate && (
          <Marker
            coordinate={hazardCoordinate}
            pinColor="#F59E0B"
            title="선택한 신고 위치"
            zIndex={5}
          />
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
        {hazardsQuery.isError && (
          <Text className="mt-2 rounded-xl bg-white/90 px-3 py-2 text-xs font-bold text-[#B91C1C] dark:bg-[#1B211D]/90 dark:text-[#FCA5A5]">
            위험 정보를 불러오지 못했어요
          </Text>
        )}
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
          contentContainerClassName="items-center gap-2 pt-2"
        >
          <View className="h-9 flex-row items-center gap-1 rounded-full bg-white px-3 dark:bg-[#1B211D]">
            <Ionicons name="layers-outline" size={15} color="#087A3F" />
            <Text className="text-xs font-extrabold text-[#24372A] dark:text-[#D4DDD6]">
              환경
            </Text>
          </View>
          {GRID_LAYERS.map((layer) => {
            const active = gridLayer === layer.key;
            return (
              <Button
                key={layer.label}
                variant="secondary"
                size="sm"
                accessibilityLabel={`${layer.label} 환경 레이어`}
                accessibilityState={{ selected: active }}
                className={`h-9 rounded-full px-3 ${active ? "bg-[#087A3F]" : "bg-white dark:bg-[#1B211D]"}`}
                onPress={() => setGridLayer(layer.key)}
              >
                <Ionicons
                  name={layer.icon}
                  size={15}
                  color={active ? "white" : "#087A3F"}
                />
                <Text
                  className={`text-xs font-extrabold ${active ? "text-white" : "text-[#24372A] dark:text-[#D4DDD6]"}`}
                >
                  {layer.label}
                </Text>
              </Button>
            );
          })}
          {gridLayer !== null && gridsQuery.isFetching && (
            <ActivityIndicator size="small" color="#087A3F" />
          )}
          {gridLayer !== null && gridsQuery.isError && (
            <Text className="text-xs font-bold text-[#B91C1C] dark:text-[#FCA5A5]">
              환경 정보를 불러오지 못했어요
            </Text>
          )}
          {gridLayer !== null &&
            !gridsQuery.isFetching &&
            !gridsQuery.isError &&
            gridsQuery.data?.length === 0 && (
              <Text className="text-xs font-bold text-[#6B756D] dark:text-[#AAB5AD]">
                이 지역에는 환경 데이터가 없어요
              </Text>
            )}
          {gridLayer !== null && (gridsQuery.data?.length ?? 0) > 0 && (
            <View className="h-9 flex-row items-center gap-1.5 rounded-full bg-white px-3 dark:bg-[#1B211D]">
              <View className="h-2.5 w-2.5 rounded-full bg-orange-500/70" />
              <Text className="text-[10px] font-bold text-[#6B756D] dark:text-[#AAB5AD]">
                낮음
              </Text>
              <View className="h-2.5 w-2.5 rounded-full bg-green-500/70" />
              <Text className="text-[10px] font-bold text-[#6B756D] dark:text-[#AAB5AD]">
                높음
              </Text>
            </View>
          )}
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
      {hazardNotice && (
        <Pressable
          onPress={() => setHazardNotice(null)}
          className="absolute left-5 right-5 top-24 rounded-2xl bg-[#163C29] px-4 py-3 shadow-lg"
        >
          <Text className="text-sm font-bold text-white">{hazardNotice}</Text>
        </Pressable>
      )}
      {selectedHazard && (
        <View className="absolute bottom-7 left-4 right-4 rounded-[22px] bg-white p-4 shadow-xl dark:bg-[#1B211D]">
          <View className="flex-row items-start justify-between">
            <View>
              <Text
                className={`text-xs font-black ${
                  selectedHazard.status === "PENDING"
                    ? "text-[#D97706]"
                    : "text-[#DC2626]"
                }`}
              >
                {selectedHazard.status === "PENDING"
                  ? "검증 대기 중"
                  : "활성 위험"}
              </Text>
              <Text className="mt-1 text-lg font-black dark:text-white">
                {selectedHazard.hazardType}
              </Text>
              <Text className="mt-1 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                {selectedHazard.status === "PENDING"
                  ? `신고 ${selectedHazard.reportCount}/3명`
                  : `위험 확인 ${selectedHazard.confirmationCount}회`}
              </Text>
            </View>
            <Pressable onPress={() => setSelectedHazard(null)} className="p-2">
              <Ionicons name="close" size={20} color="#6B756D" />
            </Pressable>
          </View>
          {selectedHazard.status === "ACTIVE" && (
            <View className="mt-3 flex-row gap-2">
              <Button
                className="flex-1 bg-[#B91C1C]"
                disabled={hazardConfirmationMutation.isPending}
                onPress={() =>
                  handleHazardConfirmation(selectedHazard.hazardId)
                }
              >
                <Text className="font-black text-white">아직 위험해요</Text>
              </Button>
              <Button
                className="flex-1 bg-[#087A3F]"
                disabled={hazardResolutionMutation.isPending}
                onPress={() => {
                  if (!isAuthenticated) return setLoginRequiredOpen(true);
                  if (selectedId !== null)
                    hazardResolutionMutation.mutate({
                      courseId: selectedId,
                      hazardId: selectedHazard.hazardId,
                    });
                }}
              >
                <Text className="font-black text-white">해결됐어요</Text>
              </Button>
            </View>
          )}
          {selectedHazard.reportedByMe && (
            <Pressable
              disabled={hazardDeleteMutation.isPending}
              onPress={() => setDeleteConfirmOpen(true)}
              className="mt-3 items-center py-2"
            >
              <Text className="text-xs font-bold text-[#6B756D] dark:text-[#AAB5AD]">
                내 신고 취소
              </Text>
            </Pressable>
          )}
        </View>
      )}
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
      {!showDetails && !hazardCoordinate && !selectedHazard && (
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
      {hazardCoordinate && (
        <SafeAreaView
          edges={["bottom"]}
          className="absolute inset-x-0 bottom-0 px-5 pb-3"
          pointerEvents="box-none"
        >
          <View className="rounded-3xl bg-white p-4 shadow-2xl dark:bg-[#1B211D]">
            <View className="flex-row items-start gap-3">
              <View className="h-10 w-10 items-center justify-center rounded-full bg-[#FFF7E6] dark:bg-[#3D3322]">
                <Ionicons name="location" size={20} color="#D97706" />
              </View>
              <View className="flex-1">
                <Text className="text-sm font-black text-[#191C1D] dark:text-[#F1F5F2]">
                  위치가 선택되었습니다
                </Text>
                <Text className="mt-1 text-xs leading-5 text-[#6B756D] dark:text-[#AAB5AD]">
                  이 위치에서 위험을 신고할 수 있어요. 다른 위치는 지도를 다시
                  길게 눌러 선택하세요.
                </Text>
              </View>
              <Button
                variant="ghost"
                size="icon"
                accessibilityLabel="선택 위치 해제"
                className="h-9 w-9 rounded-full"
                onPress={clearHazardSelection}
              >
                <Ionicons name="close" size={20} color="#6B756D" />
              </Button>
            </View>
            <Button
              className="mt-4 h-12 w-full rounded-2xl bg-[#DC2626] active:bg-[#B91C1C]"
              onPress={openHazardReportSheet}
            >
              <Ionicons name="warning-outline" size={19} color="white" />
              <Text className="font-black text-white">이 위치 위험 신고</Text>
            </Button>
          </View>
        </SafeAreaView>
      )}
      <HazardReportSheet
        open={hazardSheetOpen}
        coordinate={hazardCoordinate}
        isSubmitting={hazardCreateMutation.isPending}
        errorMessage={hazardCreateMutation.error?.message}
        onSubmit={submitHazardReport}
        onChangeLocation={() => {
          hazardCreateMutation.reset();
          setHazardSheetOpen(false);
        }}
        onClose={clearHazardSelection}
      />
      <HazardDeleteConfirmModal
        open={deleteConfirmOpen}
        isDeleting={hazardDeleteMutation.isPending}
        onClose={() => setDeleteConfirmOpen(false)}
        onConfirm={() => {
          if (selectedId === null || selectedHazard === null) return;
          hazardDeleteMutation.mutate({
            courseId: selectedId,
            hazardId: selectedHazard.hazardId,
          });
        }}
      />
      <LoginRequiredModal
        visible={loginRequiredOpen}
        onClose={() => setLoginRequiredOpen(false)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  hazardMarker: {
    width: 34,
    height: 34,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 17,
    borderWidth: 3,
    borderColor: "white",
    backgroundColor: "#DC2626",
  },
  pendingHazardMarker: {
    backgroundColor: "#D97706",
  },
  hazardCallout: {
    width: 210,
    borderRadius: 18,
    padding: 14,
    shadowColor: "#000000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.2,
    shadowRadius: 8,
    elevation: 6,
  },
});

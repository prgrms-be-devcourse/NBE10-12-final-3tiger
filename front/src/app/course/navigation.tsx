import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Linking, Platform, StyleSheet, View } from "react-native";
import MapView, { Marker, Polyline, type LatLng } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import { getCourseNavigation } from "@/api/course-api";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import {
  buildCumulativeDistances,
  distanceMeters,
  matchRouteProgress,
  splitRouteAtProgress,
  toMapCoordinates,
  type RouteProgress,
} from "@/lib/course-navigation";
import { useThemeStore } from "@/stores/theme-store";

const START_PROXIMITY_M = 100;
const OFF_ROUTE_DISTANCE_M = 30;
const OFF_ROUTE_SAMPLE_COUNT = 3;
const COMPLETION_REMAINING_M = 20;
const COMPLETION_END_DISTANCE_M = 30;

type UserLocation = LatLng & {
  accuracy: number | null;
  heading: number | null;
};

const formatDistance = (meters: number) =>
  meters >= 1000 ? `${(meters / 1000).toFixed(1)}km` : `${Math.round(meters)}m`;

export default function CourseNavigationScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const courseId = Number(id);
  const isDark = useThemeStore((state) => state.isDark);
  const mapRef = useRef<MapView>(null);
  const previousProgressRef = useRef<RouteProgress | null>(null);
  const offRouteSamplesRef = useRef(0);
  const hasFitRouteRef = useRef(false);
  const [mapReady, setMapReady] = useState(false);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [isLocating, setIsLocating] = useState(true);
  const [userLocation, setUserLocation] = useState<UserLocation | null>(null);
  const [navigationStarted, setNavigationStarted] = useState(false);
  const [followUser, setFollowUser] = useState(true);
  const [progress, setProgress] = useState<RouteProgress | null>(null);
  const [isOffRoute, setIsOffRoute] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);

  const navigationQuery = useQuery({
    queryKey: ["course-navigation", courseId],
    queryFn: () => getCourseNavigation(courseId),
    enabled: Number.isFinite(courseId),
    staleTime: Infinity,
  });

  const route = useMemo(
    () => toMapCoordinates(navigationQuery.data?.path.coordinates ?? []),
    [navigationQuery.data?.path.coordinates],
  );
  const cumulativeDistances = useMemo(
    () => buildCumulativeDistances(route),
    [route],
  );
  const routeParts = useMemo(
    () => splitRouteAtProgress(route, progress),
    [progress, route],
  );
  const startPoint = navigationQuery.data
    ? {
        latitude: navigationQuery.data.startPoint.lat,
        longitude: navigationQuery.data.startPoint.lng,
      }
    : null;
  const endPoint = navigationQuery.data
    ? {
        latitude: navigationQuery.data.endPoint.lat,
        longitude: navigationQuery.data.endPoint.lng,
      }
    : null;
  const distanceToStart =
    userLocation && startPoint
      ? distanceMeters(userLocation, startPoint)
      : null;

  useEffect(() => {
    let subscription: Location.LocationSubscription | undefined;
    let active = true;

    const watchLocation = async () => {
      setIsLocating(true);
      const permission = await Location.requestForegroundPermissionsAsync();
      if (!active) return;
      if (permission.status !== Location.PermissionStatus.GRANTED) {
        setPermissionDenied(true);
        setIsLocating(false);
        return;
      }

      setPermissionDenied(false);
      subscription = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.BestForNavigation,
          distanceInterval: 3,
          timeInterval: 1500,
        },
        (position) => {
          if (!active) return;
          setUserLocation({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            accuracy: position.coords.accuracy,
            heading: position.coords.heading,
          });
          setIsLocating(false);
        },
      );
    };

    void watchLocation().catch(() => {
      if (!active) return;
      setIsLocating(false);
    });

    return () => {
      active = false;
      subscription?.remove();
    };
  }, []);

  useEffect(() => {
    if (!mapReady || route.length < 2 || hasFitRouteRef.current) return;
    hasFitRouteRef.current = true;
    mapRef.current?.fitToCoordinates(route, {
      edgePadding: { top: 130, right: 48, bottom: 250, left: 48 },
      animated: true,
    });
  }, [mapReady, route]);

  useEffect(() => {
    if (!navigationStarted || !userLocation || route.length < 2) return;

    const nextProgress = matchRouteProgress(
      userLocation,
      route,
      cumulativeDistances,
      previousProgressRef.current?.segmentIndex,
    );
    if (!nextProgress) return;

    const previousProgress = previousProgressRef.current;
    const didRegressTooFar =
      previousProgress &&
      nextProgress.traveledDistanceM + 15 < previousProgress.traveledDistanceM;
    if (!didRegressTooFar) {
      previousProgressRef.current = nextProgress;
      setProgress(nextProgress);
    }

    const reliableLocation =
      userLocation.accuracy == null || userLocation.accuracy <= 50;
    if (
      reliableLocation &&
      nextProgress.distanceFromRouteM > OFF_ROUTE_DISTANCE_M
    ) {
      offRouteSamplesRef.current += 1;
    } else {
      offRouteSamplesRef.current = 0;
    }
    setIsOffRoute(offRouteSamplesRef.current >= OFF_ROUTE_SAMPLE_COUNT);

    if (
      endPoint &&
      nextProgress.remainingDistanceM <= COMPLETION_REMAINING_M &&
      distanceMeters(userLocation, endPoint) <= COMPLETION_END_DISTANCE_M
    ) {
      setIsCompleted(true);
      setNavigationStarted(false);
    }
  }, [cumulativeDistances, endPoint, navigationStarted, route, userLocation]);

  useEffect(() => {
    if (!followUser || !navigationStarted || !userLocation) return;
    mapRef.current?.animateCamera(
      {
        center: userLocation,
        heading:
          userLocation.heading != null && userLocation.heading >= 0
            ? userLocation.heading
            : 0,
        pitch: 45,
        zoom: 18,
      },
      { duration: 650 },
    );
  }, [followUser, navigationStarted, userLocation]);

  const openDirectionsToStart = async () => {
    if (!startPoint) return;
    const destination = `${startPoint.latitude},${startPoint.longitude}`;
    const url = Platform.select({
      ios: `http://maps.apple.com/?daddr=${destination}&dirflg=w`,
      default: `https://www.google.com/maps/dir/?api=1&destination=${destination}&travelmode=walking`,
    });
    await Linking.openURL(url);
  };

  const confirmExit = () => {
    Alert.alert("안내를 종료할까요?", "현재 진행 정보는 저장되지 않아요.", [
      { text: "계속 걷기", style: "cancel" },
      { text: "안내 종료", style: "destructive", onPress: () => router.back() },
    ]);
  };

  const canStartWalk =
    !permissionDenied &&
    !isLocating &&
    distanceToStart != null &&
    distanceToStart <= START_PROXIMITY_M;

  const startNavigation = () => {
    if (!canStartWalk) return;
    previousProgressRef.current = null;
    offRouteSamplesRef.current = 0;
    setProgress(null);
    setIsOffRoute(false);
    setIsCompleted(false);
    setFollowUser(true);
    setNavigationStarted(true);
  };

  if (!Number.isFinite(courseId)) {
    return (
      <ErrorState
        message="잘못된 코스 정보입니다."
        onRetry={() => router.back()}
      />
    );
  }
  if (navigationQuery.isPending) {
    return <LoadingState label="안내 경로를 준비하고 있어요" />;
  }
  if (navigationQuery.isError) {
    return (
      <SafeAreaView className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]">
        <ErrorState
          message={navigationQuery.error.message}
          onRetry={() => void navigationQuery.refetch()}
        />
      </SafeAreaView>
    );
  }
  if (route.length < 2 || !startPoint || !endPoint) {
    return (
      <SafeAreaView className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]">
        <ErrorState
          message="안내할 수 없는 코스입니다."
          onRetry={() => router.back()}
        />
      </SafeAreaView>
    );
  }

  const remainingDistanceM =
    progress?.remainingDistanceM ?? cumulativeDistances.at(-1) ?? 0;
  const progressPercent = Math.min(
    100,
    Math.max(0, (progress?.progress ?? 0) * 100),
  );
  const remainingMinutes = Math.max(
    0,
    Math.ceil(
      navigationQuery.data.estimatedMinutes * (1 - progressPercent / 100),
    ),
  );
  const isWaitingToStart = !navigationStarted && !isCompleted;
  const status = isCompleted
    ? "코스를 완주했어요"
    : isOffRoute
      ? "코스에서 벗어났어요"
      : navigationStarted
        ? "경로를 따라 이동하세요"
        : isLocating || distanceToStart == null
          ? "현재 위치를 확인하고 있어요"
          : canStartWalk
            ? "산책을 시작할 수 있어요"
            : "출발점 근처로 이동해주세요";

  return (
    <View className="flex-1 bg-[#E8F0E5] dark:bg-[#111411]">
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={{
          ...startPoint,
          latitudeDelta: 0.012,
          longitudeDelta: 0.01,
        }}
        showsUserLocation
        showsMyLocationButton={false}
        toolbarEnabled={false}
        userInterfaceStyle={isDark ? "dark" : "light"}
        onMapReady={() => setMapReady(true)}
        onPanDrag={() => setFollowUser(false)}
      >
        {routeParts.completed.length > 1 && (
          <Polyline
            coordinates={routeParts.completed}
            strokeColor={isDark ? "#637069" : "#A4ADA7"}
            strokeWidth={7}
            lineCap="round"
            lineJoin="round"
          />
        )}
        {routeParts.remaining.length > 1 && (
          <Polyline
            coordinates={routeParts.remaining}
            strokeColor={isOffRoute ? "#E66B3D" : "#087A3F"}
            strokeWidth={7}
            lineCap="round"
            lineJoin="round"
          />
        )}
        <Marker coordinate={startPoint} title="출발점" pinColor="#087A3F" />
        {!navigationQuery.data.isLoop && (
          <Marker coordinate={endPoint} title="도착점" pinColor="#E66B3D" />
        )}
      </MapView>

      <SafeAreaView className="flex-1 justify-between" pointerEvents="box-none">
        <View
          className="mx-4 mt-2 flex-row items-center gap-3"
          pointerEvents="box-none"
        >
          <Button
            variant="secondary"
            size="icon"
            accessibilityLabel="안내 종료"
            className="h-12 w-12 rounded-2xl bg-white dark:bg-[#1B211D]"
            onPress={confirmExit}
          >
            <Ionicons
              name="close"
              size={24}
              color={isDark ? "#F1F5F2" : "#203126"}
            />
          </Button>
          <View className="flex-1 rounded-2xl bg-white px-4 py-3 dark:bg-[#1B211D]">
            <Text
              className={`text-sm font-black ${
                isOffRoute ? "text-[#D6542A]" : "text-[#087A3F]"
              }`}
            >
              {status}
            </Text>
            <Text
              numberOfLines={1}
              className="mt-1 text-xs text-[#667168] dark:text-[#AAB5AD]"
            >
              {navigationQuery.data.name}
            </Text>
          </View>
        </View>

        <View className="items-end px-4" pointerEvents="box-none">
          <Button
            variant="secondary"
            size="icon"
            accessibilityLabel="현재 위치 따라가기"
            className="mb-3 h-12 w-12 rounded-2xl bg-white dark:bg-[#1B211D]"
            onPress={() => setFollowUser(true)}
          >
            <Ionicons
              name={followUser ? "navigate" : "locate-outline"}
              size={21}
              color="#087A3F"
            />
          </Button>

          <View className="w-full rounded-t-[28px] bg-white px-5 pb-5 pt-5 dark:bg-[#1B211D]">
            {permissionDenied ? (
              <View>
                <Text className="text-lg font-black text-[#203126] dark:text-[#F1F5F2]">
                  위치 권한이 필요해요
                </Text>
                <Text className="mt-2 text-sm leading-5 text-[#667168] dark:text-[#AAB5AD]">
                  기기 설정에서 위치 권한을 허용한 뒤 다시 시도해주세요.
                </Text>
                <Button
                  className="mt-4 h-12 rounded-2xl bg-[#087A3F]"
                  onPress={() => void Linking.openSettings()}
                >
                  <Text className="font-black text-white">설정 열기</Text>
                </Button>
              </View>
            ) : isWaitingToStart ? (
              <View>
                <Text className="text-lg font-black text-[#203126] dark:text-[#F1F5F2]">
                  {distanceToStart == null
                    ? "현재 위치를 확인하고 있어요"
                    : canStartWalk
                      ? "출발점 근처에 도착했어요"
                      : `출발점까지 ${formatDistance(distanceToStart)} 남았어요`}
                </Text>
                <View
                  className={`mt-3 flex-row items-start gap-2 rounded-2xl px-3 py-3 ${
                    canStartWalk
                      ? "bg-[#E9FBEF] dark:bg-[#24382B]"
                      : "bg-[#F3F5F3] dark:bg-[#29312C]"
                  }`}
                >
                  <Ionicons
                    name={
                      canStartWalk ? "checkmark-circle" : "location-outline"
                    }
                    size={19}
                    color={
                      canStartWalk ? "#087A3F" : isDark ? "#AAB5AD" : "#667168"
                    }
                  />
                  <Text
                    className={`flex-1 text-sm leading-5 ${
                      canStartWalk
                        ? "font-bold text-[#087A3F]"
                        : "text-[#667168] dark:text-[#AAB5AD]"
                    }`}
                  >
                    {canStartWalk
                      ? "출발점 100m 이내입니다. 산책을 시작할 수 있어요."
                      : "안전하고 정확한 안내를 위해 출발점 100m 이내에서 산책을 시작할 수 있어요."}
                  </Text>
                </View>
                <View className="mt-4 gap-2">
                  <Button
                    variant="outline"
                    className="h-12 rounded-2xl border-[#C9D7CD] bg-white dark:border-[#405047] dark:bg-[#1B211D]"
                    onPress={() => void openDirectionsToStart()}
                  >
                    <Ionicons name="map-outline" size={18} color="#087A3F" />
                    <Text className="font-black text-[#087A3F]">
                      출발점 길찾기
                    </Text>
                  </Button>
                  <Button
                    disabled={!canStartWalk}
                    className="h-12 rounded-2xl bg-[#087A3F]"
                    onPress={startNavigation}
                  >
                    <Ionicons
                      name="walk"
                      size={19}
                      color={canStartWalk ? "white" : "#D5DDD7"}
                    />
                    <Text className="font-black text-white">산책 시작</Text>
                  </Button>
                </View>
              </View>
            ) : (
              <View>
                <View className="flex-row items-end justify-between">
                  <View>
                    <Text className="text-xs font-bold text-[#738078] dark:text-[#AAB5AD]">
                      남은 거리
                    </Text>
                    <Text className="mt-1 text-3xl font-black text-[#203126] dark:text-[#F1F5F2]">
                      {formatDistance(remainingDistanceM)}
                    </Text>
                  </View>
                  <View className="items-end">
                    <Text className="text-xs font-bold text-[#738078] dark:text-[#AAB5AD]">
                      약 {remainingMinutes}분 · {Math.round(progressPercent)}%
                    </Text>
                    <Text
                      className={`mt-2 text-sm font-black ${
                        isOffRoute ? "text-[#D6542A]" : "text-[#087A3F]"
                      }`}
                    >
                      {isOffRoute
                        ? "표시된 코스로 돌아가 주세요"
                        : isCompleted
                          ? "산책을 마쳤습니다"
                          : "코스 위에서 안내 중"}
                    </Text>
                  </View>
                </View>
                <View className="mt-4 h-2 overflow-hidden rounded-full bg-[#E1E9E3] dark:bg-[#324039]">
                  <View
                    className="h-full rounded-full bg-[#087A3F]"
                    style={{ width: `${progressPercent}%` }}
                  />
                </View>
                {isCompleted && (
                  <Button
                    className="mt-4 h-12 rounded-2xl bg-[#087A3F]"
                    onPress={() => router.back()}
                  >
                    <Text className="font-black text-white">안내 마치기</Text>
                  </Button>
                )}
              </View>
            )}
          </View>
        </View>
      </SafeAreaView>
    </View>
  );
}

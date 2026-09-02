import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useLocalSearchParams } from "expo-router";
import { Fragment, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Animated,
  Easing,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  useWindowDimensions,
  View,
} from "react-native";
import MapView, { Marker, Polyline, type LatLng } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  getCourseNavigation,
  getCourseStartDirections,
} from "@/api/course-api";
import { Button } from "@/components/ui/button";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
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
import type {
  CourseStartDirections,
  DirectionRoute,
  DirectionRouteSegment,
  DirectionsMode,
} from "@/types/domain";

const START_PROXIMITY_M = 50;
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

const formatDuration = (seconds: number) => {
  const minutes = Math.max(1, Math.round(seconds / 60));
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder > 0 ? `${hours}시간 ${remainder}분` : `${hours}시간`;
};

const DIRECTIONS_MODES: {
  mode: DirectionsMode;
  label: string;
  icon: keyof typeof Ionicons.glyphMap;
}[] = [
  { mode: "WALK", label: "도보", icon: "walk-outline" },
  { mode: "PUBLIC_TRANSIT", label: "대중교통", icon: "bus-outline" },
  { mode: "BICYCLE", label: "자전거", icon: "bicycle-outline" },
];

const transitTypeLabel = (type: string) => {
  if (type === "BUS") return "버스";
  if (type === "SUBWAY") return "지하철";
  if (type === "BUS_AND_SUBWAY") return "버스 + 지하철";
  return "대중교통";
};

const transitTypeIcon = (type: string): keyof typeof Ionicons.glyphMap =>
  type === "SUBWAY" ? "train-outline" : "bus-outline";

const TRANSIT_COLORS = [
  "#2563EB",
  "#7C3AED",
  "#DC2626",
  "#0891B2",
  "#EA580C",
  "#16A34A",
  "#C026D3",
];

const SUBWAY_LINE_COLORS: Record<string, string> = {
  "1": "#0052A4",
  "2": "#00A84D",
  "3": "#EF7C1C",
  "4": "#00A5DE",
  "5": "#996CAC",
  "6": "#CD7C2F",
  "7": "#747F00",
  "8": "#E6186C",
  "9": "#BDB092",
};

const stringHash = (value: string) =>
  [...value].reduce((hash, character) => hash + character.charCodeAt(0), 0);

const segmentLabel = (segment: DirectionRouteSegment) => {
  if (segment.vehicleNames.length > 0) return segment.vehicleNames.join(" · ");
  if (segment.mode === "WALK") return "도보";
  if (segment.mode === "BICYCLE") return "자전거";
  if (segment.mode === "SUBWAY") return "지하철";
  if (segment.mode === "BUS") return "버스";
  return segment.mode;
};

const segmentIcon = (
  segment: DirectionRouteSegment,
): keyof typeof Ionicons.glyphMap => {
  if (segment.mode === "WALK") return "walk-outline";
  if (segment.mode === "BICYCLE") return "bicycle-outline";
  if (segment.mode === "SUBWAY") return "train-outline";
  return "bus-outline";
};

const segmentColor = (segment: DirectionRouteSegment) => {
  if (segment.mode === "WALK") return "#64748B";
  if (segment.mode === "BICYCLE") return "#0D9488";
  const vehicle = segment.vehicleNames[0] ?? segment.mode;
  if (segment.mode === "SUBWAY") {
    const line = vehicle.match(/[1-9]/)?.[0];
    if (line) return SUBWAY_LINE_COLORS[line];
  }
  return TRANSIT_COLORS[
    stringHash(`${segment.mode}-${vehicle}`) % TRANSIT_COLORS.length
  ];
};

const lightenColor = (hex: string, amount = 0.58) => {
  const value = hex.replace("#", "");
  const channel = (offset: number) => {
    const original = Number.parseInt(value.slice(offset, offset + 2), 16);
    return Math.round(original + (255 - original) * amount)
      .toString(16)
      .padStart(2, "0");
  };
  return `#${channel(0)}${channel(2)}${channel(4)}`;
};

const segmentIdentity = (segment: DirectionRouteSegment) =>
  `${segment.mode}:${segment.vehicleNames.join("|")}`;

const getVisualRouteSegments = (route: DirectionRoute) =>
  route.segments
    .filter(
      (segment) => segment.distanceMeters > 0 || segment.estimatedSeconds > 0,
    )
    .reduce<DirectionRouteSegment[]>((groups, segment) => {
      const previous = groups.at(-1);
      if (previous && segmentIdentity(previous) === segmentIdentity(segment)) {
        groups[groups.length - 1] = {
          ...previous,
          distanceMeters: previous.distanceMeters + segment.distanceMeters,
          estimatedSeconds:
            previous.estimatedSeconds + segment.estimatedSeconds,
        };
      } else {
        groups.push(segment);
      }
      return groups;
    }, []);

const bearingDegrees = (from: LatLng, to: LatLng) => {
  const fromLatitude = (from.latitude * Math.PI) / 180;
  const toLatitude = (to.latitude * Math.PI) / 180;
  const longitudeDelta = ((to.longitude - from.longitude) * Math.PI) / 180;
  const y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
  const x =
    Math.cos(fromLatitude) * Math.sin(toLatitude) -
    Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
};

const getDirectionArrows = (
  coordinates: LatLng[],
  requestedSpacingMeters: number,
) => {
  if (coordinates.length < 2) return [];
  const legLengths = coordinates
    .slice(1)
    .map((coordinate, index) => distanceMeters(coordinates[index], coordinate));
  const totalLength = legLengths.reduce((sum, length) => sum + length, 0);
  const arrowSpacingMeters = Math.max(
    requestedSpacingMeters,
    totalLength / 120,
  );
  if (totalLength < arrowSpacingMeters / 2) return [];
  const targetDistances: number[] = [];
  for (
    let distance = arrowSpacingMeters / 2;
    distance < totalLength;
    distance += arrowSpacingMeters
  ) {
    targetDistances.push(distance);
  }

  return targetDistances.map((targetDistance) => {
    let traveled = 0;
    for (let legIndex = 0; legIndex < legLengths.length; legIndex += 1) {
      const legLength = legLengths[legIndex];
      if (traveled + legLength >= targetDistance) {
        const start = coordinates[legIndex];
        const end = coordinates[legIndex + 1];
        const ratio =
          legLength > 0 ? (targetDistance - traveled) / legLength : 0;
        return {
          coordinate: {
            latitude: start.latitude + (end.latitude - start.latitude) * ratio,
            longitude:
              start.longitude + (end.longitude - start.longitude) * ratio,
          },
          bearing: bearingDegrees(start, end),
        };
      }
      traveled += legLength;
    }
    const end = coordinates.at(-1)!;
    const start = coordinates.at(-2)!;
    return { coordinate: end, bearing: bearingDegrees(start, end) };
  });
};

function RouteProcessBar({ route }: { route: DirectionRoute }) {
  const segments = getVisualRouteSegments(route);
  if (segments.length === 0) return null;
  const totalWeight = segments.reduce(
    (sum, segment) => sum + Math.max(segment.estimatedSeconds, 60),
    0,
  );

  return (
    <View className="mt-3">
      <View className="h-4 flex-row overflow-hidden rounded-full">
        {segments.map((segment) => (
          <View
            key={segment.segmentIndex}
            className="relative min-w-8 flex-row items-center overflow-hidden"
            style={{
              flex: Math.max(segment.estimatedSeconds, 60) / totalWeight,
            }}
          >
            <View
              className="z-10 h-4 w-4 shrink-0 items-center justify-center rounded-full border"
              style={{
                backgroundColor: "white",
                borderColor: segmentColor(segment),
              }}
            >
              <Ionicons
                name={segmentIcon(segment)}
                size={9}
                color={segmentColor(segment)}
              />
            </View>
            <View
              className="relative h-2.5 flex-1 justify-center"
              style={{ backgroundColor: segmentColor(segment) }}
            >
              <Text
                pointerEvents="none"
                numberOfLines={1}
                className="text-center text-[8px] font-black leading-[10px] text-white"
              >
                {formatDuration(segment.estimatedSeconds)}
              </Text>
            </View>
          </View>
        ))}
      </View>
    </View>
  );
}

function DirectionRouteCard({
  route,
  index,
  isDark,
  onPress,
}: {
  route: DirectionRoute;
  index: number;
  isDark: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      className="mb-3 rounded-2xl border border-[#DCE4F0] bg-white px-4 py-3 active:opacity-80 dark:border-[#334155] dark:bg-[#172033]"
      onPress={onPress}
    >
      <View className="flex-row items-center justify-between">
        <View className="flex-row items-center gap-2">
          <View className="h-9 w-9 items-center justify-center rounded-xl bg-[#E8F0FF] dark:bg-[#23365B]">
            <Ionicons
              name={transitTypeIcon(route.type)}
              size={18}
              color={isDark ? "#93B4FF" : "#2563EB"}
            />
          </View>
          <View>
            <Text className="text-xs font-bold text-[#64748B] dark:text-[#94A3B8]">
              {index === 0 ? "추천 경로" : `다른 경로 ${index}`}
            </Text>
            <Text className="mt-0.5 text-sm font-black text-[#0F172A] dark:text-[#F8FAFC]">
              {transitTypeLabel(route.type)}
            </Text>
          </View>
        </View>
        <Text className="text-xl font-black text-[#1D4ED8] dark:text-[#93B4FF]">
          {formatDuration(route.estimatedSeconds)}
        </Text>
      </View>
      <RouteProcessBar route={route} />
      <View className="mt-3 flex-row items-center gap-2">
        <Text className="text-xs font-semibold text-[#475569] dark:text-[#CBD5E1]">
          {formatDistance(route.distanceMeters)}
        </Text>
        <View className="h-1 w-1 rounded-full bg-[#94A3B8]" />
        <Text className="text-xs font-semibold text-[#475569] dark:text-[#CBD5E1]">
          환승 {route.transfers}회
        </Text>
        {route.fareWon != null && (
          <>
            <View className="h-1 w-1 rounded-full bg-[#94A3B8]" />
            <Text className="text-xs font-semibold text-[#475569] dark:text-[#CBD5E1]">
              {route.fareWon.toLocaleString("ko-KR")}원
            </Text>
          </>
        )}
        <View className="ml-auto">
          <Ionicons
            name="chevron-forward"
            size={17}
            color={isDark ? "#94A3B8" : "#64748B"}
          />
        </View>
      </View>
    </Pressable>
  );
}

function DirectionsSheet({
  visible,
  mode,
  data,
  isPending,
  isError,
  errorMessage,
  courseName,
  isDark,
  onClose,
  onModeChange,
  onRetry,
  onOpenKakao,
  onStart,
  onSelectRoute,
}: {
  visible: boolean;
  mode: DirectionsMode;
  data: CourseStartDirections | undefined;
  isPending: boolean;
  isError: boolean;
  errorMessage?: string;
  courseName: string;
  isDark: boolean;
  onClose: () => void;
  onModeChange: (mode: DirectionsMode) => void;
  onRetry: () => void;
  onOpenKakao: (data: CourseStartDirections) => void;
  onStart: (confirmedStartable: boolean) => void;
  onSelectRoute: (route: DirectionRoute) => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, onClose);

  useEffect(() => {
    if (!visible) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [sheetTranslateY, visible, windowHeight]);

  if (!visible) return null;

  return (
    <View
      pointerEvents="box-none"
      className="absolute inset-0 z-50 justify-end"
    >
      <Animated.View
        className="h-[78%] overflow-hidden rounded-t-[30px] bg-white shadow-2xl dark:bg-[#0F172A]"
        style={{ transform: [{ translateY: sheetTranslateY }] }}
      >
        <SafeAreaView edges={["bottom"]} className="flex-1">
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={sheetTranslateY}
            dismissDistance={windowHeight}
          />
          <View className="flex-row items-start justify-between px-5 pb-3 pt-1">
            <View className="mr-3 flex-1">
              <Text className="text-xl font-black text-[#0F172A] dark:text-[#F8FAFC]">
                출발점으로 이동
              </Text>
              <Text
                numberOfLines={1}
                className="mt-1 text-sm text-[#64748B] dark:text-[#94A3B8]"
              >
                {courseName} · 현재 위치에서 출발점까지
              </Text>
            </View>
            <Button
              variant="ghost"
              size="icon"
              accessibilityLabel="길찾기 닫기"
              className="h-11 w-11 rounded-full"
              onPress={dismissSheet}
            >
              <Ionicons
                name="close"
                size={22}
                color={isDark ? "#E2E8F0" : "#334155"}
              />
            </Button>
          </View>

          <View className="mx-5 flex-row rounded-2xl bg-[#EEF2F7] p-1 dark:bg-[#1E293B]">
            {DIRECTIONS_MODES.map((item) => {
              const selected = item.mode === mode;
              return (
                <Pressable
                  key={item.mode}
                  accessibilityRole="tab"
                  accessibilityState={{ selected }}
                  className={`h-12 flex-1 flex-row items-center justify-center gap-1.5 rounded-xl ${
                    selected ? "bg-[#2563EB]" : "bg-transparent"
                  }`}
                  onPress={() => onModeChange(item.mode)}
                >
                  <Ionicons
                    name={item.icon}
                    size={18}
                    color={
                      selected ? "#FFFFFF" : isDark ? "#CBD5E1" : "#475569"
                    }
                  />
                  <Text
                    className={`text-sm font-black ${
                      selected
                        ? "text-white"
                        : "text-[#475569] dark:text-[#CBD5E1]"
                    }`}
                  >
                    {item.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <ScrollView
            className="mt-4 flex-1"
            contentContainerClassName="grow px-5 pb-4"
            showsVerticalScrollIndicator
            scrollEnabled
            nestedScrollEnabled
          >
            {isPending ? (
              <View className="items-center rounded-3xl bg-[#F4F7FB] px-5 py-8 dark:bg-[#172033]">
                <ActivityIndicator size="large" color="#2563EB" />
                <Text className="mt-4 text-base font-black text-[#1E3A8A] dark:text-[#BFDBFE]">
                  경로를 찾고 있어요
                </Text>
                <Text className="mt-1 text-sm text-[#64748B] dark:text-[#94A3B8]">
                  선택한 이동 수단의 최신 정보를 확인합니다.
                </Text>
              </View>
            ) : isError ? (
              <View className="items-center rounded-3xl border border-[#FECACA] bg-[#FFF7F7] px-5 py-6 dark:border-[#7F1D1D] dark:bg-[#2A171B]">
                <Ionicons
                  name="cloud-offline-outline"
                  size={28}
                  color="#DC2626"
                />
                <Text className="mt-3 text-base font-black text-[#991B1B] dark:text-[#FCA5A5]">
                  경로를 불러오지 못했어요
                </Text>
                <Text className="mt-1 text-center text-sm leading-5 text-[#7F1D1D] dark:text-[#FECACA]">
                  {errorMessage ?? "잠시 후 다시 시도해주세요."}
                </Text>
                <Button
                  variant="outline"
                  className="mt-4 h-12 rounded-2xl border-[#FCA5A5] bg-white px-6 dark:border-[#B91C1C] dark:bg-[#2A171B]"
                  onPress={onRetry}
                >
                  <Ionicons name="refresh" size={18} color="#DC2626" />
                  <Text className="font-black text-[#B91C1C] dark:text-[#FCA5A5]">
                    다시 시도
                  </Text>
                </Button>
              </View>
            ) : data ? (
              <>
                {data.startable ? (
                  <View className="rounded-3xl border border-[#BFDBFE] bg-[#EFF6FF] px-5 py-5 dark:border-[#1D4ED8] dark:bg-[#172554]">
                    <View className="flex-row items-center gap-3">
                      <View className="h-11 w-11 items-center justify-center rounded-full bg-[#2563EB]">
                        <Ionicons name="checkmark" size={24} color="white" />
                      </View>
                      <View className="flex-1">
                        <Text className="text-base font-black text-[#1E3A8A] dark:text-[#DBEAFE]">
                          바로 산책을 시작할 수 있어요
                        </Text>
                        <Text className="mt-1 text-sm leading-5 text-[#3B5A98] dark:text-[#BFDBFE]">
                          출발점 {data.startableRadiusMeters}m 이내에
                          도착했습니다.
                        </Text>
                      </View>
                    </View>
                  </View>
                ) : (
                  <View className="overflow-hidden rounded-3xl bg-[#172554] px-5 py-5 dark:bg-[#1E3A8A]">
                    <Text className="text-xs font-bold text-[#BFDBFE]">
                      {
                        DIRECTIONS_MODES.find((item) => item.mode === data.mode)
                          ?.label
                      }{" "}
                      예상
                    </Text>
                    <View className="mt-2 flex-row items-end justify-between">
                      <Text className="text-3xl font-black text-white">
                        {data.routes[0]?.estimatedSeconds != null
                          ? formatDuration(data.routes[0].estimatedSeconds)
                          : "정보 없음"}
                      </Text>
                      <Text className="pb-1 text-base font-black text-[#DBEAFE]">
                        {data.routes[0]?.distanceMeters != null
                          ? formatDistance(data.routes[0].distanceMeters)
                          : "거리 정보 없음"}
                      </Text>
                    </View>
                    <View className="mt-4 h-px bg-white/15" />
                    <View className="mt-3 flex-row items-start gap-2">
                      <Ionicons name="location" size={17} color="#93C5FD" />
                      <Text className="flex-1 text-sm font-semibold leading-5 text-[#E0ECFF]">
                        {data.destination.name}
                      </Text>
                    </View>
                  </View>
                )}

                {data.routes.length > 0 && (
                  <View className="mt-4">
                    <Text className="mb-2 text-sm font-black text-[#334155] dark:text-[#E2E8F0]">
                      이용 가능한 경로 {data.routes.length}개
                    </Text>
                    {data.routes.map((route, index) => (
                      <DirectionRouteCard
                        key={`${route.routeIndex}-${route.type}-${route.estimatedSeconds}`}
                        route={route}
                        index={index}
                        isDark={isDark}
                        onPress={() => onSelectRoute(route)}
                      />
                    ))}
                  </View>
                )}

                <View className="mt-4 gap-2">
                  {!data.startable && data.landingUrl && (
                    <Button
                      className="h-[52px] rounded-2xl bg-[#2563EB]"
                      onPress={() => onOpenKakao(data)}
                    >
                      <Ionicons name="map-outline" size={19} color="white" />
                      <Text className="font-black text-white">
                        카카오맵에서 길찾기
                      </Text>
                    </Button>
                  )}
                  <Button
                    disabled={!data.startable}
                    variant={data.startable ? "default" : "outline"}
                    className={`h-[52px] rounded-2xl ${
                      data.startable
                        ? "bg-[#0F172A] dark:bg-[#E2E8F0]"
                        : "border-[#CBD5E1] bg-white dark:border-[#334155] dark:bg-[#0F172A]"
                    }`}
                    onPress={() => onStart(data.startable)}
                  >
                    <Ionicons
                      name="walk"
                      size={19}
                      color={
                        data.startable
                          ? isDark
                            ? "#0F172A"
                            : "white"
                          : "#94A3B8"
                      }
                    />
                    <Text
                      className={`font-black ${
                        data.startable
                          ? "text-white dark:text-[#0F172A]"
                          : "text-[#94A3B8]"
                      }`}
                    >
                      {data.startable
                        ? "산책 시작"
                        : `출발점 ${data.startableRadiusMeters}m 이내에서 시작`}
                    </Text>
                  </Button>
                </View>
              </>
            ) : null}
          </ScrollView>
        </SafeAreaView>
      </Animated.View>
    </View>
  );
}

function RouteDetailSheet({
  route,
  isDark,
  onBack,
}: {
  route: DirectionRoute | null;
  isDark: boolean;
  onBack: () => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;

  useEffect(() => {
    if (!route) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 260,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [route, sheetTranslateY, windowHeight]);

  if (!route) return null;
  const dismissSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, onBack);

  return (
    <View
      pointerEvents="box-none"
      className="absolute inset-0 z-50 justify-end"
    >
      <Animated.View
        className="h-1/2 overflow-hidden rounded-t-[30px] bg-white shadow-2xl dark:bg-[#0F172A]"
        style={{ transform: [{ translateY: sheetTranslateY }] }}
      >
        <SafeAreaView edges={["bottom"]} className="flex-1">
          <BottomSheetHandle
            onDismiss={onBack}
            translateY={sheetTranslateY}
            dismissDistance={windowHeight}
          />
          <View className="flex-row items-center px-5 pb-2 pt-1">
            <Button
              variant="ghost"
              size="icon"
              accessibilityLabel="추천 경로 목록으로 돌아가기"
              className="-ml-2 h-10 w-10 rounded-full"
              onPress={dismissSheet}
            >
              <Ionicons
                name="chevron-back"
                size={22}
                color={isDark ? "#E2E8F0" : "#334155"}
              />
            </Button>
            <View className="ml-1 flex-1">
              <Text className="text-lg font-black text-[#0F172A] dark:text-[#F8FAFC]">
                선택한 경로
              </Text>
              <Text className="mt-0.5 text-xs font-bold text-[#64748B] dark:text-[#94A3B8]">
                {formatDuration(route.estimatedSeconds)} ·{" "}
                {formatDistance(route.distanceMeters)}
                {route.transfers > 0 ? ` · 환승 ${route.transfers}회` : ""}
              </Text>
            </View>
          </View>
          <View className="px-5 pb-3">
            <RouteProcessBar route={route} />
          </View>
          <ScrollView
            className="flex-1 border-t border-[#E2E8F0] dark:border-[#263449]"
            contentContainerClassName="px-5 py-3"
            showsVerticalScrollIndicator
            nestedScrollEnabled
          >
            {route.segments.map((segment, index) => {
              const boarding = segment.stops.find(
                (stop) => stop.role === "BOARDING",
              );
              const alighting = [...segment.stops]
                .reverse()
                .find((stop) => stop.role === "ALIGHTING");
              const color = segmentColor(segment);
              return (
                <View key={segment.segmentIndex} className="flex-row">
                  <View className="mr-3 items-center">
                    <View
                      className="h-9 w-9 items-center justify-center rounded-full"
                      style={{ backgroundColor: color }}
                    >
                      <Ionicons
                        name={segmentIcon(segment)}
                        size={18}
                        color="white"
                      />
                    </View>
                    {index < route.segments.length - 1 && (
                      <View
                        className="min-h-8 w-0.5 flex-1"
                        style={{ backgroundColor: `${color}55` }}
                      />
                    )}
                  </View>
                  <View className="mb-4 flex-1 rounded-2xl bg-[#F4F7FB] px-4 py-3 dark:bg-[#172033]">
                    <View className="flex-row items-start justify-between gap-3">
                      <Text
                        className="flex-1 text-sm font-black"
                        style={{ color }}
                      >
                        {segmentLabel(segment)}
                      </Text>
                      <Text className="text-xs font-bold text-[#64748B] dark:text-[#94A3B8]">
                        {formatDuration(segment.estimatedSeconds)} ·{" "}
                        {formatDistance(segment.distanceMeters)}
                      </Text>
                    </View>
                    {!!segment.guidance && (
                      <Text className="mt-1.5 text-sm leading-5 text-[#334155] dark:text-[#CBD5E1]">
                        {segment.guidance}
                      </Text>
                    )}
                    {(boarding || alighting) && (
                      <Text className="mt-2 text-xs font-semibold text-[#64748B] dark:text-[#94A3B8]">
                        {[boarding?.name, alighting?.name]
                          .filter(Boolean)
                          .join(" → ")}
                      </Text>
                    )}
                  </View>
                </View>
              );
            })}
          </ScrollView>
        </SafeAreaView>
      </Animated.View>
    </View>
  );
}

export default function CourseNavigationScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { height: mapViewportHeight } = useWindowDimensions();
  const courseId = Number(id);
  const isDark = useThemeStore((state) => state.isDark);
  const mapRef = useRef<MapView>(null);
  const previousProgressRef = useRef<RouteProgress | null>(null);
  const offRouteSamplesRef = useRef(0);
  const hasFitRouteRef = useRef(false);
  const [mapReady, setMapReady] = useState(false);
  const [mapLatitudeDelta, setMapLatitudeDelta] = useState(0.012);
  const [showDirectionsMarkers, setShowDirectionsMarkers] = useState(false);
  const [permissionDenied, setPermissionDenied] = useState(false);
  const [isLocating, setIsLocating] = useState(true);
  const [userLocation, setUserLocation] = useState<UserLocation | null>(null);
  const [navigationStarted, setNavigationStarted] = useState(false);
  const [followUser, setFollowUser] = useState(true);
  const [progress, setProgress] = useState<RouteProgress | null>(null);
  const [isOffRoute, setIsOffRoute] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const [isDirectionsOpen, setIsDirectionsOpen] = useState(false);
  const [directionsMode, setDirectionsMode] = useState<DirectionsMode>("WALK");
  const [directionsOrigin, setDirectionsOrigin] = useState<LatLng | null>(null);
  const [selectedDirectionsRoute, setSelectedDirectionsRoute] =
    useState<DirectionRoute | null>(null);

  const navigationQuery = useQuery({
    queryKey: ["course-navigation", courseId],
    queryFn: () => getCourseNavigation(courseId),
    enabled: Number.isFinite(courseId),
    staleTime: Infinity,
  });

  const directionsQuery = useQuery({
    queryKey: [
      "course-start-directions",
      courseId,
      directionsMode,
      directionsOrigin?.latitude.toFixed(5),
      directionsOrigin?.longitude.toFixed(5),
    ],
    queryFn: () => {
      if (!directionsOrigin) throw new Error("현재 위치가 필요합니다.");
      return getCourseStartDirections(
        courseId,
        directionsOrigin.latitude,
        directionsOrigin.longitude,
        directionsMode,
      );
    },
    enabled:
      isDirectionsOpen && directionsOrigin != null && Number.isFinite(courseId),
    retry: false,
    staleTime: 5 * 60 * 1000,
    gcTime: 10 * 60 * 1000,
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
  const selectedMapSegments = useMemo(
    () =>
      (selectedDirectionsRoute?.segments ?? [])
        .map((segment) => ({
          segment,
          coordinates: toMapCoordinates(segment.path?.coordinates ?? []),
        }))
        .filter((item) => item.coordinates.length > 1),
    [selectedDirectionsRoute],
  );
  const selectedRouteCoordinates = useMemo(
    () => selectedMapSegments.flatMap((item) => item.coordinates),
    [selectedMapSegments],
  );
  const selectedDirectionArrows = useMemo(() => {
    const metersPerScreenPoint =
      (mapLatitudeDelta * 111_320) / Math.max(mapViewportHeight, 1);
    const arrowSpacingMeters = Math.max(12, metersPerScreenPoint * 72);
    return selectedMapSegments.flatMap(({ segment, coordinates }) =>
      getDirectionArrows(coordinates, arrowSpacingMeters).map(
        (arrow, index) => ({
          ...arrow,
          color: segmentColor(segment),
          key: `${segment.segmentIndex}-${index}`,
        }),
      ),
    );
  }, [mapLatitudeDelta, mapViewportHeight, selectedMapSegments]);
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

  useEffect(() => {
    const destination = directionsQuery.data?.destination;
    if (!isDirectionsOpen || !mapReady || !directionsOrigin || !destination) {
      return;
    }

    mapRef.current?.fitToCoordinates(
      [
        directionsOrigin,
        {
          latitude: destination.latitude,
          longitude: destination.longitude,
        },
      ],
      {
        edgePadding: { top: 110, right: 52, bottom: 420, left: 52 },
        animated: true,
      },
    );
  }, [
    directionsOrigin,
    directionsQuery.data?.destination,
    isDirectionsOpen,
    mapReady,
  ]);

  useEffect(() => {
    if (!mapReady || selectedRouteCoordinates.length < 2) return;
    mapRef.current?.fitToCoordinates(selectedRouteCoordinates, {
      edgePadding: { top: 100, right: 44, bottom: 390, left: 44 },
      animated: true,
    });
  }, [mapReady, selectedRouteCoordinates]);

  const openDirectionsToStart = () => {
    if (!userLocation) {
      Alert.alert(
        "현재 위치를 확인 중이에요",
        "위치가 확인된 후 다시 눌러주세요.",
      );
      return;
    }
    setDirectionsOrigin({
      latitude: userLocation.latitude,
      longitude: userLocation.longitude,
    });
    setDirectionsMode("WALK");
    setSelectedDirectionsRoute(null);
    setIsDirectionsOpen(true);
  };

  const openKakaoDirections = async (directions: CourseStartDirections) => {
    if (!directions.landingUrl) return;
    try {
      const supported = await Linking.canOpenURL(directions.landingUrl);
      if (!supported) throw new Error("unsupported");
      await Linking.openURL(directions.landingUrl);
    } catch {
      Alert.alert("카카오맵을 열 수 없어요", "잠시 후 다시 시도해주세요.");
    }
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

  const startNavigation = (confirmedStartable = false) => {
    if (!canStartWalk && !confirmedStartable) return;
    previousProgressRef.current = null;
    offRouteSamplesRef.current = 0;
    setProgress(null);
    setIsOffRoute(false);
    setIsCompleted(false);
    setFollowUser(true);
    setNavigationStarted(true);
    setIsDirectionsOpen(false);
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
        onRegionChangeComplete={(region) => {
          setMapLatitudeDelta(region.latitudeDelta);
          setShowDirectionsMarkers(region.latitudeDelta <= 0.035);
        }}
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
            strokeColor={
              selectedDirectionsRoute
                ? isDark
                  ? "#475569"
                  : "#CBD5E1"
                : isOffRoute
                  ? "#E66B3D"
                  : "#087A3F"
            }
            strokeWidth={7}
            lineCap="round"
            lineJoin="round"
          />
        )}
        {selectedMapSegments.map(({ segment, coordinates }) => {
          const color = segmentColor(segment);
          return (
            <Fragment key={`direction-segment-${segment.segmentIndex}`}>
              <Polyline
                coordinates={coordinates}
                strokeColor={`${color}38`}
                strokeWidth={15}
                lineCap="round"
                lineJoin="round"
              />
              <Polyline
                coordinates={coordinates}
                strokeColor={color}
                strokeWidth={11}
                lineCap="round"
                lineJoin="round"
              />
              <Polyline
                coordinates={coordinates}
                strokeColor={lightenColor(color)}
                strokeWidth={7}
                lineCap="round"
                lineJoin="round"
              />
            </Fragment>
          );
        })}
        {selectedDirectionArrows.map((arrow) => (
          <Marker
            key={`direction-arrow-${arrow.key}`}
            coordinate={arrow.coordinate}
            anchor={{ x: 0.5, y: 0.5 }}
            tracksViewChanges={false}
            pointerEvents="none"
          >
            <View
              className="h-5 w-5 items-center justify-center"
              style={{ transform: [{ rotate: `${arrow.bearing - 90}deg` }] }}
            >
              <Ionicons
                name="chevron-forward"
                size={16}
                color="white"
                style={{
                  textShadowColor: arrow.color,
                  textShadowOffset: { width: 0, height: 0 },
                  textShadowRadius: 2,
                }}
              />
            </View>
          </Marker>
        ))}
        {showDirectionsMarkers &&
          selectedMapSegments
            .filter(
              ({ segment }, index, segments) =>
                index === 0 ||
                segmentIdentity(segments[index - 1].segment) !==
                  segmentIdentity(segment),
            )
            .map(({ segment, coordinates }) => {
              const color = segmentColor(segment);
              const showVehicleName =
                segment.mode === "BUS" || segment.mode === "SUBWAY";
              return (
                <Marker
                  key={`direction-marker-${segment.segmentIndex}`}
                  coordinate={coordinates[0]}
                  anchor={{ x: 0.5, y: 0.5 }}
                  tracksViewChanges={false}
                  accessibilityLabel={`${segmentLabel(segment)} 구간 시작`}
                >
                  <View
                    className="flex-row items-center rounded-full border-2 bg-white px-2 py-1 shadow-md dark:bg-[#0F172A]"
                    style={{ borderColor: color }}
                  >
                    <Ionicons
                      name={segmentIcon(segment)}
                      size={14}
                      color={color}
                    />
                    {showVehicleName && (
                      <Text
                        numberOfLines={1}
                        className="ml-1 max-w-24 text-[11px] font-black"
                        style={{ color }}
                      >
                        {segmentLabel(segment)}
                      </Text>
                    )}
                  </View>
                </Marker>
              );
            })}
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
                      ? "출발점 50m 이내입니다. 산책을 시작할 수 있어요."
                      : "안전하고 정확한 안내를 위해 출발점 50m 이내에서 산책을 시작할 수 있어요."}
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
                    onPress={() => startNavigation()}
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

      <DirectionsSheet
        visible={isDirectionsOpen && selectedDirectionsRoute == null}
        mode={directionsMode}
        data={directionsQuery.data}
        isPending={directionsQuery.isPending || directionsQuery.isFetching}
        isError={directionsQuery.isError}
        errorMessage={directionsQuery.error?.message}
        courseName={navigationQuery.data.name}
        isDark={isDark}
        onClose={() => {
          setSelectedDirectionsRoute(null);
          setIsDirectionsOpen(false);
        }}
        onModeChange={(nextMode) => {
          setSelectedDirectionsRoute(null);
          setDirectionsMode(nextMode);
        }}
        onRetry={() => void directionsQuery.refetch()}
        onOpenKakao={(directions) => void openKakaoDirections(directions)}
        onStart={startNavigation}
        onSelectRoute={setSelectedDirectionsRoute}
      />
      <RouteDetailSheet
        route={selectedDirectionsRoute}
        isDark={isDark}
        onBack={() => setSelectedDirectionsRoute(null)}
      />
    </View>
  );
}

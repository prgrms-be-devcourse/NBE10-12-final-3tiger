import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import MapView, { type Region as MapRegion } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";
import { getRegions } from "@/api/course-api";
import { searchPlaces, type PlaceSearchItem } from "@/api/place-api";
import { getMyProfile } from "@/api/user-api";
import { getWeatherSnapshot } from "@/api/weather-api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Button } from "@/components/ui/button";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { Region as ServiceRegion } from "@/types/domain";

// 위도 약 0.009도는 남북 약 1km로, 화면 중심 기준 반경 약 500m다.
const FIVE_HUNDRED_METER_VIEW = {
  latitudeDelta: 0.009,
  longitudeDelta: 0.009,
} as const;

const DEFAULT_REGION: MapRegion = {
  latitude: 37.5445,
  longitude: 127.0374,
  ...FIVE_HUNDRED_METER_VIEW,
};

const CURRENT_LOCATION_TIMEOUT_MS = 3_000;

const isValidCoordinate = (latitude?: number, longitude?: number) =>
  Number.isFinite(latitude) && Number.isFinite(longitude);

const toRegion = (location: Location.LocationObject): MapRegion => ({
  latitude: location.coords.latitude,
  longitude: location.coords.longitude,
  ...FIVE_HUNDRED_METER_VIEW,
});

async function getCurrentLocationWithin(
  timeoutMs: number,
): Promise<Location.LocationObject | null> {
  let timeoutId: ReturnType<typeof setTimeout> | undefined;

  try {
    return await Promise.race([
      Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      }),
      new Promise<null>((resolve) => {
        timeoutId = setTimeout(() => resolve(null), timeoutMs);
      }),
    ]);
  } finally {
    if (timeoutId) clearTimeout(timeoutId);
  }
}

type BboxBounds = {
  minLat: number;
  maxLat: number;
  minLng: number;
  maxLng: number;
};

function parseBboxBounds(geoJson: string): BboxBounds | null {
  try {
    const parsed = JSON.parse(geoJson) as { coordinates?: number[][][] };
    const ring = parsed.coordinates?.[0];
    if (!ring || ring.length < 3) return null;
    let minLat = Infinity;
    let maxLat = -Infinity;
    let minLng = Infinity;
    let maxLng = -Infinity;
    for (const point of ring) {
      const [lng, lat] = point;
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
      if (lat < minLat) minLat = lat;
      if (lat > maxLat) maxLat = lat;
      if (lng < minLng) minLng = lng;
      if (lng > maxLng) maxLng = lng;
    }
    if (!Number.isFinite(minLat)) return null;
    return { minLat, maxLat, minLng, maxLng };
  } catch {
    return null;
  }
}

export default function MapScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const mapRef = useRef<MapView>(null);
  const lastViewedRegionRef = useRef<MapRegion | null>(null);
  const { height: windowHeight } = useWindowDimensions();
  const regionsSheetTranslateY = useRef(
    new Animated.Value(windowHeight),
  ).current;
  const [query, setQuery] = useState("");
  const [locating, setLocating] = useState(true);
  const [showLocationLoading, setShowLocationLoading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [placeResults, setPlaceResults] = useState<PlaceSearchItem[]>([]);
  const [regionsOpen, setRegionsOpen] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [mapCenter, setMapCenter] = useState<{
    latitude: number;
    longitude: number;
  }>({
    latitude: DEFAULT_REGION.latitude,
    longitude: DEFAULT_REGION.longitude,
  });
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const regionsQuery = useQuery({
    queryKey: ["regions"],
    queryFn: getRegions,
    staleTime: 5 * 60 * 1000,
  });
  const serviceRegions = regionsQuery.data ?? [];
  const regionBounds = useMemo(
    () =>
      serviceRegions
        .map((region) => {
          const bounds = parseBboxBounds(region.bbox);
          return bounds ? { region, bounds } : null;
        })
        .filter(
          (entry): entry is { region: ServiceRegion; bounds: BboxBounds } =>
            entry !== null,
        ),
    [serviceRegions],
  );
  const activeRegion = useMemo(() => {
    for (const { region, bounds } of regionBounds) {
      if (
        mapCenter.latitude >= bounds.minLat &&
        mapCenter.latitude <= bounds.maxLat &&
        mapCenter.longitude >= bounds.minLng &&
        mapCenter.longitude <= bounds.maxLng
      ) {
        return region;
      }
    }
    return null;
  }, [regionBounds, mapCenter]);
  const weatherQuery = useQuery({
    queryKey: ["weather", activeRegion?.regionCode ?? "none"],
    queryFn: () =>
      activeRegion
        ? getWeatherSnapshot(activeRegion.centerLat, activeRegion.centerLng)
        : Promise.resolve(null),
    enabled: !!activeRegion,
    staleTime: 10 * 60 * 1000,
  });
  const upcomingWeather = activeRegion ? weatherQuery.data?.upcoming ?? null : null;
  const upcomingBannerText =
    upcomingWeather && activeRegion
      ? (() => {
          const label = upcomingWeather.type === "rain" ? "비" : "눈";
          const when =
            upcomingWeather.hoursFromNow <= 0
              ? "지금"
              : `${upcomingWeather.hoursFromNow}시간 후`;
          return `${activeRegion.name}에 ${when} ${label} 소식이 있어요`;
        })()
      : null;
  const moveTo = useCallback((next: MapRegion) => {
    if (!isValidCoordinate(next.latitude, next.longitude)) return;
    lastViewedRegionRef.current = next;
    mapRef.current?.animateToRegion(next, 500);
  }, []);

  const locate = useCallback(async () => {
    setLocating(true);
    setMessage(null);

    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== Location.PermissionStatus.GRANTED) {
        setMessage("위치 권한이 없어 서울숲을 표시하고 있어요.");
        moveTo(DEFAULT_REGION);
        return;
      }

      const lastKnown = await Location.getLastKnownPositionAsync({
        maxAge: 60_000,
        requiredAccuracy: 500,
      });

      if (
        lastKnown &&
        isValidCoordinate(lastKnown.coords.latitude, lastKnown.coords.longitude)
      ) {
        moveTo(toRegion(lastKnown));
        return;
      }

      setShowLocationLoading(true);
      const current = await getCurrentLocationWithin(
        CURRENT_LOCATION_TIMEOUT_MS,
      );

      if (
        current &&
        isValidCoordinate(current.coords.latitude, current.coords.longitude)
      ) {
        moveTo(toRegion(current));
        return;
      }

      moveTo(DEFAULT_REGION);
      setMessage("현재 위치를 찾지 못해 서울숲을 표시하고 있어요.");
    } catch {
      moveTo(DEFAULT_REGION);
      setMessage("현재 위치를 확인하지 못해 서울숲을 표시하고 있어요.");
    } finally {
      setShowLocationLoading(false);
      setLocating(false);
    }
  }, [moveTo]);

  const searchPlace = useCallback(async () => {
    const keyword = query.trim();
    if (!keyword) return;

    Keyboard.dismiss();
    setSearching(true);
    setMessage(null);

    try {
      const results = await searchPlaces(keyword);
      setPlaceResults(results);
      if (results.length === 0) {
        setMessage(
          "검색 결과가 없어요. 다른 동네나 공원 이름을 입력해 주세요.",
        );
        return;
      }
    } catch {
      setPlaceResults([]);
      setMessage("장소를 검색하지 못했어요. 네트워크 연결을 확인해 주세요.");
    } finally {
      setSearching(false);
    }
  }, [query]);

  const dismissRegionsSheet = useCallback(
    () =>
      dismissBottomSheet(regionsSheetTranslateY, windowHeight, () =>
        setRegionsOpen(false),
      ),
    [regionsSheetTranslateY, windowHeight],
  );

  useEffect(() => {
    if (!regionsOpen) return;
    regionsSheetTranslateY.setValue(windowHeight);
    Animated.timing(regionsSheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [regionsOpen, regionsSheetTranslateY, windowHeight]);

  useFocusEffect(
    useCallback(() => {
      const lastViewedRegion = lastViewedRegionRef.current;
      if (!lastViewedRegion) {
        void locate();
        return;
      }

      const animationFrame = requestAnimationFrame(() => {
        mapRef.current?.animateToRegion(lastViewedRegion, 0);
      });
      return () => cancelAnimationFrame(animationFrame);
    }, [locate]),
  );

  const updateRegion = useCallback((next: MapRegion) => {
    if (!isValidCoordinate(next.latitude, next.longitude)) return;
    if (lastViewedRegionRef.current) lastViewedRegionRef.current = next;
    setMapCenter((prev) =>
      Math.abs(prev.latitude - next.latitude) < 0.005 &&
      Math.abs(prev.longitude - next.longitude) < 0.005
        ? prev
        : { latitude: next.latitude, longitude: next.longitude },
    );
  }, []);

  const moveToServiceRegion = useCallback(
    (item: ServiceRegion) => {
      moveTo({
        latitude: item.centerLat,
        longitude: item.centerLng,
        ...FIVE_HUNDRED_METER_VIEW,
      });
      dismissRegionsSheet();
    },
    [dismissRegionsSheet, moveTo],
  );

  const exploreServiceRegion = useCallback(
    (item: ServiceRegion) => {
      dismissRegionsSheet();
      router.push({
        pathname: "/(tabs)/course",
        params: {
          regionCode: item.regionCode,
          regionName: item.name,
          lat: String(item.centerLat),
          lng: String(item.centerLng),
        },
      });
    },
    [dismissRegionsSheet],
  );

  return (
    <View className="flex-1 bg-[#E8F0E5] dark:bg-[#111411]">
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={DEFAULT_REGION}
        showsUserLocation
        showsMyLocationButton={false}
        showsCompass={false}
        mapType="standard"
        userInterfaceStyle={isDark ? "dark" : "light"}
        mapPadding={{ top: 120, right: 16, bottom: 170, left: 16 }}
        onRegionChangeComplete={updateRegion}
      />

      {showLocationLoading && (
        <View
          style={StyleSheet.absoluteFill}
          className="z-20 items-center justify-center bg-[#111411]/80"
          accessibilityLiveRegion="polite"
        >
          <View className="items-center gap-3 rounded-3xl bg-white px-7 py-6 shadow-lg dark:bg-[#1B211D]">
            <ActivityIndicator size="large" color="#22C55E" />
            <Text className="text-[15px] font-bold text-[#24372A] dark:text-[#F1F5F2]">
              내 위치를 찾고 있어요
            </Text>
            <Text className="text-xs text-[#6D7B6D] dark:text-[#AAB5AD]">
              잠시만 기다려 주세요
            </Text>
          </View>
        </View>
      )}

      <SafeAreaView
        edges={["top"]}
        className="px-[18px]"
        pointerEvents="box-none"
      >
        <View className="mt-1.5 flex-row items-center">
          <View className="h-[54px] flex-1 flex-row items-center gap-2.5 rounded-[18px] bg-white px-[17px] shadow-md dark:bg-[#1B211D]">
            <Ionicons name="search" size={21} color="#526056" />
            <TextInput
              className="flex-1 text-[15px] font-semibold text-[#24372A] dark:text-[#F1F5F2]"
              placeholder="동네나 공원을 검색해 보세요"
              placeholderTextColor="#7A847C"
              returnKeyType="search"
              value={query}
              onChangeText={setQuery}
              onSubmitEditing={() => void searchPlace()}
            />
            {searching && <ActivityIndicator size="small" color="#087A3F" />}
            <Button
              variant="ghost"
              size="icon"
              accessibilityLabel="마이페이지로 이동"
              className="-mr-2 h-11 w-11 rounded-full p-0"
              onPress={() => router.push("/(tabs)/profile" as never)}
            >
              <Avatar
                alt="프로필 사진"
                className="h-9 w-9 border border-[#DDE5DA] bg-[#EEF6EB]"
              >
                <AvatarImage
                  source={
                    profileQuery.data?.profileImageUrl
                      ? { uri: profileQuery.data.profileImageUrl }
                      : DEFAULT_PROFILE_IMAGE
                  }
                />
                <AvatarFallback className="bg-[#EEF6EB]" />
              </Avatar>
            </Button>
          </View>
        </View>
        {upcomingBannerText && (
          <View
            accessibilityLiveRegion="polite"
            className="mt-2 flex-row items-center gap-2 rounded-2xl bg-[#E7F0FB] px-3 py-2.5 shadow-md dark:bg-[#1F2A38]"
          >
            <Ionicons
              name={upcomingWeather?.type === "snow" ? "snow-outline" : "water-outline"}
              size={18}
              color="#2563EB"
            />
            <Text className="flex-1 text-[13px] font-semibold text-[#1E3A5F] dark:text-[#BCD3EE]">
              {upcomingBannerText}
            </Text>
          </View>
        )}
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 pt-3 pr-[18px]"
        >
          <View className="h-[38px] flex-row items-center gap-1 rounded-full bg-[#E9FBEF] px-3 dark:bg-[#24382B]">
            <Ionicons name="location" size={16} color="#087A3F" />
            <Text className="text-[13px] font-bold text-[#24372A] dark:text-[#D4DDD6]">
              내 주변
            </Text>
          </View>
          <Button
            variant="secondary"
            accessibilityLabel="탐색 가능 구역 보기"
            className="h-[38px] rounded-full bg-white px-3 dark:bg-[#1B211D]"
            onPress={() => setRegionsOpen(true)}
          >
            <Ionicons name="map-outline" size={16} color="#087A3F" />
            <Text className="text-[13px] font-bold text-[#24372A] dark:text-[#D4DDD6]">
              탐색 가능 구역
            </Text>
          </Button>
        </ScrollView>
        {placeResults.length > 0 && (
          <View className="mt-2 max-h-64 overflow-hidden rounded-2xl bg-white shadow-lg dark:bg-[#1B211D]">
            <ScrollView keyboardShouldPersistTaps="handled">
              {placeResults.map((place, index) => (
                <Pressable
                  key={`${place.latitude}-${place.longitude}-${index}`}
                  className="flex-row items-center border-b border-[#EEF1EE] px-4 py-3 dark:border-[#343D36]"
                  onPress={() => {
                    Keyboard.dismiss();
                    moveTo({
                      latitude: place.latitude,
                      longitude: place.longitude,
                      ...FIVE_HUNDRED_METER_VIEW,
                    });
                    setPlaceResults([]);
                  }}
                >
                  <View className="h-9 w-9 items-center justify-center rounded-xl bg-[#E9FBEF] dark:bg-[#24382B]">
                    <Ionicons name="location" size={18} color="#087A3F" />
                  </View>
                  <View className="ml-3 flex-1">
                    <Text className="text-sm font-extrabold text-[#24372A] dark:text-[#F1F5F2]">
                      {place.name}
                    </Text>
                    <Text
                      numberOfLines={1}
                      className="mt-0.5 text-xs text-[#6D7B6D] dark:text-[#AAB5AD]"
                    >
                      {place.roadAddress || place.address || "주소 정보 없음"}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={18} color="#94A09A" />
                </Pressable>
              ))}
            </ScrollView>
          </View>
        )}
      </SafeAreaView>

      <View className="absolute bottom-7 left-[18px] right-[18px] items-end gap-3">
        {message && (
          <Text
            accessibilityLiveRegion="polite"
            className="self-center rounded-[10px] bg-[#26372C] px-3 py-2 text-xs text-white"
          >
            {message}
          </Text>
        )}
        <Button
          variant="secondary"
          size="icon"
          accessibilityLabel="현재 위치로 이동"
          className="h-[52px] w-[52px] rounded-[18px] bg-[#22C55E] shadow-md"
          disabled={locating}
          onPress={() => void locate()}
        >
          {locating ? (
            <ActivityIndicator color="white" />
          ) : (
            <Ionicons name="navigate" size={22} color="white" />
          )}
        </Button>
        <Button
          className="h-[68px] w-full justify-start rounded-[22px] bg-[#22C55E] px-4 shadow-lg"
          onPress={() => router.push("/course/generate" as never)}
        >
          <View className="h-9 w-9 items-center justify-center rounded-xl bg-[#BDF4CB]">
            <Ionicons name="sparkles" size={18} color="#087A3F" />
          </View>
          <View className="flex-1">
            <Text className="text-base font-extrabold text-white">
              나에게 딱 맞는 코스 발굴
            </Text>
            <Text className="mt-1 text-xs text-[#C9F4D5]">
              그늘 · 경사 · 동행자를 고려해 추천해요
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color="white" />
        </Button>
      </View>

      <Modal
        visible={regionsOpen}
        transparent
        animationType="none"
        onRequestClose={dismissRegionsSheet}
      >
        <View className="flex-1 justify-end">
          <Pressable
            accessibilityLabel="탐색 가능 구역 닫기"
            className="absolute inset-0 bg-black/40"
            onPress={dismissRegionsSheet}
          />
          <Animated.View
            className="h-[78%] rounded-t-[30px] bg-[#FCFDFC] pt-2.5 dark:bg-[#171C18]"
            style={{ transform: [{ translateY: regionsSheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={() => setRegionsOpen(false)}
              translateY={regionsSheetTranslateY}
              dismissDistance={windowHeight}
            />
            <View className="px-5 pb-3">
              <Text className="text-xl font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
                탐색 가능 구역
              </Text>
              <Text className="mt-1 text-sm text-[#6B756D] dark:text-[#AAB5AD]">
                코스를 제공하는 지역으로 지도를 이동할 수 있어요.
              </Text>
            </View>
            {regionsQuery.isPending ? (
              <View className="items-center gap-3 px-5 py-12">
                <ActivityIndicator color="#087A3F" />
                <Text className="text-sm text-[#6B756D] dark:text-[#AAB5AD]">
                  탐색 구역을 불러오는 중이에요
                </Text>
              </View>
            ) : regionsQuery.isError ? (
              <View className="items-center px-5 py-10">
                <Ionicons
                  name="alert-circle-outline"
                  size={30}
                  color="#DC2626"
                />
                <Text className="mt-3 text-sm font-bold text-[#191C1D] dark:text-[#F1F5F2]">
                  탐색 구역을 불러오지 못했어요
                </Text>
                <Button
                  variant="secondary"
                  className="mt-4 rounded-xl bg-[#E9FBEF] px-5 active:bg-[#D8F3E0]"
                  onPress={() => void regionsQuery.refetch()}
                >
                  <Text className="font-extrabold text-[#087A3F]">
                    다시 시도
                  </Text>
                </Button>
              </View>
            ) : (
              <ScrollView
                className="flex-1"
                contentContainerClassName="gap-2 px-5 pb-6"
                showsVerticalScrollIndicator
                nestedScrollEnabled
              >
                {regionsQuery.data?.map((item) => (
                  <View
                    key={item.regionCode}
                    className="flex-row items-center gap-3 rounded-2xl border border-[#E1E8E2] bg-white p-4 dark:border-[#343D36] dark:bg-[#1B211D]"
                  >
                    <View className="h-10 w-10 items-center justify-center rounded-full bg-[#E9FBEF]">
                      <Ionicons name="location" size={19} color="#087A3F" />
                    </View>
                    <View className="flex-1">
                      <Text className="font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
                        {item.name}
                      </Text>
                      <Text className="mt-0.5 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                        이용 가능한 코스 {item.courseCount}개
                      </Text>
                    </View>
                    <Button
                      variant="secondary"
                      size="sm"
                      accessibilityLabel={`${item.name} 코스 탐색`}
                      className="h-10 rounded-xl bg-[#E9FBEF] px-3 active:bg-[#D8F3E0]"
                      onPress={() => exploreServiceRegion(item)}
                    >
                      <Ionicons
                        name="compass-outline"
                        size={15}
                        color="#087A3F"
                      />
                      <Text className="text-xs font-extrabold text-[#087A3F]">
                        탐색
                      </Text>
                    </Button>
                    <Button
                      size="sm"
                      accessibilityLabel={`${item.name} 지도로 이동`}
                      className="h-10 rounded-xl bg-[#087A3F] px-4"
                      onPress={() => moveToServiceRegion(item)}
                    >
                      <Text className="text-xs font-extrabold text-white">
                        이동
                      </Text>
                    </Button>
                  </View>
                ))}
              </ScrollView>
            )}
          </Animated.View>
        </View>
      </Modal>
    </View>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useRef, useState } from "react";
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import MapView, { type Region } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";
import { getMyProfile } from "@/api/user-api";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/stores/auth-store";

const DEFAULT_REGION: Region = {
  latitude: 37.5445,
  longitude: 127.0374,
  latitudeDelta: 0.025,
  longitudeDelta: 0.018,
};

const CURRENT_LOCATION_TIMEOUT_MS = 3_000;

const isValidCoordinate = (latitude?: number, longitude?: number) =>
  Number.isFinite(latitude) && Number.isFinite(longitude);

const toRegion = (location: Location.LocationObject): Region => ({
  latitude: location.coords.latitude,
  longitude: location.coords.longitude,
  latitudeDelta: 0.018,
  longitudeDelta: 0.014,
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

const CALM_DARK_MAP_STYLE = [
  { elementType: "geometry", stylers: [{ color: "#111411" }] },
  { elementType: "labels.icon", stylers: [{ visibility: "off" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#8F9891" }] },
  { elementType: "labels.text.stroke", stylers: [{ color: "#111411" }] },
  {
    featureType: "administrative",
    elementType: "geometry.stroke",
    stylers: [{ color: "#343A35" }],
  },
  {
    featureType: "landscape",
    elementType: "geometry",
    stylers: [{ color: "#101310" }],
  },
  {
    featureType: "poi",
    elementType: "geometry",
    stylers: [{ color: "#171B18" }],
  },
  {
    featureType: "poi.park",
    elementType: "geometry",
    stylers: [{ color: "#18211B" }],
  },
  {
    featureType: "road",
    elementType: "geometry",
    stylers: [{ color: "#5E645F" }],
  },
  {
    featureType: "road",
    elementType: "geometry.stroke",
    stylers: [{ color: "#2F3430" }],
  },
  {
    featureType: "road.highway",
    elementType: "geometry",
    stylers: [{ color: "#777D78" }],
  },
  {
    featureType: "road.local",
    elementType: "geometry",
    stylers: [{ color: "#4D524E" }],
  },
  {
    featureType: "transit",
    elementType: "geometry",
    stylers: [{ color: "#242925" }],
  },
  {
    featureType: "water",
    elementType: "geometry",
    stylers: [{ color: "#090C0A" }],
  },
];

export default function MapScreen() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const mapRef = useRef<MapView>(null);
  const lastViewedRegionRef = useRef<Region | null>(null);
  const [query, setQuery] = useState("");
  const [locating, setLocating] = useState(true);
  const [showLocationLoading, setShowLocationLoading] = useState(false);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });

  const moveTo = useCallback((next: Region) => {
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
      const [result] = await Location.geocodeAsync(keyword);
      if (!result) {
        setMessage(
          "검색 결과가 없어요. 다른 동네나 공원 이름을 입력해 주세요.",
        );
        return;
      }
      moveTo({
        latitude: result.latitude,
        longitude: result.longitude,
        latitudeDelta: 0.018,
        longitudeDelta: 0.014,
      });
    } catch {
      setMessage("장소를 검색하지 못했어요. 네트워크 연결을 확인해 주세요.");
    } finally {
      setSearching(false);
    }
  }, [moveTo, query]);

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

  const updateRegion = useCallback((next: Region) => {
    if (!isValidCoordinate(next.latitude, next.longitude)) return;
    if (lastViewedRegionRef.current) lastViewedRegionRef.current = next;
  }, []);

  return (
    <View className="flex-1 bg-[#E8F0E5]">
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={DEFAULT_REGION}
        showsUserLocation
        showsMyLocationButton={false}
        showsCompass={false}
        customMapStyle={CALM_DARK_MAP_STYLE}
        userInterfaceStyle="dark"
        mapPadding={{ top: 120, right: 16, bottom: 170, left: 16 }}
        onRegionChangeComplete={updateRegion}
      />

      {showLocationLoading && (
        <View
          style={StyleSheet.absoluteFill}
          className="z-20 items-center justify-center bg-[#111411]/80"
          accessibilityLiveRegion="polite"
        >
          <View className="items-center gap-3 rounded-3xl bg-white px-7 py-6 shadow-lg">
            <ActivityIndicator size="large" color="#22C55E" />
            <Text className="text-[15px] font-bold text-[#24372A]">
              내 위치를 찾고 있어요
            </Text>
            <Text className="text-xs text-[#6D7B6D]">잠시만 기다려 주세요</Text>
          </View>
        </View>
      )}

      <SafeAreaView
        edges={["top"]}
        className="px-[18px]"
        pointerEvents="box-none"
      >
        <View className="mt-1.5 flex-row items-center">
          <View className="h-[54px] flex-1 flex-row items-center gap-2.5 rounded-[18px] bg-white px-[17px] shadow-md">
            <Ionicons name="search" size={21} color="#526056" />
            <TextInput
              className="flex-1 text-[15px] font-semibold text-[#24372A]"
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
                {profileQuery.data?.profileImageUrl && (
                  <AvatarImage
                    source={{ uri: profileQuery.data.profileImageUrl }}
                  />
                )}
                <AvatarFallback className="bg-[#EEF6EB]">
                  <Ionicons name="person" size={18} color="#365F49" />
                </AvatarFallback>
              </Avatar>
            </Button>
          </View>
        </View>
        <View className="mt-3 flex-row gap-2">
          <View className="h-[38px] flex-row items-center gap-1.5 rounded-full bg-white px-[13px]">
            <Ionicons name="partly-sunny" size={18} color="#E38A00" />
            <Text className="text-[13px] font-bold text-[#24372A]">
              산책하기 좋은 날
            </Text>
          </View>
          <View className="h-[38px] flex-row items-center gap-1 rounded-full bg-[#E9FBEF] px-3">
            <Ionicons name="location" size={16} color="#087A3F" />
            <Text className="text-[13px] font-bold text-[#24372A]">
              내 주변
            </Text>
          </View>
        </View>
      </SafeAreaView>

      <View className="absolute bottom-14 left-[18px] right-[18px] items-end gap-3">
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
          onPress={() => router.push("/(tabs)/course" as never)}
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
    </View>
  );
}

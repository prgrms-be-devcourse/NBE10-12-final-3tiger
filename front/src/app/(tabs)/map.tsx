import { Ionicons } from "@expo/vector-icons";
import * as Location from "expo-location";
import { router } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import MapView, { Marker, type Region } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

const DEFAULT_REGION: Region = {
  latitude: 37.5445,
  longitude: 127.0374,
  latitudeDelta: 0.025,
  longitudeDelta: 0.018,
};

export default function MapScreen() {
  const mapRef = useRef<MapView>(null);
  const [region, setRegion] = useState(DEFAULT_REGION);
  const [query, setQuery] = useState("");
  const [locating, setLocating] = useState(true);
  const [searching, setSearching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const moveTo = useCallback((next: Region) => {
    setRegion(next);
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

      const current = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      moveTo({
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
        latitudeDelta: 0.018,
        longitudeDelta: 0.014,
      });
    } catch {
      setMessage("현재 위치를 확인하지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
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

  useEffect(() => {
    void locate();
  }, [locate]);

  return (
    <View className="flex-1 bg-[#E8F0E5]">
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        initialRegion={DEFAULT_REGION}
        showsUserLocation
        showsMyLocationButton={false}
        showsCompass={false}
        mapPadding={{ top: 120, right: 16, bottom: 170, left: 16 }}
        onRegionChangeComplete={setRegion}
      >
        <Marker
          title="산책 추천 장소"
          coordinate={{
            latitude: region.latitude + 0.003,
            longitude: region.longitude - 0.002,
          }}
        >
          <View className="h-[42px] w-[42px] items-center justify-center rounded-full border-[3px] border-white bg-[#087A3F]">
            <Ionicons name="leaf" size={19} color="white" />
          </View>
        </Marker>
        <Marker
          title="반려동물 추천 장소"
          coordinate={{
            latitude: region.latitude - 0.004,
            longitude: region.longitude + 0.003,
          }}
        >
          <View className="h-[42px] w-[42px] items-center justify-center rounded-full border-[3px] border-white bg-amber-500">
            <Ionicons name="paw" size={19} color="white" />
          </View>
        </Marker>
      </MapView>

      <SafeAreaView
        edges={["top"]}
        className="px-[18px]"
        pointerEvents="box-none"
      >
        <View className="mt-1.5 flex-row items-center gap-2.5">
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
          </View>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="알림"
            className="h-[52px] w-[52px] items-center justify-center rounded-[18px] bg-white shadow-md"
          >
            <Ionicons name="notifications-outline" size={22} color="#203126" />
          </Pressable>
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

      <View className="absolute bottom-[18px] left-[18px] right-[18px] items-end gap-3">
        {message && (
          <Text
            accessibilityLiveRegion="polite"
            className="self-center rounded-[10px] bg-[#26372C] px-3 py-2 text-xs text-white"
          >
            {message}
          </Text>
        )}
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="현재 위치로 이동"
          className="h-[52px] w-[52px] items-center justify-center rounded-[18px] bg-white shadow-md"
          disabled={locating}
          onPress={() => void locate()}
        >
          {locating ? (
            <ActivityIndicator color="#087A3F" />
          ) : (
            <Ionicons name="navigate" size={22} color="#087A3F" />
          )}
        </Pressable>
        <Pressable
          accessibilityRole="button"
          className="min-h-[82px] w-full flex-row items-center gap-3 rounded-3xl bg-[#087A3F] px-4 shadow-lg"
          onPress={() => router.push("/(tabs)/course" as never)}
        >
          <View className="h-[42px] w-[42px] items-center justify-center rounded-[14px] bg-[#BDF4CB]">
            <Ionicons name="sparkles" size={20} color="#087A3F" />
          </View>
          <View className="flex-1">
            <Text className="text-[17px] font-extrabold text-white">
              나에게 딱 맞는 코스 발굴
            </Text>
            <Text className="mt-1 text-xs text-[#C9F4D5]">
              그늘 · 경사 · 동행자를 고려해 추천해요
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color="white" />
        </Pressable>
      </View>
    </View>
  );
}

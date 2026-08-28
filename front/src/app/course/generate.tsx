import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router } from "expo-router";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
} from "react-native";
import MapView, { Marker, Polyline, type Region } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  generateCourseCandidates,
  saveGeneratedCourse,
} from "@/api/course-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";
import type { GenerateCandidate } from "@/types/domain";

const DEFAULT_COORDS = { latitude: 37.5462, longitude: 127.0372 };

const DISTANCE_OPTIONS = [
  { value: 1000, label: "1km" },
  { value: 3000, label: "3km" },
  { value: 5000, label: "5km" },
  { value: 8000, label: "8km" },
];

const PERSONA_OPTIONS: Array<{ key: string | null; label: string }> = [
  { key: null, label: "전체" },
  { key: "walker", label: "일반" },
  { key: "dog", label: "반려견" },
  { key: "senior", label: "시니어" },
  { key: "stroller", label: "유모차" },
];

const CANDIDATE_COLORS = ["#087A3F", "#F97316", "#A855F7"];

const toPolyline = (candidate: GenerateCandidate) =>
  (candidate.path.coordinates ?? []).map(([lng, lat]) => ({
    latitude: lat,
    longitude: lng,
  }));

export default function CourseGenerateScreen() {
  const queryClient = useQueryClient();
  const mapRef = useRef<MapView>(null);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [coords, setCoords] = useState(DEFAULT_COORDS);
  const [locating, setLocating] = useState(false);
  const [distanceM, setDistanceM] = useState(3000);
  const [persona, setPersona] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<GenerateCandidate[]>([]);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
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
  }, []);

  const useMyLocation = async () => {
    setLocating(true);
    try {
      const permission = await Location.requestForegroundPermissionsAsync();
      if (permission.status !== Location.PermissionStatus.GRANTED) {
        setErrorMessage("위치 권한이 없어 기본 위치를 사용해요.");
        return;
      }
      const current = await Location.getCurrentPositionAsync({
        accuracy: Location.Accuracy.Balanced,
      });
      const next = {
        latitude: current.coords.latitude,
        longitude: current.coords.longitude,
      };
      setCoords(next);
      mapRef.current?.animateToRegion(
        { ...next, latitudeDelta: 0.02, longitudeDelta: 0.02 },
        400,
      );
    } catch {
      setErrorMessage("현재 위치를 가져오지 못했어요.");
    } finally {
      setLocating(false);
    }
  };

  const generateMutation = useMutation({
    mutationFn: generateCourseCandidates,
    onSuccess: (result) => {
      const list = result?.candidates ?? [];
      setCandidates(list);
      setSelectedIndex(list.length > 0 ? 0 : null);
      setErrorMessage(
        list.length === 0
          ? "이 조건으로 만들 수 있는 코스가 없어요. 거리나 페르소나를 바꿔 보세요."
          : null,
      );
    },
    onError: (error: Error) => {
      setCandidates([]);
      setSelectedIndex(null);
      setErrorMessage(error.message);
    },
  });

  const saveMutation = useMutation({
    mutationFn: saveGeneratedCourse,
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: ["courses"] });
      router.replace(`/course/${result.courseId}` as never);
    },
    onError: (error: Error) => setErrorMessage(error.message),
  });

  const handleGenerate = () => {
    setErrorMessage(null);
    generateMutation.mutate({
      lat: coords.latitude,
      lng: coords.longitude,
      distanceM,
      persona: persona ?? undefined,
    });
  };

  const handleSave = () => {
    if (!isAuthenticated) {
      setLoginRequiredOpen(true);
      return;
    }
    if (selectedIndex === null) return;
    const picked = candidates[selectedIndex];
    if (!picked) return;
    saveMutation.mutate({ path: picked.path, regionCode: picked.regionCode });
  };

  const mapRegion: Region = useMemo(
    () => ({
      latitude: coords.latitude,
      longitude: coords.longitude,
      latitudeDelta: 0.02,
      longitudeDelta: 0.02,
    }),
    [coords],
  );

  const isBusy = generateMutation.isPending || saveMutation.isPending;

  return (
    <SafeAreaView className="flex-1 bg-[#F2F7F2]" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between bg-white px-3">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          onPress={() => router.back()}
        >
          <Ionicons name="arrow-back" size={23} color="#203126" />
        </Button>
        <Text className="text-lg font-black text-[#18271D]">코스 생성</Text>
        <View className="w-10" />
      </View>

      <ScrollView contentContainerClassName="gap-3 p-4 pb-24">
        <View className="overflow-hidden rounded-2xl bg-white">
          <MapView
            ref={mapRef}
            style={styles.map}
            region={mapRegion}
          >
            <Marker coordinate={coords} pinColor="#087A3F" />
            {candidates.map((candidate, index) => (
              <Polyline
                key={index}
                coordinates={toPolyline(candidate)}
                strokeColor={CANDIDATE_COLORS[index] ?? "#087A3F"}
                strokeWidth={selectedIndex === index ? 6 : 3}
              />
            ))}
          </MapView>
        </View>

        <View className="rounded-2xl bg-white p-4">
          <View className="flex-row items-center justify-between">
            <Text className="text-sm font-extrabold text-[#18271D]">
              출발 위치
            </Text>
            <Button
              variant="ghost"
              size="sm"
              onPress={() => void useMyLocation()}
              disabled={locating}
            >
              {locating ? (
                <ActivityIndicator size="small" color="#087A3F" />
              ) : (
                <Ionicons name="locate" size={16} color="#087A3F" />
              )}
              <Text className="text-xs font-bold text-[#087A3F]">
                내 위치로
              </Text>
            </Button>
          </View>
          <Text className="mt-1 text-xs text-[#6B756D]">
            {coords.latitude.toFixed(5)}, {coords.longitude.toFixed(5)}
          </Text>
        </View>

        <View className="rounded-2xl bg-white p-4">
          <Text className="text-sm font-extrabold text-[#18271D]">거리</Text>
          <View className="mt-2 flex-row gap-2">
            {DISTANCE_OPTIONS.map((option) => {
              const active = distanceM === option.value;
              return (
                <Pressable
                  key={option.value}
                  className={`h-11 flex-1 items-center justify-center rounded-xl border ${active ? "border-[#087A3F] bg-[#E9FBEF]" : "border-slate-200 bg-[#F8FAF8]"}`}
                  onPress={() => setDistanceM(option.value)}
                >
                  <Text
                    className={`text-sm font-bold ${active ? "text-[#087A3F]" : "text-[#526056]"}`}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View className="rounded-2xl bg-white p-4">
          <Text className="text-sm font-extrabold text-[#18271D]">
            페르소나
          </Text>
          <View className="mt-2 flex-row flex-wrap gap-2">
            {PERSONA_OPTIONS.map((option) => {
              const active = persona === option.key;
              return (
                <Pressable
                  key={option.label}
                  className={`h-10 rounded-full border px-4 ${active ? "border-[#087A3F] bg-[#E9FBEF]" : "border-slate-200 bg-[#F8FAF8]"}`}
                  onPress={() => setPersona(option.key)}
                >
                  <View className="h-full items-center justify-center">
                    <Text
                      className={`text-xs font-bold ${active ? "text-[#087A3F]" : "text-[#526056]"}`}
                    >
                      {option.label}
                    </Text>
                  </View>
                </Pressable>
              );
            })}
          </View>
        </View>

        <Button
          className="h-14 rounded-2xl"
          disabled={isBusy}
          onPress={handleGenerate}
        >
          {generateMutation.isPending ? (
            <ActivityIndicator color="white" />
          ) : (
            <Ionicons name="sparkles" size={18} color="white" />
          )}
          <Text className="text-base font-black text-white">
            {candidates.length > 0 ? "다시 생성" : "코스 후보 만들기"}
          </Text>
        </Button>

        {errorMessage && (
          <View className="rounded-xl bg-[#FEE2E2] p-3">
            <Text className="text-xs font-bold text-[#B91C1C]">
              {errorMessage}
            </Text>
          </View>
        )}

        {candidates.length > 0 && (
          <View className="gap-2">
            <Text className="mt-1 text-xs font-bold text-[#6B756D]">
              마음에 드는 코스를 선택하고 저장하세요
            </Text>
            {candidates.map((candidate, index) => {
              const selected = selectedIndex === index;
              return (
                <Pressable
                  key={index}
                  className={`flex-row items-center gap-3 rounded-2xl border-2 bg-white p-4 ${selected ? "" : "border-transparent"}`}
                  style={
                    selected
                      ? {
                          borderColor:
                            CANDIDATE_COLORS[index] ?? "#087A3F",
                        }
                      : undefined
                  }
                  onPress={() => setSelectedIndex(index)}
                >
                  <View
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{
                      backgroundColor:
                        CANDIDATE_COLORS[index] ?? "#087A3F",
                    }}
                  >
                    <Text className="text-sm font-black text-white">
                      #{index + 1}
                    </Text>
                  </View>
                  <View className="flex-1">
                    <Text className="text-sm font-extrabold text-[#18271D]">
                      {(candidate.totalM / 1000).toFixed(2)}km
                    </Text>
                    <Text className="mt-0.5 text-[11px] text-[#6B756D]">
                      점수 {Number(candidate.avgScore ?? 0).toFixed(2)} · 오차{" "}
                      {Number(candidate.errorPct ?? 0).toFixed(1)}%
                    </Text>
                  </View>
                  <Ionicons
                    name={selected ? "checkmark-circle" : "ellipse-outline"}
                    size={22}
                    color={selected ? "#087A3F" : "#94A09A"}
                  />
                </Pressable>
              );
            })}
            <Button
              className="mt-2 h-14 rounded-2xl"
              disabled={selectedIndex === null || isBusy}
              onPress={handleSave}
            >
              {saveMutation.isPending ? (
                <ActivityIndicator color="white" />
              ) : (
                <Ionicons name="bookmark" size={18} color="white" />
              )}
              <Text className="text-base font-black text-white">
                선택한 코스 저장
              </Text>
            </Button>
          </View>
        )}
      </ScrollView>

      <LoginRequiredModal
        visible={loginRequiredOpen}
        onClose={() => setLoginRequiredOpen(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  map: {
    width: "100%",
    height: 260,
  },
});

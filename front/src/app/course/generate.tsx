import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Location from "expo-location";
import { router } from "expo-router";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from "react-native";
import MapView, {
  Marker,
  Polyline,
  type MapPressEvent,
  type Region,
} from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  generateCourseCandidates,
  saveGeneratedCourse,
} from "@/api/course-api";
import { searchPlaces, type PlaceSearchItem } from "@/api/place-api";
import { LoginRequiredModal } from "@/components/auth/login-required-modal";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { getMyProfile } from "@/api/user-api";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
import type { GenerateCandidate } from "@/types/domain";

const DEFAULT_COORDS = { latitude: 37.5462, longitude: 127.0372 };

type CourseMode = "loop" | "oneway";

const MODE_OPTIONS: Array<{ key: CourseMode; label: string; hint: string }> = [
  { key: "loop", label: "순환", hint: "출발지로 돌아오는 코스" },
  { key: "oneway", label: "편도", hint: "도착지까지 가는 코스" },
];

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
  const isDark = useThemeStore((state) => state.isDark);
  const [loginRequiredOpen, setLoginRequiredOpen] = useState(false);
  const [mode, setMode] = useState<CourseMode>("loop");
  const [coords, setCoords] = useState(DEFAULT_COORDS);
  const [endCoords, setEndCoords] = useState<{
    latitude: number;
    longitude: number;
  } | null>(null);
  const [placeSearchOpen, setPlaceSearchOpen] = useState(false);
  const [placeQuery, setPlaceQuery] = useState("");
  const [placeResults, setPlaceResults] = useState<PlaceSearchItem[]>([]);
  const [placeSearching, setPlaceSearching] = useState(false);
  const [placeSearchError, setPlaceSearchError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const [distanceM, setDistanceM] = useState(3000);
  const [persona, setPersona] = useState<string | null>(null);
  const [personaSelected, setPersonaSelected] = useState(false);
  const [candidates, setCandidates] = useState<GenerateCandidate[]>([]);
  const [selectedIndex, setSelectedIndex] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [addressQuery, setAddressQuery] = useState("");
  const [geocoding, setGeocoding] = useState(false);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });

  useEffect(() => {
    const preferredPersona = profileQuery.data?.primaryPersona;
    if (!personaSelected && preferredPersona) setPersona(preferredPersona);
  }, [personaSelected, profileQuery.data?.primaryPersona]);

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

  const resetCandidates = () => {
    setCandidates([]);
    setSelectedIndex(null);
  };

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
      resetCandidates();
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

  const handleMapPress = (event: MapPressEvent) => {
    const { latitude, longitude } = event.nativeEvent.coordinate;
    if (mode === "loop") {
      setCoords({ latitude, longitude });
    } else {
      setEndCoords({ latitude, longitude });
    }
    resetCandidates();
    setErrorMessage(null);
  };

  const handleModeChange = (next: CourseMode) => {
    if (next === mode) return;
    setMode(next);
    resetCandidates();
    setErrorMessage(null);
    if (next === "loop") setEndCoords(null);
  };

  const handleGeocode = async () => {
    const query = addressQuery.trim();
    if (!query) return;
    setGeocoding(true);
    setErrorMessage(null);
    try {
      const results = await Location.geocodeAsync(query);
      if (results.length === 0) {
        setErrorMessage("주소를 찾을 수 없어요. 다른 키워드로 검색해 보세요.");
        return;
      }
      const first = results[0];
      const next = { latitude: first.latitude, longitude: first.longitude };
      setEndCoords(next);
      resetCandidates();
      mapRef.current?.animateToRegion(
        { ...next, latitudeDelta: 0.02, longitudeDelta: 0.02 },
        400,
      );
    } catch {
      setErrorMessage("주소 검색 중 문제가 발생했어요.");
    } finally {
      setGeocoding(false);
    }
  };

  const openPlaceSearch = () => {
    setPlaceQuery("");
    setPlaceResults([]);
    setPlaceSearchError(null);
    setPlaceSearchOpen(true);
  };

  const handlePlaceSearch = async () => {
    const keyword = placeQuery.trim();
    if (!keyword || placeSearching) return;
    setPlaceSearching(true);
    setPlaceSearchError(null);
    try {
      const results = await searchPlaces(keyword);
      setPlaceResults(results);
      if (results.length === 0)
        setPlaceSearchError("검색 결과가 없어요. 장소명을 다시 입력해 주세요.");
    } catch {
      setPlaceResults([]);
      setPlaceSearchError("장소를 검색하지 못했어요. 네트워크 연결을 확인해 주세요.");
    } finally {
      setPlaceSearching(false);
    }
  };

  const selectPlace = (place: PlaceSearchItem) => {
    if (!place.supportedRegion) return;
    setCoords({ latitude: place.latitude, longitude: place.longitude });
    setCandidates([]);
    setSelectedIndex(null);
    setErrorMessage(null);
    setPlaceSearchOpen(false);
  };

  const generateMutation = useMutation({
    mutationFn: generateCourseCandidates,
    onSuccess: (result) => {
      const list = result?.candidates ?? [];
      setCandidates(list);
      setSelectedIndex(list.length > 0 ? 0 : null);
      setErrorMessage(
        list.length === 0
          ? mode === "oneway"
            ? "두 지점을 잇는 도보 경로를 찾지 못했어요. 도착지를 조금 옮겨 보세요."
            : "이 조건으로 만들 수 있는 코스가 없어요. 거리나 페르소나를 바꿔 보세요."
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
    if (mode === "oneway") {
      if (!endCoords) {
        setErrorMessage("도착지를 먼저 선택해 주세요.");
        return;
      }
      generateMutation.mutate({
        lat: coords.latitude,
        lng: coords.longitude,
        endLat: endCoords.latitude,
        endLng: endCoords.longitude,
        persona: persona ?? undefined,
      });
      return;
    }
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
    if (mode === "oneway" && endCoords) {
      saveMutation.mutate({
        path: picked.path,
        regionCode: picked.regionCode,
        isLoop: false,
        endLat: endCoords.latitude,
        endLng: endCoords.longitude,
      });
      return;
    }
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
  const isOneway = mode === "oneway";
  const canGenerate = !isBusy && (isOneway ? endCoords !== null : true);

  return (
    <SafeAreaView
      className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-14 flex-row items-center justify-between bg-white px-3 dark:bg-[#1B211D]">
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
        <Text className="text-lg text-[#006E2F] dark:text-[#F1F5F2]">
          코스 생성
        </Text>
        <View className="w-10" />
      </View>

      <ScrollView contentContainerClassName="gap-3 p-4 pb-24">
        <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
          <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
            코스 유형
          </Text>
          <View className="mt-2 flex-row gap-2">
            {MODE_OPTIONS.map((option) => {
              const active = mode === option.key;
              return (
                <Pressable
                  key={option.key}
                  className={`h-16 flex-1 items-center justify-center rounded-xl border ${active ? "border-[#087A3F] bg-[#E9FBEF] dark:bg-[#24382B]" : "border-slate-200 bg-[#F8FAF8] dark:border-[#343D36] dark:bg-[#242B26]"}`}
                  onPress={() => handleModeChange(option.key)}
                >
                  <Text
                    className={`text-sm font-bold ${active ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#526056] dark:text-[#AAB5AD]"}`}
                  >
                    {option.label}
                  </Text>
                  <Text
                    className={`mt-0.5 text-[10px] ${active ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#6B756D] dark:text-[#AAB5AD]"}`}
                  >
                    {option.hint}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View className="overflow-hidden rounded-2xl bg-white dark:bg-[#1B211D]">
          <MapView
            ref={mapRef}
            style={styles.map}
            region={mapRegion}
            userInterfaceStyle={isDark ? "dark" : "light"}
            onPress={handleMapPress}
          >
            <Marker coordinate={coords} pinColor="#087A3F" title="출발지" />
            {isOneway && endCoords && (
              <Marker
                coordinate={endCoords}
                pinColor="#F97316"
                title="도착지"
              />
            )}
            {candidates.map((candidate, index) => (
              <Polyline
                key={index}
                coordinates={toPolyline(candidate)}
                strokeColor={CANDIDATE_COLORS[index] ?? "#087A3F"}
                strokeWidth={selectedIndex === index ? 6 : 3}
              />
            ))}
          </MapView>
          <View className="px-3 py-2">
            <Text className="text-[11px] text-[#6B756D] dark:text-[#AAB5AD]">
              지도를 탭하면{" "}
              {isOneway ? "도착지" : "출발지"}가 그 지점으로 이동해요.
            </Text>
          </View>
        </View>

        <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
          <View className="flex-row items-center justify-between">
            <View className="flex-1 pr-2">
              <Text className="text-left text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
                출발 위치
              </Text>
              <Text className="mt-1 text-left text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                {coords.latitude.toFixed(5)}, {coords.longitude.toFixed(5)}
              </Text>
            </View>
            <View className="flex-row items-center gap-1">
              <Button variant="ghost" size="sm" onPress={openPlaceSearch}>
                <Ionicons name="search" size={16} color="#087A3F" />
                <Text className="text-xs font-bold text-[#087A3F]">검색</Text>
              </Button>
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
          </View>
        </View>

        {isOneway && (
          <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
            <View className="flex-row items-center justify-between">
              <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
                도착 위치
              </Text>
              {endCoords && (
                <Button
                  variant="ghost"
                  size="sm"
                  onPress={() => {
                    setEndCoords(null);
                    resetCandidates();
                  }}
                >
                  <Ionicons name="close-circle" size={16} color="#B91C1C" />
                  <Text className="text-xs font-bold text-[#B91C1C]">
                    선택 취소
                  </Text>
                </Button>
              )}
            </View>
            <Text className="mt-1 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
              {endCoords
                ? `${endCoords.latitude.toFixed(5)}, ${endCoords.longitude.toFixed(5)}`
                : "지도를 탭하거나 아래에서 주소를 검색하세요."}
            </Text>

            <View className="mt-3 flex-row items-center gap-2">
              <View className="h-11 flex-1 flex-row items-center rounded-xl border border-slate-200 bg-[#F8FAF8] px-3 dark:border-[#343D36] dark:bg-[#242B26]">
                <Ionicons name="search" size={16} color="#6B756D" />
                <TextInput
                  className="ml-2 flex-1 text-sm text-[#18271D] dark:text-[#F1F5F2]"
                  placeholder="주소 또는 장소 이름"
                  placeholderTextColor={isDark ? "#6B756D" : "#94A09A"}
                  value={addressQuery}
                  onChangeText={setAddressQuery}
                  onSubmitEditing={() => void handleGeocode()}
                  returnKeyType="search"
                  editable={!geocoding}
                />
              </View>
              <Button
                variant="ghost"
                size="sm"
                onPress={() => void handleGeocode()}
                disabled={geocoding || addressQuery.trim().length === 0}
              >
                {geocoding ? (
                  <ActivityIndicator size="small" color="#087A3F" />
                ) : (
                  <Text className="text-xs font-bold text-[#087A3F]">
                    검색
                  </Text>
                )}
              </Button>
            </View>
          </View>
        )}

        {!isOneway && (
          <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
            <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
              거리
            </Text>
            <View className="mt-2 flex-row gap-2">
              {DISTANCE_OPTIONS.map((option) => {
                const active = distanceM === option.value;
                return (
                  <Pressable
                    key={option.value}
                    className={`h-11 flex-1 items-center justify-center rounded-xl border ${active ? "border-[#087A3F] bg-[#E9FBEF] dark:bg-[#24382B]" : "border-slate-200 bg-[#F8FAF8] dark:border-[#343D36] dark:bg-[#242B26]"}`}
                    onPress={() => setDistanceM(option.value)}
                  >
                    <Text
                      className={`text-sm font-bold ${active ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#526056] dark:text-[#AAB5AD]"}`}
                    >
                      {option.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        )}

        <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
          <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
            페르소나
          </Text>
          <View className="mt-2 flex-row flex-wrap gap-2">
            {PERSONA_OPTIONS.map((option) => {
              const active = persona === option.key;
              return (
                <Pressable
                  key={option.label}
                  className={`h-10 rounded-full border px-4 ${active ? "border-[#087A3F] bg-[#E9FBEF] dark:bg-[#24382B]" : "border-slate-200 bg-[#F8FAF8] dark:border-[#343D36] dark:bg-[#242B26]"}`}
                  onPress={() => {
                    setPersonaSelected(true);
                    setPersona(option.key);
                  }}
                >
                  <View className="h-full items-center justify-center">
                    <Text
                      className={`text-xs font-bold ${active ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#526056] dark:text-[#AAB5AD]"}`}
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
          disabled={!canGenerate}
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
            <Text className="mt-1 text-xs font-bold text-[#6B756D] dark:text-[#AAB5AD]">
              마음에 드는 코스를 선택하고 저장하세요
            </Text>
            {candidates.map((candidate, index) => {
              const selected = selectedIndex === index;
              return (
                <Pressable
                  key={index}
                  className={`flex-row items-center gap-3 rounded-2xl border-2 bg-white p-4 dark:bg-[#1B211D] ${selected ? "" : "border-transparent"}`}
                  style={
                    selected
                      ? {
                          borderColor: CANDIDATE_COLORS[index] ?? "#087A3F",
                        }
                      : undefined
                  }
                  onPress={() => setSelectedIndex(index)}
                >
                  <View
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{
                      backgroundColor: CANDIDATE_COLORS[index] ?? "#087A3F",
                    }}
                  >
                    <Text className="text-sm font-black text-white">
                      #{index + 1}
                    </Text>
                  </View>
                  <View className="flex-1">
                    <Text className="text-sm font-extrabold text-[#18271D] dark:text-[#F1F5F2]">
                      {(candidate.totalM / 1000).toFixed(2)}km
                    </Text>
                    <Text className="mt-0.5 text-[11px] text-[#6B756D] dark:text-[#AAB5AD]">
                      {isOneway
                        ? `점수 ${Number(candidate.avgScore ?? 0).toFixed(2)}`
                        : `점수 ${Number(candidate.avgScore ?? 0).toFixed(2)} · 오차 ${Number(candidate.errorPct ?? 0).toFixed(1)}%`}
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

      <Modal
        visible={placeSearchOpen}
        transparent
        animationType="slide"
        onRequestClose={() => setPlaceSearchOpen(false)}
      >
        <KeyboardAvoidingView behavior="padding" className="flex-1 justify-end">
          <Pressable
            className="absolute inset-0 bg-black/40"
            onPress={() => setPlaceSearchOpen(false)}
          />
          <View className="h-[78%] rounded-t-[30px] bg-[#FCFDFC] px-5 pt-5 dark:bg-[#171C18]">
            <View className="mb-4 flex-row items-center justify-between">
              <Text className="text-[17px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
                출발 위치 검색
              </Text>
              <Button
                variant="ghost"
                size="icon"
                accessibilityLabel="검색 닫기"
                onPress={() => setPlaceSearchOpen(false)}
              >
                <Ionicons name="close" size={22} color={isDark ? "#F1F5F2" : "#334139"} />
              </Button>
            </View>
            <View className="flex-row items-center rounded-xl border border-[#D7E2D8] bg-white px-3 dark:border-[#475249] dark:bg-[#1B211D]">
              <Ionicons name="search" size={19} color="#7A847C" />
              <TextInput
                value={placeQuery}
                onChangeText={setPlaceQuery}
                onSubmitEditing={() => void handlePlaceSearch()}
                returnKeyType="search"
                placeholder="공원이나 장소를 검색해 보세요"
                placeholderTextColor={isDark ? "#758078" : "#94A09A"}
                className="h-12 flex-1 px-2 text-[#18271D] dark:text-[#F1F5F2]"
              />
              {placeSearching ? (
                <ActivityIndicator size="small" color="#087A3F" />
              ) : (
                <Pressable accessibilityLabel="장소 검색" onPress={() => void handlePlaceSearch()}>
                  <Ionicons name="arrow-forward-circle" size={23} color="#087A3F" />
                </Pressable>
              )}
            </View>
            {placeSearchError && (
              <Text className="mt-3 text-xs font-bold text-[#B91C1C]">
                {placeSearchError}
              </Text>
            )}
            <ScrollView className="mt-3" contentContainerClassName="gap-2 pb-8">
              {placeResults.map((place, index) => (
                <Pressable
                  key={`${place.latitude}-${place.longitude}-${index}`}
                  disabled={!place.supportedRegion}
                  className={`flex-row items-center rounded-2xl border p-4 ${place.supportedRegion ? "border-[#E5EBE5] bg-white dark:border-[#343D36] dark:bg-[#1B211D]" : "border-[#E5EBE5] bg-[#F2F4F2] opacity-60 dark:border-[#343D36] dark:bg-[#202520]"}`}
                  onPress={() => selectPlace(place)}
                >
                  <View className="h-10 w-10 items-center justify-center rounded-xl bg-[#E9F5EC] dark:bg-[#24382B]">
                    <Ionicons name="location" size={20} color="#087A3F" />
                  </View>
                  <View className="ml-3 flex-1">
                    <Text className="text-sm font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
                      {place.name}
                    </Text>
                    <Text className="mt-1 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                      {place.roadAddress || place.address || "주소 정보 없음"}
                    </Text>
                  </View>
                  {place.supportedRegion ? (
                    <Ionicons name="chevron-forward" size={20} color="#64748B" />
                  ) : (
                    <Text className="text-[10px] font-bold text-[#7A847C]">
                      서비스 지역 밖
                    </Text>
                  )}
                </Pressable>
              ))}
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>

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

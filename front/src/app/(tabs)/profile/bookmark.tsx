import { Ionicons } from "@expo/vector-icons";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Button } from "@/components/ui/button";
import { router, usePathname } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

type Course = {
  id: string;
  name: string;
  distance: string;
  minutes: string;
  badge?: string;
  badgeColor?: string;
  image: string;
  summary: string;
};
const COURSES: Course[] = [
  {
    id: "101",
    name: "성수 서울숲 순환",
    distance: "2.5km",
    minutes: "35분",
    badge: "유모차",
    badgeColor: "#0EA5E9",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuB7HGo2x5xVJhpBxmNNUyccRXDEKmCGCIgFhoEh_2j6CXcq1dJCFrFiOgoudNo4okUPhlImO_TpCDeZ6r6N0mZxjCEzsNQc6iRvWL6BXEX2j5JzPZc0wDcpTsBNdILm3fGYeu_NtljTW-dGmh4Fwh8bMNJsPKi8Cjv66oEkeSpEeOF-fUNVInsU8N2p5G38rIRxZJ8wlq0_q0I4Vl9xckkUvbAYwORbSIcrL4F6y06mCasrWwNaszB5gQ",
    summary: "그늘 78% · 경사 완만 · 흙길 위주 · 벤치 5개 · 화장실 2곳",
  },
  {
    id: "102",
    name: "한강공원 뚝섬길",
    distance: "3.2km",
    minutes: "45분",
    badge: "반려견",
    badgeColor: "#F97316",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuBWpe1PgtZx-GmQZdkz1brXe-TRKnk70RS6IpKziY4XJImFF0Wc3VllTfrslO9vpbS-3GIJuKSBxM5hY13fF74c-OUwae9vui8D3APNs0MjIaYDaU_nSWrbbAs9hciVNjWyHkr4ogwksrYIJpbjQ3akv8fxFuvceUhe-_eROW3hw7Sh54aOmNnck8kdjU3laGJ977OIcSwJKDq7vAoVWBA_BtIqoRRlXJWwXu0HMwVG9EdO124bOPHxyA",
    summary: "한강 바람이 시원하고 반려견과 함께 걷기 좋은 평탄한 수변 코스",
  },
  {
    id: "103",
    name: "연남동 경의선 숲길",
    distance: "1.8km",
    minutes: "25분",
    badge: "시니어",
    badgeColor: "#A855F7",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuCS3goN0J6hVlG4OwqbUJXI9C-kJ40Dc2kQ8G_wSA8VEMDGslygorxXaDuOiLLcXmB0ixNDhvbag84nqKHUIbK0ac7XRdnJJEWaR-lZtq258Y-MNXA2NDiVctKBocfVWZUGqchamrXGlrE3giBWDEaCvQojCIoXmd5Pg7rIg-fqoIaW-ZtP0Y8p4TP02wh07WSODRSMWcvUWkBzwVUzpSw9l43g-6WdXFkw2Kccqu769yakj3ENKOQMrg",
    summary: "짧고 평탄하며 곳곳에 쉴 수 있는 공간이 있는 도심 숲길",
  },
  {
    id: "104",
    name: "보라매공원 둘레길",
    distance: "4.0km",
    minutes: "60분",
    image:
      "https://lh3.googleusercontent.com/aida-public/AB6AXuC8IeGGiaHwu0gLJuacSlNRsTAtK59EA3AaqmKRwiOBEeo7fh8LzTUM6WngywvDqxWVhOumpxUoRpwDNvZeK-LTyrMnUYPEWYeBjXUPvgx-BUcQ8XL0_AadTIAO4vYz3AjYrHxPGQk0Lp2njnklRN5Nfoas_D3VY_ktpado7-gOq3vPf51V5yenjdlZ0hizeETJ57wMGgABX4BrnsrhYck-5vJ20_pCspjuidrFgQNrSQczoIpyuEKeQw",
    summary: "넓은 공원을 한 바퀴 도는 여유로운 장거리 순환 코스",
  },
];

export default function ProfileBookmarkScreen() {
  const pathname = usePathname();
  const isTabRoot = pathname === "/bookmark";
  const [selected, setSelected] = useState<Course | null>(null);
  const [visibleCount, setVisibleCount] = useState(4);
  const [loadingMore, setLoadingMore] = useState(false);
  const [savedIds, setSavedIds] = useState(
    () => new Set(COURSES.map((course) => course.id)),
  );
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, () => setSelected(null));
  useEffect(() => {
    if (!selected) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [selected, sheetTranslateY, windowHeight]);
  const openCourseDetails = (courseId: string) => {
    setSelected(null);
    setTimeout(() => router.push(`/course/${courseId}` as never), 300);
  };
  const loadMore = () => {
    if (loadingMore || visibleCount >= COURSES.length) return;
    setLoadingMore(true);
    setTimeout(() => {
      setVisibleCount((count) => Math.min(count + 4, COURSES.length));
      setLoadingMore(false);
    }, 350);
  };
  return (
    <SafeAreaView className="flex-1 bg-white" edges={["top"]}>
      {!isTabRoot && (
        <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-5">
          <Button
            variant="ghost"
            size="icon"
            accessibilityLabel="뒤로 가기"
            className="h-11 w-11"
            onPress={() => router.back()}
          >
            <Ionicons name="arrow-back" size={22} color="#223128" />
          </Button>
          <Text className="text-lg text-[#006E2F]">저장한 코스</Text>
          <View className="w-11" />
        </View>
      )}
      <FlatList
        data={COURSES.slice(0, visibleCount)}
        keyExtractor={(item) => item.id}
        contentContainerClassName="gap-6 p-5 pb-[30px]"
        showsVerticalScrollIndicator={false}
        onEndReached={loadMore}
        onEndReachedThreshold={0.5}
        ListHeaderComponent={
          <Text className="mb-0.5 text-sm text-slate-600">
            총 {savedIds.size}개의 코스
          </Text>
        }
        ListFooterComponent={
          loadingMore ? (
            <ActivityIndicator color="#006E2F" className="m-4" />
          ) : null
        }
        renderItem={({ item }) => (
          <Pressable className="w-full" onPress={() => setSelected(item)}>
            <View>
              <Image
                source={{ uri: item.image }}
                className="h-40 w-full rounded-2xl bg-slate-200"
              />
              <Button
                variant="secondary"
                size="icon"
                accessibilityRole="button"
                accessibilityLabel={
                  savedIds.has(item.id) ? "북마크 해제" : "북마크 추가"
                }
                className="absolute right-2.5 top-2.5 h-9 w-9 rounded-full bg-white/90"
                onPress={(event) => {
                  event.stopPropagation();
                  setSavedIds((current) => {
                    const next = new Set(current);
                    next.has(item.id)
                      ? next.delete(item.id)
                      : next.add(item.id);
                    return next;
                  });
                }}
              >
                <Ionicons
                  name={savedIds.has(item.id) ? "bookmark" : "bookmark-outline"}
                  size={21}
                  color={savedIds.has(item.id) ? "#22C55E" : "#64748B"}
                />
              </Button>
            </View>
            <View className="mt-3 flex-row items-center gap-2 px-1">
              <Text className="flex-1 text-[17px] font-bold text-[#191C1D]">
                {item.name}
              </Text>
              {item.badge && (
                <View
                  className="rounded-full px-[9px] py-1"
                  style={{ backgroundColor: `${item.badgeColor}18` }}
                >
                  <Text
                    className="text-[11px] font-extrabold"
                    style={{ color: item.badgeColor }}
                  >
                    {item.badge}
                  </Text>
                </View>
              )}
            </View>
            <View className="mt-2 flex-row items-center gap-1 px-1">
              <Ionicons name="git-branch-outline" size={17} color="#475569" />
              <Text className="mr-[9px] text-[13px] text-slate-600">
                {item.distance}
              </Text>
              <Ionicons name="timer-outline" size={17} color="#475569" />
              <Text className="text-[13px] text-slate-600">{item.minutes}</Text>
            </View>
          </Pressable>
        )}
      />
      <Modal
        visible={!!selected}
        transparent
        animationType="none"
        onRequestClose={dismissSheet}
      >
        <View className="flex-1 justify-end">
          <Pressable
            className="absolute inset-0 bg-black/40"
            onPress={dismissSheet}
          />
          <Animated.View
            className="h-[66%] rounded-t-[28px] bg-white pt-2.5"
            style={{ transform: [{ translateY: sheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={() => setSelected(null)}
              translateY={sheetTranslateY}
              dismissDistance={windowHeight}
            />
            {selected && (
              <ScrollView
                className="flex-1"
                contentContainerClassName="px-5 pb-8"
                nestedScrollEnabled
                showsVerticalScrollIndicator
              >
                <Image
                  source={{ uri: selected.image }}
                  className="h-[170px] w-full rounded-xl"
                />
                <View className="mt-4 flex-row items-center">
                  <View className="flex-1">
                    <Text className="text-[11px] font-extrabold text-[#006E2F]">
                      저장한 코스 상세
                    </Text>
                    <Text className="mt-1 text-[22px] font-black text-[#191C1D]">
                      {selected.name}
                    </Text>
                  </View>
                </View>
                <View className="mt-3 flex-row items-center gap-1.5">
                  <Ionicons
                    name="git-branch-outline"
                    size={18}
                    color="#006E2F"
                  />
                  <Text>{selected.distance}</Text>
                  <Ionicons name="timer-outline" size={18} color="#006E2F" />
                  <Text>{selected.minutes}</Text>
                </View>
                <Text className="mt-[13px] text-sm leading-[21px] text-slate-600">
                  {selected.summary}
                </Text>
                <View className="mt-[15px] flex-row rounded-xl bg-[#F2F7F2] py-[13px]">
                  {[
                    ["90%", "평탄함"],
                    ["78%", "그늘"],
                    ["좋음", "편의시설"],
                  ].map(([v, l]) => (
                    <View key={l} className="flex-1 items-center">
                      <Text className="text-[15px] font-black text-[#006E2F]">
                        {v}
                      </Text>
                      <Text className="mt-1 text-[11px] text-slate-500">
                        {l}
                      </Text>
                    </View>
                  ))}
                </View>
                <Button
                  className="mt-4 h-14 rounded-xl bg-[#006E2F]"
                  onPress={() => openCourseDetails(selected.id)}
                >
                  <Text className="font-black text-white">
                    코스 자세히 보기
                  </Text>
                </Button>
              </ScrollView>
            )}
          </Animated.View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

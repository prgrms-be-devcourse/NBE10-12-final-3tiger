import { Ionicons } from "@expo/vector-icons";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  Animated,
  Easing,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from "react-native";
import MapView, { Marker, Polyline } from "react-native-maps";
import { SafeAreaView } from "react-native-safe-area-context";
const ROUTE = [
  { latitude: 37.5445, longitude: 127.0374 },
  { latitude: 37.5462, longitude: 127.0396 },
  { latitude: 37.5482, longitude: 127.0378 },
  { latitude: 37.5471, longitude: 127.0348 },
  { latitude: 37.5445, longitude: 127.0374 },
];
export default function CourseScreen() {
  const [showDetails, setShowDetails] = useState(true);
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const openDetails = () => {
    setShowDetails(true);
  };
  const dismissDetails = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, () =>
      setShowDetails(false),
    );
  useEffect(() => {
    if (!showDetails) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [sheetTranslateY, showDetails, windowHeight]);
  return (
    <View className="flex-1 bg-[#E8F0E5]">
      <MapView
        style={StyleSheet.absoluteFill}
        onPress={dismissDetails}
        initialRegion={{
          latitude: 37.5462,
          longitude: 127.0372,
          latitudeDelta: 0.014,
          longitudeDelta: 0.012,
        }}
      >
        <Polyline coordinates={ROUTE} strokeColor="#087A3F" strokeWidth={7} />
        <Marker
          coordinate={ROUTE[0]}
          onPress={(event) => {
            event.stopPropagation();
            openDetails();
          }}
        >
          <View className="h-[42px] w-[42px] items-center justify-center rounded-full border-[3px] border-white bg-[#087A3F]">
            <Ionicons name="walk" size={19} color="white" />
          </View>
        </Marker>
      </MapView>
      <SafeAreaView
        edges={["top"]}
        className="px-[18px]"
        pointerEvents="box-none"
      >
        <View className="mt-1 flex-row items-center justify-between">
          <Pressable
            className="h-12 w-12 items-center justify-center rounded-[17px] bg-white shadow"
            onPress={() => router.back()}
          >
            <Ionicons name="arrow-back" size={23} color="#203126" />
          </Pressable>
          <Text className="rounded-2xl bg-white px-[18px] py-[13px] text-lg font-black text-[#1A2B20]">
            추천 코스
          </Text>
          <Pressable className="h-12 w-12 items-center justify-center rounded-[17px] bg-white shadow">
            <Ionicons name="options-outline" size={23} color="#203126" />
          </Pressable>
        </View>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerClassName="gap-2 pt-3"
        >
          <View className="h-10 flex-row items-center gap-1.5 rounded-full bg-[#087A3F] px-[15px]">
            <Ionicons name="leaf" size={16} color="white" />
            <Text className="text-[13px] font-extrabold text-white">
              가장 쾌적한 길
            </Text>
          </View>
          {["그늘 많은 길", "평탄한 길"].map((x) => (
            <View
              key={x}
              className="h-10 justify-center rounded-full bg-white px-[15px]"
            >
              <Text className="text-[13px] font-extrabold text-[#536158]">
                {x}
              </Text>
            </View>
          ))}
        </ScrollView>
      </SafeAreaView>
      {showDetails && (
        <Animated.View
          className="absolute inset-x-0 bottom-0 h-[68%] rounded-t-[30px] bg-white pt-2.5 shadow-2xl"
          style={{ transform: [{ translateY: sheetTranslateY }] }}
        >
          <BottomSheetHandle
            onDismiss={() => setShowDetails(false)}
            translateY={sheetTranslateY}
            dismissDistance={windowHeight}
          />
          <ScrollView
            className="flex-1"
            contentContainerClassName="px-5 pb-[22px]"
            nestedScrollEnabled
            showsVerticalScrollIndicator
          >
            <View className="flex-row items-center">
              <View className="flex-1">
                <Text className="text-[11px] font-black text-[#087A3F]">
                  오늘 걷기 좋은 1순위
                </Text>
                <Text className="mt-1 text-[22px] font-black text-[#18271D]">
                  서울숲 그린 순환길
                </Text>
              </View>
              <Pressable className="h-[46px] w-[46px] items-center justify-center rounded-2xl bg-[#E6F8EB]">
                <Ionicons name="bookmark-outline" size={23} color="#087A3F" />
              </Pressable>
            </View>
            <Text className="mt-1.5 text-[13px] text-[#78837B]">
              2.5km · 약 35분 · 순환 코스
            </Text>
            <View className="mt-[15px] flex-row rounded-[18px] bg-[#F2F8F2] py-3">
              {[
                ["leaf", "82%", "그늘"],
                ["trending-down", "완만", "경사"],
                ["thermometer", "24°", "체감"],
              ].map(([icon, v, l]) => (
                <View key={l} className="flex-1 items-center">
                  <Ionicons name={icon as any} size={18} color="#087A3F" />
                  <Text className="mt-1 text-sm font-black text-[#25352B]">
                    {v}
                  </Text>
                  <Text className="mt-0.5 text-[10px] text-slate-500">{l}</Text>
                </View>
              ))}
            </View>
            <Pressable className="mt-[15px] h-14 flex-row items-center justify-center gap-2 rounded-[18px] bg-[#087A3F]">
              <Ionicons name="navigate" size={20} color="white" />
              <Text className="text-base font-black text-white">안내 시작</Text>
            </Pressable>
          </ScrollView>
        </Animated.View>
      )}
      {!showDetails && (
        <SafeAreaView
          edges={["bottom"]}
          className="absolute inset-x-0 bottom-5 items-center"
          pointerEvents="box-none"
        >
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="코스 정보 다시 열기"
            className="h-14 flex-row items-center gap-2 rounded-full bg-[#087A3F] px-6 shadow-lg"
            onPress={openDetails}
          >
            <Ionicons name="chevron-up" size={20} color="white" />
            <Text className="text-[15px] font-black text-white">
              코스 정보 보기
            </Text>
          </Pressable>
        </SafeAreaView>
      )}
    </View>
  );
}

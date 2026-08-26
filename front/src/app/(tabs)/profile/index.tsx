import { Ionicons } from "@expo/vector-icons";
import { Button } from "@/components/ui/button";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { Image, Pressable, ScrollView, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
const PROFILE =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuCJsRPYBQCHgxBRzPFw66AOkmuy-9AcNt_g2vsX57A8W5WkBn2NTvk8pFMWNVdsTfB9j5-00K7LwDAttKzEqCEUpxz40iWZklmWuCCcnJapH0ozdk-JNtRzP-j3d1u3JqLk8W02FSfkUNj4lT6eT9hyxMflOn3Fk36NJbW9YAjrVawmzPvEb1mC8y_lFK_h3vCesJQSqde1Kmut7D5DynM8blIyQOG-sWzPXphy32YPAxjvirdKML5-PQ";
const PERSONAS = [
  { key: "dog", label: "반려견", icon: "paw" as const, color: "#F97316" },
  {
    key: "senior",
    label: "시니어",
    icon: "accessibility" as const,
    color: "#A855F7",
  },
  {
    key: "stroller",
    label: "유모차",
    icon: "happy" as const,
    color: "#0EA5E9",
  },
];
const MENUS = [
  {
    label: "저장한 코스",
    description: "다시 걷고 싶은 코스를 확인해요",
    icon: "bookmark" as const,
    color: "bg-[#22C55E]",
    route: "/(tabs)/profile/bookmark",
  },
  {
    label: "나의 게시글",
    description: "내가 남긴 산책 기록을 모아봐요",
    icon: "list" as const,
    color: "bg-[#22C55E]",
    route: "/(tabs)/profile/mypost",
  },
  {
    label: "게시글 작성",
    description: "새로운 산책 기록을 남겨보세요",
    icon: "pencil" as const,
    color: "bg-[#22C55E]",
    route: "/review/write",
  },
  {
    label: "좋아요한 글",
    description: "공감한 산책 이야기를 확인해요",
    icon: "heart" as const,
    color: "bg-[#EF4444]",
    route: "/(tabs)/profile/like",
  },
];
export default function ProfileScreen() {
  const [persona, setPersona] = useState("dog");
  const [tags, setTags] = useState(["공원 위주", "식수대 있음"]);
  useFocusEffect(
    useCallback(() => {
      setPersona("dog");
      setTags(["공원 위주", "식수대 있음"]);
    }, []),
  );
  const toggle = (tag: string) =>
    setTags((v) =>
      v.includes(tag) ? v.filter((x) => x !== tag) : [...v, tag],
    );
  return (
    <SafeAreaView className="flex-1 bg-[#F2F7F2]" edges={["top"]}>
      <ScrollView contentContainerClassName="gap-3.5 p-5 pb-9">
        <View className="rounded-xl bg-[#F9FCF9] p-4">
          <View className="flex-row items-center gap-3">
            <View>
              <Image
                source={{ uri: PROFILE }}
                className="h-16 w-16 rounded-full border-2 border-slate-100"
              />
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="프로필 설정"
                className="absolute bottom-0 right-0 h-6 w-6 items-center justify-center rounded-full bg-[#22C55E]"
                onPress={() => router.push("/settings" as never)}
              >
                <Ionicons name="pencil" size={11} color="#004B1E" />
              </Pressable>
            </View>
            <View className="flex-1">
              <Text className="text-[17px] font-semibold text-[#191C1D]">
                산책러
              </Text>
              <Text className="mt-0.5 text-xs text-slate-500">
                walker@example.com
              </Text>
            </View>
          </View>
          <View className="mt-3.5 border-t border-slate-200 pt-3">
            <Text className="mb-2 text-[11px] font-medium text-slate-500">
              계정 관리
            </Text>
            <View className="flex-row gap-2">
              <Button
                variant="secondary"
                size="sm"
                className="h-9 flex-1 rounded-lg bg-slate-200 px-3"
                onPress={() => router.replace("/(auth)/login" as never)}
              >
                <Text className="text-xs font-semibold text-slate-600">
                  로그아웃
                </Text>
              </Button>
              <Button
                variant="destructive"
                size="sm"
                className="h-9 flex-1 rounded-lg px-3"
              >
                <Text className="text-xs font-semibold text-white">
                  계정 삭제
                </Text>
              </Button>
            </View>
          </View>
        </View>
        <View className="rounded-xl bg-[#F9FCF9] p-4">
          <View className="flex-row items-center gap-2">
            <Ionicons name="person-circle" size={24} color="#22C55E" />
            <Text className="text-[17px] font-extrabold text-[#191C1D]">
              나의 걷기 유형
            </Text>
          </View>
          <Text className="mb-3 mt-1.5 text-xs leading-[19px] text-slate-600">
            맞춤형 경로를 위해 주된 유형을 선택해주세요.
          </Text>
          <View className="flex-row gap-1">
            {PERSONAS.map((item) => {
              const active = persona === item.key;
              return (
                <Pressable
                  key={item.key}
                  className={`h-20 flex-1 items-center justify-center gap-1 rounded-lg border-2 ${active ? "bg-orange-50" : "border-slate-200 bg-slate-100"}`}
                  style={active ? { borderColor: item.color } : undefined}
                  onPress={() => setPersona(item.key)}
                >
                  <Ionicons
                    name={item.icon}
                    size={27}
                    color={active ? item.color : "#64748B"}
                  />
                  <Text
                    style={active ? { color: item.color } : undefined}
                    className="text-xs font-bold text-slate-600"
                  >
                    {item.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>
        <View className="rounded-xl bg-[#F9FCF9] p-4">
          <View className="flex-row items-center gap-2">
            <Ionicons name="pricetag" size={22} color="#22C55E" />
            <Text className="text-[17px] font-extrabold text-[#191C1D]">
              관심 태그
            </Text>
          </View>
          <Text className="mb-3 mt-1.5 text-xs text-slate-600">
            선호하는 산책 환경을 알려주세요.
          </Text>
          <View className="flex-row gap-1.5">
            {["공원 위주", "그늘 많은 곳", "평탄한 길", "식수대 있음"].map(
              (tag) => (
                <Pressable
                  key={tag}
                  onPress={() => toggle(tag)}
                  className={`h-10 flex-1 items-center justify-center rounded-full border px-1 ${tags.includes(tag) ? "border-[#22C55E] bg-[#22C55E]" : "border-[#BCCBB9] bg-slate-200"}`}
                >
                  <Text
                    numberOfLines={1}
                    className="text-[11px] font-bold text-[#26372D]"
                  >
                    {tag}
                  </Text>
                </Pressable>
              ),
            )}
          </View>
        </View>
        <View className="overflow-hidden rounded-xl bg-[#F9FCF9] px-4">
          {MENUS.map((item, i) => (
            <Pressable
              key={item.label}
              className={`min-h-[66px] flex-row items-center gap-3 ${i < MENUS.length - 1 ? "border-b border-slate-200" : ""}`}
              onPress={() => router.push(item.route as never)}
            >
              <View
                className={`h-[38px] w-[38px] items-center justify-center rounded-full ${item.color}`}
              >
                <Ionicons
                  name={item.icon}
                  size={20}
                  color={item.label === "설정" ? "#475569" : "white"}
                />
              </View>
              <View className="flex-1">
                <Text className="text-[15px] font-semibold text-[#191C1D]">
                  {item.label}
                </Text>
                <Text className="mt-0.5 text-[11px] text-slate-500">
                  {item.description}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={20} color="#64748B" />
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

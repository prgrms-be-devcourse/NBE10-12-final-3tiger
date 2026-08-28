import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Image, Pressable, ScrollView, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { logout } from "@/api/auth-api";
import { getMyProfile, updateMyProfile, withdraw } from "@/api/user-api";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
const PERSONAS = [
  {
    key: "walker",
    label: "일반",
    icon: "walk" as const,
    color: "#087A3F",
    activeBackground: "#E9FBEF",
  },
  {
    key: "dog",
    label: "반려견",
    icon: "paw" as const,
    color: "#F97316",
    activeBackground: "#F4F7F4",
  },
  {
    key: "senior",
    label: "시니어",
    icon: "accessibility" as const,
    color: "#A855F7",
    activeBackground: "#F4F7F4",
  },
  {
    key: "stroller",
    label: "유모차",
    icon: "happy" as const,
    color: "#0EA5E9",
    activeBackground: "#F4F7F4",
  },
];
const MENUS = [
  {
    label: "코스 생성",
    description: "원하는 조건으로 새 코스를 만들어요",
    icon: "sparkles" as const,
    color: "bg-[#087A3F]",
    route: "/course/generate",
  },
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
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const clearSession = useAuthStore((state) => state.clearSession);
  const [persona, setPersona] = useState("dog");
  const [tags, setTags] = useState<string[]>([]);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const profileMutation = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["my-profile"] }),
  });
  const logoutMutation = useMutation({
    mutationFn: () =>
      refreshToken ? logout(refreshToken) : Promise.resolve(null),
    onSettled: async () => {
      await clearSession();
      queryClient.clear();
    },
  });
  const withdrawMutation = useMutation({
    mutationFn: withdraw,
    onSuccess: async () => {
      await clearSession();
      queryClient.clear();
    },
  });
  useEffect(() => {
    if (profileQuery.data) {
      setPersona(profileQuery.data.primaryPersona ?? "dog");
      setTags(profileQuery.data.personaTags ?? []);
    }
  }, [profileQuery.data]);
  const savePreferences = (nextPersona: string, nextTags: string[]) => {
    setPersona(nextPersona);
    setTags(nextTags);
    if (profileQuery.data)
      profileMutation.mutate({
        nickname: profileQuery.data.nickname,
        primaryPersona: nextPersona,
        personaTags: nextTags,
      });
  };
  const toggle = (tag: string) =>
    savePreferences(
      persona,
      tags.includes(tag) ? tags.filter((x) => x !== tag) : [...tags, tag],
    );
  if (!isAuthenticated)
    return (
      <SafeAreaView
        className="flex-1 items-center justify-center bg-[#F2F7F2] px-6"
        edges={["top"]}
      >
        <View className="w-full max-w-md items-center rounded-3xl bg-white px-6 py-9 shadow-sm">
          <View className="h-16 w-16 items-center justify-center rounded-full bg-[#E9FBEF]">
            <Ionicons name="person-outline" size={30} color="#087A3F" />
          </View>
          <Text className="mt-5 text-xl font-extrabold text-[#191C1D]">
            로그인하고 산책 기록을 관리하세요
          </Text>
          <Text className="mt-2 text-center text-sm leading-5 text-slate-500">
            저장한 코스와 게시글, 나에게 맞는 걷기 유형을 한곳에서 확인할 수
            있어요.
          </Text>
          <Button
            className="mt-7 h-12 w-full rounded-xl bg-[#087A3F]"
            onPress={() => router.push("/(auth)/login" as never)}
          >
            <Text className="font-extrabold text-white">로그인하기</Text>
          </Button>
          <Button
            variant="secondary"
            className="mt-2.5 h-12 w-full rounded-xl bg-[#BDF4CB]"
            onPress={() => router.push("/(auth)/signup" as never)}
          >
            <Text className="font-bold text-[#075E34]">회원가입</Text>
          </Button>
        </View>
      </SafeAreaView>
    );
  if (profileQuery.isPending)
    return <LoadingState label="프로필을 불러오는 중이에요" />;
  if (profileQuery.isError)
    return (
      <ErrorState
        message={profileQuery.error.message}
        onRetry={() => void profileQuery.refetch()}
      />
    );
  const profile = profileQuery.data;
  return (
    <SafeAreaView className="flex-1 bg-[#F2F7F2]" edges={["top"]}>
      <ScrollView contentContainerClassName="gap-3.5 p-5 pb-9">
        <View className="rounded-xl bg-white p-4">
          <View className="flex-row items-center gap-3">
            <View>
              <Image
                source={
                  profile?.profileImageUrl
                    ? { uri: profile.profileImageUrl }
                    : DEFAULT_PROFILE_IMAGE
                }
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
                {profile?.nickname}
              </Text>
              <Text className="mt-0.5 text-xs text-slate-500">
                {profile?.email}
              </Text>
            </View>
          </View>
          <View className="mt-3.5 border-t border-slate-200 pt-3">
            <Text className="mb-2 text-[11px] font-medium text-slate-500">
              계정 관리
            </Text>
            <View className="flex-row gap-2">
              <Button
                variant="ghost"
                size="sm"
                className="h-10 flex-1 rounded-lg border-0 bg-[#EEF0EE] px-3"
                disabled={logoutMutation.isPending}
                onPress={() => logoutMutation.mutate()}
              >
                <Text className="text-xs font-bold text-[#4B5563]">
                  로그아웃
                </Text>
              </Button>
              <Button
                variant="secondary"
                size="sm"
                className="h-10 flex-1 rounded-lg bg-[#FEE2E2] px-3"
                disabled={withdrawMutation.isPending}
                onPress={() => withdrawMutation.mutate()}
              >
                <Text className="text-xs font-extrabold text-[#B91C1C]">
                  계정 삭제
                </Text>
              </Button>
            </View>
          </View>
        </View>
        <View className="rounded-xl bg-white p-4">
          <View className="flex-row items-center gap-2">
            <Ionicons name="person-circle" size={24} color="#22C55E" />
            <Text className="text-[17px] font-extrabold text-[#191C1D]">
              나의 걷기 유형
            </Text>
          </View>
          <Text className="mb-3 mt-1.5 text-xs leading-[19px] text-slate-600">
            맞춤형 경로를 위해 주된 유형을 선택해주세요.
          </Text>
          <View className="flex-row gap-1.5">
            {PERSONAS.map((item) => {
              const active = persona === item.key;
              return (
                <Pressable
                  key={item.key}
                  className={`h-20 flex-1 items-center justify-center gap-1 rounded-lg border-2 ${active ? "" : "border-slate-200 bg-[#F8FAF8]"}`}
                  style={
                    active
                      ? {
                          borderColor: item.color,
                          backgroundColor: item.activeBackground,
                        }
                      : undefined
                  }
                  onPress={() => savePreferences(item.key, tags)}
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
        <View className="rounded-xl bg-white p-4">
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
            {[
              ["park", "공원 위주"],
              ["shade", "그늘 많은 곳"],
              ["flat", "평탄한 길"],
              ["water", "식수대 있음"],
            ].map(([tag, label]) => (
              <Pressable
                key={tag}
                onPress={() => toggle(tag)}
                className={`h-10 flex-1 items-center justify-center rounded-full border px-1 ${tags.includes(tag) ? "border-[#22C55E] bg-[#22C55E]" : "border-[#BCCBB9] bg-slate-200"}`}
              >
                <Text
                  numberOfLines={1}
                  className="text-[11px] font-bold text-[#26372D]"
                >
                  {label}
                </Text>
              </Pressable>
            ))}
          </View>
        </View>
        <View className="overflow-hidden rounded-xl bg-white px-4">
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

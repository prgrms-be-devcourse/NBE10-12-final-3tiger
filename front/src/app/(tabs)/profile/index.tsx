import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { getMyProfile, updateMyProfile } from "@/api/user-api";
import { getMyItems } from "@/api/shop-api";
import {
  badgeAppearance,
  profileBorderStyle,
} from "@/components/shop/cosmetics";
import { ErrorState } from "@/components/ui/data-state";
import { InterestTagsSheet } from "@/components/profile/interest-tags-sheet";
import { Separator } from "@/components/ui/separator";
import { WalkHistoryCard } from "@/components/profile/walk-history-card";
import { WalkingTypeSheet } from "@/components/profile/walking-type-sheet";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { useThemeStore } from "@/stores/theme-store";
const PRIMARY_MENUS = [
  {
    label: "코스 생성",
    icon: "sparkles" as const,
    route: "/course/generate",
  },
  {
    label: "나의 게시글",
    icon: "document-text" as const,
    route: "/(tabs)/profile/mypost",
  },
  {
    label: "게시글 작성",
    icon: "create" as const,
    route: "/review/write",
  },
  {
    label: "좋아요한 글",
    icon: "heart" as const,
    route: "/(tabs)/profile/like",
  },
];

const SECONDARY_MENUS = [
  {
    label: "꾸미기 상점",
    icon: "color-palette" as const,
    route: "/shop",
  },
  {
    label: "저장한 코스",
    icon: "bookmark" as const,
    route: "/(tabs)/profile/bookmark",
  },
  { label: "관심 태그", icon: "pricetag" as const, action: "tags" as const },
  {
    label: "나의 걷기 유형",
    icon: "accessibility" as const,
    action: "persona" as const,
  },
];
export default function ProfileScreen() {
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const isDark = useThemeStore((state) => state.isDark);
  const [persona, setPersona] = useState("dog");
  const [tags, setTags] = useState<string[]>([]);
  const [walkingTypeOpen, setWalkingTypeOpen] = useState(false);
  const [interestTagsOpen, setInterestTagsOpen] = useState(false);
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
    enabled: isAuthenticated,
  });
  const myItemsQuery = useQuery({
    queryKey: ["my-items"],
    queryFn: getMyItems,
    enabled: isAuthenticated,
  });
  const profileMutation = useMutation({
    mutationFn: updateMyProfile,
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["my-profile"] }),
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
        className="flex-1 items-center justify-center bg-[#F2F7F2] px-6 dark:bg-[#111411]"
        edges={["top"]}
      >
        <View className="w-full max-w-md items-center rounded-3xl bg-white px-6 py-9 shadow-sm dark:bg-[#1B211D]">
          <View className="h-16 w-16 items-center justify-center rounded-full bg-[#E9FBEF] dark:bg-[#24382B]">
            <Ionicons name="person-outline" size={30} color="#087A3F" />
          </View>
          <Text className="mt-5 text-xl font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
            로그인하고 산책 기록을 관리하세요
          </Text>
          <Text className="mt-2 text-center text-sm leading-5 text-slate-500 dark:text-[#AAB5AD]">
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
            className="mt-2.5 h-12 w-full rounded-xl bg-[#BDF4CB] dark:bg-[#24382B]"
            onPress={() => router.push("/(auth)/signup" as never)}
          >
            <Text className="font-bold text-[#075E34] dark:text-[#86EFAC]">
              회원가입
            </Text>
          </Button>
        </View>
      </SafeAreaView>
    );
  if (profileQuery.isPending)
    return (
      <SafeAreaView
        className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]"
        edges={["top"]}
      >
        <View className="flex-1 items-center justify-center gap-3 bg-[#F2F7F2] px-6 py-12 dark:bg-[#111411]">
          <ActivityIndicator color="#087A3F" />
          <Text className="text-sm text-slate-500 dark:text-[#AAB5AD]">
            프로필을 불러오는 중이에요
          </Text>
        </View>
      </SafeAreaView>
    );
  if (profileQuery.isError)
    return (
      <ErrorState
        message={profileQuery.error.message}
        onRetry={() => void profileQuery.refetch()}
        appearance="light"
        className="bg-[#F2F7F2] dark:bg-[#111411]"
      />
    );
  const profile = profileQuery.data;
  const profileBorderCode = myItemsQuery.data?.find(
    (item) => item.type === "PROFILE_BORDER" && item.equipped,
  )?.code;
  const profileBadge = badgeAppearance(
    myItemsQuery.data?.find(
      (item) => item.type === "PROFILE_BADGE" && item.equipped,
    )?.code,
  );
  return (
    <SafeAreaView
      className="flex-1 bg-[#F2F7F2] dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-12 flex-row items-center justify-end px-4">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="설정"
          className="h-11 w-11 items-center justify-center"
          onPress={() => router.push("/settings" as never)}
        >
          <Ionicons
            name="settings-outline"
            size={23}
            color={isDark ? "#F1F5F2" : "#33443A"}
          />
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="홈으로 돌아가기"
          className="h-11 w-11 items-center justify-center"
          onPress={() => router.replace("/(tabs)/map" as never)}
        >
          <Ionicons
            name="close"
            size={27}
            color={isDark ? "#F1F5F2" : "#33443A"}
          />
        </Pressable>
      </View>
      <ScrollView contentContainerClassName="gap-3.5 px-5 pb-9 pt-1.5">
        <View className="px-1 py-2">
          <View className="flex-row items-center gap-3">
            <View className="relative h-12 w-12 rounded-full">
              <Image
                source={
                  profile?.profileImageUrl
                    ? { uri: profile.profileImageUrl }
                    : DEFAULT_PROFILE_IMAGE
                }
                className="h-12 w-12 rounded-full border border-slate-100"
                style={profileBorderStyle(profileBorderCode)}
              />
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="프로필 설정"
                className="absolute bottom-0 right-0 h-5 w-5 items-center justify-center rounded-full bg-[#22C55E]"
                onPress={() => router.push("/settings" as never)}
              >
                <Ionicons name="pencil" size={9} color="#004B1E" />
              </Pressable>
            </View>
            <View className="flex-1">
              <View className="flex-row items-center gap-1.5">
                <Text className="text-[17px] font-semibold text-[#191C1D] dark:text-[#F1F5F2]">
                  {profile?.nickname}
                </Text>
                {profileBadge && (
                  <Ionicons
                    name={profileBadge.icon}
                    size={16}
                    color={profileBadge.color}
                  />
                )}
              </View>
              <Text className="mt-0.5 text-xs text-slate-500 dark:text-[#AAB5AD]">
                {profile?.email}
              </Text>
            </View>
          </View>
        </View>
        <View className="overflow-hidden rounded-xl bg-white dark:bg-[#1B211D]">
          <View className="flex-row px-1 py-2">
            {PRIMARY_MENUS.map((item) => (
              <Pressable
                key={item.label}
                accessibilityRole="button"
                className="h-[70px] flex-1 items-center justify-center gap-1.5"
                onPress={() => router.push(item.route as never)}
              >
                <Ionicons name={item.icon} size={27} color="#22C55E" />
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  className="px-0.5 text-center text-[13px] font-extrabold text-[#26372D] dark:text-[#F1F5F2]"
                >
                  {item.label}
                </Text>
              </Pressable>
            ))}
          </View>
          <Separator className="bg-[#DDE7DE] dark:bg-[#343D36]" />
          <View className="flex-row flex-wrap px-2 py-1.5">
            {SECONDARY_MENUS.map((item) => (
              <View key={item.label} className="w-1/2">
                <Pressable
                  accessibilityRole="button"
                  className="h-10 flex-row items-center justify-start gap-1.5 px-3"
                  onPress={() => {
                    if ("route" in item) router.push(item.route as never);
                    else if (item.action === "tags") setInterestTagsOpen(true);
                    else setWalkingTypeOpen(true);
                  }}
                >
                  <Ionicons name={item.icon} size={13} color="#FFFFFF" />
                  <Text
                    numberOfLines={1}
                    adjustsFontSizeToFit
                    className="text-[13px] font-extrabold text-[#26372D] dark:text-[#F1F5F2]"
                  >
                    {item.label}
                  </Text>
                </Pressable>
              </View>
            ))}
          </View>
        </View>
        <View className="-mx-5 mt-1">
          <WalkHistoryCard persona={persona} />
        </View>
      </ScrollView>
      <WalkingTypeSheet
        open={walkingTypeOpen}
        persona={persona}
        pending={profileMutation.isPending}
        onSelect={(nextPersona) => savePreferences(nextPersona, tags)}
        onClose={() => setWalkingTypeOpen(false)}
      />
      <InterestTagsSheet
        open={interestTagsOpen}
        tags={tags}
        pending={profileMutation.isPending}
        onToggle={toggle}
        onClose={() => setInterestTagsOpen(false)}
      />
    </SafeAreaView>
  );
}

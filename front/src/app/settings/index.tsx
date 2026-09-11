import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import {
  Alert,
  Image,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { logout } from "@/api/auth-api";
import {
  getNotificationSetting,
  updateNotificationSetting,
} from "@/api/notification-api";
import { unregisterPushToken } from "@/api/push-token-api";
import {
  getMyProfile,
  updateMyProfile,
  uploadProfileImage,
  withdraw,
} from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useAuthStore } from "@/stores/auth-store";
import { usePushTokenStore } from "@/stores/push-token-store";
import { useThemeStore } from "@/stores/theme-store";

async function deregisterPushToken() {
  const expoPushToken = usePushTokenStore.getState().expoPushToken;
  if (!expoPushToken) return;
  try {
    await unregisterPushToken(expoPushToken);
    usePushTokenStore.getState().setExpoPushToken(null);
  } catch (error) {
    console.warn("[push-token] 서버 푸시 토큰 해제에 실패했습니다.", error);
  }
}

export default function SettingsScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const setDark = useThemeStore((state) => state.setDark);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const clearSession = useAuthStore((state) => state.clearSession);
  const queryClient = useQueryClient();
  const [image, setImage] = useState<string | null>(null);
  const [imageAsset, setImageAsset] =
    useState<ImagePicker.ImagePickerAsset | null>(null);
  const [name, setName] = useState("");
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
  });
  const notificationSettingQuery = useQuery({
    queryKey: ["notification-setting"],
    queryFn: getNotificationSetting,
  });
  const notificationEnabled = notificationSettingQuery.data?.enabled ?? true;
  const notificationSettingMutation = useMutation({
    mutationFn: updateNotificationSetting,
    onMutate: (enabled) =>
      queryClient.setQueryData(["notification-setting"], { enabled }),
    onError: () =>
      void queryClient.invalidateQueries({
        queryKey: ["notification-setting"],
      }),
  });
  const logoutMutation = useMutation({
    mutationFn: () =>
      refreshToken ? logout(refreshToken) : Promise.resolve(null),
    onSettled: async () => {
      await deregisterPushToken();
      await clearSession();
      queryClient.clear();
      router.replace("/(auth)/login" as never);
    },
  });
  const withdrawMutation = useMutation({
    mutationFn: withdraw,
    onSuccess: async () => {
      await clearSession();
      queryClient.clear();
      router.replace("/(auth)/login" as never);
    },
  });
  useEffect(() => {
    if (profileQuery.data) {
      setName(profileQuery.data.nickname);
      setImage(profileQuery.data.profileImageUrl ?? null);
    }
  }, [profileQuery.data]);
  const saveMutation = useMutation({
    mutationFn: async () => {
      const profile = profileQuery.data!;
      await updateMyProfile({
        nickname: name.trim(),
        primaryPersona: profile.primaryPersona,
        personaTags: profile.personaTags,
      });
      if (imageAsset) {
        const formData = new FormData();
        formData.append("file", {
          uri: imageAsset.uri,
          name: imageAsset.fileName ?? "profile.jpg",
          type: imageAsset.mimeType ?? "image/jpeg",
        } as never);
        await uploadProfileImage(formData);
      }
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["my-profile"] }),
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["post-comments"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
      ]);
      router.replace("/(tabs)/profile" as never);
    },
  });
  const pick = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.85,
    });
    if (!result.canceled) {
      setImageAsset(result.assets[0]);
      setImage(result.assets[0].uri);
    }
  };
  if (profileQuery.isPending)
    return <LoadingState label="프로필 설정을 불러오는 중이에요" />;
  if (profileQuery.isError)
    return (
      <SafeAreaView className="flex-1">
        <ErrorState
          message={profileQuery.error.message}
          onRetry={() => void profileQuery.refetch()}
        />
      </SafeAreaView>
    );
  return (
    <SafeAreaView className="flex-1 bg-white dark:bg-[#111411]">
      <View className="h-[58px] flex-row items-center justify-between bg-white px-5 dark:bg-[#1B211D]">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="뒤로 가기"
          onPress={() => router.back()}
        >
          <Ionicons
            name="arrow-back"
            size={24}
            color={isDark ? "#F1F5F2" : "#33443A"}
          />
        </Button>
        <Text className="text-2xl font-black text-[#006E2F]">설정</Text>
        <View className="w-11" />
      </View>
      <ScrollView contentContainerClassName="p-5 pb-10">
        <View className="mt-[18px] self-center">
          {image ? (
            <Image source={{ uri: image }} className="h-28 w-28 rounded-full" />
          ) : (
            <Image
              source={DEFAULT_PROFILE_IMAGE}
              className="h-28 w-28 rounded-full"
            />
          )}
          <Pressable
            accessibilityLabel="프로필 이미지 변경"
            className="absolute bottom-0 right-0 h-[38px] w-[38px] items-center justify-center rounded-full border-[3px] border-white bg-[#006E2F]"
            onPress={() => void pick()}
          >
            <Ionicons name="camera" size={20} color="white" />
          </Pressable>
        </View>
        <Button variant="link" onPress={() => void pick()}>
          <Text className="font-extrabold text-[#006E2F]">이미지 변경</Text>
        </Button>
        <Text className="mb-2 mt-3.5 text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
          이름
        </Text>
        <TextInput
          value={name}
          onChangeText={setName}
          className="h-14 rounded-xl border border-[#BCCBB9] px-[15px] text-[15px] text-[#191C1D] dark:border-[#475249] dark:bg-[#1B211D] dark:text-[#F1F5F2]"
        />
        {saveMutation.isError && (
          <Text className="mt-3 text-sm text-destructive">
            {saveMutation.error.message}
          </Text>
        )}
        <Button
          className="mt-6 h-14 rounded-xl"
          disabled={!name.trim() || saveMutation.isPending}
          onPress={() => saveMutation.mutate()}
        >
          <Text className="text-[15px] font-black text-primary-foreground">
            {saveMutation.isPending ? "저장 중..." : "변경 사항 저장"}
          </Text>
        </Button>

        <Separator className="my-8 bg-[#DDE7DE] dark:bg-[#343D36]" />

        <Text className="mb-3 text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
          앱 설정
        </Text>
        <View className="overflow-hidden rounded-xl border border-[#DDE7DE] bg-white px-4 dark:border-[#343D36] dark:bg-[#1B211D]">
          <View className="h-14 flex-row items-center gap-3">
            <Ionicons
              name="moon-outline"
              size={20}
              color={isDark ? "#86EFAC" : "#33443A"}
            />
            <Text className="flex-1 text-[15px] font-semibold text-[#191C1D] dark:text-[#F1F5F2]">
              다크 모드
            </Text>
            <View className="h-full items-center justify-center">
              <Switch
                accessibilityLabel="다크 모드"
                value={isDark}
                onValueChange={(value) => void setDark(value)}
              />
            </View>
          </View>
          <Separator className="bg-[#E8EEE8] dark:bg-[#343D36]" />
          <View className="h-14 flex-row items-center gap-3">
            <Ionicons
              name={notificationEnabled ? "notifications" : "notifications-off"}
              size={20}
              color={notificationEnabled ? "#22C55E" : "#64748B"}
            />
            <Text className="flex-1 text-[15px] font-semibold text-[#191C1D] dark:text-[#F1F5F2]">
              알림 받기
            </Text>
            <View className="h-full items-center justify-center">
              <Switch
                accessibilityLabel="알림 받기"
                value={notificationEnabled}
                disabled={notificationSettingQuery.isPending}
                onValueChange={(value) =>
                  notificationSettingMutation.mutate(value)
                }
              />
            </View>
          </View>
        </View>

        <Text className="mb-2 mt-8 text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
          개인정보 보호
        </Text>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="차단한 사용자 관리"
          className="h-14 flex-row items-center gap-3 rounded-xl border border-[#BCCBB9] px-[15px] dark:border-[#475249] dark:bg-[#1B211D]"
          onPress={() => router.push("/settings/blocked-users" as never)}
        >
          <Ionicons
            name="ban-outline"
            size={20}
            color={isDark ? "#F1F5F2" : "#33443A"}
          />
          <Text className="flex-1 text-[15px] font-semibold text-[#191C1D] dark:text-[#F1F5F2]">
            차단한 사용자
          </Text>
          <Ionicons name="chevron-forward" size={20} color="#64748B" />
        </Pressable>

        <Text className="mb-2 mt-8 text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
          계정 관리
        </Text>
        <View className="flex-row gap-2">
          <Button
            variant="secondary"
            className="h-12 flex-1 rounded-xl bg-[#EEF0EE] dark:bg-[#2A312C]"
            disabled={logoutMutation.isPending}
            onPress={() => logoutMutation.mutate()}
          >
            <Text className="text-xs font-bold text-[#4B5563] dark:text-[#D4DDD6]">
              로그아웃
            </Text>
          </Button>
          <Button
            variant="secondary"
            className="h-12 flex-1 rounded-xl bg-[#FEE2E2] dark:bg-[#4A2424]"
            disabled={withdrawMutation.isPending}
            onPress={() =>
              Alert.alert(
                "계정을 삭제할까요?",
                "삭제한 계정은 복구할 수 없습니다.",
                [
                  { text: "취소", style: "cancel" },
                  {
                    text: "계정 삭제",
                    style: "destructive",
                    onPress: () => withdrawMutation.mutate(),
                  },
                ],
              )
            }
          >
            <Text className="text-xs font-extrabold text-[#B91C1C] dark:text-[#FCA5A5]">
              계정 삭제
            </Text>
          </Button>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

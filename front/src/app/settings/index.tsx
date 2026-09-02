import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import { useEffect, useState } from "react";
import { Image, Pressable, ScrollView, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import {
  getMyProfile,
  updateMyProfile,
  uploadProfileImage,
} from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { ErrorState, LoadingState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import { DEFAULT_PROFILE_IMAGE } from "@/lib/assets";
import { useThemeStore } from "@/stores/theme-store";

export default function SettingsScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const queryClient = useQueryClient();
  const [image, setImage] = useState<string | null>(null);
  const [imageAsset, setImageAsset] =
    useState<ImagePicker.ImagePickerAsset | null>(null);
  const [name, setName] = useState("");
  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: getMyProfile,
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
      </ScrollView>
    </SafeAreaView>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import { useState } from "react";
import {
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getMyBookmarks } from "@/api/course-api";
import { createPost, getPhotoUploadUrl, uploadPostPhoto } from "@/api/post-api";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/data-state";
import { Text } from "@/components/ui/text";
import type { Course } from "@/types/domain";

export default function WritePostScreen() {
  const queryClient = useQueryClient();
  const [asset, setAsset] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [content, setContent] = useState("");
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null);
  const [courseSheetOpen, setCourseSheetOpen] = useState(false);
  const coursesQuery = useQuery({
    queryKey: ["bookmarks", "post-picker"],
    queryFn: () => getMyBookmarks({ page: 0, size: 50 }),
  });
  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!asset || !selectedCourse)
        throw new Error("사진과 코스를 선택해 주세요.");
      const fileName = asset.fileName ?? `walk-${Date.now()}.jpg`;
      const contentType = asset.mimeType ?? "image/jpeg";
      const upload = await getPhotoUploadUrl(fileName, contentType);
      const blob = await fetch(asset.uri).then((response) => response.blob());
      await uploadPostPhoto(upload.uploadUrl, blob, contentType);
      return createPost({
        courseId: selectedCourse.courseId,
        content: content.trim(),
        photoUrl: upload.photoUrl,
        walkedAt: new Date().toISOString(),
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["posts"] });
      router.back();
    },
  });
  const pick = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      quality: 0.8,
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (!result.canceled) setAsset(result.assets[0]);
  };
  const canSubmit = Boolean(asset && selectedCourse && content.trim());
  return (
    <SafeAreaView className="flex-1 bg-slate-50" edges={["top"]}>
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-5">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="게시글 작성 닫기"
          className="h-11 w-11"
          onPress={() => router.back()}
        >
          <Ionicons name="close" size={22} color="#223128" />
        </Button>
        <Text className="text-lg text-[#006E2F]">새 게시글</Text>
        <Button
          variant="ghost"
          size="sm"
          className="h-11 min-w-11 px-1"
          disabled={!canSubmit || submitMutation.isPending}
          onPress={() => submitMutation.mutate()}
        >
          <Text className="text-[14px] font-semibold text-[#006E2F]">
            {submitMutation.isPending ? "등록 중" : "등록"}
          </Text>
        </Button>
      </View>
      <ScrollView
        contentContainerClassName="gap-5 p-5"
        keyboardShouldPersistTaps="handled"
      >
        <Pressable
          className="h-[300px] items-center justify-center overflow-hidden rounded-xl border border-dashed border-[#BCCBB9] bg-white"
          onPress={() => void pick()}
        >
          {asset ? (
            <Image source={{ uri: asset.uri }} className="h-full w-full" />
          ) : (
            <>
              <View className="h-[58px] w-[58px] items-center justify-center rounded-full bg-[#DDF8E5]">
                <Ionicons name="camera" size={30} color="#006E2F" />
              </View>
              <Text className="mt-3 text-base font-extrabold text-slate-900">
                산책 사진을 업로드해주세요
              </Text>
              <Text className="mt-1 text-xs text-slate-500">
                사진을 눌러 선택하세요
              </Text>
            </>
          )}
        </Pressable>
        <Pressable
          className="min-h-[76px] flex-row items-center gap-3 rounded-xl bg-white p-3.5 shadow-sm"
          onPress={() => setCourseSheetOpen(true)}
        >
          <View className="h-11 w-11 items-center justify-center rounded-[14px] bg-[#DDF8E5]">
            <Ionicons name="map" size={23} color="#006E2F" />
          </View>
          <View className="flex-1">
            <Text className="mb-1 text-sm font-extrabold text-slate-900">
              {selectedCourse?.name ?? "코스 선택하기"}
            </Text>
            <Text className="text-xs text-slate-500">
              {selectedCourse
                ? `${(selectedCourse.distanceM / 1000).toFixed(1)}km`
                : "저장한 코스 중 다녀온 곳을 선택하세요"}
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color="#64748B" />
        </Pressable>
        <Text className="text-sm font-extrabold text-slate-900">
          산책 이야기
        </Text>
        <TextInput
          value={content}
          onChangeText={setContent}
          className="h-[120px] rounded-xl border border-slate-200 bg-white p-3.5"
          multiline
          placeholder="오늘의 산책은 어땠나요?"
          textAlignVertical="top"
        />
        {submitMutation.isError && (
          <Text className="text-sm text-destructive">
            {submitMutation.error.message}
          </Text>
        )}
      </ScrollView>
      <Modal
        visible={courseSheetOpen}
        transparent
        animationType="slide"
        onRequestClose={() => setCourseSheetOpen(false)}
      >
        <Pressable
          className="flex-1 justify-end bg-black/40"
          onPress={() => setCourseSheetOpen(false)}
        >
          <Pressable
            className="h-[60%] rounded-t-[28px] bg-white p-5 pt-2.5"
            onPress={(event) => event.stopPropagation()}
          >
            <View className="mb-4 h-[5px] w-[42px] self-center rounded-full bg-slate-300" />
            <Text className="mb-4 text-xl font-black">코스 선택</Text>
            <FlatList
              data={coursesQuery.data?.content ?? []}
              keyExtractor={(item) => String(item.courseId)}
              ListEmptyComponent={<EmptyState title="저장한 코스가 없어요" />}
              renderItem={({ item }) => (
                <Pressable
                  className="flex-row items-center border-b border-border py-4"
                  onPress={() => {
                    setSelectedCourse(item);
                    setCourseSheetOpen(false);
                  }}
                >
                  <View className="flex-1">
                    <Text className="font-bold">{item.name}</Text>
                    <Text className="mt-1 text-xs text-muted-foreground">
                      {(item.distanceM / 1000).toFixed(1)}km
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={20} color="#64748B" />
                </Pressable>
              )}
            />
          </Pressable>
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}

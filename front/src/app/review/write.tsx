import { Ionicons } from "@expo/vector-icons";
import { Button } from "@/components/ui/button";
import { apiRequest, resolveApiUrl } from "@/lib/api";
import { getAccessToken } from "@/lib/auth";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Image,
  Modal,
  Pressable,
  ScrollView,
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getMyBookmarks } from "@/api/course-api";
import { createPost, getPhotoUploadUrl, uploadPostPhoto } from "@/api/post-api";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Text } from "@/components/ui/text";
import type { Course } from "@/types/domain";

export default function WritePostScreen() {
  const [image, setImage] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const pick = async () => {
    const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert("권한 필요", "사진을 선택하려면 사진 보관함 권한이 필요합니다.");
      return;
    }
    const r = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      quality: 0.8,
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (!r.canceled) {
      const selected = r.assets[0];
      if (selected.fileSize && selected.fileSize > 10 * 1024 * 1024) {
        Alert.alert("파일 크기 초과", "10MB 이하의 이미지를 선택해주세요.");
        return;
      }
      setImage(selected);
    }
  };

  const submit = async () => {
    if (!image || !title.trim() || !content.trim()) {
      Alert.alert("입력 확인", "사진, 제목, 산책 이야기를 모두 입력해주세요.");
      return;
    }

    setSubmitting(true);
    try {
      const token = await getAccessToken();
      if (!token) throw new Error("로그인이 필요합니다.");

      const contentType = image.mimeType ?? "image/jpeg";
      const fileName = image.fileName ?? `walk-${Date.now()}.jpg`;
      const authorization = { Authorization: `Bearer ${token}` };
      const target = await apiRequest<{
        uploadUrl: string;
        photoUrl: string;
        expireInSeconds: number;
      }>("/api/v1/posts/photo-upload-url", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authorization },
        body: JSON.stringify({ fileName, contentType }),
      });

      const imageBlob = await (await fetch(image.uri)).blob();
      const uploadUrl = resolveApiUrl(target.uploadUrl);
      const uploadHeaders: Record<string, string> = { "Content-Type": contentType };
      if (uploadUrl.startsWith(resolveApiUrl("/local-uploads/"))) {
        uploadHeaders.Authorization = `Bearer ${token}`;
      }
      const uploadResponse = await fetch(uploadUrl, {
        method: "PUT",
        headers: uploadHeaders,
        body: imageBlob,
      });
      if (!uploadResponse.ok) throw new Error("이미지 업로드에 실패했습니다.");

      await apiRequest<{ postId: number }>("/api/v1/posts", {
        method: "POST",
        headers: { "Content-Type": "application/json", ...authorization },
        body: JSON.stringify({
          courseId: 1,
          title: title.trim(),
          content: content.trim(),
          photoUrl: target.photoUrl,
          walkedAt: new Date().toISOString(),
        }),
      });

      Alert.alert("등록 완료", "게시글이 등록되었습니다.", [
        { text: "확인", onPress: () => router.back() },
      ]);
    } catch (error) {
      Alert.alert("등록 실패", error instanceof Error ? error.message : "게시글 등록에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
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
          onPress={submit}
          disabled={submitting}
        >
          {submitting ? <ActivityIndicator color="#006E2F" /> : (
            <Text className="text-[14px] font-semibold text-[#006E2F]">등록</Text>
          )}
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
          {image ? (
            <Image source={{ uri: image.uri }} className="h-full w-full" />
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
          className="h-[52px] rounded-xl border border-slate-200 bg-white px-3.5"
          placeholder="게시글 제목"
          value={title}
          onChangeText={setTitle}
          maxLength={200}
        />
        <TextInput
          className="h-[120px] rounded-xl border border-slate-200 bg-white p-3.5"
          multiline
          placeholder="오늘의 산책은 어땠나요?"
          textAlignVertical="top"
          value={content}
          onChangeText={setContent}
          maxLength={1000}
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
        animationType="none"
        onRequestClose={dismissCourseSheet}
      >
        <View className="flex-1 justify-end">
          <Pressable
            className="absolute inset-0 bg-black/40"
            onPress={dismissCourseSheet}
          >
          </Pressable>
          <Animated.View
            className="h-[78%] rounded-t-[30px] bg-background pt-2.5"
            style={{ transform: [{ translateY: sheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={() => setCourseSheetOpen(false)}
              translateY={sheetTranslateY}
              dismissDistance={windowHeight}
            />
            <View className="h-10 items-center justify-center px-5">
              <Text className="text-[17px] font-black text-foreground">
                코스 선택
              </Text>
            </View>
            {coursesQuery.isPending ? (
              <View className="flex-1 items-center justify-center">
                <ActivityIndicator color="#087A3F" />
              </View>
            ) : coursesQuery.isError ? (
              <ErrorState
                message={coursesQuery.error.message}
                onRetry={() => void coursesQuery.refetch()}
              />
            ) : (
              <FlatList
                className="flex-1"
                data={coursesQuery.data?.content ?? []}
                keyExtractor={(item) => String(item.courseId)}
                contentContainerClassName="grow gap-3 px-5 py-4 pb-8"
                showsVerticalScrollIndicator
                nestedScrollEnabled
                keyboardShouldPersistTaps="handled"
                ListEmptyComponent={
                  <EmptyState
                    title="저장한 코스가 없어요"
                    description="먼저 마음에 드는 코스를 저장해 보세요."
                  />
                }
                renderItem={({ item }) => (
                  <Button
                    variant="ghost"
                    accessibilityLabel={`${item.name} 선택`}
                    className="min-h-[72px] flex-row items-center justify-start gap-3 rounded-2xl border border-[#E5ECE5] bg-white px-4 py-3"
                    onPress={() => {
                      setSelectedCourse(item);
                      dismissCourseSheet();
                    }}
                  >
                    <View className="h-11 w-11 items-center justify-center rounded-xl bg-[#E9F5EC]">
                      <Ionicons name="map-outline" size={22} color="#087A3F" />
                    </View>
                    <View className="flex-1 items-start">
                      <Text className="text-sm font-black text-foreground">
                        {item.name}
                      </Text>
                      <Text className="mt-1 text-xs text-muted-foreground">
                        {(item.distanceM / 1000).toFixed(1)}km · 약 {item.estimatedMinutes ?? "-"}분
                      </Text>
                    </View>
                    <Ionicons
                      name={selectedCourse?.courseId === item.courseId ? "checkmark-circle" : "chevron-forward"}
                      size={22}
                      color={selectedCourse?.courseId === item.courseId ? "#087A3F" : "#64748B"}
                    />
                  </Button>
                )}
              />
            )}
          </Animated.View>
        </View>
      </Modal>
    </SafeAreaView>
  );
}

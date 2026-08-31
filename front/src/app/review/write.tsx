import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
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
  TextInput,
  useWindowDimensions,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getMyBookmarks } from "@/api/course-api";
import { createPost, uploadPostPhoto } from "@/api/post-api";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState } from "@/components/ui/data-state";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { useThemeStore } from "@/stores/theme-store";
import { Text } from "@/components/ui/text";
import type { Course } from "@/types/domain";

export default function WritePostScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const queryClient = useQueryClient();
  const [asset, setAsset] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [content, setContent] = useState("");
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null);
  const [courseSheetOpen, setCourseSheetOpen] = useState(false);
  const { height: windowHeight } = useWindowDimensions();
  const sheetTranslateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissCourseSheet = () =>
    dismissBottomSheet(sheetTranslateY, windowHeight, () =>
      setCourseSheetOpen(false),
    );
  useEffect(() => {
    if (!courseSheetOpen) return;
    sheetTranslateY.setValue(windowHeight);
    Animated.timing(sheetTranslateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [courseSheetOpen, sheetTranslateY, windowHeight]);
  const coursesQuery = useQuery({
    queryKey: ["bookmarks", "post-picker"],
    queryFn: () => getMyBookmarks({ page: 0, size: 50 }),
  });
  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!asset || !selectedCourse)
        throw new Error("사진과 코스를 선택해 주세요.");
      const photoUrl = await uploadPostPhoto(asset);
      return createPost({
        courseId: selectedCourse.courseId,
        content: content.trim(),
        photoUrl,
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
    <SafeAreaView
      className="flex-1 bg-slate-50 dark:bg-[#111411]"
      edges={["top"]}
    >
      <View className="h-14 flex-row items-center justify-between border-b border-[#EEF1EE] bg-white px-5 dark:border-[#343D36] dark:bg-[#1B211D]">
        <Button
          variant="ghost"
          size="icon"
          accessibilityLabel="게시글 작성 닫기"
          className="h-11 w-11"
          onPress={() => router.back()}
        >
          <Ionicons
            name="close"
            size={22}
            color={isDark ? "#F1F5F2" : "#223128"}
          />
        </Button>
        <Text className="text-lg text-[#006E2F] dark:text-[#F1F5F2]">
          새 게시글
        </Text>
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
          className="h-[300px] items-center justify-center overflow-hidden rounded-xl border border-dashed border-[#BCCBB9] bg-white dark:border-[#475249] dark:bg-[#1B211D]"
          onPress={() => void pick()}
        >
          {asset ? (
            <Image source={{ uri: asset.uri }} className="h-full w-full" />
          ) : (
            <>
              <View className="h-[58px] w-[58px] items-center justify-center rounded-full bg-[#DDF8E5]">
                <Ionicons name="camera" size={30} color="#006E2F" />
              </View>
              <Text className="mt-3 text-base font-extrabold text-slate-900 dark:text-[#F1F5F2]">
                산책 사진을 업로드해주세요
              </Text>
              <Text className="mt-1 text-xs text-slate-500 dark:text-[#AAB5AD]">
                사진을 눌러 선택하세요
              </Text>
            </>
          )}
        </Pressable>
        <Pressable
          className="min-h-[76px] flex-row items-center gap-3 rounded-xl bg-white p-3.5 shadow-sm dark:bg-[#1B211D]"
          onPress={() => setCourseSheetOpen(true)}
        >
          <View className="h-11 w-11 items-center justify-center rounded-[14px] bg-[#DDF8E5]">
            <Ionicons name="map" size={23} color="#006E2F" />
          </View>
          <View className="flex-1">
            <Text className="mb-1 text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
              {selectedCourse?.name ?? "코스 선택하기"}
            </Text>
            <Text className="text-xs text-slate-500 dark:text-[#AAB5AD]">
              {selectedCourse
                ? `${(selectedCourse.distanceM / 1000).toFixed(1)}km`
                : "저장한 코스 중 다녀온 곳을 선택하세요"}
            </Text>
          </View>
          <Ionicons name="chevron-forward" size={22} color="#64748B" />
        </Pressable>
        <Text className="text-sm font-extrabold text-slate-900 dark:text-[#F1F5F2]">
          산책 이야기
        </Text>
        <TextInput
          value={content}
          onChangeText={setContent}
          className="h-[120px] rounded-xl border border-slate-200 bg-white p-3.5 text-[#191C1D] dark:border-[#343D36] dark:bg-[#1B211D] dark:text-[#F1F5F2]"
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
        animationType="none"
        onRequestClose={dismissCourseSheet}
      >
        <View className="flex-1 justify-end">
          <Pressable
            className="absolute inset-0 bg-black/40"
            onPress={dismissCourseSheet}
          ></Pressable>
          <Animated.View
            className="h-[78%] rounded-t-[30px] bg-[#FCFDFC] pt-2.5 dark:bg-[#171C18]"
            style={{ transform: [{ translateY: sheetTranslateY }] }}
          >
            <BottomSheetHandle
              onDismiss={() => setCourseSheetOpen(false)}
              translateY={sheetTranslateY}
              dismissDistance={windowHeight}
            />
            <View className="h-10 items-center justify-center px-5">
              <Text className="text-[17px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
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
                contentContainerClassName="grow px-5 py-4 pb-8"
                ItemSeparatorComponent={() => <View className="h-3" />}
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
                    className="h-[76px] flex-row items-center justify-start gap-3 rounded-2xl border border-[#E5EBE5] bg-white px-4 py-3 dark:border-[#343D36] dark:bg-[#1B211D]"
                    onPress={() => {
                      setSelectedCourse(item);
                      dismissCourseSheet();
                    }}
                  >
                    <View className="h-11 w-11 items-center justify-center rounded-xl bg-[#E9F5EC]">
                      <Ionicons name="map-outline" size={22} color="#087A3F" />
                    </View>
                    <View className="flex-1 items-start">
                      <Text className="text-sm font-black text-[#6B756D] dark:text-[#F1F5F2]">
                        {item.name}
                      </Text>
                      <Text className="mt-1 text-xs text-[#6B756D] dark:text-[#AAB5AD]">
                        {(item.distanceM / 1000).toFixed(1)}km · 약{" "}
                        {item.estimatedMinutes ?? "-"}분
                      </Text>
                    </View>
                    <Ionicons
                      name={
                        selectedCourse?.courseId === item.courseId
                          ? "checkmark-circle"
                          : "chevron-forward"
                      }
                      size={22}
                      color={
                        selectedCourse?.courseId === item.courseId
                          ? "#087A3F"
                          : "#64748B"
                      }
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

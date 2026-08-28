import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Animated,
  Easing,
  Modal,
  Pressable,
  useWindowDimensions,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { deletePost } from "@/api/post-api";
import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

const SHEET_TOP_PADDING = 10;
const HANDLE_HEIGHT = 32;
const TITLE_HEIGHT = 40;
const MENU_ROW_HEIGHT = 56;
const DIVIDER_HEIGHT = 1;
const MIN_BOTTOM_PADDING = 16;

export function PostMenuSheet({
  postId,
  open,
  canDelete,
  onClose,
}: {
  postId: number;
  open: boolean;
  canDelete: boolean;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const menuItemCount = canDelete ? 2 : 1;
  const dividerCount = canDelete ? 1 : 0;
  const bottomPadding = Math.max(insets.bottom, MIN_BOTTOM_PADDING);
  const sheetHeight =
    SHEET_TOP_PADDING +
    HANDLE_HEIGHT +
    TITLE_HEIGHT +
    MENU_ROW_HEIGHT * menuItemCount +
    DIVIDER_HEIGHT * dividerCount +
    bottomPadding;

  useEffect(() => {
    if (!open) return;
    translateY.setValue(windowHeight);
    Animated.timing(translateY, {
      toValue: 0,
      duration: 280,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [open, translateY, windowHeight]);

  const dismiss = (afterDismiss?: () => void) =>
    dismissBottomSheet(translateY, windowHeight, () => {
      onClose();
      afterDismiss?.();
    });

  const deleteMutation = useMutation({
    mutationFn: () => deletePost(postId),
    onSuccess: async () => {
      setDeleteConfirmOpen(false);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
      ]);
    },
  });

  return (
    <>
      <Modal
        visible={open}
        transparent
        animationType="none"
        onRequestClose={() => dismiss()}
      >
        <View className="flex-1 justify-end">
          <Pressable
            className="absolute inset-0 bg-black/40"
            onPress={() => dismiss()}
          />
          <Animated.View
            accessibilityRole="menu"
            className="rounded-t-[30px] bg-[#FCFDFC] pt-2.5"
            style={{
              height: sheetHeight,
              paddingBottom: bottomPadding,
              transform: [{ translateY }],
            }}
          >
            <BottomSheetHandle
              onDismiss={onClose}
              translateY={translateY}
              dismissDistance={windowHeight}
            />
            <View className="h-10 items-center justify-center px-5">
              <Text className="text-[17px] font-black text-[#191C1D]">
                게시물 메뉴
              </Text>
            </View>
            {canDelete && (
              <Button
                variant="ghost"
                className="h-14 justify-start rounded-none px-5"
                onPress={() => dismiss(() => setDeleteConfirmOpen(true))}
              >
                <Ionicons name="trash-outline" size={20} color="#DC2626" />
                <Text className="text-[15px] font-semibold text-[#DC2626]">
                  삭제
                </Text>
              </Button>
            )}
            {canDelete && <View className="h-px bg-[#EEF1EE]" />}
            <Button
              variant="ghost"
              className="h-14 justify-start rounded-none px-5"
              onPress={() => dismiss()}
            >
              <Ionicons name="flag-outline" size={20} color="#33443A" />
              <Text className="text-[15px] font-semibold text-[#33443A]">
                신고
              </Text>
            </Button>
          </Animated.View>
        </View>
      </Modal>

      <Modal
        visible={deleteConfirmOpen}
        transparent
        animationType="fade"
        onRequestClose={() => {
          if (!deleteMutation.isPending) setDeleteConfirmOpen(false);
        }}
      >
        <View className="flex-1 items-center justify-center bg-black/40 px-6">
          <View className="w-full max-w-[360px] rounded-2xl bg-white p-5 shadow-lg">
            <View className="mb-4 h-11 w-11 items-center justify-center rounded-full bg-[#FEECEC]">
              <Ionicons name="trash-outline" size={22} color="#DC2626" />
            </View>
            <Text className="text-lg font-extrabold text-[#17251B]">
              게시물을 삭제할까요?
            </Text>
            <Text className="mt-2 text-sm leading-5 text-[#667168]">
              삭제한 게시물은 다시 복구할 수 없습니다.
            </Text>
            {deleteMutation.isError && (
              <Text className="mt-3 text-sm text-[#DC2626]">
                {deleteMutation.error.message}
              </Text>
            )}
            <View className="mt-6 flex-row gap-2.5">
              <Button
                variant="secondary"
                className="h-12 flex-1 rounded-xl bg-[#EEF2EF]"
                disabled={deleteMutation.isPending}
                onPress={() => setDeleteConfirmOpen(false)}
              >
                <Text className="font-bold text-[#33443A]">취소</Text>
              </Button>
              <Button
                variant="destructive"
                className="h-12 flex-1 rounded-xl bg-[#DC2626]"
                disabled={deleteMutation.isPending}
                onPress={() => deleteMutation.mutate()}
              >
                {deleteMutation.isPending && (
                  <ActivityIndicator size="small" color="white" />
                )}
                <Text className="font-bold text-white">
                  {deleteMutation.isPending ? "삭제 중" : "삭제"}
                </Text>
              </Button>
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

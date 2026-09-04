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
import { ReportModal } from "@/components/report/report-modal";
import { BlockUserDialog } from "@/components/user/block-user-dialog";
import { useBlockedUserIds } from "@/hooks/use-blocked-users";

const SHEET_TOP_PADDING = 10;
const HANDLE_HEIGHT = 32;
const TITLE_HEIGHT = 40;
const MENU_ROW_HEIGHT = 56;
const MIN_BOTTOM_PADDING = 16;

export function PostMenuSheet({
  postId,
  open,
  canDelete,
  authorUserId,
  authorNickname,
  onClose,
}: {
  postId: number;
  open: boolean;
  canDelete: boolean;
  /** 작성자 id. 없으면(목록에 내려오지 않으면) 사용자 신고/차단 메뉴를 숨긴다. */
  authorUserId?: number;
  authorNickname?: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [reportTarget, setReportTarget] = useState<"POST" | "USER" | null>(null);
  const [blockOpen, setBlockOpen] = useState(false);
  const { isBlocked } = useBlockedUserIds();
  const blocked = isBlocked(authorUserId);
  const nickname = authorNickname ?? "이 사용자";
  const canActOnUser = !canDelete && authorUserId != null;

  // 1행: 삭제(내 글) 또는 게시물 신고(남의 글) + 남의 글이면 사용자 신고·차단 2행
  const menuRowCount = 1 + (canActOnUser ? 2 : 0);
  const bottomPadding = Math.max(insets.bottom, MIN_BOTTOM_PADDING);
  const sheetHeight =
    SHEET_TOP_PADDING +
    HANDLE_HEIGHT +
    TITLE_HEIGHT +
    MENU_ROW_HEIGHT * menuRowCount +
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
            className="rounded-t-[30px] bg-[#FCFDFC] pt-2.5 dark:bg-[#171C18]"
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
              <Text className="text-[17px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
                게시물 메뉴
              </Text>
            </View>

            {canDelete ? (
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
            ) : (
              <Button
                variant="ghost"
                className="h-14 justify-start rounded-none px-5"
                onPress={() => dismiss(() => setReportTarget("POST"))}
              >
                <Ionicons name="flag-outline" size={20} color="#33443A" />
                <Text className="text-[15px] font-semibold text-[#33443A] dark:text-[#D4DDD6]">
                  게시물 신고
                </Text>
              </Button>
            )}

            {canActOnUser && (
              <>
                <Button
                  variant="ghost"
                  className="h-14 justify-start rounded-none px-5"
                  onPress={() => dismiss(() => setReportTarget("USER"))}
                >
                  <Ionicons
                    name="person-remove-outline"
                    size={20}
                    color="#33443A"
                  />
                  <Text className="text-[15px] font-semibold text-[#33443A] dark:text-[#D4DDD6]">
                    이 사용자 신고
                  </Text>
                </Button>
                <Button
                  variant="ghost"
                  className="h-14 justify-start rounded-none px-5"
                  onPress={() => dismiss(() => setBlockOpen(true))}
                >
                  <Ionicons name="ban-outline" size={20} color="#DC2626" />
                  <Text className="text-[15px] font-semibold text-[#DC2626]">
                    {blocked ? "차단 해제" : "차단하기"}
                  </Text>
                </Button>
              </>
            )}
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
          <View className="w-full max-w-[360px] rounded-2xl bg-white p-5 shadow-lg dark:bg-[#1B211D]">
            <View className="mb-4 h-11 w-11 items-center justify-center rounded-full bg-[#FEECEC]">
              <Ionicons name="trash-outline" size={22} color="#DC2626" />
            </View>
            <Text className="text-lg font-extrabold text-[#17251B] dark:text-[#F1F5F2]">
              게시물을 삭제할까요?
            </Text>
            <Text className="mt-2 text-sm leading-5 text-[#667168] dark:text-[#AAB5AD]">
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
                className="h-12 flex-1 rounded-xl bg-[#EEF2EF] dark:bg-[#2A312C]"
                disabled={deleteMutation.isPending}
                onPress={() => setDeleteConfirmOpen(false)}
              >
                <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                  취소
                </Text>
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

      <ReportModal
        open={reportTarget === "POST"}
        targetType="POST"
        targetId={postId}
        onClose={() => setReportTarget(null)}
      />
      {authorUserId != null && (
        <>
          <ReportModal
            open={reportTarget === "USER"}
            targetType="USER"
            targetId={authorUserId}
            onClose={() => setReportTarget(null)}
          />
          <BlockUserDialog
            open={blockOpen}
            userId={authorUserId}
            nickname={nickname}
            blocked={blocked}
            onClose={() => setBlockOpen(false)}
          />
        </>
      )}
    </>
  );
}

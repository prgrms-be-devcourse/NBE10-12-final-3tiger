import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Alert, Modal, Pressable, View } from "react-native";

import { reportComment, reportPost, reportUser } from "@/api/report-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import type { ReportReason, ReportTargetType } from "@/types/domain";

const REPORT_REASONS: Array<{ key: ReportReason; label: string }> = [
  { key: "SPAM", label: "스팸/광고" },
  { key: "ABUSE", label: "욕설/비방" },
  { key: "SEXUAL", label: "음란물/선정성" },
  { key: "HARASSMENT", label: "괴롭힘/따돌림" },
  { key: "ILLEGAL", label: "불법 정보" },
  { key: "ETC", label: "기타" },
];

const TARGET_LABEL: Record<ReportTargetType, string> = {
  POST: "게시물",
  COMMENT: "댓글",
  USER: "사용자",
};

type Props = {
  open: boolean;
  targetType: ReportTargetType;
  targetId: number;
  onClose: () => void;
};

export function ReportModal({ open, targetType, targetId, onClose }: Props) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState<ReportReason | null>(null);

  useEffect(() => {
    if (open) setReason(null);
  }, [open]);

  const mutation = useMutation({
    mutationFn: (selected: ReportReason) => {
      if (targetType === "POST") return reportPost(targetId, selected);
      if (targetType === "COMMENT") return reportComment(targetId, selected);
      return reportUser(targetId, selected);
    },
    onSuccess: (result) => {
      // 임계치 도달로 서버가 대상을 숨긴 경우 목록을 갱신해 바로 사라지게 한다.
      if (result.hidden) {
        void queryClient.invalidateQueries({ queryKey: ["posts"] });
        void queryClient.invalidateQueries({ queryKey: ["post-comments"] });
        void queryClient.invalidateQueries({ queryKey: ["liked-posts"] });
        void queryClient.invalidateQueries({ queryKey: ["my-posts"] });
      }
      onClose();
      Alert.alert("신고 접수", "신고가 접수되었습니다.");
    },
  });

  return (
    <Modal visible={open} transparent animationType="fade" onRequestClose={onClose}>
      <View className="flex-1 items-center justify-center bg-black/45 px-5">
        <Pressable
          accessibilityLabel="신고 닫기"
          className="absolute inset-0"
          onPress={onClose}
        />
        <View className="w-full rounded-3xl bg-white p-5 dark:bg-[#1B211D]">
          <View className="flex-row items-center justify-between">
            <View className="flex-1">
              <Text className="text-lg font-black text-[#191C1D] dark:text-[#F1F5F2]">
                {TARGET_LABEL[targetType]} 신고
              </Text>
              <Text className="mt-1 text-xs text-slate-500 dark:text-[#AAB5AD]">
                신고 사유를 선택해 주세요.
              </Text>
            </View>
            <Button
              variant="ghost"
              size="icon"
              onPress={onClose}
              accessibilityLabel="신고 닫기"
            >
              <Ionicons name="close" size={22} color="#526056" />
            </Button>
          </View>

          <View className="mt-4 gap-1.5">
            {REPORT_REASONS.map((option) => {
              const selected = reason === option.key;
              return (
                <Pressable
                  key={option.key}
                  accessibilityRole="radio"
                  accessibilityState={{ selected }}
                  className={`h-12 flex-row items-center gap-3 rounded-xl border px-3.5 ${
                    selected
                      ? "border-[#087A3F] bg-[#E9FBEF] dark:border-[#4ADE80] dark:bg-[#24382B]"
                      : "border-[#DCE8DD] bg-white dark:border-[#3A473D] dark:bg-[#242B26]"
                  }`}
                  onPress={() => setReason(option.key)}
                >
                  <Ionicons
                    name={selected ? "radio-button-on" : "radio-button-off"}
                    size={20}
                    color={selected ? "#087A3F" : "#94A09A"}
                  />
                  <Text
                    className={`text-sm font-semibold ${
                      selected
                        ? "text-[#087A3F] dark:text-[#86EFAC]"
                        : "text-[#34443A] dark:text-[#D4DDD6]"
                    }`}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>

          {mutation.isError ? (
            <Text className="mt-3 text-xs text-red-600">
              {(mutation.error as Error).message}
            </Text>
          ) : null}

          <View className="mt-5 flex-row gap-2">
            <Button
              variant="outline"
              className="h-11 flex-1 rounded-xl"
              disabled={mutation.isPending}
              onPress={onClose}
            >
              <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                취소
              </Text>
            </Button>
            <Button
              className="h-11 flex-1 rounded-xl bg-[#DC2626]"
              disabled={!reason || mutation.isPending}
              onPress={() => reason && mutation.mutate(reason)}
            >
              <Text className="font-bold text-white">
                {mutation.isPending ? "신고 중..." : "신고하기"}
              </Text>
            </Button>
          </View>
        </View>
      </View>
    </Modal>
  );
}

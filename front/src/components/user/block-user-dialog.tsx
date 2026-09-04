import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Alert, Modal, View } from "react-native";

import { blockUser, unblockUser } from "@/api/user-block-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import {
  BLOCKED_USER_IDS_KEY,
  BLOCKED_USERS_KEY,
} from "@/hooks/use-blocked-users";

type Props = {
  open: boolean;
  userId: number;
  nickname: string;
  /** 현재 차단 상태. true면 "차단 해제", false면 "차단하기" */
  blocked: boolean;
  onClose: () => void;
  /** 차단/해제 성공 후 추가 처리 (예: 목록에서 즉시 제거) */
  onDone?: (blocked: boolean) => void;
};

export function BlockUserDialog({
  open,
  userId,
  nickname,
  blocked,
  onClose,
  onDone,
}: Props) {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => (blocked ? unblockUser(userId) : blockUser(userId)),
    onSuccess: async (result) => {
      onClose();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: BLOCKED_USER_IDS_KEY }),
        queryClient.invalidateQueries({ queryKey: BLOCKED_USERS_KEY }),
        // 차단/해제하면 서버가 피드·댓글에서 서로 숨기므로 다시 불러온다.
        queryClient.invalidateQueries({ queryKey: ["posts"] }),
        queryClient.invalidateQueries({ queryKey: ["post-comments"] }),
        queryClient.invalidateQueries({ queryKey: ["liked-posts"] }),
        queryClient.invalidateQueries({ queryKey: ["my-posts"] }),
      ]);
      onDone?.(result.blocked);
      Alert.alert(
        result.blocked ? "차단 완료" : "차단 해제",
        result.blocked
          ? `${nickname}님을 차단했습니다.`
          : `${nickname}님 차단을 해제했습니다.`,
      );
    },
  });

  return (
    <Modal
      visible={open}
      transparent
      animationType="fade"
      onRequestClose={() => {
        if (!mutation.isPending) onClose();
      }}
    >
      <View className="flex-1 items-center justify-center bg-black/40 px-6">
        <View className="w-full max-w-[360px] rounded-2xl bg-white p-5 shadow-lg dark:bg-[#1B211D]">
          <View className="mb-4 h-11 w-11 items-center justify-center rounded-full bg-[#FEECEC]">
            <Ionicons
              name={blocked ? "person-add-outline" : "person-remove-outline"}
              size={22}
              color="#DC2626"
            />
          </View>
          <Text className="text-lg font-extrabold text-[#17251B] dark:text-[#F1F5F2]">
            {blocked
              ? `${nickname}님 차단을 해제할까요?`
              : `${nickname}님을 차단하시겠습니까?`}
          </Text>
          <Text className="mt-2 text-sm leading-5 text-[#667168] dark:text-[#AAB5AD]">
            {blocked
              ? "차단을 해제하면 서로의 게시물과 댓글이 다시 보여요."
              : "차단하면 서로의 게시물·댓글이 숨겨지고, 댓글·좋아요를 남길 수 없어요."}
          </Text>
          {mutation.isError ? (
            <Text className="mt-3 text-sm text-[#DC2626]">
              {(mutation.error as Error).message}
            </Text>
          ) : null}
          <View className="mt-6 flex-row gap-2.5">
            <Button
              variant="secondary"
              className="h-12 flex-1 rounded-xl bg-[#EEF2EF] dark:bg-[#2A312C]"
              disabled={mutation.isPending}
              onPress={onClose}
            >
              <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                취소
              </Text>
            </Button>
            <Button
              variant="destructive"
              className="h-12 flex-1 rounded-xl bg-[#DC2626]"
              disabled={mutation.isPending}
              onPress={() => mutation.mutate()}
            >
              {mutation.isPending && (
                <ActivityIndicator size="small" color="white" />
              )}
              <Text className="font-bold text-white">
                {blocked ? "차단 해제" : "차단하기"}
              </Text>
            </Button>
          </View>
        </View>
      </View>
    </Modal>
  );
}

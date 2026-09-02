import { Ionicons } from "@expo/vector-icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ActivityIndicator, Modal, Pressable, TextInput, View } from "react-native";

import {
  deletePersonalUserMemo,
  getPersonalUserMemo,
  savePersonalUserMemo,
} from "@/api/user-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

type Props = {
  open: boolean;
  targetUserId: number;
  nickname: string;
  onClose: () => void;
};

export function PersonalUserMemoSheet({ open, targetUserId, nickname, onClose }: Props) {
  const queryClient = useQueryClient();
  const [tagsText, setTagsText] = useState("");
  const [memo, setMemo] = useState("");
  const memoQuery = useQuery({
    queryKey: ["personal-user-memo", targetUserId],
    queryFn: () => getPersonalUserMemo(targetUserId),
    enabled: open,
    retry: false,
  });
  useEffect(() => {
    if (!open) return;
    if (memoQuery.data) {
      setTagsText(memoQuery.data.tags.join(", "));
      setMemo(memoQuery.data.memo ?? "");
    } else if (memoQuery.isError) {
      setTagsText("");
      setMemo("");
    }
  }, [open, memoQuery.data, memoQuery.isError]);
  const saveMutation = useMutation({
    mutationFn: () => {
      const tags = [...new Set(tagsText.split(",").map((tag) => tag.trim()).filter(Boolean))];
      return savePersonalUserMemo(targetUserId, { tags, memo: memo.trim() || null });
    },
    onSuccess: (data) => {
      queryClient.setQueryData(["personal-user-memo", targetUserId], data);
      onClose();
    },
  });
  const deleteMutation = useMutation({
    mutationFn: () => deletePersonalUserMemo(targetUserId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["personal-user-memo", targetUserId] });
      onClose();
    },
  });
  const isPending = saveMutation.isPending || deleteMutation.isPending;
  const error = saveMutation.error ?? deleteMutation.error;

  return (
    <Modal visible={open} transparent animationType="fade" onRequestClose={onClose}>
      <View className="flex-1 items-center justify-center bg-black/45 px-5">
        <Pressable className="absolute inset-0" onPress={onClose} />
        <View className="w-full rounded-3xl bg-white p-5 dark:bg-[#1B211D]">
          <View className="flex-row items-center justify-between">
            <View>
              <Text className="text-lg font-black text-[#191C1D] dark:text-[#F1F5F2]">
                {nickname}님 메모
              </Text>
              <Text className="mt-1 text-xs text-slate-500 dark:text-[#AAB5AD]">
                나에게만 보이는 개인 기록이에요.
              </Text>
            </View>
            <Button variant="ghost" size="icon" onPress={onClose} accessibilityLabel="메모 닫기">
              <Ionicons name="close" size={22} color="#526056" />
            </Button>
          </View>
          {memoQuery.isPending ? (
            <ActivityIndicator color="#087A3F" className="my-10" />
          ) : (
            <>
              <Text className="mt-5 text-xs font-bold text-[#526056] dark:text-[#AAB5AD]">
                태그 (쉼표로 구분, 최대 10개)
              </Text>
              <TextInput
                value={tagsText}
                onChangeText={setTagsText}
                placeholder="예: 반려견 동반, 코스 추천"
                placeholderTextColor="#94A09A"
                maxLength={209}
                className="mt-2 h-12 rounded-xl border border-[#DCE8DD] px-3 text-sm text-[#191C1D] dark:border-[#3A473D] dark:text-white"
              />
              <Text className="mt-4 text-xs font-bold text-[#526056] dark:text-[#AAB5AD]">메모</Text>
              <TextInput
                value={memo}
                onChangeText={setMemo}
                placeholder="이 사용자에 대해 기억할 내용을 남겨 보세요."
                placeholderTextColor="#94A09A"
                multiline
                textAlignVertical="top"
                maxLength={1000}
                className="mt-2 h-28 rounded-xl border border-[#DCE8DD] p-3 text-sm text-[#191C1D] dark:border-[#3A473D] dark:text-white"
              />
              {error ? <Text className="mt-2 text-xs text-red-600">{error.message}</Text> : null}
              <View className="mt-5 flex-row gap-2">
                {memoQuery.data ? (
                  <Button variant="outline" className="h-11 flex-1 rounded-xl" disabled={isPending} onPress={() => deleteMutation.mutate()}>
                    <Text className="font-bold text-red-600">삭제</Text>
                  </Button>
                ) : null}
                <Button className="h-11 flex-1 rounded-xl bg-[#087A3F]" disabled={isPending} onPress={() => saveMutation.mutate()}>
                  <Text className="font-bold text-white">저장</Text>
                </Button>
              </View>
            </>
          )}
        </View>
      </View>
    </Modal>
  );
}

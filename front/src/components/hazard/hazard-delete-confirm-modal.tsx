import { Ionicons } from "@expo/vector-icons";
import { ActivityIndicator, Modal, Pressable, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

type Props = {
  open: boolean;
  isDeleting: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function HazardDeleteConfirmModal({
  open,
  isDeleting,
  onClose,
  onConfirm,
}: Props) {
  return (
    <Modal
      visible={open}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={() => {
        if (!isDeleting) onClose();
      }}
    >
      <View className="flex-1 items-center justify-center bg-black/40 px-6">
        <Pressable
          accessibilityLabel="신고 취소 확인 닫기"
          className="absolute inset-0"
          disabled={isDeleting}
          onPress={onClose}
        />
        <View className="w-full max-w-[360px] rounded-[28px] bg-white p-5 shadow-lg dark:bg-[#1B211D]">
          <View className="h-11 w-11 items-center justify-center rounded-full bg-[#FFF7E6] dark:bg-[#3D3322]">
            <Ionicons name="warning-outline" size={22} color="#D97706" />
          </View>
          <Text className="mt-4 text-lg font-extrabold text-[#17251B] dark:text-[#F1F5F2]">
            내 신고를 취소할까요?
          </Text>
          <Text className="mt-2 text-sm leading-5 text-[#667168] dark:text-[#AAB5AD]">
            신고를 취소하면 위험 상태가 다시 검증 대기 상태로 변경될 수
            있습니다.
          </Text>
          <View className="mt-6 flex-row gap-2.5">
            <Button
              variant="secondary"
              className="h-12 flex-1 rounded-xl bg-[#EEF2EF] dark:bg-[#2A312C]"
              disabled={isDeleting}
              onPress={onClose}
            >
              <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                돌아가기
              </Text>
            </Button>
            <Button
              variant="destructive"
              className="h-12 flex-1 rounded-xl bg-[#DC2626]"
              disabled={isDeleting}
              onPress={onConfirm}
            >
              {isDeleting && <ActivityIndicator size="small" color="white" />}
              <Text className="font-bold text-white">신고 취소</Text>
            </Button>
          </View>
        </View>
      </View>
    </Modal>
  );
}

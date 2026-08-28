import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { Modal, Pressable, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

type LoginRequiredModalProps = {
  visible: boolean;
  onClose: () => void;
};

export function LoginRequiredModal({
  visible,
  onClose,
}: LoginRequiredModalProps) {
  const goToLogin = () => {
    onClose();
    router.push("/(auth)/login" as never);
  };

  return (
    <Modal
      transparent
      visible={visible}
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}
    >
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="로그인 안내 닫기"
        className="flex-1 items-center justify-center bg-black/40 px-6"
        onPress={onClose}
      >
        <Pressable
          accessibilityRole="none"
          className="w-full max-w-sm items-center rounded-[28px] bg-white px-6 pb-5 pt-7"
          onPress={(event) => event.stopPropagation()}
        >
          <View className="h-14 w-14 items-center justify-center rounded-full bg-[#E9FBEF]">
            <Ionicons name="lock-closed-outline" size={27} color="#087A3F" />
          </View>
          <Text className="mt-4 text-xl font-extrabold text-[#191C1D]">
            로그인이 필요합니다
          </Text>
          <Text className="mt-2 text-center text-sm leading-5 text-[#6B756D]">
            로그인하면 좋아요와 저장 기능을 사용할 수 있어요.
          </Text>
          <Button
            className="mt-6 h-12 w-full rounded-xl bg-[#087A3F]"
            onPress={goToLogin}
          >
            <Text className="font-extrabold text-white">로그인하기</Text>
          </Button>
          <Button
            variant="ghost"
            className="mt-1 h-11 w-full rounded-xl"
            onPress={onClose}
          >
            <Text className="font-bold text-[#6B756D]">나중에</Text>
          </Button>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

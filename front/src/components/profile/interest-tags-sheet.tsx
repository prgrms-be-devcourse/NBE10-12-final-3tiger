import { Ionicons } from "@expo/vector-icons";
import { useEffect, useRef } from "react";
import {
  Animated,
  Easing,
  Modal,
  Pressable,
  Text,
  useWindowDimensions,
  View,
} from "react-native";

import {
  BottomSheetHandle,
  dismissBottomSheet,
} from "@/components/ui/bottom-sheet-handle";

const INTEREST_TAGS = [
  { key: "park", label: "공원 위주", icon: "leaf" as const },
  { key: "shade", label: "그늘 많은 곳", icon: "umbrella" as const },
  { key: "flat", label: "평탄한 길", icon: "remove" as const },
  { key: "water", label: "식수대 있음", icon: "water" as const },
];

export function InterestTagsSheet({
  open,
  tags,
  pending,
  onToggle,
  onClose,
}: {
  open: boolean;
  tags: string[];
  pending: boolean;
  onToggle: (tag: string) => void;
  onClose: () => void;
}) {
  const { height: windowHeight } = useWindowDimensions();
  const translateY = useRef(new Animated.Value(windowHeight)).current;
  const dismissSheet = () =>
    dismissBottomSheet(translateY, windowHeight, onClose);

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

  return (
    <Modal
      visible={open}
      transparent
      animationType="none"
      onRequestClose={dismissSheet}
    >
      <View className="flex-1 justify-end">
        <Pressable
          accessibilityLabel="관심 태그 닫기"
          className="absolute inset-0 bg-black/40"
          onPress={dismissSheet}
        />
        <Animated.View
          className="rounded-t-[30px] bg-[#FCFDFC] pb-9 pt-2.5 dark:bg-[#171C18]"
          style={{ transform: [{ translateY }] }}
        >
          <BottomSheetHandle
            onDismiss={onClose}
            translateY={translateY}
            dismissDistance={windowHeight}
          />
          <View className="px-5">
            <Text className="text-[20px] font-black text-[#191C1D] dark:text-[#F1F5F2]">
              관심 태그
            </Text>
            <Text className="mb-5 mt-1.5 text-xs leading-[19px] text-slate-600 dark:text-[#AAB5AD]">
              선호하는 산책 환경을 선택해주세요.
            </Text>
            <View className="flex-row flex-wrap gap-2">
              {INTEREST_TAGS.map((item) => {
                const active = tags.includes(item.key);
                return (
                  <Pressable
                    key={item.key}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active, disabled: pending }}
                    disabled={pending}
                    className={`h-14 w-[48.5%] flex-row items-center gap-2 rounded-xl border px-3 ${active ? "border-[#22C55E] bg-[#E9FBEF] dark:bg-[#24382B]" : "border-slate-200 bg-white dark:border-[#343D36] dark:bg-[#242B26]"}`}
                    onPress={() => onToggle(item.key)}
                  >
                    <Ionicons
                      name={item.icon}
                      size={18}
                      color={active ? "#087A3F" : "#64748B"}
                    />
                    <Text
                      className={`text-xs font-extrabold ${active ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-slate-600 dark:text-[#AAB5AD]"}`}
                    >
                      {item.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}

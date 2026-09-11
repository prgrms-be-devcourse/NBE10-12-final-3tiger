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

const PERSONAS = [
  { key: "walker", label: "일반", icon: "walk" as const, color: "#087A3F" },
  { key: "dog", label: "반려견", icon: "paw" as const, color: "#F97316" },
  {
    key: "senior",
    label: "시니어",
    icon: "accessibility" as const,
    color: "#A855F7",
  },
  {
    key: "stroller",
    label: "유모차",
    icon: "happy" as const,
    color: "#0EA5E9",
  },
];

export function WalkingTypeSheet({
  open,
  persona,
  pending,
  onSelect,
  onClose,
}: {
  open: boolean;
  persona: string;
  pending: boolean;
  onSelect: (persona: string) => void;
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
          accessibilityLabel="걷기 유형 닫기"
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
              나의 걷기 유형
            </Text>
            <Text className="mb-5 mt-1.5 text-xs leading-[19px] text-slate-600 dark:text-[#AAB5AD]">
              맞춤형 경로를 위해 주된 유형을 선택해주세요.
            </Text>
            <View className="flex-row gap-2">
              {PERSONAS.map((item) => {
                const active = persona === item.key;
                return (
                  <Pressable
                    key={item.key}
                    accessibilityRole="button"
                    accessibilityState={{ selected: active, disabled: pending }}
                    disabled={pending}
                    className={`h-24 flex-1 items-center justify-center gap-2 rounded-xl border-2 ${active ? "bg-[#E9FBEF] dark:bg-[#24382B]" : "border-slate-200 bg-white dark:border-[#343D36] dark:bg-[#242B26]"}`}
                    style={active ? { borderColor: item.color } : undefined}
                    onPress={() => onSelect(item.key)}
                  >
                    <Ionicons
                      name={item.icon}
                      size={29}
                      color={active ? item.color : "#64748B"}
                    />
                    <Text
                      className="text-xs font-extrabold text-slate-600 dark:text-[#AAB5AD]"
                      style={active ? { color: item.color } : undefined}
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

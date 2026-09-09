import { Ionicons } from "@expo/vector-icons";
import { useEffect, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import type { LatLng } from "react-native-maps";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import type { HazardCreateRequest } from "@/types/domain";

const HAZARD_TYPES = ["빙판", "공사", "파손", "장애물", "기타"] as const;
const SEVERITIES = ["하", "중", "상"] as const;

type HazardReportSheetProps = {
  open: boolean;
  coordinate: LatLng | null;
  isSubmitting: boolean;
  errorMessage?: string | null;
  onSubmit: (request: HazardCreateRequest) => void;
  onChangeLocation: () => void;
  onClose: () => void;
};

export function HazardReportSheet({
  open,
  coordinate,
  isSubmitting,
  errorMessage,
  onSubmit,
  onChangeLocation,
  onClose,
}: HazardReportSheetProps) {
  const [hazardType, setHazardType] = useState<string | null>(null);
  const [severity, setSeverity] = useState<string | null>(null);
  const [content, setContent] = useState("");

  useEffect(() => {
    if (!open) return;
    setHazardType(null);
    setSeverity(null);
    setContent("");
  }, [open]);

  const canSubmit =
    coordinate !== null &&
    hazardType !== null &&
    severity !== null &&
    content.trim().length > 0 &&
    !isSubmitting;

  const submit = () => {
    if (!canSubmit || !coordinate || !hazardType || !severity) return;
    onSubmit({
      hazardType,
      severity,
      content,
      latitude: coordinate.latitude,
      longitude: coordinate.longitude,
    });
  };

  return (
    <Modal
      visible={open}
      transparent
      animationType="fade"
      onRequestClose={() => {
        if (!isSubmitting) onClose();
      }}
    >
      <KeyboardAvoidingView
        className="flex-1 justify-end"
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <Pressable
          accessibilityLabel="위험 신고 닫기"
          className="absolute inset-0 bg-black/40"
          disabled={isSubmitting}
          onPress={onClose}
        />
        <View className="max-h-[86%] rounded-t-[30px] bg-[#FCFDFC] px-5 pb-8 pt-5 dark:bg-[#171C18]">
          <View className="flex-row items-start justify-between">
            <View className="flex-1 pr-3">
              <Text className="text-xl font-black text-[#191C1D] dark:text-[#F1F5F2]">
                위험 신고
              </Text>
              <Text className="mt-1 text-xs leading-5 text-[#6B756D] dark:text-[#AAB5AD]">
                선택한 위치의 위험 정보를 알려주세요.
              </Text>
            </View>
            <Button
              variant="ghost"
              size="icon"
              accessibilityLabel="위험 신고 닫기"
              disabled={isSubmitting}
              onPress={onClose}
            >
              <Ionicons name="close" size={23} color="#526056" />
            </Button>
          </View>

          <ScrollView
            className="mt-4"
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}
          >
            <View className="flex-row items-center justify-between rounded-2xl bg-[#F2F8F2] px-4 py-3 dark:bg-[#242B26]">
              <View className="flex-row items-center gap-2">
                <Ionicons name="location" size={19} color="#DC2626" />
                <Text className="text-xs font-bold text-[#34443A] dark:text-[#D4DDD6]">
                  {coordinate
                    ? `${coordinate.latitude.toFixed(5)}, ${coordinate.longitude.toFixed(5)}`
                    : "위치를 선택해 주세요"}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={isSubmitting}
                onPress={onChangeLocation}
              >
                <Text className="text-xs font-black text-[#087A3F]">
                  위치 변경
                </Text>
              </Pressable>
            </View>

            <Text className="mt-5 text-sm font-black text-[#34443A] dark:text-[#D4DDD6]">
              위험 유형
            </Text>
            <View className="mt-2 flex-row flex-wrap gap-2">
              {HAZARD_TYPES.map((option) => {
                const selected = hazardType === option;
                return (
                  <Pressable
                    key={option}
                    accessibilityRole="radio"
                    accessibilityState={{ selected }}
                    className={`h-10 justify-center rounded-full border px-4 ${
                      selected
                        ? "border-[#DC2626] bg-[#FEECEC] dark:bg-[#422626]"
                        : "border-[#DCE8DD] bg-white dark:border-[#3A473D] dark:bg-[#242B26]"
                    }`}
                    onPress={() => setHazardType(option)}
                  >
                    <Text
                      className={`text-sm font-bold ${
                        selected
                          ? "text-[#B91C1C] dark:text-[#FCA5A5]"
                          : "text-[#526056] dark:text-[#D4DDD6]"
                      }`}
                    >
                      {option}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            <Text className="mt-5 text-sm font-black text-[#34443A] dark:text-[#D4DDD6]">
              심각도
            </Text>
            <View className="mt-2 flex-row gap-2">
              {SEVERITIES.map((option) => {
                const selected = severity === option;
                return (
                  <Pressable
                    key={option}
                    accessibilityRole="radio"
                    accessibilityState={{ selected }}
                    className={`h-11 flex-1 items-center justify-center rounded-xl border ${
                      selected
                        ? "border-[#DC2626] bg-[#FEECEC] dark:bg-[#422626]"
                        : "border-[#DCE8DD] bg-white dark:border-[#3A473D] dark:bg-[#242B26]"
                    }`}
                    onPress={() => setSeverity(option)}
                  >
                    <Text
                      className={`font-bold ${
                        selected
                          ? "text-[#B91C1C] dark:text-[#FCA5A5]"
                          : "text-[#526056] dark:text-[#D4DDD6]"
                      }`}
                    >
                      {option}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            <View className="mt-5 flex-row items-center justify-between">
              <Text className="text-sm font-black text-[#34443A] dark:text-[#D4DDD6]">
                신고 내용
              </Text>
              <Text className="text-xs text-[#94A09A]">
                {content.length}/1000
              </Text>
            </View>
            <TextInput
              value={content}
              onChangeText={setContent}
              placeholder="위험 상황을 구체적으로 알려주세요."
              placeholderTextColor="#94A09A"
              multiline
              textAlignVertical="top"
              maxLength={1000}
              className="mt-2 h-28 rounded-2xl border border-[#DCE8DD] bg-white p-3 text-sm text-[#191C1D] dark:border-[#3A473D] dark:bg-[#242B26] dark:text-white"
            />

            {errorMessage ? (
              <Text className="mt-3 text-xs font-bold text-[#DC2626]">
                {errorMessage}
              </Text>
            ) : null}

            <View className="mt-5 flex-row gap-2.5">
              <Button
                variant="secondary"
                className="h-12 flex-1 rounded-xl bg-[#EEF2EF] dark:bg-[#2A312C]"
                disabled={isSubmitting}
                onPress={onClose}
              >
                <Text className="font-bold text-[#33443A] dark:text-[#D4DDD6]">
                  취소
                </Text>
              </Button>
              <Button
                className="h-12 flex-1 rounded-xl bg-[#DC2626] active:bg-[#B91C1C]"
                disabled={!canSubmit}
                onPress={submit}
              >
                {isSubmitting ? (
                  <ActivityIndicator size="small" color="white" />
                ) : (
                  <Ionicons name="warning" size={18} color="white" />
                )}
                <Text className="font-black text-white">
                  {isSubmitting ? "신고 중" : "신고하기"}
                </Text>
              </Button>
            </View>
          </ScrollView>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

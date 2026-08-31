import { Ionicons } from "@expo/vector-icons";
import { useMutation } from "@tanstack/react-query";
import { router } from "expo-router";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { checkEmail, signup } from "@/api/auth-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useThemeStore } from "@/stores/theme-store";

export default function SignupScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [nickname, setNickname] = useState("");
  const [checkedEmail, setCheckedEmail] = useState<string | null>(null);

  const emailMutation = useMutation({
    mutationFn: checkEmail,
    onSuccess: () => setCheckedEmail(email.trim()),
  });
  const signupMutation = useMutation({
    mutationFn: signup,
    onSuccess: () => router.replace("/(auth)/login" as never),
  });
  const passwordMatches = password.length >= 8 && password === passwordConfirm;
  const canSubmit =
    checkedEmail === email.trim() &&
    passwordMatches &&
    Boolean(nickname.trim());
  const submit = () => {
    if (!canSubmit) return;
    signupMutation.mutate({
      email: email.trim(),
      password,
      nickname: nickname.trim(),
    });
  };

  const fields = [
    {
      label: "이메일",
      icon: "mail-outline" as const,
      placeholder: "이메일을 입력하세요",
      value: email,
      onChangeText: (value: string) => {
        setEmail(value);
        setCheckedEmail(null);
      },
      secure: false,
    },
    {
      label: "비밀번호",
      icon: "lock-closed-outline" as const,
      placeholder: "8자 이상 입력하세요",
      value: password,
      onChangeText: setPassword,
      secure: true,
    },
    {
      label: "비밀번호 확인",
      icon: "lock-closed-outline" as const,
      placeholder: "비밀번호를 다시 입력하세요",
      value: passwordConfirm,
      onChangeText: setPasswordConfirm,
      secure: true,
    },
    {
      label: "닉네임",
      icon: "person-outline" as const,
      placeholder: "사용할 닉네임을 입력하세요",
      value: nickname,
      onChangeText: setNickname,
      secure: false,
    },
  ];

  return (
    <SafeAreaView className="flex-1 bg-[#F6FBF6] dark:bg-[#111411]">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          contentContainerClassName="p-5 pb-10"
          keyboardShouldPersistTaps="handled"
        >
          <View className="h-[52px] flex-row items-center justify-between">
            <Button
              variant="ghost"
              size="icon"
              accessibilityLabel="뒤로 가기"
              className="h-12 w-12"
              onPress={() => router.back()}
            >
              <Ionicons
                name="arrow-back"
                size={24}
                color={isDark ? "#F1F5F2" : "#33443A"}
              />
            </Button>
            <Text className="text-xl font-semibold text-[#0B1C30] dark:text-[#F1F5F2]">
              회원가입
            </Text>
            <View className="w-12" />
          </View>
          <Text className="mb-[22px] mt-[22px] text-[15px] text-slate-500 dark:text-[#AAB5AD]">
            오늘의산책과 함께 건강한 일상을 시작하세요
          </Text>
          {fields.map((field, index) => (
            <View key={field.label}>
              <Text className="mb-2 mt-3 text-sm font-extrabold text-[#26372D] dark:text-[#D4DDD6]">
                {field.label}
              </Text>
              <View className="flex-row gap-2">
                <View className="h-14 flex-1 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-[15px] dark:border-[#475249] dark:bg-[#1B211D]">
                  <Ionicons name={field.icon} size={21} color="#64748B" />
                  <TextInput
                    value={field.value}
                    onChangeText={field.onChangeText}
                    className="flex-1 text-sm"
                    placeholder={field.placeholder}
                    secureTextEntry={field.secure}
                    autoCapitalize={index === 0 ? "none" : undefined}
                    keyboardType={index === 0 ? "email-address" : "default"}
                  />
                </View>
                {index === 0 && (
                  <Button
                    variant="outline"
                    className={`h-14 w-[88px] rounded-xl border-[#22C55E] ${checkedEmail === email.trim() ? "bg-[#DDF8E5]" : ""}`}
                    disabled={!email.trim() || emailMutation.isPending}
                    onPress={() => emailMutation.mutate(email.trim())}
                  >
                    <Text className="text-xs font-extrabold text-[#006E2F]">
                      {emailMutation.isPending
                        ? "확인 중"
                        : checkedEmail === email.trim()
                          ? "확인완료"
                          : "중복확인"}
                    </Text>
                  </Button>
                )}
              </View>
            </View>
          ))}
          {passwordConfirm.length > 0 && !passwordMatches && (
            <Text className="mt-2 text-sm text-destructive">
              비밀번호는 8자 이상이며 서로 같아야 합니다.
            </Text>
          )}
          {(emailMutation.isError || signupMutation.isError) && (
            <Text className="mt-3 text-sm text-destructive">
              {(emailMutation.error ?? signupMutation.error)?.message}
            </Text>
          )}
          <Button
            className="mt-7 h-14 rounded-xl"
            disabled={!canSubmit || signupMutation.isPending}
            onPress={submit}
          >
            <Text className="text-base font-black text-primary-foreground">
              {signupMutation.isPending ? "가입 중..." : "회원가입 완료"}
            </Text>
          </Button>
          <View className="mt-[22px] flex-row justify-center">
            <Text className="text-[13px] text-slate-500 dark:text-[#AAB5AD]">
              이미 계정이 있으신가요?{" "}
            </Text>
            <Button
              variant="link"
              className="h-auto p-0"
              onPress={() => router.replace("/(auth)/login" as never)}
            >
              <Text className="text-[13px] font-extrabold text-[#006E2F]">
                로그인
              </Text>
            </Button>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

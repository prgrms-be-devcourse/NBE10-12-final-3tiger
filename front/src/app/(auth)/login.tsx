import { Ionicons } from "@expo/vector-icons";
import { useMutation } from "@tanstack/react-query";
import { router } from "expo-router";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { login } from "@/api/auth-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";

export default function LoginScreen() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [secure, setSecure] = useState(true);
  const saveTokens = useAuthStore((state) => state.saveTokens);
  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: async (tokens) => {
      await saveTokens(tokens);
      router.replace("/(tabs)/map" as never);
    },
  });
  const submit = () => {
    if (!email.trim() || !password) return;
    loginMutation.mutate({ email: email.trim(), password });
  };

  return (
    <SafeAreaView className="flex-1 bg-[#F6FBF6]">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          className="flex-1"
          contentContainerClassName="p-6 pb-10"
          keyboardShouldPersistTaps="handled"
        >
          <Button
            variant="ghost"
            size="icon"
            accessibilityLabel="뒤로 가기"
            className="h-12 w-12"
            onPress={() => router.back()}
          >
            <Ionicons name="arrow-back" size={24} color="#33443A" />
          </Button>
          <View className="mt-5 h-[72px] w-[72px] self-center items-center justify-center rounded-3xl bg-[#DDF8E5]">
            <Ionicons name="leaf" size={38} color="#006E2F" />
          </View>
          <Text className="mt-5 text-center text-[32px] font-black text-[#0B1C30]">
            환영합니다
          </Text>
          <Text className="mb-[34px] mt-2 text-center text-base text-slate-500">
            안전하고 즐거운 산책을 시작하세요
          </Text>

          <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D]">
            이메일
          </Text>
          <View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4">
            <Ionicons name="mail-outline" size={21} color="#64748B" />
            <TextInput
              value={email}
              onChangeText={setEmail}
              className="flex-1 text-[15px] text-[#0B1C30]"
              placeholder="이메일을 입력하세요"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
            />
          </View>
          <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D]">
            비밀번호
          </Text>
          <View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4">
            <Ionicons name="lock-closed-outline" size={21} color="#64748B" />
            <TextInput
              value={password}
              onChangeText={setPassword}
              className="flex-1 text-[15px] text-[#0B1C30]"
              placeholder="비밀번호를 입력하세요"
              secureTextEntry={secure}
              autoComplete="password"
              onSubmitEditing={submit}
            />
            <Pressable
              accessibilityLabel={secure ? "비밀번호 표시" : "비밀번호 숨기기"}
              onPress={() => setSecure(!secure)}
            >
              <Ionicons
                name={secure ? "eye-off-outline" : "eye-outline"}
                size={21}
                color="#64748B"
              />
            </Pressable>
          </View>
          {loginMutation.isError && (
            <Text className="mt-3 text-sm text-destructive">
              {loginMutation.error.message}
            </Text>
          )}
          <Button
            className="mt-6 h-14 rounded-xl"
            disabled={!email.trim() || !password || loginMutation.isPending}
            onPress={submit}
          >
            <Text className="text-base font-black text-primary-foreground">
              {loginMutation.isPending ? "로그인 중..." : "로그인"}
            </Text>
          </Button>
          <Button
            variant="outline"
            className="mt-3 h-14 rounded-xl border-[#087A3F] bg-white"
            onPress={() => router.push("/(auth)/signup" as never)}
          >
            <Text className="text-base font-black text-[#087A3F]">
              회원가입
            </Text>
          </Button>

          <View className="my-6 flex-row items-center gap-2.5">
            <View className="h-px flex-1 bg-[#D7DED8]" />
            <Text className="text-xs text-[#7B867E]">
              소셜 로그인은 인가 코드 설정 후 사용할 수 있어요
            </Text>
            <View className="h-px flex-1 bg-[#D7DED8]" />
          </View>
          <Button
            variant="secondary"
            className="mb-2.5 h-[54px] rounded-xl bg-[#FEE500]"
            disabled
          >
            <Text className="font-extrabold text-black">카카오로 시작하기</Text>
          </Button>
          <Button
            variant="outline"
            className="mb-2.5 h-[54px] rounded-xl bg-white"
            disabled
          >
            <Text className="font-extrabold">G　Google로 시작하기</Text>
          </Button>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

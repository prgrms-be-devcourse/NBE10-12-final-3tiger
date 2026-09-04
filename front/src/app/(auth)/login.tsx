import { Ionicons } from "@expo/vector-icons";
import { useMutation } from "@tanstack/react-query";
import * as AuthSession from "expo-auth-session";
import * as Linking from "expo-linking";
import { router, useFocusEffect } from "expo-router";
import * as WebBrowser from "expo-web-browser";
import { useCallback, useEffect, useState } from "react";
import {
  BackHandler,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { login, socialLogin } from "@/api/auth-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";

WebBrowser.maybeCompleteAuthSession();

const KAKAO_BACKEND_REDIRECT_URI = "http://172.30.1.40:8080/api/v1/auth/kakao/callback";
const GOOGLE_REDIRECT_URI = AuthSession.makeRedirectUri({ scheme: "front" });

export default function LoginScreen() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [secure, setSecure] = useState(true);
  const saveTokens = useAuthStore((state) => state.saveTokens);

  const [kakaoRequest, , kakaoPromptAsync] =
    AuthSession.useAuthRequest(
      {
        clientId: process.env.EXPO_PUBLIC_KAKAO_CLIENT_ID ?? "",
        redirectUri: KAKAO_BACKEND_REDIRECT_URI,
      },
      { authorizationEndpoint: "https://kauth.kakao.com/oauth/authorize" },
    );

  const [googleRequest, googleResponse, googlePromptAsync] =
    AuthSession.useAuthRequest(
      {
        clientId: process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID ?? "",
        redirectUri: GOOGLE_REDIRECT_URI,
        scopes: ["openid", "profile", "email"],
      },
      { authorizationEndpoint: "https://accounts.google.com/o/oauth2/v2/auth" },
    );

  const loginMutation = useMutation({
    mutationFn: login,
    onSuccess: async (tokens) => {
      await saveTokens(tokens);
      router.replace("/(tabs)/map" as never);
    },
  });

  const socialLoginMutation = useMutation({
    mutationFn: ({
      provider,
      code,
    }: {
      provider: "kakao" | "google";
      code: string;
    }) => socialLogin(provider, code),
    onSuccess: async (tokens) => {
      await saveTokens(tokens);
      router.replace("/(tabs)/map" as never);
    },
  });

  useEffect(() => {
    const subscription = Linking.addEventListener("url", ({ url }) => {
      const { path, queryParams } = Linking.parse(url);
      if (path !== "oauth-callback") return;

      const params = queryParams as Record<string, string>;
      if (params.error) {
        socialLoginMutation.reset();
        return;
      }
      if (params.accessToken && params.refreshToken) {
        saveTokens({
          accessToken: params.accessToken,
          refreshToken: params.refreshToken,
          isNewUser: params.isNewUser === "true",
        }).then(() => router.replace("/(tabs)/map" as never));
      }
    });
    return () => subscription.remove();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

useEffect(() => {
  if (googleResponse?.type === "success") {
    socialLoginMutation.mutate({
      provider: "google",
      code: googleResponse.params.code,
    });
  }
}, [googleResponse]); // eslint-disable-line react-hooks/exhaustive-deps

  useFocusEffect(
    useCallback(() => {
      const subscription = BackHandler.addEventListener(
        "hardwareBackPress",
        () => true,
      );
      return () => subscription.remove();
    }, []),
  );
  const submit = () => {
    if (!email.trim() || !password) return;
    loginMutation.mutate({ email: email.trim(), password });
  };

  return (
    <SafeAreaView className="flex-1 bg-[#F6FBF6] dark:bg-[#111411]">
      <KeyboardAvoidingView
        className="flex-1"
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        <ScrollView
          className="flex-1"
          contentContainerClassName="p-6 pb-10"
          keyboardShouldPersistTaps="handled"
        >
          <View className="mt-8 h-[72px] w-[72px] self-center items-center justify-center rounded-3xl bg-[#DDF8E5]">
            <Ionicons name="leaf" size={38} color="#006E2F" />
          </View>
          <Text className="mt-5 text-center text-[32px] font-black text-[#0B1C30] dark:text-[#F1F5F2]">
            환영합니다
          </Text>
          <Text className="mb-[34px] mt-2 text-center text-base text-slate-500 dark:text-[#AAB5AD]">
            안전하고 즐거운 산책을 시작하세요
          </Text>

          <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D] dark:text-[#D4DDD6]">
            이메일
          </Text>
          <View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4 dark:border-[#475249] dark:bg-[#1B211D]">
            <Ionicons name="mail-outline" size={21} color="#64748B" />
            <TextInput
              value={email}
              onChangeText={setEmail}
              className="flex-1 text-[15px] text-[#0B1C30] dark:text-[#F1F5F2]"
              placeholder="이메일을 입력하세요"
              placeholderTextColor="#94A3B8"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
            />
          </View>
          <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D] dark:text-[#D4DDD6]">
            비밀번호
          </Text>
          <View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4 dark:border-[#475249] dark:bg-[#1B211D]">
            <Ionicons name="lock-closed-outline" size={21} color="#64748B" />
            <TextInput
              value={password}
              onChangeText={setPassword}
              className="flex-1 text-[15px] text-[#0B1C30] dark:text-[#F1F5F2]"
              placeholder="비밀번호를 입력하세요"
              placeholderTextColor="#94A3B8"
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
          {(loginMutation.isError || socialLoginMutation.isError) && (
            <Text className="mt-3 text-sm text-destructive">
              {loginMutation.error?.message ?? socialLoginMutation.error?.message}
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
            variant="secondary"
            className="mt-3 h-12 rounded-xl bg-white dark:bg-[#1B211D]"
            onPress={() => router.replace("/(tabs)/map" as never)}
          >
            <Ionicons name="compass-outline" size={19} color="#365F49" />
            <Text className="font-extrabold text-[#365F49] dark:text-[#86EFAC]">
              로그인 없이 이용하기
            </Text>
          </Button>
          <View className="my-6 flex-row items-center gap-2.5">
            <View className="h-px flex-1 bg-[#D7DED8]" />
            <Text className="text-xs text-[#7B867E] dark:text-[#AAB5AD]">
              또는
            </Text>
            <View className="h-px flex-1 bg-[#D7DED8]" />
          </View>
          <Button
            variant="secondary"
            className="mb-2.5 h-[54px] rounded-xl bg-[#FEE500]"
            disabled={!kakaoRequest || socialLoginMutation.isPending}
            onPress={() => kakaoPromptAsync()}
          >
            <Text className="font-extrabold text-black">
              {socialLoginMutation.isPending ? "로그인 중..." : "카카오로 시작하기"}
            </Text>
          </Button>
          <Button
            variant="secondary"
            className="mb-2.5 h-[54px] rounded-xl bg-white dark:bg-[#1B211D]"
            disabled={!googleRequest || socialLoginMutation.isPending}
            onPress={() => googlePromptAsync()}
          >
            <Text className="font-extrabold text-black">
              {socialLoginMutation.isPending ? "로그인 중..." : "G　Google로 시작하기"}
            </Text>
          </Button>
          <View className="mt-5 flex-row justify-center">
            <Text className="text-[13px] text-slate-500 dark:text-[#AAB5AD]">
              아직 계정이 없으신가요?{" "}
            </Text>
            <Pressable
              accessibilityRole="link"
              accessibilityLabel="회원가입"
              onPress={() => router.push("/(auth)/signup" as never)}
            >
              <Text className="text-[13px] font-extrabold text-[#006E2F]">
                회원가입
              </Text>
            </Pressable>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

import { Ionicons } from "@expo/vector-icons";
import { useMutation } from "@tanstack/react-query";
import { router } from "expo-router";
import { useEffect, useRef, useState } from "react";
import {
  AppState,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { sendEmailVerification, signup, verifyEmailCode } from "@/api/auth-api";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { useThemeStore } from "@/stores/theme-store";
import { ApiError } from "@/types/api";

type VerificationStatus = "idle" | "sent" | "verified" | "locked";
type EmailVerificationAttempt = {
  email: string;
  version: number;
};

const RESEND_COOLDOWN_MS = 60_000;

export default function SignupScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [passwordConfirm, setPasswordConfirm] = useState("");
  const [nickname, setNickname] = useState("");
  const [verificationCode, setVerificationCode] = useState("");
  const [verificationToken, setVerificationToken] = useState<string | null>(
    null,
  );
  const [verificationStatus, setVerificationStatus] =
    useState<VerificationStatus>("idle");
  const [sentEmail, setSentEmail] = useState<string | null>(null);
  const [resendAvailableAt, setResendAvailableAt] = useState<number | null>(
    null,
  );
  const [timerNow, setTimerNow] = useState(Date.now());
  const [verificationErrorMessage, setVerificationErrorMessage] = useState<
    string | null
  >(null);
  const currentEmailRef = useRef("");
  const emailVersionRef = useRef(0);

  const verifyMutation = useMutation({
    mutationFn: ({
      email,
      code,
    }: EmailVerificationAttempt & { code: string }) =>
      verifyEmailCode(email, code),
    onSuccess: (response, request) => {
      if (
        request.email !== currentEmailRef.current ||
        request.version !== emailVersionRef.current
      ) {
        return;
      }
      setVerificationToken(response.verificationToken);
      setVerificationStatus("verified");
      setVerificationErrorMessage(null);
    },
    onError: (error, request) => {
      if (
        request.email !== currentEmailRef.current ||
        request.version !== emailVersionRef.current
      ) {
        return;
      }
      setVerificationToken(null);
      if (error instanceof ApiError && error.resultCode === "AUTH_429_1") {
        setVerificationStatus("locked");
        setResendAvailableAt(null);
        setVerificationErrorMessage(
          "인증 시도 횟수를 초과했습니다. 인증번호를 다시 받아주세요.",
        );
        return;
      }
      if (error instanceof ApiError && error.resultCode === "AUTH_400_3") {
        setVerificationCode("");
        setVerificationStatus("sent");
      }
    },
  });
  const sendMutation = useMutation({
    mutationFn: ({ email }: EmailVerificationAttempt) =>
      sendEmailVerification(email),
    onSuccess: (_, request) => {
      if (
        request.email !== currentEmailRef.current ||
        request.version !== emailVersionRef.current
      ) {
        return;
      }
      const now = Date.now();
      setVerificationCode("");
      setVerificationToken(null);
      setVerificationStatus("sent");
      setSentEmail(request.email);
      setResendAvailableAt(now + RESEND_COOLDOWN_MS);
      setTimerNow(now);
      setVerificationErrorMessage(null);
      verifyMutation.reset();
    },
    onError: (error, request) => {
      if (
        request.email !== currentEmailRef.current ||
        request.version !== emailVersionRef.current
      ) {
        return;
      }
      if (error instanceof ApiError && error.resultCode === "AUTH_429_2") {
        const now = Date.now();
        setVerificationStatus((current) =>
          current === "locked" ? "locked" : "sent",
        );
        setSentEmail(request.email);
        setResendAvailableAt(now + RESEND_COOLDOWN_MS);
        setTimerNow(now);
        return;
      }
      if (error instanceof ApiError && error.resultCode === "AUTH_502_2") {
        setResendAvailableAt(null);
      }
    },
  });
  const signupMutation = useMutation({
    mutationFn: signup,
    onSuccess: () => router.replace("/(auth)/login" as never),
    onError: (error) => {
      if (error instanceof ApiError && error.resultCode === "AUTH_403_1") {
        setVerificationCode("");
        setVerificationToken(null);
        setVerificationStatus("idle");
        setSentEmail(null);
        setResendAvailableAt(null);
        setVerificationErrorMessage(
          "이메일 인증이 만료되었습니다. 인증번호를 다시 받아주세요.",
        );
      }
    },
  });

  useEffect(() => {
    if (!resendAvailableAt) return;

    const updateTimer = () => setTimerNow(Date.now());
    const intervalId = setInterval(() => {
      const now = Date.now();
      setTimerNow(now);
      if (now >= resendAvailableAt) clearInterval(intervalId);
    }, 1_000);
    const appStateSubscription = AppState.addEventListener(
      "change",
      (nextState) => {
        if (nextState === "active") updateTimer();
      },
    );

    return () => {
      clearInterval(intervalId);
      appStateSubscription.remove();
    };
  }, [resendAvailableAt]);

  const trimmedEmail = email.trim();
  const remainingSeconds = resendAvailableAt
    ? Math.max(0, Math.ceil((resendAvailableAt - timerNow) / 1_000))
    : 0;
  const passwordMatches = password.length >= 8 && password === passwordConfirm;
  const canSubmit =
    verificationStatus === "verified" &&
    Boolean(verificationToken) &&
    sentEmail === trimmedEmail &&
    passwordMatches &&
    Boolean(nickname.trim());

  const resetVerification = () => {
    setVerificationCode("");
    setVerificationToken(null);
    setVerificationStatus("idle");
    setSentEmail(null);
    setResendAvailableAt(null);
    setVerificationErrorMessage(null);
    sendMutation.reset();
    verifyMutation.reset();
    signupMutation.reset();
  };
  const sendCode = () => {
    if (!trimmedEmail || sendMutation.isPending) return;
    setVerificationErrorMessage(null);
    sendMutation.reset();
    verifyMutation.reset();
    signupMutation.reset();
    sendMutation.mutate({
      email: trimmedEmail,
      version: emailVersionRef.current,
    });
  };
  const verifyCode = () => {
    if (
      verificationStatus !== "sent" ||
      verificationCode.length !== 6 ||
      verifyMutation.isPending ||
      !sentEmail
    ) {
      return;
    }
    setVerificationErrorMessage(null);
    sendMutation.reset();
    verifyMutation.reset();
    signupMutation.reset();
    verifyMutation.mutate({
      email: sentEmail,
      code: verificationCode,
      version: emailVersionRef.current,
    });
  };
  const submit = () => {
    if (!canSubmit || !verificationToken) return;
    signupMutation.mutate({
      email: trimmedEmail,
      password,
      nickname: nickname.trim(),
      emailVerificationToken: verificationToken,
    });
  };

  const fields = [
    {
      label: "이메일",
      icon: "mail-outline" as const,
      placeholder: "이메일을 입력하세요",
      value: email,
      onChangeText: (value: string) => {
        emailVersionRef.current += 1;
        currentEmailRef.current = value.trim();
        setEmail(value);
        resetVerification();
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
                    className={`h-14 w-[104px] rounded-xl border-[#22C55E] px-2 ${verificationStatus === "verified" ? "bg-[#DDF8E5]" : ""}`}
                    disabled={
                      !trimmedEmail ||
                      sendMutation.isPending ||
                      verificationStatus !== "idle"
                    }
                    onPress={sendCode}
                  >
                    <Text
                      numberOfLines={1}
                      className="text-xs font-extrabold text-[#006E2F]"
                    >
                      {sendMutation.isPending
                        ? "발송 중"
                        : verificationStatus === "verified"
                          ? "인증완료"
                          : verificationStatus === "idle"
                            ? "인증번호 발송"
                            : "발송완료"}
                    </Text>
                  </Button>
                )}
              </View>
              {index === 0 && verificationStatus !== "idle" && (
                <View>
                  <Text className="mb-2 mt-3 text-sm font-extrabold text-[#26372D] dark:text-[#D4DDD6]">
                    인증번호
                  </Text>
                  <View className="flex-row gap-2">
                    <View className="h-14 flex-1 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-[15px] dark:border-[#475249] dark:bg-[#1B211D]">
                      <Ionicons
                        name="keypad-outline"
                        size={21}
                        color="#64748B"
                      />
                      <TextInput
                        value={verificationCode}
                        onChangeText={(value) =>
                          setVerificationCode(
                            value.replace(/[^0-9]/g, "").slice(0, 6),
                          )
                        }
                        className="flex-1 text-sm"
                        placeholder="6자리 인증번호"
                        keyboardType="number-pad"
                        maxLength={6}
                        editable={verificationStatus === "sent"}
                      />
                    </View>
                    <Button
                      variant="outline"
                      className={`h-14 w-[104px] rounded-xl border-[#22C55E] px-2 ${verificationStatus === "verified" ? "bg-[#DDF8E5]" : ""}`}
                      disabled={
                        verificationStatus !== "sent" ||
                        verificationCode.length !== 6 ||
                        verifyMutation.isPending
                      }
                      onPress={verifyCode}
                    >
                      <Text className="text-xs font-extrabold text-[#006E2F]">
                        {verifyMutation.isPending
                          ? "확인 중"
                          : verificationStatus === "verified"
                            ? "인증완료"
                            : "인증 확인"}
                      </Text>
                    </Button>
                  </View>
                  {verificationStatus === "verified" ? (
                    <Text className="mt-2 text-sm font-semibold text-[#006E2F]">
                      이메일 인증이 완료되었습니다.
                    </Text>
                  ) : (
                    <View className="mt-2 flex-row items-center justify-between">
                      {verificationStatus === "sent" ? (
                        <Text className="text-sm text-[#006E2F]">
                          인증번호를 발송했습니다.
                        </Text>
                      ) : (
                        <View />
                      )}
                      <Button
                        variant="link"
                        className="h-9 px-1"
                        disabled={
                          remainingSeconds > 0 || sendMutation.isPending
                        }
                        onPress={sendCode}
                      >
                        <Text className="text-xs font-extrabold text-[#006E2F]">
                          {sendMutation.isPending
                            ? "재전송 중"
                            : remainingSeconds > 0
                              ? `재전송 (${remainingSeconds}초)`
                              : "인증번호 재전송"}
                        </Text>
                      </Button>
                    </View>
                  )}
                </View>
              )}
            </View>
          ))}
          {passwordConfirm.length > 0 && !passwordMatches && (
            <Text className="mt-2 text-sm text-destructive">
              비밀번호는 8자 이상이며 서로 같아야 합니다.
            </Text>
          )}
          {(verificationErrorMessage ||
            sendMutation.isError ||
            verifyMutation.isError ||
            signupMutation.isError) && (
            <Text className="mt-3 text-sm text-destructive">
              {verificationErrorMessage ??
                sendMutation.error?.message ??
                verifyMutation.error?.message ??
                signupMutation.error?.message}
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

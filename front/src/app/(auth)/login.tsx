import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { useState } from "react";
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

export default function LoginScreen() {
  const [secure, setSecure] = useState(true);
  return <SafeAreaView className="flex-1 bg-[#F6FBF6]"><KeyboardAvoidingView className="flex-1" behavior={Platform.OS === "ios" ? "padding" : undefined}><ScrollView className="flex-1" contentContainerClassName="p-6 pb-10" keyboardShouldPersistTaps="handled">
    <Pressable className="h-12 w-12 justify-center" onPress={() => router.back()}><Ionicons name="arrow-back" size={24} color="#33443A" /></Pressable>
    <View className="mt-5 h-[72px] w-[72px] self-center items-center justify-center rounded-3xl bg-[#DDF8E5]"><Ionicons name="leaf" size={38} color="#006E2F" /></View>
    <Text className="mt-5 text-center text-[32px] font-black text-[#0B1C30]">환영합니다</Text><Text className="mb-[34px] mt-2 text-center text-base text-slate-500">안전하고 즐거운 산책을 시작하세요</Text>
    <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D]">이메일</Text><View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4"><Ionicons name="mail-outline" size={21} color="#64748B" /><TextInput className="flex-1 text-[15px] text-[#0B1C30]" placeholder="이메일을 입력하세요" keyboardType="email-address" autoCapitalize="none" /></View>
    <Text className="mb-2 mt-3.5 text-sm font-extrabold text-[#26372D]">비밀번호</Text><View className="h-14 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-4"><Ionicons name="lock-closed-outline" size={21} color="#64748B" /><TextInput className="flex-1 text-[15px] text-[#0B1C30]" placeholder="비밀번호를 입력하세요" secureTextEntry={secure} /><Pressable onPress={() => setSecure(!secure)}><Ionicons name={secure ? "eye-off-outline" : "eye-outline"} size={21} color="#64748B" /></Pressable></View>
    <View className="mt-3.5 flex-row justify-between"><Text className="text-[13px] text-slate-500">□  로그인 유지</Text><Text className="text-[13px] font-extrabold text-[#006E2F]">비밀번호 찾기</Text></View>
    <Pressable className="mt-6 h-14 items-center justify-center rounded-xl bg-[#006E2F]" onPress={() => router.replace("/(tabs)/map" as never)}><Text className="text-base font-black text-white">로그인</Text></Pressable>
    <View className="my-6 flex-row items-center gap-2.5"><View className="h-px flex-1 bg-[#D7DED8]" /><Text className="text-xs text-[#7B867E]">또는 소셜 계정으로 로그인</Text><View className="h-px flex-1 bg-[#D7DED8]" /></View>
    <Pressable className="mb-2.5 h-[54px] items-center justify-center rounded-xl bg-[#FEE500]" onPress={() => router.replace("/(tabs)/map" as never)}><Text className="font-extrabold">카카오로 시작하기</Text></Pressable><Pressable className="mb-2.5 h-[54px] items-center justify-center rounded-xl border border-[#D7DED8] bg-white" onPress={() => router.replace("/(tabs)/map" as never)}><Text className="font-extrabold">G　Google로 시작하기</Text></Pressable>
    <View className="mt-5 flex-row justify-center"><Text className="text-[13px] text-slate-500">아직 계정이 없으신가요? </Text><Pressable onPress={() => router.push("/(auth)/signup" as never)}><Text className="text-[13px] font-extrabold text-[#006E2F]">회원가입</Text></Pressable></View>
  </ScrollView></KeyboardAvoidingView></SafeAreaView>;
}

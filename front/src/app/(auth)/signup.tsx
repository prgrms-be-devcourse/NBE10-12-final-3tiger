import { Ionicons } from "@expo/vector-icons";
import { router } from "expo-router";
import { useState } from "react";
import { Image, KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

const FIELDS = [{ label: "이메일", icon: "mail-outline" as const, placeholder: "이메일을 입력하세요" }, { label: "비밀번호", icon: "lock-closed-outline" as const, placeholder: "8자 이상 입력하세요" }, { label: "비밀번호 확인", icon: "lock-closed-outline" as const, placeholder: "비밀번호를 다시 입력하세요" }, { label: "닉네임", icon: "person-outline" as const, placeholder: "사용할 닉네임을 입력하세요" }];
export default function SignupScreen() {
  const [checked, setChecked] = useState(false);
  return <SafeAreaView className="flex-1 bg-[#F6FBF6]"><KeyboardAvoidingView className="flex-1" behavior={Platform.OS === "ios" ? "padding" : undefined}><ScrollView contentContainerClassName="p-5 pb-10" keyboardShouldPersistTaps="handled">
    <View className="h-[52px] flex-row items-center justify-between"><Pressable className="h-12 w-12 justify-center" onPress={() => router.back()}><Ionicons name="arrow-back" size={24} color="#33443A" /></Pressable><Image source={require("../../../assets/title.png")} className="h-9 w-[138px]" resizeMode="contain" /><View className="w-12" /></View>
    <Text className="mt-[22px] text-3xl font-black text-[#0B1C30]">회원가입</Text><Text className="mb-[22px] mt-2 text-[15px] text-slate-500">오늘의산책과 함께 건강한 일상을 시작하세요</Text>
    {FIELDS.map((field, index) => <View key={field.label}><Text className="mb-2 mt-3 text-sm font-extrabold text-[#26372D]">{field.label}</Text><View className="flex-row gap-2"><View className="h-14 flex-1 flex-row items-center gap-2.5 rounded-xl border border-[#BCCBB9] bg-white px-[15px]"><Ionicons name={field.icon} size={21} color="#64748B" /><TextInput className="flex-1 text-sm" placeholder={field.placeholder} secureTextEntry={index === 1 || index === 2} autoCapitalize={index === 0 ? "none" : undefined} /></View>{index === 0 && <Pressable className={`h-14 w-[82px] items-center justify-center rounded-xl border border-[#22C55E] ${checked ? "bg-[#DDF8E5]" : ""}`} onPress={() => setChecked(true)}><Text className="text-xs font-extrabold text-[#006E2F]">{checked ? "확인완료" : "중복확인"}</Text></Pressable>}</View></View>)}
    <Pressable className="mt-7 h-14 items-center justify-center rounded-xl bg-[#006E2F]" onPress={() => router.replace("/(auth)/login" as never)}><Text className="text-base font-black text-white">회원가입 완료</Text></Pressable><View className="mt-[22px] flex-row justify-center"><Text className="text-[13px] text-slate-500">이미 계정이 있으신가요? </Text><Pressable onPress={() => router.replace("/(auth)/login" as never)}><Text className="text-[13px] font-extrabold text-[#006E2F]">로그인</Text></Pressable></View>
  </ScrollView></KeyboardAvoidingView></SafeAreaView>;
}

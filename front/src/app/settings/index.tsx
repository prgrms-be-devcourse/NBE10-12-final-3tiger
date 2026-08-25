import { Ionicons } from "@expo/vector-icons";
import * as ImagePicker from "expo-image-picker";
import { router } from "expo-router";
import { useState } from "react";
import {
  Image,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
const INITIAL =
  "https://lh3.googleusercontent.com/aida-public/AB6AXuCJsRPYBQCHgxBRzPFw66AOkmuy-9AcNt_g2vsX57A8W5WkBn2NTvk8pFMWNVdsTfB9j5-00K7LwDAttKzEqCEUpxz40iWZklmWuCCcnJapH0ozdk-JNtRzP-j3d1u3JqLk8W02FSfkUNj4lT6eT9hyxMflOn3Fk36NJbW9YAjrVawmzPvEb1mC8y_lFK_h3vCesJQSqde1Kmut7D5DynM8blIyQOG-sWzPXphy32YPAxjvirdKML5-PQ";
export default function SettingsScreen() {
  const [image, setImage] = useState(INITIAL),
    [name, setName] = useState("산책러");
  const pick = async () => {
    const r = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      allowsEditing: true,
      aspect: [1, 1],
    });
    if (!r.canceled) setImage(r.assets[0].uri);
  };
  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="h-[58px] flex-row items-center justify-between bg-white px-5 shadow-sm">
        <Pressable
          className="h-11 w-11 justify-center"
          onPress={() => router.back()}
        >
          <Ionicons name="arrow-back" size={24} color="#33443A" />
        </Pressable>
        <Text className="text-2xl font-black text-[#006E2F]">설정</Text>
        <View className="w-11" />
      </View>
      <ScrollView contentContainerClassName="p-5 pb-10">
        <View className="mt-[18px] self-center">
          <Image source={{ uri: image }} className="h-28 w-28 rounded-full" />
          <Pressable
            className="absolute bottom-0 right-0 h-[38px] w-[38px] items-center justify-center rounded-full border-[3px] border-white bg-[#006E2F]"
            onPress={pick}
          >
            <Ionicons name="camera" size={20} color="white" />
          </Pressable>
        </View>
        <Pressable onPress={pick}>
          <Text className="mb-6 mt-3 text-center font-extrabold text-[#006E2F]">
            이미지 변경
          </Text>
        </Pressable>
        <Text className="mb-2 mt-3.5 text-sm font-extrabold text-slate-900">
          이름
        </Text>
        <TextInput
          value={name}
          onChangeText={setName}
          className="h-14 rounded-xl border border-[#BCCBB9] px-[15px] text-[15px]"
        />
        <Text className="mb-2 mt-3.5 text-sm font-extrabold text-slate-900">
          소개 (선택사항)
        </Text>
        <TextInput
          className="h-[100px] rounded-xl border border-[#BCCBB9] p-[15px]"
          multiline
          placeholder="나를 소개해 주세요"
          textAlignVertical="top"
        />
        <Pressable
          className="mt-6 h-14 items-center justify-center rounded-xl bg-[#006E2F]"
          onPress={() => router.replace("/(tabs)/profile" as never)}
        >
          <Text className="text-[15px] font-black text-white">
            변경 사항 저장
          </Text>
        </Pressable>
        <View className="mt-6 rounded-xl bg-slate-50 px-[15px]">
          {[
            ["lock-closed", "계정 보안"],
            ["notifications", "알림 설정"],
            ["document-text", "서비스 이용약관"],
          ].map(([icon, label], i) => (
            <Pressable
              key={label}
              className={`h-[62px] flex-row items-center gap-3 ${i < 2 ? "border-b border-slate-200" : ""}`}
            >
              <Ionicons name={icon as any} size={21} color="#475569" />
              <Text className="flex-1 text-[15px] text-slate-900">{label}</Text>
              <Ionicons name="chevron-forward" size={21} color="#64748B" />
            </Pressable>
          ))}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

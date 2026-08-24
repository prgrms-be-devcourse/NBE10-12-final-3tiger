import { Pressable, Text, View } from "react-native";

export default function HomeScreen() {
  return (
    <View className="flex-1 items-center justify-center bg-slate-100 px-6">
      <Text className="text-2xl font-bold text-slate-900">
        NativeWind 적용 완료
      </Text>

      <Text className="mt-2 text-center text-base text-slate-500">
        React Native 컴포넌트에 Tailwind 클래스를 사용할 수 있습니다.
      </Text>

      <Pressable className="mt-6 rounded-xl bg-blue-500 px-5 py-3 active:bg-blue-700">
        <Text className="font-semibold text-white">시작하기</Text>
      </Pressable>
    </View>
  );
}

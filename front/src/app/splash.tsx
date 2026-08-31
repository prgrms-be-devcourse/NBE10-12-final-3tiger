import { router } from "expo-router";
import { useEffect, useRef } from "react";
import { Animated, Easing, Image, Text } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

export default function SplashScreen() {
  const opacity = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(10)).current;
  const scale = useRef(new Animated.Value(1)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(opacity, {
        toValue: 1,
        duration: 900,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(translateY, {
        toValue: 0,
        duration: 900,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.loop(
        Animated.sequence([
          Animated.timing(scale, {
            toValue: 1.025,
            duration: 1300,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
          Animated.timing(scale, {
            toValue: 1,
            duration: 1300,
            easing: Easing.inOut(Easing.ease),
            useNativeDriver: true,
          }),
        ]),
      ),
    ]).start();

    const timer = setTimeout(
      () => router.replace("/(tabs)/map" as never),
      1900,
    );
    return () => clearTimeout(timer);
  }, [opacity, scale, translateY]);

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-white dark:bg-[#111411]">
      <Animated.View
        className="items-center justify-center"
        style={{ opacity, transform: [{ translateY }] }}
      >
        <Animated.View style={{ transform: [{ scale }] }}>
          <Image
            source={require("../../assets/logo.jpg")}
            className="h-40 w-40 rounded-[32px] dark:hidden"
            resizeMode="contain"
          />
          <Image
            source={require("../../assets/logo-dark.png")}
            className="hidden h-40 w-40 rounded-[32px] dark:flex"
            resizeMode="contain"
          />
        </Animated.View>
        <Image
          source={require("../../assets/title.png")}
          className="mt-6 h-[54px] w-[205px] dark:hidden"
          resizeMode="contain"
        />
        <Image
          source={require("../../assets/title-dark.png")}
          className="mt-6 hidden h-[54px] w-[205px] dark:flex"
          resizeMode="contain"
        />
        <Text className="mt-2 text-base leading-6 text-slate-600 dark:text-[#AAB5AD]">
          안전하고 즐거운 우리 동네 산책
        </Text>
      </Animated.View>
      <Animated.Text
        className="absolute bottom-5 text-sm font-bold tracking-[0.3px] text-[#BCCBB9]"
        style={{ opacity }}
      >
        v1.0.0
      </Animated.Text>
    </SafeAreaView>
  );
}

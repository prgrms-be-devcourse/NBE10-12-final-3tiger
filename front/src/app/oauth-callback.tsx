import { useEffect } from "react";
import { router, useLocalSearchParams } from "expo-router";
import { ActivityIndicator } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { Text } from "@/components/ui/text";
import { useAuthStore } from "@/stores/auth-store";

export default function OAuthCallbackScreen() {
  const params = useLocalSearchParams<{
    accessToken?: string;
    refreshToken?: string;
    isNewUser?: string;
    error?: string;
  }>();
  const saveTokens = useAuthStore((state) => state.saveTokens);

  useEffect(() => {
    if (params.error) {
      const timer = setTimeout(() => router.replace("/(auth)/login" as never), 2000);
      return () => clearTimeout(timer);
    }
    if (params.accessToken && params.refreshToken) {
      saveTokens({
        accessToken: params.accessToken,
        refreshToken: params.refreshToken,
        isNewUser: params.isNewUser === "true",
      }).then(() => router.replace("/(tabs)/map" as never));
    }
  }, [params.accessToken, params.refreshToken, params.isNewUser, params.error, saveTokens]);

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-background">
      <ActivityIndicator />
      {params.error && (
        <Text className="mt-4 px-6 text-center text-destructive">{params.error}</Text>
      )}
    </SafeAreaView>
  );
}

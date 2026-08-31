import "../global.css";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PortalHost } from "@rn-primitives/portal";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { useColorScheme } from "nativewind";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";

import { useAuthStore } from "@/stores/auth-store";
import { useNotificationStream } from "@/hooks/use-notification-stream";
import { useThemeStore } from "@/stores/theme-store";

function NotificationStreamConnector() {
  useNotificationStream();
  return null;
}

export default function RootLayout() {
  const { setColorScheme } = useColorScheme();
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, staleTime: 30_000 },
          mutations: { retry: 0 },
        },
      }),
  );
  const isInitialized = useAuthStore((state) => state.isInitialized);
  const restoreSession = useAuthStore((state) => state.restoreSession);
  const isDark = useThemeStore((state) => state.isDark);
  const isThemeInitialized = useThemeStore((state) => state.isInitialized);
  const restoreTheme = useThemeStore((state) => state.restoreTheme);

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  useEffect(() => {
    void restoreTheme();
  }, [restoreTheme]);

  useEffect(() => {
    if (isThemeInitialized) setColorScheme(isDark ? "dark" : "light");
  }, [isDark, isThemeInitialized]);

  if (!isInitialized || !isThemeInitialized) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#087A3F" />
      </View>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <StatusBar style={isDark ? "light" : "dark"} />
      <NotificationStreamConnector />
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: isDark ? "#111411" : "#FFFFFF" },
        }}
      />
      <PortalHost />
    </QueryClientProvider>
  );
}

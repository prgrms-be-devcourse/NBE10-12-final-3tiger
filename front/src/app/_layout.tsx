import "../global.css";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PortalHost } from "@rn-primitives/portal";
import { Stack } from "expo-router";
import { useEffect, useState } from "react";
import { ActivityIndicator, View } from "react-native";

import { useAuthStore } from "@/stores/auth-store";
import { useNotificationStream } from "@/hooks/use-notification-stream";

function NotificationStreamConnector() {
  useNotificationStream();
  return null;
}

export default function RootLayout() {
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

  useEffect(() => {
    void restoreSession();
  }, [restoreSession]);

  if (!isInitialized) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#087A3F" />
      </View>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <NotificationStreamConnector />
      <Stack screenOptions={{ headerShown: false }} />
      <PortalHost />
    </QueryClientProvider>
  );
}

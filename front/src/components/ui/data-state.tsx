import { Ionicons } from "@expo/vector-icons";
import { ActivityIndicator, View } from "react-native";

import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";

export function LoadingState({
  label = "불러오는 중이에요",
}: {
  label?: string;
}) {
  return (
    <View className="flex-1 items-center justify-center gap-3 bg-background px-6 py-12">
      <ActivityIndicator color="#087A3F" />
      <Text className="text-sm text-muted-foreground">{label}</Text>
    </View>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <View className="flex-1 items-center justify-center px-6 py-12">
      <View className="size-14 items-center justify-center rounded-full bg-destructive/10">
        <Ionicons name="alert-circle-outline" size={28} color="#DC2626" />
      </View>
      <Text className="mt-4 text-center font-bold text-foreground">
        정보를 불러오지 못했어요
      </Text>
      <Text className="mt-2 text-center text-sm leading-5 text-muted-foreground">
        {message ?? "잠시 후 다시 시도해 주세요."}
      </Text>
      {onRetry && (
        <Button variant="outline" className="mt-5" onPress={onRetry}>
          <Text>다시 시도</Text>
        </Button>
      )}
    </View>
  );
}

export function EmptyState({
  title,
  description,
}: {
  title: string;
  description?: string;
}) {
  return (
    <View className="flex-1 items-center justify-center px-6 py-12">
      <Ionicons name="leaf-outline" size={36} color="#94A09A" />
      <Text className="mt-3 font-bold text-[#191C1D]">{title}</Text>
      {description && (
        <Text className="mt-1 text-center text-sm text-muted-foreground">
          {description}
        </Text>
      )}
    </View>
  );
}

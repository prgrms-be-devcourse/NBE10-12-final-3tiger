import type { ComponentProps } from "react";
import type { ViewStyle } from "react-native";
import type { Ionicons } from "@expo/vector-icons";

type ProfileBorderStyle = Pick<
  ViewStyle,
  "borderColor" | "borderRadius" | "borderWidth"
>;

const BORDER_COLORS: Record<string, string> = {
  GOLD: "#D4A017",
  BLUE: "#3B82F6",
  PINK: "#EC4899",
};

const BADGES: Record<
  string,
  { icon: ComponentProps<typeof Ionicons>["name"]; color: string }
> = {
  STAR: { icon: "star", color: "#D4A017" },
  LEAF: { icon: "leaf", color: "#22C55E" },
  PAW: { icon: "paw", color: "#F97316" },
};

export function profileBorderStyle(
  code?: string | null,
): ProfileBorderStyle | undefined {
  const color = code ? BORDER_COLORS[code] : undefined;
  return color
    ? { borderColor: color, borderRadius: 9999, borderWidth: 3 }
    : undefined;
}

export function postBorderStyle(code?: string | null): ViewStyle | undefined {
  const color = code ? BORDER_COLORS[code] : undefined;
  return color ? { borderColor: `${color}80`, borderWidth: 1 } : undefined;
}

export function badgeAppearance(code?: string | null) {
  return code ? BADGES[code] : undefined;
}

export function cosmeticColor(code: string) {
  return BORDER_COLORS[code] ?? BADGES[code]?.color ?? "#087A3F";
}

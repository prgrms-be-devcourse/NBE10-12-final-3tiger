import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import { create } from "zustand";

const THEME_KEY = "colorTheme";

const themeStorage = {
  async get() {
    if (Platform.OS === "web")
      return globalThis.localStorage?.getItem(THEME_KEY) ?? null;
    return SecureStore.getItemAsync(THEME_KEY);
  },
  async set(value: "light" | "dark") {
    if (Platform.OS === "web")
      globalThis.localStorage?.setItem(THEME_KEY, value);
    else await SecureStore.setItemAsync(THEME_KEY, value);
  },
};

type ThemeState = {
  isDark: boolean;
  isInitialized: boolean;
  restoreTheme: () => Promise<void>;
  setDark: (isDark: boolean) => Promise<void>;
};

export const useThemeStore = create<ThemeState>((set) => ({
  isDark: false,
  isInitialized: false,
  restoreTheme: async () => {
    try {
      const storedTheme = await themeStorage.get();
      set({ isDark: storedTheme === "dark", isInitialized: true });
    } catch {
      set({ isDark: false, isInitialized: true });
    }
  },
  setDark: async (isDark) => {
    set({ isDark });
    await themeStorage.set(isDark ? "dark" : "light");
  },
}));

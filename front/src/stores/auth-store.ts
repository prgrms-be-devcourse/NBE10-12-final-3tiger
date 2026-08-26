import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import { create } from "zustand";

import type { AuthTokens } from "@/types/auth";

const ACCESS_TOKEN_KEY = "accessToken";
const REFRESH_TOKEN_KEY = "refreshToken";

const tokenStorage = {
  async get(key: string) {
    if (Platform.OS === "web")
      return globalThis.localStorage?.getItem(key) ?? null;
    return SecureStore.getItemAsync(key);
  },
  async set(key: string, value: string) {
    if (Platform.OS === "web") globalThis.localStorage?.setItem(key, value);
    else await SecureStore.setItemAsync(key, value);
  },
  async remove(key: string) {
    if (Platform.OS === "web") globalThis.localStorage?.removeItem(key);
    else await SecureStore.deleteItemAsync(key);
  },
};

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isInitialized: boolean;
  saveTokens: (tokens: AuthTokens) => Promise<void>;
  restoreSession: () => Promise<void>;
  clearSession: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  isInitialized: false,
  saveTokens: async ({ accessToken, refreshToken }) => {
    await Promise.all([
      tokenStorage.set(ACCESS_TOKEN_KEY, accessToken),
      tokenStorage.set(REFRESH_TOKEN_KEY, refreshToken),
    ]);
    set({
      accessToken,
      refreshToken,
      isAuthenticated: true,
      isInitialized: true,
    });
  },
  restoreSession: async () => {
    try {
      const [accessToken, refreshToken] = await Promise.all([
        tokenStorage.get(ACCESS_TOKEN_KEY),
        tokenStorage.get(REFRESH_TOKEN_KEY),
      ]);
      set({
        accessToken,
        refreshToken,
        isAuthenticated: Boolean(accessToken),
        isInitialized: true,
      });
    } catch {
      set({
        accessToken: null,
        refreshToken: null,
        isAuthenticated: false,
        isInitialized: true,
      });
    }
  },
  clearSession: async () => {
    await Promise.all([
      tokenStorage.remove(ACCESS_TOKEN_KEY),
      tokenStorage.remove(REFRESH_TOKEN_KEY),
    ]);
    set({
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isInitialized: true,
    });
  },
}));

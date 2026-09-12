import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import { create } from "zustand";

const MUTE_KEY = "voiceGuideMuted";

const muteStorage = {
  async get(): Promise<boolean> {
    try {
      const raw =
        Platform.OS === "web"
          ? (globalThis.localStorage?.getItem(MUTE_KEY) ?? null)
          : await SecureStore.getItemAsync(MUTE_KEY);
      return raw === "true";
    } catch {
      return false;
    }
  },
  async set(value: boolean) {
    const raw = value ? "true" : "false";
    if (Platform.OS === "web") globalThis.localStorage?.setItem(MUTE_KEY, raw);
    else await SecureStore.setItemAsync(MUTE_KEY, raw);
  },
};

export type VoiceGuideLocation = {
  latitude: number;
  longitude: number;
  accuracy: number | null;
  heading: number | null;
  timestamp: number;
};

type VoiceGuideState = {
  muted: boolean;
  muteInitialized: boolean;
  location: VoiceGuideLocation | null;
  restoreMute: () => Promise<void>;
  toggleMute: () => Promise<void>;
  setLocation: (location: VoiceGuideLocation) => void;
  clearLocation: () => void;
};

export const useVoiceGuideStore = create<VoiceGuideState>((set, get) => ({
  muted: false,
  muteInitialized: false,
  location: null,
  restoreMute: async () => {
    const muted = await muteStorage.get();
    set({ muted, muteInitialized: true });
  },
  toggleMute: async () => {
    const next = !get().muted;
    set({ muted: next });
    await muteStorage.set(next);
  },
  setLocation: (location) => set({ location }),
  clearLocation: () => set({ location: null }),
}));

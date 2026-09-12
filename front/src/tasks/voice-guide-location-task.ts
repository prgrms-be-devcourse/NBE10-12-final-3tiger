import * as Location from "expo-location";
import * as TaskManager from "expo-task-manager";

import { useVoiceGuideStore } from "@/stores/voice-guide-store";

export const VOICE_GUIDE_LOCATION_TASK = "VOICE_GUIDE_LOCATION_TASK";

type LocationTaskBody = {
  locations?: Location.LocationObject[];
};

TaskManager.defineTask<LocationTaskBody>(
  VOICE_GUIDE_LOCATION_TASK,
  async ({ data, error }) => {
    if (error) return;
    const locations = data?.locations;
    if (!locations || locations.length === 0) return;
    const latest = locations[locations.length - 1];
    if (!latest) return;
    useVoiceGuideStore.getState().setLocation({
      latitude: latest.coords.latitude,
      longitude: latest.coords.longitude,
      accuracy: latest.coords.accuracy ?? null,
      heading: latest.coords.heading ?? null,
      timestamp: latest.timestamp,
    });
  },
);

export const ensureNoOrphanLocationTask = async () => {
  try {
    const started = await Location.hasStartedLocationUpdatesAsync(
      VOICE_GUIDE_LOCATION_TASK,
    );
    if (started) {
      await Location.stopLocationUpdatesAsync(VOICE_GUIDE_LOCATION_TASK);
    }
  } catch {
    // Task 등록 전이거나 권한 문제인 경우 무시.
  }
};

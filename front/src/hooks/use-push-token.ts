import Constants from "expo-constants";
import * as Device from "expo-device";
import * as Notifications from "expo-notifications";
import { useEffect } from "react";
import { Platform } from "react-native";

import { registerPushToken } from "@/api/push-token-api";
import { useAuthStore } from "@/stores/auth-store";
import { usePushTokenStore } from "@/stores/push-token-store";

const ANDROID_DEFAULT_CHANNEL_ID = "default";
const PUSH_PLATFORM = "android";

async function setupAndroidChannel() {
  if (Platform.OS !== "android") return;

  await Notifications.setNotificationChannelAsync(ANDROID_DEFAULT_CHANNEL_ID, {
    name: "기본 알림",
    importance: Notifications.AndroidImportance.DEFAULT,
    lightColor: "#208AEF",
  });
}

async function resolvePermissionGranted() {
  const existing = await Notifications.getPermissionsAsync();
  if (existing.granted) return true;

  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
}

async function registerForPushNotifications() {
  // OS 레벨 푸시는 실기기에서만 동작한다. 에뮬레이터/시뮬레이터는 스킵.
  if (!Device.isDevice) {
    console.warn("[push-token] 실기기가 아니므로 푸시 토큰 발급을 건너뜁니다.");
    return;
  }

  await setupAndroidChannel();

  const granted = await resolvePermissionGranted();
  if (!granted) {
    console.warn("[push-token] 알림 권한이 거부되어 푸시 토큰을 발급하지 않습니다.");
    return;
  }

  const projectId = Constants.expoConfig?.extra?.eas?.projectId;
  if (!projectId) {
    console.warn(
      "[push-token] expoConfig.extra.eas.projectId 가 없어 토큰을 발급할 수 없습니다.",
    );
    return;
  }

  let expoPushToken: string;
  try {
    const { data: token } = await Notifications.getExpoPushTokenAsync({
      projectId,
    });
    expoPushToken = token;
    console.log("[push-token] Expo push token:", token);
  } catch (error) {
    console.warn("[push-token] 푸시 토큰 발급에 실패했습니다.", error);
    return;
  }

  // 다른 파일(로그아웃 등)에서도 조회할 수 있도록 전역 스토어에 보관한다.
  usePushTokenStore.getState().setExpoPushToken(expoPushToken);

  // 등록 API는 인증이 필요하다. 비로그인 상태면 발급/저장까지만 하고, 로그인되면 재시도한다.
  if (!useAuthStore.getState().isAuthenticated) {
    console.log(
      "[push-token] 인증 안 됨(isAuthenticated=false), 서버 등록 스킵",
    );
    return;
  }

  // 서버 등록. 서버가 token 기준 upsert 하므로 매번 그냥 보낸다.
  // 실패해도 앱이 죽지 않도록 삼켜서 로그만 남긴다.
  console.log("[push-token] 서버 등록 요청 시작", {
    token: expoPushToken,
    platform: PUSH_PLATFORM,
  });
  try {
    await registerPushToken(expoPushToken, PUSH_PLATFORM);
    console.log("[push-token] 서버 등록 요청 성공");
  } catch (error) {
    console.warn("[push-token] 서버 등록 요청 실패", error);
  }
}

/**
 * 앱 진입 시 OS 레벨 푸시(FCM) 토큰을 발급하고 서버에 등록한다.
 * 로그인 상태가 바뀌면(비로그인 → 로그인) 다시 실행해 등록을 재시도한다.
 * 기존 SSE 인앱 알림과는 별개의 채널이다.
 */
export function usePushToken() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  useEffect(() => {
    void registerForPushNotifications();
  }, [isAuthenticated]);
}

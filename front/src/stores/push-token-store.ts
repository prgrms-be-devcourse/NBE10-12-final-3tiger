import { create } from "zustand";

/**
 * 발급된 OS 레벨 푸시(Expo/FCM) 토큰을 앱 전역에서 조회할 수 있게 들고 있는 스토어.
 * 토큰은 앱 진입 시 usePushToken 에서 매번 다시 발급하므로 별도 영속화는 하지 않는다.
 * (로그아웃 시 서버 해제 요청에 사용)
 */
type PushTokenState = {
  expoPushToken: string | null;
  setExpoPushToken: (token: string | null) => void;
};

export const usePushTokenStore = create<PushTokenState>((set) => ({
  expoPushToken: null,
  setExpoPushToken: (expoPushToken) => set({ expoPushToken }),
}));

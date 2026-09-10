import { apiRequest } from "@/api/client";

const PUSH_TOKENS_URL = "/api/v1/push-tokens";

/**
 * OS 레벨 푸시 토큰을 서버에 등록한다. 서버가 token 기준으로 upsert 하므로
 * 프론트는 중복 체크 없이 매 마운트 시 호출하면 된다.
 */
export const registerPushToken = (token: string, platform: string) => {
  const body = { token, platform };
  console.log("[push-token-api] POST 요청", { url: PUSH_TOKENS_URL, body });
  return apiRequest<void>({
    url: PUSH_TOKENS_URL,
    method: "POST",
    data: body,
  });
};

/** 로그아웃 시 현재 기기 토큰을 서버에서 해제한다. 인증 사용자와 무관하게 token 값으로 삭제된다. */
export const unregisterPushToken = (token: string) => {
  const body = { token };
  console.log("[push-token-api] DELETE 요청", { url: PUSH_TOKENS_URL, body });
  return apiRequest<void>({
    url: PUSH_TOKENS_URL,
    method: "DELETE",
    data: body,
  });
};

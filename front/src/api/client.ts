import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";

import { useAuthStore } from "@/stores/auth-store";
import { ApiError, type ApiResponse } from "@/types/api";
import type { AuthTokens } from "@/types/auth";

export const API_BASE_URL = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "");

if (!API_BASE_URL) {
  console.warn(
    "EXPO_PUBLIC_API_URL이 설정되지 않아 API 요청을 보낼 수 없습니다.",
  );
}

/**
 * 로컬 저장소가 localhost URL을 반환하는 경우, 실제 앱이 접속 중인 API 호스트로 보정한다.
 * 물리 기기에서 localhost는 개발 PC가 아니라 기기 자신을 가리킨다.
 */
export function resolveApiHostUrl(url: string) {
  if (!API_BASE_URL) return url;

  try {
    const target = new URL(url);
    const api = new URL(API_BASE_URL);
    if (target.hostname === "localhost" || target.hostname === "127.0.0.1") {
      target.protocol = api.protocol;
      target.host = api.host;
    }
    return target.toString();
  } catch {
    return url;
  }
}

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { "Content-Type": "application/json" },
});

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { "Content-Type": "application/json" },
});

type RetryableRequestConfig = InternalAxiosRequestConfig & {
  _retry?: boolean;
};

type ApiErrorResponse = {
  resultCode?: string;
  code?: string;
  message?: string;
  data?: unknown;
};

let refreshPromise: Promise<AuthTokens> | null = null;

const isAuthRequest = (url?: string) => Boolean(url?.includes("/api/v1/auth/"));

async function requestTokenRefresh() {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) throw new Error("저장된 리프레시 토큰이 없습니다.");

  const response = await refreshClient.post<ApiResponse<AuthTokens>>(
    "/api/v1/auth/refresh",
    { refreshToken },
  );
  const tokens = response.data.data;
  await useAuthStore.getState().saveTokens(tokens);
  return tokens;
}

export function refreshAccessToken() {
  if (!refreshPromise) {
    refreshPromise = requestTokenRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

const getErrorCode = (error: AxiosError<ApiErrorResponse>) =>
  error.response?.data?.resultCode ?? error.response?.data?.code;

const toApiError = (error: AxiosError<ApiErrorResponse>) =>
  new ApiError(
    error.response?.data?.message ??
      (error.request
        ? "서버에 연결할 수 없습니다."
        : "요청 처리 중 오류가 발생했습니다."),
    error.response?.status,
    getErrorCode(error),
  );

apiClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorResponse>) => {
    const originalRequest = error.config as RetryableRequestConfig | undefined;
    const isMissingCurrentUser = getErrorCode(error) === "USER_404";

    if (isMissingCurrentUser) {
      await useAuthStore.getState().clearSession();
      return Promise.reject(toApiError(error));
    }

    const shouldRefresh =
      error.response?.status === 401 &&
      originalRequest !== undefined &&
      !originalRequest._retry &&
      !isAuthRequest(originalRequest.url);

    if (shouldRefresh) {
      originalRequest._retry = true;
      try {
        const tokens = await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${tokens.accessToken}`;
        return apiClient.request(originalRequest);
      } catch {
        await useAuthStore.getState().clearSession();
      }
    } else if (error.response?.status === 401 && originalRequest?._retry) {
      await useAuthStore.getState().clearSession();
    }

    return Promise.reject(toApiError(error));
  },
);

export async function apiRequest<T>(config: AxiosRequestConfig) {
  const response = await apiClient.request<ApiResponse<T>>(config);
  return response.data.data;
}

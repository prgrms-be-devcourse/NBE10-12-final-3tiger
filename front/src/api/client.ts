import axios, { AxiosError, type AxiosRequestConfig } from "axios";

import { useAuthStore } from "@/stores/auth-store";
import { ApiError, type ApiResponse } from "@/types/api";

const baseURL = process.env.EXPO_PUBLIC_API_URL?.replace(/\/$/, "");

if (!baseURL) {
  console.warn(
    "EXPO_PUBLIC_API_URL이 설정되지 않아 API 요청을 보낼 수 없습니다.",
  );
}

export const apiClient = axios.create({
  baseURL,
  timeout: 15_000,
  headers: { "Content-Type": "application/json" },
});

apiClient.interceptors.request.use((config) => {
  const accessToken = useAuthStore.getState().accessToken;
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiResponse<unknown>>) => {
    if (error.response?.status === 401)
      await useAuthStore.getState().clearSession();
    const message =
      error.response?.data?.message ??
      (error.request
        ? "서버에 연결할 수 없습니다."
        : "요청 처리 중 오류가 발생했습니다.");
    return Promise.reject(
      new ApiError(
        message,
        error.response?.status,
        error.response?.data?.resultCode,
      ),
    );
  },
);

export async function apiRequest<T>(config: AxiosRequestConfig) {
  const response = await apiClient.request<ApiResponse<T>>(config);
  return response.data.data;
}

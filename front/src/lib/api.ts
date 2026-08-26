import { Platform } from "react-native";

const defaultBaseUrl = Platform.OS === "android" ? "http://10.0.2.2:8080" : "http://localhost:8080";
export const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? defaultBaseUrl;

export function resolveApiUrl(url: string) {
  if (url.startsWith("/")) return `${API_BASE_URL}${url}`;
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

type ApiEnvelope<T> = { resultCode: string; message: string; data: T };

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(resolveApiUrl(path), init);
  const text = await response.text();
  const payload = text ? (JSON.parse(text) as ApiEnvelope<T>) : null;
  if (!response.ok) throw new Error(payload?.message ?? `요청에 실패했습니다. (${response.status})`);
  return payload?.data as T;
}

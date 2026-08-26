import { apiRequest } from "./client";
import type { AuthTokens, LoginRequest, SignupRequest } from "@/types/auth";

export const login = (data: LoginRequest) =>
  apiRequest<AuthTokens>({ url: "/api/v1/auth/login", method: "POST", data });
export const socialLogin = (
  provider: "kakao" | "google",
  authorizationCode: string,
) =>
  apiRequest<AuthTokens>({
    url: `/api/v1/auth/oauth/${provider}/login`,
    method: "POST",
    data: { authorizationCode },
  });
export const logout = () =>
  apiRequest<null>({ url: "/api/v1/auth/logout", method: "POST" });
export const signup = (data: SignupRequest) =>
  apiRequest<{ userId: number; loginType: string }>({
    url: "/api/v1/users/signup",
    method: "POST",
    data,
  });
export const checkEmail = (email: string) =>
  apiRequest<null>({ url: "/api/v1/users/check-email", params: { email } });

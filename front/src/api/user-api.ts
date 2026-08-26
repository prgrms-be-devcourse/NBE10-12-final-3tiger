import { apiRequest } from "./client";
import type { UserProfile } from "@/types/domain";

export const getMyProfile = () =>
  apiRequest<UserProfile>({ url: "/api/v1/users/me" });
export const updateMyProfile = (
  data: Pick<UserProfile, "nickname" | "primaryPersona" | "personaTags">,
) => apiRequest<null>({ url: "/api/v1/users/me", method: "PATCH", data });
export const uploadProfileImage = (formData: FormData) =>
  apiRequest<{ profileImageUrl: string }>({
    url: "/api/v1/users/me/profile-image",
    method: "POST",
    data: formData,
    headers: { "Content-Type": "multipart/form-data" },
  });
export const withdraw = () =>
  apiRequest<null>({ url: "/api/v1/users/withdraw", method: "PATCH" });

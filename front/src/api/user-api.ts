import { apiRequest } from "./client";
import type { PersonalUserMemo, UserProfile } from "@/types/domain";

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

export const getPersonalUserMemo = (targetUserId: number) =>
  apiRequest<PersonalUserMemo>({
    url: `/api/v1/users/${targetUserId}/personal-memo`,
  });

export const savePersonalUserMemo = (
  targetUserId: number,
  data: Pick<PersonalUserMemo, "tags" | "memo">,
) =>
  apiRequest<PersonalUserMemo>({
    url: `/api/v1/users/${targetUserId}/personal-memo`,
    method: "PUT",
    data,
  });

export const deletePersonalUserMemo = (targetUserId: number) =>
  apiRequest<null>({
    url: `/api/v1/users/${targetUserId}/personal-memo`,
    method: "DELETE",
  });

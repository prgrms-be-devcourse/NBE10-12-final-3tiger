import { apiRequest } from "./client";
import type { PageParams, PageResponse } from "@/types/api";
import type { BlockResult, BlockedUser } from "@/types/domain";

export const blockUser = (userId: number) =>
  apiRequest<BlockResult>({
    url: `/api/v1/users/${userId}/block`,
    method: "PUT",
  });

export const unblockUser = (userId: number) =>
  apiRequest<BlockResult>({
    url: `/api/v1/users/${userId}/block`,
    method: "DELETE",
  });

export const getBlockedUsers = (params: PageParams) =>
  apiRequest<PageResponse<BlockedUser>>({
    url: "/api/v1/users/blocks",
    params,
  });

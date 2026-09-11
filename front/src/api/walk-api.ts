import { apiRequest } from "./client";
import type { PageParams, PageResponse } from "@/types/api";
import type { WalkRecord } from "@/types/domain";

export const getMyWalks = (params: PageParams) =>
  apiRequest<PageResponse<WalkRecord>>({
    url: "/api/v1/users/me/walks",
    params,
  });

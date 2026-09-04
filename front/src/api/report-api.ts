import { apiRequest } from "./client";
import type { ReportReason, ReportResult } from "@/types/domain";

export const reportPost = (postId: number, reason: ReportReason) =>
  apiRequest<ReportResult>({
    url: `/api/v1/posts/${postId}/reports`,
    method: "POST",
    data: { reason },
  });

export const reportComment = (commentId: number, reason: ReportReason) =>
  apiRequest<ReportResult>({
    url: `/api/v1/comments/${commentId}/reports`,
    method: "POST",
    data: { reason },
  });

export const reportUser = (userId: number, reason: ReportReason) =>
  apiRequest<ReportResult>({
    url: `/api/v1/users/${userId}/reports`,
    method: "POST",
    data: { reason },
  });

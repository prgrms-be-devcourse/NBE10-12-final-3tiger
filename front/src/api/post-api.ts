import axios from "axios";
import { apiRequest } from "./client";
import type { PageParams, PageResponse } from "@/types/api";
import type { Post } from "@/types/domain";

export const getPosts = (
  params: PageParams & { sort?: "latest" | "popularity" },
) => apiRequest<PageResponse<Post>>({ url: "/api/v1/posts", params });
export const getMyPosts = (params: PageParams) =>
  apiRequest<PageResponse<Post>>({ url: "/api/v1/posts/me", params });
export const getMyLikedPosts = (params: PageParams) =>
  apiRequest<PageResponse<Post>>({ url: "/api/v1/users/me/likes", params });
export const likePost = (postId: number) =>
  apiRequest<{ isLiked: boolean; likeCount: number }>({
    url: `/api/v1/posts/${postId}/likes`,
    method: "PUT",
  });
export const unlikePost = (postId: number) =>
  apiRequest<{ isLiked: boolean; likeCount: number }>({
    url: `/api/v1/posts/${postId}/likes`,
    method: "DELETE",
  });
export const deletePost = (postId: number) =>
  apiRequest<null>({ url: `/api/v1/posts/${postId}`, method: "DELETE" });
export const getPhotoUploadUrl = (fileName: string, contentType: string) =>
  apiRequest<{ uploadUrl: string; photoUrl: string; expireInSeconds: number }>({
    url: "/api/v1/posts/photo-upload-url",
    method: "POST",
    data: { fileName, contentType },
  });
export const uploadPostPhoto = (
  uploadUrl: string,
  body: Blob,
  contentType: string,
) => axios.put(uploadUrl, body, { headers: { "Content-Type": contentType } });
export const createPost = (data: {
  courseId: number;
  content: string;
  photoUrl?: string;
  walkedAt: string;
}) =>
  apiRequest<{ postId: number }>({
    url: "/api/v1/posts",
    method: "POST",
    data,
  });

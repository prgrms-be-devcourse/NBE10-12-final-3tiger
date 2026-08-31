import { apiRequest } from "./client";
import type { PageParams, PageResponse } from "@/types/api";
import type {
  LikedPostItem,
  Post,
  PostComment,
  PostFeedItem,
} from "@/types/domain";

export const getPosts = (
  params: PageParams & { sort?: "latest" | "popularity" },
) => apiRequest<PageResponse<PostFeedItem>>({ url: "/api/v1/posts", params });
export const getMyPosts = (params: PageParams) =>
  apiRequest<PageResponse<Post>>({ url: "/api/v1/posts/me", params });
export const getMyLikedPosts = (params: PageParams) =>
  apiRequest<PageResponse<LikedPostItem>>({
    url: "/api/v1/users/me/likes",
    params,
  });
export const getPostComments = (postId: number, params: PageParams) =>
  apiRequest<PageResponse<PostComment>>({
    url: `/api/v1/posts/${postId}/comments`,
    params,
  });
export const addPostComment = (postId: number, content: string) =>
  apiRequest<number>({
    url: `/api/v1/posts/${postId}/comments`,
    method: "POST",
    data: { content },
  });
export const toggleCommentUpvote = (commentId: number) =>
  apiRequest<{ upvoted: boolean; upvoteCount: number }>({
    url: `/api/v1/comments/${commentId}/upvote`,
    method: "POST",
  });
export const deleteComment = (commentId: number) =>
  apiRequest<null>({
    url: `/api/v1/comments/${commentId}`,
    method: "DELETE",
  });
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
type PhotoUploadTarget = {
  uploadUrl: string;
  photoUrl: string;
  expireInSeconds: number;
};

type PostPhotoFile = {
  uri: string;
  fileName?: string | null;
  mimeType?: string | null;
  fileSize?: number;
};

const ALLOWED_PHOTO_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);
const MAX_PHOTO_SIZE = 10 * 1024 * 1024;

const getPhotoUploadUrl = (fileName: string, contentType: string) =>
  apiRequest<PhotoUploadTarget>({
    url: "/api/v1/posts/photo-upload-url",
    method: "POST",
    data: { fileName, contentType },
  });

export const uploadPostPhoto = async (file: PostPhotoFile) => {
  const contentType = file.mimeType ?? "image/jpeg";
  if (!ALLOWED_PHOTO_TYPES.has(contentType)) {
    throw new Error("JPEG, PNG, WebP 형식의 사진만 업로드할 수 있습니다.");
  }
  if (file.fileSize && file.fileSize > MAX_PHOTO_SIZE) {
    throw new Error("사진은 10MB 이하만 업로드할 수 있습니다.");
  }

  const fileResponse = await fetch(file.uri);
  if (!fileResponse.ok) {
    throw new Error("선택한 사진을 불러오지 못했습니다.");
  }
  const body = await fileResponse.blob();
  if (body.size === 0) {
    throw new Error("빈 사진 파일은 업로드할 수 없습니다.");
  }
  if (body.size > MAX_PHOTO_SIZE) {
    throw new Error("사진은 10MB 이하만 업로드할 수 있습니다.");
  }

  const fileName = file.fileName ?? `walk-${Date.now()}.jpg`;
  const target = await getPhotoUploadUrl(fileName, contentType);
  const uploadResponse = await fetch(target.uploadUrl, {
    method: "PUT",
    headers: { "Content-Type": contentType },
    body,
  });
  if (!uploadResponse.ok) {
    throw new Error("사진 업로드에 실패했습니다. 다시 시도해 주세요.");
  }

  return target.photoUrl;
};
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

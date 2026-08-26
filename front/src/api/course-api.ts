import type { PageParams, PageResponse } from "@/types/api";
import type { Course, CourseComment, Region } from "@/types/domain";
import { apiRequest } from "./client";

export type CourseSearchParams = PageParams & {
  regionCode?: string;
  lat?: number;
  lng?: number;
  radiusM?: number;
  persona?: string;
  distanceMinM?: number;
  distanceMaxM?: number;
  isLoop?: boolean;
  sort?: "score" | "distance" | "popularity";
};

export const getRegions = () =>
  apiRequest<Region[]>({ url: "/api/v1/regions" });
export const getCourses = (params: CourseSearchParams) =>
  apiRequest<PageResponse<Course>>({ url: "/api/v1/courses", params });
export const getCourseDetail = (courseId: number) =>
  apiRequest<Course>({ url: `/api/v1/courses/${courseId}` });
export const getComments = (courseId: number, params: PageParams) =>
  apiRequest<PageResponse<CourseComment>>({
    url: `/api/v1/courses/${courseId}/comments`,
    params,
  });
export const addComment = (courseId: number, content: string) =>
  apiRequest<{ commentId: number }>({
    url: `/api/v1/courses/${courseId}/comments`,
    method: "POST",
    data: { content },
  });
export const bookmarkCourse = (courseId: number) =>
  apiRequest<{ isBookmarked: boolean }>({
    url: `/api/v1/courses/${courseId}/bookmarks`,
    method: "PUT",
  });
export const unbookmarkCourse = (courseId: number) =>
  apiRequest<{ isBookmarked: boolean }>({
    url: `/api/v1/courses/${courseId}/bookmarks`,
    method: "DELETE",
  });
export const getMyBookmarks = (params: PageParams) =>
  apiRequest<PageResponse<Course>>({
    url: "/api/v1/users/me/bookmarks",
    params,
  });

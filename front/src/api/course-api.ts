import type { PageParams, PageResponse } from "@/types/api";
import type { Course, Region } from "@/types/domain";
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

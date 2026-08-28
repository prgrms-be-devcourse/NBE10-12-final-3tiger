import type { PageParams, PageResponse } from "@/types/api";
import type {
  BookmarkedCourse,
  Course,
  GenerateResponse,
  GeoJsonLineString,
  Region,
} from "@/types/domain";
import { apiRequest } from "./client";

export type GenerateCourseParams = {
  lat: number;
  lng: number;
  distanceM: number;
  persona?: string;
};

export type SaveCourseParams = {
  path: GeoJsonLineString;
  regionCode: string;
};

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
  apiRequest<PageResponse<BookmarkedCourse>>({
    url: "/api/v1/users/me/bookmarks",
    params,
  });
export const generateCourseCandidates = (data: GenerateCourseParams) =>
  apiRequest<GenerateResponse>({
    url: "/api/v1/courses/generate",
    method: "POST",
    data,
  });
export const saveGeneratedCourse = (data: SaveCourseParams) =>
  apiRequest<{ courseId: number }>({
    url: "/api/v1/courses/save",
    method: "POST",
    data,
  });

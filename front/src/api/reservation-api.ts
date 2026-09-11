import { apiRequest } from "./client";
import type { PageParams, PageResponse } from "@/types/api";
import type { WalkReservation } from "@/types/domain";

export const createWalkReservation = (data: {
  courseId: number;
  scheduledAt: string;
}) =>
  apiRequest<WalkReservation>({
    url: "/api/v1/walk-reservations",
    method: "POST",
    data,
  });

export const getMyWalkReservations = (params: PageParams) =>
  apiRequest<PageResponse<WalkReservation>>({
    url: "/api/v1/users/me/walk-reservations",
    params,
  });

export const getMonthlyWalkReservations = (yearMonth: string) =>
  apiRequest<WalkReservation[]>({
    url: "/api/v1/users/me/walk-reservations/monthly",
    params: { yearMonth },
  });

export const cancelWalkReservation = (reservationId: number) =>
  apiRequest<WalkReservation>({
    url: `/api/v1/walk-reservations/${reservationId}`,
    method: "DELETE",
  });

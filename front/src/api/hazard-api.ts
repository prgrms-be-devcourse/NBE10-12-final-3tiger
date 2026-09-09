import { apiRequest } from "@/api/client";
import type {
  Hazard,
  HazardConfirmationResponse,
  HazardCreateRequest,
  HazardCreateResponse,
  HazardResolutionResponse,
} from "@/types/domain";

export const getActiveHazards = (courseId: number) =>
  apiRequest<Hazard[]>({ url: `/api/v1/courses/${courseId}/hazards` });

export const createHazard = (courseId: number, request: HazardCreateRequest) =>
  apiRequest<HazardCreateResponse>({
    url: `/api/v1/courses/${courseId}/hazards`,
    method: "POST",
    data: request,
  });

export const confirmHazard = (hazardId: number) =>
  apiRequest<HazardConfirmationResponse>({
    url: `/api/v1/hazards/${hazardId}/confirmations`,
    method: "POST",
  });

export const deleteMyHazardReport = (hazardId: number) =>
  apiRequest<void>({
    url: `/api/v1/hazards/${hazardId}/reports/me`,
    method: "DELETE",
  });

export const resolveHazard = (hazardId: number) =>
  apiRequest<HazardResolutionResponse>({
    url: `/api/v1/hazards/${hazardId}/resolutions`,
    method: "POST",
  });

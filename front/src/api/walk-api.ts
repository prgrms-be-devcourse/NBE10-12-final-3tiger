import { apiRequest } from "./client";
import type { WeeklyWalkRecord } from "@/types/domain";

export const getWeeklyWalks = (weekStart: string) =>
  apiRequest<WeeklyWalkRecord>({
    url: "/api/v1/users/me/walks/weekly",
    params: { weekStart },
  });

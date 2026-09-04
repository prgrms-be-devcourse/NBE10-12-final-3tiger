import type { GridOverlay } from "@/types/domain";

import { apiRequest } from "./client";

export const getGridOverlays = (bbox: string, hour?: number) =>
  apiRequest<GridOverlay[]>({
    url: "/api/v1/grids",
    params: hour != null ? { bbox, hour } : { bbox },
  });

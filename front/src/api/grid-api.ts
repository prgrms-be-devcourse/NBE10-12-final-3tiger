import type { GridOverlay } from "@/types/domain";

import { apiRequest } from "./client";

export const getGridOverlays = (bbox: string) =>
  apiRequest<GridOverlay[]>({
    url: "/api/v1/grids",
    params: { bbox },
  });

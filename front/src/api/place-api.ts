import { apiRequest } from "@/api/client";

export type PlaceSearchItem = {
  name: string;
  address: string;
  roadAddress: string;
  latitude: number;
  longitude: number;
  category: string;
  placeUrl: string;
  supportedRegion: boolean;
};

export const searchPlaces = (query: string) =>
  apiRequest<PlaceSearchItem[]>({
    url: "/api/v1/places/search",
    params: { query },
  });

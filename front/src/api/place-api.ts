import { apiRequest } from "@/api/client";
import { ApiError } from "@/types/api";

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

export type PlaceSearchResult = {
  originalQuery: string;
  correctedQuery: string | null;
  correctionApplied: boolean;
  items: PlaceSearchItem[];
};

export type ReverseGeocodeResult = {
  latitude: number;
  longitude: number;
  roadAddress: string | null;
  jibunAddress: string | null;
  city: string;
  district: string;
  neighborhood: string;
  supportedRegion: boolean;
};

export const searchPlaces = (query: string) =>
  apiRequest<PlaceSearchResult>({
    url: "/api/v1/places/search",
    params: { query },
  });

export const reverseGeocode = (latitude: number, longitude: number) =>
  apiRequest<ReverseGeocodeResult>({
    url: "/api/v1/locations/reverse-geocode",
    params: { latitude, longitude },
  });

export function getPlaceSearchErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) {
    return "장소를 검색하지 못했어요. 잠시 후 다시 시도해 주세요.";
  }

  if (error.resultCode === "EXTERNAL_503_1") {
    return "장소 검색 서비스가 잠시 원활하지 않아요. 잠시 후 다시 시도해 주세요.";
  }

  if (error.resultCode === "PLACE_429_1" || error.status === 429) {
    return "검색 요청이 많아요. 잠시 후 다시 검색해 주세요.";
  }

  if (error.status === undefined) {
    return "장소를 검색하지 못했어요. 네트워크 연결을 확인해 주세요.";
  }

  return error.message;
}

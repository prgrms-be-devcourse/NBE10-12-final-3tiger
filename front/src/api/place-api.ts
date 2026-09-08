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
  apiRequest<PlaceSearchItem[]>({
    url: "/api/v1/places/search",
    params: { query },
  });

export const reverseGeocode = (latitude: number, longitude: number) =>
  apiRequest<ReverseGeocodeResult>({
    url: "/api/v1/locations/reverse-geocode",
    params: { latitude, longitude },
  });

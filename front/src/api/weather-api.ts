export type PrecipitationType = "rain" | "snow";

export type WeatherAlert = {
  type: PrecipitationType;
  hoursFromNow: number;
};

export type WeatherSnapshot = {
  current: PrecipitationType | null;
  upcoming: WeatherAlert | null;
};

type OpenMeteoResponse = {
  current?: {
    time?: string;
    precipitation?: number;
    snowfall?: number;
  };
  hourly?: {
    time?: string[];
    precipitation?: number[];
    snowfall?: number[];
  };
};

const FORECAST_HOURS = 6;

const buildUrl = (lat: number, lng: number) =>
  "https://api.open-meteo.com/v1/forecast" +
  `?latitude=${lat}&longitude=${lng}` +
  "&current=precipitation,snowfall" +
  "&hourly=precipitation,snowfall" +
  `&forecast_hours=${FORECAST_HOURS}` +
  "&timezone=Asia%2FSeoul";

const pickCurrent = (data: OpenMeteoResponse): PrecipitationType | null => {
  const snow = data.current?.snowfall ?? 0;
  if (snow > 0) return "snow";
  const rain = data.current?.precipitation ?? 0;
  if (rain > 0) return "rain";
  return null;
};

const pickUpcoming = (data: OpenMeteoResponse): WeatherAlert | null => {
  const times = data.hourly?.time ?? [];
  const rain = data.hourly?.precipitation ?? [];
  const snow = data.hourly?.snowfall ?? [];
  const now = Date.now();

  for (let i = 0; i < times.length; i++) {
    const forecastTime = new Date(times[i]).getTime();
    if (Number.isNaN(forecastTime)) continue;
    const hoursFromNow = Math.round((forecastTime - now) / 3_600_000);
    if (hoursFromNow < 0) continue;

    if ((snow[i] ?? 0) > 0) return { type: "snow", hoursFromNow };
    if ((rain[i] ?? 0) > 0) return { type: "rain", hoursFromNow };
  }
  return null;
};

export async function getWeatherSnapshot(
  lat: number,
  lng: number,
): Promise<WeatherSnapshot> {
  const response = await fetch(buildUrl(lat, lng));
  if (!response.ok) {
    throw new Error(`날씨 정보를 가져오지 못했어요 (${response.status})`);
  }
  const data = (await response.json()) as OpenMeteoResponse;
  return {
    current: pickCurrent(data),
    upcoming: pickUpcoming(data),
  };
}

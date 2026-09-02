import type { LatLng } from "react-native-maps";

const EARTH_RADIUS_M = 6_371_000;
const DEGREE_TO_RADIAN = Math.PI / 180;
const MIN_COS_LATITUDE = 0.01;

export type RouteProgress = {
  segmentIndex: number;
  segmentFraction: number;
  snappedCoordinate: LatLng;
  distanceFromRouteM: number;
  traveledDistanceM: number;
  remainingDistanceM: number;
  progress: number;
};

export const toMapCoordinates = (coordinates: [number, number][]): LatLng[] =>
  coordinates
    .map(([longitude, latitude]) => ({ latitude, longitude }))
    .filter(
      ({ latitude, longitude }) =>
        Number.isFinite(latitude) && Number.isFinite(longitude),
    );

export const distanceMeters = (from: LatLng, to: LatLng) => {
  const latitudeDelta = (to.latitude - from.latitude) * DEGREE_TO_RADIAN;
  const longitudeDelta = (to.longitude - from.longitude) * DEGREE_TO_RADIAN;
  const fromLatitude = from.latitude * DEGREE_TO_RADIAN;
  const toLatitude = to.latitude * DEGREE_TO_RADIAN;
  const haversine =
    Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(fromLatitude) *
      Math.cos(toLatitude) *
      Math.sin(longitudeDelta / 2) ** 2;

  return (
    2 *
    EARTH_RADIUS_M *
    Math.atan2(Math.sqrt(haversine), Math.sqrt(1 - haversine))
  );
};

export const buildCumulativeDistances = (route: LatLng[]) => {
  const cumulative = [0];
  for (let index = 1; index < route.length; index += 1) {
    cumulative.push(
      cumulative[index - 1] + distanceMeters(route[index - 1], route[index]),
    );
  }
  return cumulative;
};

const projectToSegment = (point: LatLng, start: LatLng, end: LatLng) => {
  const referenceLatitude =
    ((point.latitude + start.latitude + end.latitude) / 3) * DEGREE_TO_RADIAN;
  const longitudeScale = Math.max(
    Math.abs(Math.cos(referenceLatitude)),
    MIN_COS_LATITUDE,
  );
  const pointX = point.longitude * longitudeScale;
  const pointY = point.latitude;
  const startX = start.longitude * longitudeScale;
  const startY = start.latitude;
  const endX = end.longitude * longitudeScale;
  const endY = end.latitude;
  const segmentX = endX - startX;
  const segmentY = endY - startY;
  const lengthSquared = segmentX ** 2 + segmentY ** 2;
  const fraction =
    lengthSquared === 0
      ? 0
      : Math.max(
          0,
          Math.min(
            1,
            ((pointX - startX) * segmentX + (pointY - startY) * segmentY) /
              lengthSquared,
          ),
        );

  return {
    fraction,
    coordinate: {
      latitude: start.latitude + (end.latitude - start.latitude) * fraction,
      longitude: start.longitude + (end.longitude - start.longitude) * fraction,
    },
  };
};

export const matchRouteProgress = (
  location: LatLng,
  route: LatLng[],
  cumulativeDistances: number[],
  previousSegmentIndex?: number,
): RouteProgress | null => {
  if (route.length < 2 || cumulativeDistances.length !== route.length)
    return null;

  const lastSegmentIndex = route.length - 2;
  const searchStart =
    previousSegmentIndex == null ? 0 : Math.max(0, previousSegmentIndex - 8);
  const searchEnd =
    previousSegmentIndex == null
      ? lastSegmentIndex
      : Math.min(lastSegmentIndex, previousSegmentIndex + 120);

  let best:
    | {
        segmentIndex: number;
        fraction: number;
        coordinate: LatLng;
        distanceM: number;
      }
    | undefined;

  for (let index = searchStart; index <= searchEnd; index += 1) {
    const projection = projectToSegment(
      location,
      route[index],
      route[index + 1],
    );
    const distanceM = distanceMeters(location, projection.coordinate);
    if (!best || distanceM < best.distanceM) {
      best = {
        segmentIndex: index,
        fraction: projection.fraction,
        coordinate: projection.coordinate,
        distanceM,
      };
    }
  }

  if (!best) return null;

  const segmentLength = distanceMeters(
    route[best.segmentIndex],
    route[best.segmentIndex + 1],
  );
  const traveledDistanceM =
    cumulativeDistances[best.segmentIndex] + segmentLength * best.fraction;
  const totalDistanceM = cumulativeDistances.at(-1) ?? 0;

  return {
    segmentIndex: best.segmentIndex,
    segmentFraction: best.fraction,
    snappedCoordinate: best.coordinate,
    distanceFromRouteM: best.distanceM,
    traveledDistanceM,
    remainingDistanceM: Math.max(0, totalDistanceM - traveledDistanceM),
    progress: totalDistanceM > 0 ? traveledDistanceM / totalDistanceM : 0,
  };
};

export const splitRouteAtProgress = (
  route: LatLng[],
  progress: RouteProgress | null,
) => {
  if (!progress) return { completed: [] as LatLng[], remaining: route };

  return {
    completed: [
      ...route.slice(0, progress.segmentIndex + 1),
      progress.snappedCoordinate,
    ],
    remaining: [
      progress.snappedCoordinate,
      ...route.slice(progress.segmentIndex + 1),
    ],
  };
};

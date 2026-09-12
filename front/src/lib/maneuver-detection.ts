import type { LatLng } from "react-native-maps";

import { buildCumulativeDistances, distanceMeters } from "./course-navigation";

export type ManeuverType =
  | "LEFT"
  | "RIGHT"
  | "SLIGHT_LEFT"
  | "SLIGHT_RIGHT"
  | "U_TURN";

export type Maneuver = {
  index: number;
  coord: LatLng;
  distanceFromStartM: number;
  type: ManeuverType;
  angleDelta: number;
};

const SIMPLIFY_TOLERANCE_M = 5;
const TURN_ANGLE_MIN = 55;
const UTURN_ANGLE_MIN = 135;
const MERGE_DISTANCE_M = 15;
const ZIGZAG_WINDOW_M = 30;

const bearingDegrees = (from: LatLng, to: LatLng) => {
  const fromLat = (from.latitude * Math.PI) / 180;
  const toLat = (to.latitude * Math.PI) / 180;
  const lngDelta = ((to.longitude - from.longitude) * Math.PI) / 180;
  const y = Math.sin(lngDelta) * Math.cos(toLat);
  const x =
    Math.cos(fromLat) * Math.sin(toLat) -
    Math.sin(fromLat) * Math.cos(toLat) * Math.cos(lngDelta);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
};

const normalizeSignedDelta = (delta: number) => ((delta + 540) % 360) - 180;

const perpendicularDistanceM = (p: LatLng, a: LatLng, b: LatLng): number => {
  const refLatRad = (((p.latitude + a.latitude + b.latitude) / 3) * Math.PI) / 180;
  const scale = Math.max(Math.cos(refLatRad), 0.01);
  const px = p.longitude * scale;
  const py = p.latitude;
  const ax = a.longitude * scale;
  const ay = a.latitude;
  const bx = b.longitude * scale;
  const by = b.latitude;
  const dx = bx - ax;
  const dy = by - ay;
  const len2 = dx * dx + dy * dy;
  let t = len2 === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
  t = Math.max(0, Math.min(1, t));
  const projLat = a.latitude + t * (b.latitude - a.latitude);
  const projLng = a.longitude + t * (b.longitude - a.longitude);
  return distanceMeters(p, { latitude: projLat, longitude: projLng });
};

// Douglas-Peucker on a route; returns sorted list of original indices to keep.
const simplifyIndices = (route: LatLng[], toleranceM: number): number[] => {
  if (route.length <= 2) return route.map((_, i) => i);
  const keep = new Array<boolean>(route.length).fill(false);
  keep[0] = true;
  keep[route.length - 1] = true;
  const stack: [number, number][] = [[0, route.length - 1]];
  while (stack.length > 0) {
    const range = stack.pop();
    if (!range) break;
    const [start, end] = range;
    let maxDist = 0;
    let maxIdx = -1;
    for (let i = start + 1; i < end; i += 1) {
      const d = perpendicularDistanceM(route[i], route[start], route[end]);
      if (d > maxDist) {
        maxDist = d;
        maxIdx = i;
      }
    }
    if (maxIdx >= 0 && maxDist > toleranceM) {
      keep[maxIdx] = true;
      stack.push([start, maxIdx]);
      stack.push([maxIdx, end]);
    }
  }
  const indices: number[] = [];
  for (let i = 0; i < keep.length; i += 1) if (keep[i]) indices.push(i);
  return indices;
};

const classify = (absDelta: number, delta: number): ManeuverType | null => {
  if (absDelta >= UTURN_ANGLE_MIN) return "U_TURN";
  if (absDelta >= TURN_ANGLE_MIN) return delta > 0 ? "RIGHT" : "LEFT";
  return null;
};

const mergeClose = (list: Maneuver[]): Maneuver[] => {
  const merged: Maneuver[] = [];
  for (const m of list) {
    const last = merged[merged.length - 1];
    if (last && m.distanceFromStartM - last.distanceFromStartM < MERGE_DISTANCE_M) {
      if (Math.abs(m.angleDelta) > Math.abs(last.angleDelta)) {
        merged[merged.length - 1] = m;
      }
      continue;
    }
    merged.push(m);
  }
  return merged;
};

const filterZigZag = (list: Maneuver[]): Maneuver[] => {
  const skip = new Set<number>();
  for (let i = 0; i + 2 < list.length; i += 1) {
    const a = list[i];
    const b = list[i + 1];
    const c = list[i + 2];
    const sA = Math.sign(a.angleDelta);
    const sB = Math.sign(b.angleDelta);
    const sC = Math.sign(c.angleDelta);
    if (
      sA !== 0 &&
      sB !== 0 &&
      sC !== 0 &&
      sA === -sB &&
      sB === -sC &&
      c.distanceFromStartM - a.distanceFromStartM < ZIGZAG_WINDOW_M
    ) {
      skip.add(i);
      skip.add(i + 1);
      skip.add(i + 2);
    }
  }
  return list.filter((_, i) => !skip.has(i));
};

export const detectManeuvers = (route: LatLng[]): Maneuver[] => {
  if (route.length < 3) return [];

  const cumulative = buildCumulativeDistances(route);
  const keepIndices = simplifyIndices(route, SIMPLIFY_TOLERANCE_M);
  if (keepIndices.length < 3) return [];

  const raw: Maneuver[] = [];
  for (let j = 1; j < keepIndices.length - 1; j += 1) {
    const prevIdx = keepIndices[j - 1];
    const midIdx = keepIndices[j];
    const nextIdx = keepIndices[j + 1];
    const inBearing = bearingDegrees(route[prevIdx], route[midIdx]);
    const outBearing = bearingDegrees(route[midIdx], route[nextIdx]);
    const delta = normalizeSignedDelta(outBearing - inBearing);
    const type = classify(Math.abs(delta), delta);
    if (!type) continue;

    raw.push({
      index: midIdx,
      coord: route[midIdx],
      distanceFromStartM: cumulative[midIdx],
      type,
      angleDelta: delta,
    });
  }

  return filterZigZag(mergeClose(raw));
};

export const findNextManeuver = (
  maneuvers: Maneuver[],
  traveledDistanceM: number,
): Maneuver | null => {
  for (const m of maneuvers) {
    if (m.distanceFromStartM > traveledDistanceM) return m;
  }
  return null;
};

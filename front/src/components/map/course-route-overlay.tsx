import { Fragment, useMemo } from "react";
import { View } from "react-native";
import { Marker, Polyline, type LatLng } from "react-native-maps";
import Svg, { Path } from "react-native-svg";

import { distanceMeters } from "@/lib/course-navigation";

type CourseRouteOverlayProps = {
  coordinates: LatLng[];
  color?: string;
  mapHeading?: number;
  zIndex?: number;
};

const lightenColor = (hex: string, amount = 0.58) => {
  const value = hex.replace("#", "");
  const channel = (offset: number) => {
    const original = Number.parseInt(value.slice(offset, offset + 2), 16);
    return Math.round(original + (255 - original) * amount)
      .toString(16)
      .padStart(2, "0");
  };
  return `#${channel(0)}${channel(2)}${channel(4)}`;
};

const bearingDegrees = (from: LatLng, to: LatLng) => {
  const fromLatitude = (from.latitude * Math.PI) / 180;
  const toLatitude = (to.latitude * Math.PI) / 180;
  const longitudeDelta = ((to.longitude - from.longitude) * Math.PI) / 180;
  const y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
  const x =
    Math.cos(fromLatitude) * Math.sin(toLatitude) -
    Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
};

const getCoordinateAtDistance = (
  coordinates: LatLng[],
  targetDistance: number,
) => {
  let traveled = 0;
  for (let index = 0; index < coordinates.length - 1; index += 1) {
    const start = coordinates[index];
    const end = coordinates[index + 1];
    const length = distanceMeters(start, end);
    if (traveled + length >= targetDistance) {
      const ratio = length > 0 ? (targetDistance - traveled) / length : 0;
      return {
        latitude: start.latitude + (end.latitude - start.latitude) * ratio,
        longitude: start.longitude + (end.longitude - start.longitude) * ratio,
      };
    }
    traveled += length;
  }
  return null;
};

const getDirectionPoint = (
  coordinates: LatLng[],
  targetDistance: number,
  totalLength: number,
) => {
  const coordinate = getCoordinateAtDistance(coordinates, targetDistance);
  if (!coordinate) return null;

  const tangentRadius = Math.min(12, Math.max(2, totalLength / 100));
  const before = getCoordinateAtDistance(
    coordinates,
    Math.max(0, targetDistance - tangentRadius),
  );
  const after = getCoordinateAtDistance(
    coordinates,
    Math.min(totalLength, targetDistance + tangentRadius),
  );
  if (!before || !after) return null;

  return {
    coordinate,
    bearing: bearingDegrees(before, after),
  };
};

export function CourseRouteOverlay({
  coordinates,
  color = "#087A3F",
  mapHeading = 0,
  zIndex = 10,
}: CourseRouteOverlayProps) {
  const arrows = useMemo(() => {
    const totalLength = coordinates
      .slice(1)
      .reduce(
        (total, coordinate, index) =>
          total + distanceMeters(coordinates[index], coordinate),
        0,
      );
    if (totalLength <= 0) return [];

    const arrowCount = 12;
    return Array.from({ length: arrowCount }, (_, index) => {
      const point = getDirectionPoint(
        coordinates,
        (totalLength * (index + 1)) / (arrowCount + 1),
        totalLength,
      );
      return point ? { ...point, key: index } : null;
    }).filter((arrow): arrow is NonNullable<typeof arrow> => arrow !== null);
  }, [coordinates]);

  if (coordinates.length < 2) return null;

  return (
    <Fragment>
      <Polyline
        coordinates={coordinates}
        strokeColor={`${color}42`}
        strokeWidth={13}
        lineCap="round"
        lineJoin="round"
        zIndex={zIndex}
      />
      <Polyline
        coordinates={coordinates}
        strokeColor={color}
        strokeWidth={9}
        lineCap="round"
        lineJoin="round"
        zIndex={zIndex + 1}
      />
      <Polyline
        coordinates={coordinates}
        strokeColor={lightenColor(color)}
        strokeWidth={5}
        lineCap="round"
        lineJoin="round"
        zIndex={zIndex + 2}
      />
      {arrows.map((arrow) => (
        <Marker
          key={`route-arrow-${arrow.key}`}
          coordinate={arrow.coordinate}
          anchor={{ x: 0.5, y: 0.5 }}
          centerOffset={{ x: 0, y: 0 }}
          tracksViewChanges
          pointerEvents="none"
          zIndex={zIndex + 3}
        >
          <View
            style={{
              width: 20,
              height: 20,
              alignItems: "center",
              justifyContent: "center",
              transform: [{ rotate: `${arrow.bearing - 90 - mapHeading}deg` }],
            }}
          >
            <Svg width={20} height={20} viewBox="0 0 20 20">
              <Path
                d="M6 4 L14 10 L6 16"
                fill="none"
                stroke="white"
                strokeWidth={3}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </Svg>
          </View>
        </Marker>
      ))}
    </Fragment>
  );
}

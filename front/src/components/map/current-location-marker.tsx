import { useEffect, useRef } from "react";
import { Animated, Platform, View } from "react-native";
import { Marker, type LatLng } from "react-native-maps";
import Svg, { Path } from "react-native-svg";

type CurrentLocationMarkerProps = {
  coordinate: LatLng;
  heading?: number | null;
  mapHeading?: number;
  alignToTop?: boolean;
};

export function CurrentLocationMarker({
  coordinate,
  heading,
  mapHeading = 0,
  alignToTop = false,
}: CurrentLocationMarkerProps) {
  const rotation = Number.isFinite(heading) ? (heading as number) : 0;
  const screenRotation = ((rotation - mapHeading + 540) % 360) - 180;
  const animatedRotation = useRef(new Animated.Value(screenRotation)).current;
  const rotationStyle = animatedRotation.interpolate({
    inputRange: [-360, 360],
    outputRange: ["-360deg", "360deg"],
  });

  useEffect(() => {
    if (Platform.OS !== "ios" || alignToTop) return;

    animatedRotation.stopAnimation();
    animatedRotation.setValue(screenRotation);
  }, [alignToTop, animatedRotation, screenRotation]);

  useEffect(() => {
    if (Platform.OS !== "ios" || !alignToTop) return;

    animatedRotation.stopAnimation();
    Animated.timing(animatedRotation, {
      toValue: 0,
      duration: 500,
      useNativeDriver: true,
    }).start();
  }, [alignToTop, animatedRotation]);

  return (
    <Marker
      coordinate={coordinate}
      anchor={{ x: 0.5, y: 0.5 }}
      flat
      rotation={Platform.OS === "android" ? rotation : 0}
      tracksViewChanges
      zIndex={1000}
    >
      <Animated.View
        className="h-16 w-12 items-center justify-center"
        style={
          Platform.OS === "ios"
            ? { transform: [{ rotate: rotationStyle }] }
            : undefined
        }
      >
        <Svg
          width={24}
          height={13}
          viewBox="0 0 24 13"
          style={{ position: "absolute", top: 9 }}
        >
          <Path
            d="M12 1.5 L18 12 Q12 5.25 6 12 Z"
            fill="#2563EB"
            stroke="white"
            strokeWidth={1.5}
            strokeLinejoin="round"
          />
        </Svg>
        <View className="absolute h-5 w-5 items-center justify-center rounded-full bg-white shadow-sm">
          <View className="h-4 w-4 rounded-full bg-[#2563EB]" />
        </View>
      </Animated.View>
    </Marker>
  );
}

import { useEffect, useMemo, useState } from "react";
import {
  type LayoutChangeEvent,
  StyleSheet,
  View,
} from "react-native";
import Animated, {
  cancelAnimation,
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withRepeat,
  withTiming,
} from "react-native-reanimated";

import type { PrecipitationType } from "@/api/weather-api";

type ParticleSpec = {
  x: number;
  delay: number;
  duration: number;
  size: number;
};

const RAIN_COUNT = 28;
const SNOW_COUNT = 22;

const buildParticles = (
  count: number,
  width: number,
  fastest: number,
  slowest: number,
  minSize: number,
  maxSize: number,
): ParticleSpec[] =>
  Array.from({ length: count }, () => ({
    x: Math.random() * width,
    delay: Math.random() * slowest,
    duration: fastest + Math.random() * (slowest - fastest),
    size: minSize + Math.random() * (maxSize - minSize),
  }));

function Drop({
  spec,
  type,
  height,
}: {
  spec: ParticleSpec;
  type: PrecipitationType;
  height: number;
}) {
  const progress = useSharedValue(0);

  useEffect(() => {
    progress.value = withDelay(
      spec.delay,
      withRepeat(
        withTiming(1, { duration: spec.duration, easing: Easing.linear }),
        -1,
        false,
      ),
    );
    return () => cancelAnimation(progress);
  }, [progress, spec.delay, spec.duration]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: -20 + progress.value * (height + 40) }],
  }));

  if (type === "snow") {
    return (
      <Animated.View
        style={[
          styles.snowFlake,
          { left: spec.x, width: spec.size, height: spec.size },
          animatedStyle,
        ]}
      />
    );
  }

  return (
    <Animated.View
      style={[
        styles.rainDrop,
        { left: spec.x, height: spec.size * 3 },
        animatedStyle,
      ]}
    />
  );
}

export function PrecipitationOverlay({
  type,
}: {
  type: PrecipitationType | null;
}) {
  const [size, setSize] = useState<{ width: number; height: number } | null>(
    null,
  );

  const onLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    setSize((prev) =>
      prev && prev.width === width && prev.height === height
        ? prev
        : { width, height },
    );
  };

  const particles = useMemo(() => {
    if (!type || !size) return [];
    if (type === "rain") {
      return buildParticles(RAIN_COUNT, size.width, 700, 1400, 3, 5);
    }
    return buildParticles(SNOW_COUNT, size.width, 3500, 6500, 4, 8);
  }, [type, size]);

  if (!type) return null;

  return (
    <View
      pointerEvents="none"
      style={StyleSheet.absoluteFill}
      onLayout={onLayout}
      accessibilityElementsHidden
      importantForAccessibility="no-hide-descendants"
    >
      {size &&
        particles.map((spec, index) => (
          <Drop key={index} spec={spec} type={type} height={size.height} />
        ))}
    </View>
  );
}

const styles = StyleSheet.create({
  rainDrop: {
    position: "absolute",
    top: 0,
    width: 2,
    borderRadius: 1,
    backgroundColor: "rgba(120, 170, 220, 0.55)",
  },
  snowFlake: {
    position: "absolute",
    top: 0,
    borderRadius: 999,
    backgroundColor: "rgba(255, 255, 255, 0.85)",
  },
});

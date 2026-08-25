import { useMemo, useRef } from "react";
import { Animated, PanResponder } from "react-native";

export function BottomSheetHandle({
  onDismiss,
  translateY: sharedTranslateY,
  dismissThreshold = 70,
  dismissDistance = 700,
  velocityDismiss = true,
}: {
  onDismiss: () => void;
  translateY?: Animated.Value;
  dismissThreshold?: number;
  dismissDistance?: number;
  velocityDismiss?: boolean;
}) {
  const localTranslateY = useRef(new Animated.Value(0)).current;
  const translateY = sharedTranslateY ?? localTranslateY;
  const responder = useMemo(
    () =>
      PanResponder.create({
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: (_, gesture) =>
          gesture.dy > 4 && Math.abs(gesture.dy) > Math.abs(gesture.dx),
        onPanResponderMove: (_, gesture) =>
          translateY.setValue(Math.max(0, gesture.dy)),
        onPanResponderRelease: (_, gesture) => {
          if (
            gesture.dy >= dismissThreshold ||
            (velocityDismiss && gesture.vy > 0.8)
          ) {
            Animated.timing(translateY, {
              toValue: dismissDistance,
              duration: 180,
              useNativeDriver: true,
            }).start(() => {
              onDismiss();
            });
            return;
          }
          Animated.spring(translateY, {
            toValue: 0,
            useNativeDriver: true,
            speed: 24,
            bounciness: 4,
          }).start();
        },
        onPanResponderTerminate: () =>
          Animated.spring(translateY, {
            toValue: 0,
            useNativeDriver: true,
          }).start(),
      }),
    [dismissDistance, dismissThreshold, onDismiss, translateY, velocityDismiss],
  );

  return (
    <Animated.View
      accessibilityLabel="아래로 밀어 닫기"
      className="h-8 w-full items-center justify-center"
      style={sharedTranslateY ? undefined : { transform: [{ translateY }] }}
      {...responder.panHandlers}
    >
      <Animated.View className="h-[5px] w-[42px] rounded-full bg-slate-300" />
    </Animated.View>
  );
}

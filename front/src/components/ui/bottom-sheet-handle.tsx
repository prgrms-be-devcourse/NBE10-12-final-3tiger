import { useMemo, useRef } from "react";
import { Animated, PanResponder } from "react-native";

export function dismissBottomSheet(
  translateY: Animated.Value,
  dismissDistance: number,
  onDismiss: () => void,
) {
  translateY.stopAnimation();
  Animated.timing(translateY, {
    toValue: dismissDistance,
    duration: 180,
    useNativeDriver: true,
  }).start(onDismiss);
}

export function BottomSheetHandle({
  onDismiss,
  translateY: sharedTranslateY,
  dismissDistance = 700,
  dismissVelocity = 0.8,
}: {
  onDismiss: () => void;
  translateY?: Animated.Value;
  dismissDistance?: number;
  dismissVelocity?: number;
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
          if (gesture.vy >= dismissVelocity) {
            dismissBottomSheet(translateY, dismissDistance, onDismiss);
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
    [dismissDistance, dismissVelocity, onDismiss, translateY],
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

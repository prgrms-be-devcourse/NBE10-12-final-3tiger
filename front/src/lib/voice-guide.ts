import * as Haptics from "expo-haptics";
import * as Speech from "expo-speech";
import { useCallback, useEffect, useRef } from "react";

export type SpeechCue =
  | { kind: "COMPLETE"; text: string }
  | { kind: "OFF_ROUTE_ENTER"; text: string }
  | { kind: "OFF_ROUTE_REPEAT"; text: string }
  | { kind: "OFF_ROUTE_RECOVER"; text: string }
  | { kind: "TURN_AT"; text: string }
  | { kind: "TURN_50M"; text: string }
  | { kind: "TURN_200M"; text: string }
  | { kind: "MILESTONE"; text: string }
  | { kind: "START"; text: string };

const PRIORITY: Record<SpeechCue["kind"], number> = {
  COMPLETE: 1,
  OFF_ROUTE_ENTER: 2,
  OFF_ROUTE_REPEAT: 2,
  OFF_ROUTE_RECOVER: 2,
  TURN_AT: 3,
  TURN_50M: 4,
  TURN_200M: 5,
  MILESTONE: 6,
  START: 7,
};

// TURN_AT 이상 긴급도만 진행 중 발화를 중단시킨다.
const preemptiveThreshold = PRIORITY.TURN_AT;

export function useVoiceGuide({ muted }: { muted: boolean }) {
  const currentCueRef = useRef<SpeechCue | null>(null);
  const mutedRef = useRef(muted);

  useEffect(() => {
    mutedRef.current = muted;
    if (muted) {
      currentCueRef.current = null;
      Speech.stop().catch(() => undefined);
    }
  }, [muted]);

  const speak = useCallback((cue: SpeechCue) => {
    if (mutedRef.current) {
      if (cue.kind === "COMPLETE" || cue.kind === "OFF_ROUTE_ENTER") {
        void Haptics.notificationAsync(
          Haptics.NotificationFeedbackType.Warning,
        ).catch(() => undefined);
      }
      return;
    }

    const current = currentCueRef.current;
    if (current) {
      const newPriority = PRIORITY[cue.kind];
      const currentPriority = PRIORITY[current.kind];
      const isPreemptive = newPriority <= preemptiveThreshold;
      if (!isPreemptive || newPriority >= currentPriority) {
        return;
      }
      Speech.stop().catch(() => undefined);
    }

    currentCueRef.current = cue;
    Speech.speak(cue.text, {
      language: "ko-KR",
      pitch: 1.0,
      rate: 1.0,
      useApplicationAudioSession: true,
      onDone: () => {
        if (currentCueRef.current === cue) currentCueRef.current = null;
      },
      onStopped: () => {
        if (currentCueRef.current === cue) currentCueRef.current = null;
      },
      onError: () => {
        if (currentCueRef.current === cue) currentCueRef.current = null;
      },
    });
  }, []);

  const cancel = useCallback(() => {
    currentCueRef.current = null;
    Speech.stop().catch(() => undefined);
  }, []);

  useEffect(
    () => () => {
      currentCueRef.current = null;
      Speech.stop().catch(() => undefined);
    },
    [],
  );

  return { speak, cancel };
}

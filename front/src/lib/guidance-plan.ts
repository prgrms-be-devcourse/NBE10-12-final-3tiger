import { useCallback, useEffect, useRef } from "react";

import type { RouteProgress } from "./course-navigation";
import {
  findNextManeuver,
  type Maneuver,
  type ManeuverType,
} from "./maneuver-detection";
import type { SpeechCue } from "./voice-guide";

type MilestoneKey = "HALF" | "REMAIN_1KM" | "REMAIN_500M";

type PlannerState = {
  announced: Map<number, Set<0 | 50 | 200>>;
  announcedMilestones: Set<MilestoneKey>;
  announcedStart: boolean;
  announcedComplete: boolean;
  offRoute: {
    active: boolean;
    utteredCount: number;
    lastUtteredAt: number | null;
  };
};

const OFF_ROUTE_REPEAT_INTERVAL_MS = 30_000;
const OFF_ROUTE_MAX_UTTERANCES = 3;

const turnLabel = (type: ManeuverType): string => {
  switch (type) {
    case "LEFT":
      return "좌회전";
    case "RIGHT":
      return "우회전";
    case "SLIGHT_LEFT":
      return "왼쪽 방향";
    case "SLIGHT_RIGHT":
      return "오른쪽 방향";
    case "U_TURN":
      return "유턴";
  }
};

const turnCueText = (
  type: ManeuverType,
  level: 0 | 50 | 200,
): string => {
  const label = turnLabel(type);
  if (level === 200) return `잠시 후 ${label}입니다`;
  if (level === 50) return `50m 앞 ${label}입니다`;
  return type === "U_TURN" ? "유턴하세요" : label;
};

const formatDistance = (meters: number) => {
  if (meters >= 1000) return `${(meters / 1000).toFixed(1)}km`;
  return `${Math.round(meters)}m`;
};

const createInitialState = (): PlannerState => ({
  announced: new Map(),
  announcedMilestones: new Set(),
  announcedStart: false,
  announcedComplete: false,
  offRoute: { active: false, utteredCount: 0, lastUtteredAt: null },
});

type PlannerInput = {
  maneuvers: Maneuver[];
  progress: RouteProgress | null;
  totalDistanceM: number;
  navigationStarted: boolean;
  isOffRoute: boolean;
  isCompleted: boolean;
  isLoop: boolean;
  speak: (cue: SpeechCue) => void;
};

export function useGuidancePlanner({
  maneuvers,
  progress,
  totalDistanceM,
  navigationStarted,
  isOffRoute,
  isCompleted,
  isLoop,
  speak,
}: PlannerInput) {
  const stateRef = useRef<PlannerState>(createInitialState());
  const speakRef = useRef(speak);

  useEffect(() => {
    speakRef.current = speak;
  }, [speak]);

  const reset = useCallback(() => {
    stateRef.current = createInitialState();
  }, []);

  useEffect(() => {
    const state = stateRef.current;
    const emit = speakRef.current;

    if (isCompleted) {
      if (!state.announcedComplete) {
        state.announcedComplete = true;
        emit({ kind: "COMPLETE", text: "산책을 완료했습니다. 수고하셨어요" });
      }
      return;
    }

    if (!navigationStarted) return;

    // 이탈 처리
    const now = Date.now();
    if (isOffRoute) {
      if (!state.offRoute.active) {
        state.offRoute = {
          active: true,
          utteredCount: 1,
          lastUtteredAt: now,
        };
        emit({
          kind: "OFF_ROUTE_ENTER",
          text: "코스에서 벗어났어요. 지도를 확인해 주세요",
        });
        return;
      }
      const { utteredCount, lastUtteredAt } = state.offRoute;
      if (
        utteredCount < OFF_ROUTE_MAX_UTTERANCES &&
        (lastUtteredAt == null ||
          now - lastUtteredAt >= OFF_ROUTE_REPEAT_INTERVAL_MS)
      ) {
        state.offRoute = {
          active: true,
          utteredCount: utteredCount + 1,
          lastUtteredAt: now,
        };
        emit({
          kind: "OFF_ROUTE_REPEAT",
          text: "코스에서 벗어났어요. 지도를 확인해 주세요",
        });
      }
      return;
    }

    if (state.offRoute.active) {
      state.offRoute = { active: false, utteredCount: 0, lastUtteredAt: null };
      emit({ kind: "OFF_ROUTE_RECOVER", text: "경로로 돌아왔습니다" });
      return;
    }

    // 시작 안내
    if (!state.announcedStart) {
      state.announcedStart = true;
      emit({
        kind: "START",
        text: `산책을 시작합니다. 총 ${formatDistance(totalDistanceM)}입니다`,
      });
      return;
    }

    if (!progress) return;

    // 다음 코너
    const next = findNextManeuver(maneuvers, progress.traveledDistanceM);
    if (next) {
      const dToTurn = next.distanceFromStartM - progress.traveledDistanceM;
      const marked = state.announced.get(next.index) ?? new Set<0 | 50 | 200>();
      const tryLevel = (level: 0 | 50 | 200, maxDist: number) => {
        if (dToTurn > maxDist) return false;
        if (marked.has(level)) return false;
        marked.add(level);
        state.announced.set(next.index, marked);
        const kind =
          level === 0 ? "TURN_AT" : level === 50 ? "TURN_50M" : "TURN_200M";
        emit({ kind, text: turnCueText(next.type, level) });
        return true;
      };
      if (tryLevel(0, 5)) return;
      if (tryLevel(50, 60)) return;
      if (tryLevel(200, 220)) return;
    }

    // 반환점 (순환)
    if (
      isLoop &&
      !state.announcedMilestones.has("HALF") &&
      progress.progress >= 0.5
    ) {
      state.announcedMilestones.add("HALF");
      emit({ kind: "MILESTONE", text: "반환점입니다" });
      return;
    }

    // 남은 거리 마일스톤
    if (
      !state.announcedMilestones.has("REMAIN_1KM") &&
      progress.remainingDistanceM <= 1000 &&
      totalDistanceM > 1200
    ) {
      state.announcedMilestones.add("REMAIN_1KM");
      emit({ kind: "MILESTONE", text: "1km 남았어요" });
      return;
    }

    if (
      !state.announcedMilestones.has("REMAIN_500M") &&
      progress.remainingDistanceM <= 500 &&
      totalDistanceM > 700
    ) {
      state.announcedMilestones.add("REMAIN_500M");
      emit({ kind: "MILESTONE", text: "500m 남았어요" });
    }
  }, [
    maneuvers,
    progress,
    totalDistanceM,
    navigationStarted,
    isOffRoute,
    isCompleted,
    isLoop,
  ]);

  return { reset };
}

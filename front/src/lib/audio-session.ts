import { setAudioModeAsync } from "expo-audio";

export const activateGuidanceAudioSession = async () => {
  await setAudioModeAsync({
    playsInSilentMode: true,
    shouldPlayInBackground: true,
    interruptionMode: "duckOthers",
    shouldRouteThroughEarpiece: false,
    allowsRecording: false,
  });
};

export const releaseGuidanceAudioSession = async () => {
  try {
    await setAudioModeAsync({
      playsInSilentMode: false,
      shouldPlayInBackground: false,
      interruptionMode: "mixWithOthers",
      shouldRouteThroughEarpiece: false,
      allowsRecording: false,
    });
  } catch {
    // 화면 unmount 중 세션 복원 실패는 무시. 앱 종료 시 시스템이 정리한다.
  }
};

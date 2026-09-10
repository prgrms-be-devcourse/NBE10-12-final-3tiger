import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ActivityIndicator, Pressable, Text, View } from "react-native";

import { getWeeklyWalks } from "@/api/walk-api";
import { Button } from "@/components/ui/button";

const DAY_LABELS = ["월", "화", "수", "목", "금", "토", "일"];

const toDateKey = (date: Date) => {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const parseDate = (date: string) => new Date(`${date}T12:00:00`);

const startOfWeek = (date: Date) => {
  const result = new Date(date);
  result.setHours(12, 0, 0, 0);
  const day = result.getDay();
  result.setDate(result.getDate() - (day === 0 ? 6 : day - 1));
  return result;
};

const moveWeek = (date: string, amount: number) => {
  const result = parseDate(date);
  result.setDate(result.getDate() + amount * 7);
  return toDateKey(result);
};

const formatRange = (start: string, end: string) => {
  const startDate = parseDate(start);
  const endDate = parseDate(end);
  const sameYear = startDate.getFullYear() === endDate.getFullYear();
  const startLabel = `${sameYear ? "" : `${startDate.getFullYear()}. `}${startDate.getMonth() + 1}. ${startDate.getDate()}.`;
  return `${startLabel} – ${endDate.getFullYear()}. ${endDate.getMonth() + 1}. ${endDate.getDate()}.`;
};

const formatDistance = (meters: number) =>
  meters >= 1_000
    ? `${(meters / 1_000).toFixed(meters % 1_000 === 0 ? 0 : 1)} km`
    : `${meters} m`;

const formatMinutes = (minutes: number) => {
  if (minutes < 60) return `${minutes}분`;
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return remainder ? `${hours}시간 ${remainder}분` : `${hours}시간`;
};

export function WeeklyWalkCard() {
  const currentWeekStart = useMemo(
    () => toDateKey(startOfWeek(new Date())),
    [],
  );
  const [weekStart, setWeekStart] = useState(currentWeekStart);
  const weeklyQuery = useQuery({
    queryKey: ["weekly-walks", weekStart],
    queryFn: () => getWeeklyWalks(weekStart),
  });

  const recordsByDate = useMemo(
    () =>
      new Map(
        (weeklyQuery.data?.dailyRecords ?? []).map((record) => [
          record.date,
          record,
        ]),
      ),
    [weeklyQuery.data?.dailyRecords],
  );
  const dates = useMemo(
    () =>
      Array.from({ length: 7 }, (_, index) => {
        const date = parseDate(weekStart);
        date.setDate(date.getDate() + index);
        return date;
      }),
    [weekStart],
  );
  const fallbackWeekEnd = toDateKey(dates[6]);
  const isCurrentWeek = weekStart === currentWeekStart;

  return (
    <View className="rounded-xl bg-white p-4 dark:bg-[#1B211D]">
      <View className="flex-row items-center justify-between">
        <View className="flex-row items-center gap-2">
          <View className="h-9 w-9 items-center justify-center rounded-full bg-[#E9FBEF] dark:bg-[#24382B]">
            <Ionicons name="calendar" size={19} color="#087A3F" />
          </View>
          <View>
            <Text className="text-[17px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
              주간 산책 기록
            </Text>
            <Text className="mt-0.5 text-[11px] text-slate-500 dark:text-[#AAB5AD]">
              일주일의 산책을 한눈에 확인해요
            </Text>
          </View>
        </View>
        {!isCurrentWeek && (
          <Button
            variant="ghost"
            size="sm"
            className="h-8 rounded-full bg-[#EEF6EB] px-3 dark:bg-[#2A312C]"
            onPress={() => setWeekStart(currentWeekStart)}
          >
            <Text className="text-[11px] font-bold text-[#087A3F] dark:text-[#86EFAC]">
              이번 주
            </Text>
          </Button>
        )}
      </View>

      <View className="mt-4 flex-row items-center justify-between rounded-lg bg-[#F4F7F4] px-1 py-1 dark:bg-[#242B26]">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="이전 주"
          className="h-10 w-10 items-center justify-center rounded-lg active:bg-[#E2EBE0] dark:active:bg-[#343D36]"
          onPress={() => setWeekStart((current) => moveWeek(current, -1))}
        >
          <Ionicons name="chevron-back" size={20} color="#526056" />
        </Pressable>
        <View className="items-center">
          <Text className="text-sm font-extrabold text-[#26372D] dark:text-[#F1F5F2]">
            {formatRange(
              weeklyQuery.data?.weekStart ?? weekStart,
              weeklyQuery.data?.weekEnd ?? fallbackWeekEnd,
            )}
          </Text>
          <Text className="mt-0.5 text-[10px] font-medium text-slate-500 dark:text-[#AAB5AD]">
            {isCurrentWeek ? "이번 주" : "선택한 주"}
          </Text>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="다음 주"
          className="h-10 w-10 items-center justify-center rounded-lg active:bg-[#E2EBE0] dark:active:bg-[#343D36]"
          onPress={() => setWeekStart((current) => moveWeek(current, 1))}
        >
          <Ionicons name="chevron-forward" size={20} color="#526056" />
        </Pressable>
      </View>

      {weeklyQuery.isPending ? (
        <View className="h-[144px] items-center justify-center gap-2">
          <ActivityIndicator color="#087A3F" />
          <Text className="text-xs text-slate-500 dark:text-[#AAB5AD]">
            산책 기록을 불러오는 중이에요
          </Text>
        </View>
      ) : weeklyQuery.isError ? (
        <View className="h-[144px] items-center justify-center gap-2 px-4">
          <Ionicons name="cloud-offline-outline" size={24} color="#64748B" />
          <Text className="text-center text-xs text-slate-600 dark:text-[#D4DDD6]">
            주간 기록을 불러올 수 없습니다
          </Text>
          <Button
            variant="secondary"
            size="sm"
            className="h-8 rounded-full bg-[#E9FBEF] px-4 dark:bg-[#24382B]"
            onPress={() => void weeklyQuery.refetch()}
          >
            <Text className="text-xs font-bold text-[#087A3F] dark:text-[#86EFAC]">
              다시 시도
            </Text>
          </Button>
        </View>
      ) : (
        <>
          <View className="mt-4 flex-row">
            {dates.map((date, index) => {
              const dateKey = toDateKey(date);
              const record = recordsByDate.get(dateKey);
              const walked = (record?.walkCount ?? 0) > 0;
              const isToday = dateKey === toDateKey(new Date());
              return (
                <View key={dateKey} className="flex-1 items-center">
                  <Text
                    className={`text-[11px] font-bold ${index === 6 ? "text-[#EF4444]" : "text-slate-500 dark:text-[#AAB5AD]"}`}
                  >
                    {DAY_LABELS[index]}
                  </Text>
                  <View
                    className={`mt-2 h-9 w-9 items-center justify-center rounded-full ${walked ? "bg-[#22C55E]" : isToday ? "border border-[#22C55E] bg-[#E9FBEF] dark:bg-[#24382B]" : "bg-[#F1F5F1] dark:bg-[#2A312C]"}`}
                  >
                    <Text
                      className={`text-[13px] font-extrabold ${walked ? "text-white" : isToday ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#526056] dark:text-[#AAB5AD]"}`}
                    >
                      {date.getDate()}
                    </Text>
                  </View>
                  <View className="mt-1.5 h-4 items-center">
                    {walked && (
                      <Text className="text-[9px] font-bold text-[#087A3F] dark:text-[#86EFAC]">
                        {record?.walkCount}회
                      </Text>
                    )}
                  </View>
                </View>
              );
            })}
          </View>

          <View className="mt-3 flex-row gap-2 border-t border-slate-100 pt-3 dark:border-[#343D36]">
            {[
              {
                icon: "footsteps-outline" as const,
                label: "산책",
                value: `${weeklyQuery.data.totalWalkCount}회`,
              },
              {
                icon: "map-outline" as const,
                label: "거리",
                value: formatDistance(weeklyQuery.data.totalDistanceMeters),
              },
              {
                icon: "time-outline" as const,
                label: "시간",
                value: formatMinutes(weeklyQuery.data.totalMinutes),
              },
            ].map((item) => (
              <View
                key={item.label}
                className="flex-1 items-center rounded-lg bg-[#F4F7F4] px-1 py-2.5 dark:bg-[#242B26]"
              >
                <Ionicons name={item.icon} size={16} color="#087A3F" />
                <Text className="mt-1 text-[10px] text-slate-500 dark:text-[#AAB5AD]">
                  {item.label}
                </Text>
                <Text
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  className="mt-0.5 text-xs font-extrabold text-[#26372D] dark:text-[#F1F5F2]"
                >
                  {item.value}
                </Text>
              </View>
            ))}
          </View>
        </>
      )}
    </View>
  );
}

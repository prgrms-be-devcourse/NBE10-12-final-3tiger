import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  ScrollView,
  Text,
  useWindowDimensions,
  View,
} from "react-native";

import { getMonthlyWalkReservations } from "@/api/reservation-api";
import {
  MonthlyCalendar,
  type CalendarMarker,
} from "@/components/calendar/monthly-calendar";
import { localDateKey } from "@/lib/korean-holidays";

type ScheduleItem = {
  id: string;
  courseName: string;
  date: Date;
  status: "scheduled" | "completed";
};

const parseDate = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const inMonth = (date: Date, month: Date) =>
  date.getFullYear() === month.getFullYear() &&
  date.getMonth() === month.getMonth();

const formatScheduleDay = (date: Date) =>
  `${date.getMonth() + 1}월 ${date.getDate()}일`;

const formatScheduleTime = (date: Date) =>
  `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;

export function MyScheduleCard() {
  const { width: windowWidth } = useWindowDimensions();
  const now = useMemo(() => new Date(), []);
  const [visibleMonth, setVisibleMonth] = useState(
    new Date(now.getFullYear(), now.getMonth(), 1),
  );
  const yearMonth = `${visibleMonth.getFullYear()}-${String(visibleMonth.getMonth() + 1).padStart(2, "0")}`;
  const reservationsQuery = useQuery({
    queryKey: ["walk-reservations", "monthly", yearMonth],
    queryFn: () => getMonthlyWalkReservations(yearMonth),
  });

  const items = useMemo<ScheduleItem[]>(() => {
    return (reservationsQuery.data ?? [])
      .flatMap((reservation) => {
        const date = parseDate(reservation.scheduledAt);
        return date
          ? [
              {
                id: `reservation-${reservation.reservationId}`,
                courseName: reservation.courseName,
                date,
                status:
                  reservation.status === "COMPLETED" ||
                  date.getTime() < now.getTime()
                    ? ("completed" as const)
                    : ("scheduled" as const),
              },
            ]
          : [];
      })
      .filter((item) => inMonth(item.date, visibleMonth))
      .sort((left, right) => left.date.getTime() - right.date.getTime());
  }, [now, reservationsQuery.data, visibleMonth]);

  const markers = useMemo(() => {
    const result: Record<string, CalendarMarker> = {};
    items.forEach((item) => {
      const key = localDateKey(item.date);
      const previous = result[key];
      result[key] = previous && previous !== item.status ? "both" : item.status;
    });
    return result;
  }, [items]);

  const pending = reservationsQuery.isPending;
  const failed = reservationsQuery.isError;
  const scheduleCardWidth = Math.max(
    112,
    Math.floor((windowWidth - 48) / 2.65),
  );

  return (
    <View className="bg-white p-4 dark:bg-[#1B211D]">
      <Text className="mb-4 text-[17px] font-extrabold text-[#191C1D] dark:text-[#F1F5F2]">
        내 일정
      </Text>
      <MonthlyCalendar
        visibleMonth={visibleMonth}
        markers={markers}
        onChangeMonth={setVisibleMonth}
      />
      <View className="mt-3 flex-row justify-center gap-4">
        <View className="flex-row items-center gap-1.5">
          <View className="h-1.5 w-1.5 rounded-full bg-[#22C55E]" />
          <Text className="text-[10px] font-bold text-[#637064] dark:text-[#AAB5AD]">
            예정
          </Text>
        </View>
        <View className="flex-row items-center gap-1.5">
          <View className="h-1.5 w-1.5 rounded-full bg-[#3B82F6]" />
          <Text className="text-[10px] font-bold text-[#637064] dark:text-[#AAB5AD]">
            완료
          </Text>
        </View>
      </View>

      {pending ? (
        <View className="h-24 items-center justify-center">
          <ActivityIndicator color="#087A3F" />
        </View>
      ) : failed ? (
        <Text className="py-8 text-center text-xs font-bold text-[#B91C1C] dark:text-[#FCA5A5]">
          일정을 불러올 수 없습니다
        </Text>
      ) : items.length === 0 ? (
        <Text className="py-8 text-center text-xs font-bold text-[#718075] dark:text-[#AAB5AD]">
          이 달에는 산책 일정이 없어요
        </Text>
      ) : (
        <View className="mt-4">
          <View className="mb-2 flex-row items-center justify-between">
            <Text className="text-[10px] font-bold text-[#718075] dark:text-[#AAB5AD]">
              옆으로 밀어 일정 보기
            </Text>
            <Text className="text-[10px] font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
              {items.length}개
            </Text>
          </View>
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            decelerationRate="fast"
            snapToInterval={scheduleCardWidth + 8}
            snapToAlignment="start"
            contentContainerClassName="gap-2 pr-10"
          >
            {items.map((item) => (
              <View
                key={item.id}
                className={`h-[148px] justify-between rounded-xl border p-3 ${item.status === "scheduled" ? "border-[#B7EAC4] bg-[#F0FBF3] dark:border-[#31573A] dark:bg-[#203027]" : "border-[#BFDBFE] bg-[#EFF6FF] dark:border-[#315273] dark:bg-[#202C3A]"}`}
                style={{ width: scheduleCardWidth }}
              >
                <View className="flex-row items-center justify-between gap-1">
                  <Text className="shrink text-xs font-extrabold text-[#26372D] dark:text-[#F1F5F2]">
                    {formatScheduleDay(item.date)}
                  </Text>
                  <Text
                    className={`text-[10px] font-extrabold ${item.status === "scheduled" ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#2563EB] dark:text-[#93C5FD]"}`}
                  >
                    {item.status === "scheduled" ? "예정" : "완료"}
                  </Text>
                </View>
                <Text
                  numberOfLines={2}
                  className="text-[13px] font-bold leading-[18px] text-[#26372D] dark:text-[#F1F5F2]"
                >
                  {item.courseName}
                </Text>
                <Text
                  className={`text-xs font-extrabold ${item.status === "scheduled" ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#2563EB] dark:text-[#93C5FD]"}`}
                >
                  {formatScheduleTime(item.date)}
                </Text>
              </View>
            ))}
          </ScrollView>
        </View>
      )}
    </View>
  );
}

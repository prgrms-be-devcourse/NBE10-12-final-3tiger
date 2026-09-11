import { Ionicons } from "@expo/vector-icons";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";

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

const formatScheduleDate = (date: Date) =>
  `${date.getMonth() + 1}월 ${date.getDate()}일 ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;

export function MyScheduleCard() {
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
                  reservation.status === "COMPLETED"
                    ? ("completed" as const)
                    : ("scheduled" as const),
              },
            ]
          : [];
      })
      .filter((item) => inMonth(item.date, visibleMonth))
      .sort((left, right) => left.date.getTime() - right.date.getTime());
  }, [reservationsQuery.data, visibleMonth]);

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
        <View className="mt-4 gap-2">
          {items.map((item) => (
            <View
              key={item.id}
              className="flex-row items-center rounded-xl bg-[#F5F9F5] px-3 py-3 dark:bg-[#242B26]"
            >
              <View
                className={`mr-3 h-8 w-8 items-center justify-center rounded-full ${item.status === "scheduled" ? "bg-[#DDFBE5] dark:bg-[#24382B]" : "bg-[#DBEAFE] dark:bg-[#25354B]"}`}
              >
                <Ionicons
                  name={item.status === "scheduled" ? "time" : "checkmark"}
                  size={16}
                  color={item.status === "scheduled" ? "#087A3F" : "#2563EB"}
                />
              </View>
              <View className="flex-1">
                <Text
                  numberOfLines={1}
                  className="text-sm font-extrabold text-[#26372D] dark:text-[#F1F5F2]"
                >
                  {item.courseName}
                </Text>
                <Text className="mt-0.5 text-[10px] font-medium text-[#718075] dark:text-[#AAB5AD]">
                  {formatScheduleDate(item.date)}
                </Text>
              </View>
              <View
                className={`rounded-full px-2 py-1 ${item.status === "scheduled" ? "bg-[#DDFBE5] dark:bg-[#24382B]" : "bg-[#DBEAFE] dark:bg-[#25354B]"}`}
              >
                <Text
                  className={`text-[10px] font-extrabold ${item.status === "scheduled" ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#2563EB] dark:text-[#93C5FD]"}`}
                >
                  {item.status === "scheduled" ? "예정" : "완료"}
                </Text>
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

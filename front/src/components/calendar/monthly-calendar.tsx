import { Ionicons } from "@expo/vector-icons";
import { useMemo } from "react";
import { Pressable, Text, View } from "react-native";

import { Button } from "@/components/ui/button";
import { isKoreanPublicHoliday, localDateKey } from "@/lib/korean-holidays";

export type CalendarMarker = "scheduled" | "completed" | "both";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const startOfDay = (date: Date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate());
const sameDay = (left: Date, right: Date) =>
  left.getFullYear() === right.getFullYear() &&
  left.getMonth() === right.getMonth() &&
  left.getDate() === right.getDate();

export function MonthlyCalendar({
  visibleMonth,
  selectedDate,
  markers,
  disablePast = false,
  onChangeMonth,
  onSelectDate,
}: {
  visibleMonth: Date;
  selectedDate?: Date;
  markers?: Record<string, CalendarMarker>;
  disablePast?: boolean;
  onChangeMonth: (month: Date) => void;
  onSelectDate?: (date: Date) => void;
}) {
  const today = useMemo(() => startOfDay(new Date()), []);
  const days = useMemo(() => {
    const firstCell = new Date(
      visibleMonth.getFullYear(),
      visibleMonth.getMonth(),
      1 - visibleMonth.getDay(),
    );
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(firstCell);
      date.setDate(firstCell.getDate() + index);
      return date;
    });
  }, [visibleMonth]);
  const moveMonth = (amount: number) =>
    onChangeMonth(
      new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + amount, 1),
    );

  return (
    <View>
      <View className="mb-4 flex-row items-center justify-between">
        <Button variant="ghost" size="icon" onPress={() => moveMonth(-1)}>
          <Ionicons name="chevron-back" size={20} color="#64748B" />
        </Button>
        <Text className="text-base font-black text-[#087A3F] dark:text-[#86EFAC]">
          {visibleMonth.getFullYear()}년 {visibleMonth.getMonth() + 1}월
        </Text>
        <Button variant="ghost" size="icon" onPress={() => moveMonth(1)}>
          <Ionicons name="chevron-forward" size={20} color="#64748B" />
        </Button>
      </View>
      <View className="flex-row">
        {WEEKDAYS.map((weekday, index) => (
          <Text
            key={weekday}
            className={`w-[14.285%] text-center text-[10px] font-bold ${index === 0 ? "text-[#EF4444]" : index === 6 ? "text-[#3B82F6]" : "text-[#718075] dark:text-[#AAB5AD]"}`}
          >
            {weekday}
          </Text>
        ))}
      </View>
      <View className="mt-2 flex-row flex-wrap">
        {days.map((date) => {
          const inMonth = date.getMonth() === visibleMonth.getMonth();
          const selected = selectedDate ? sameDay(date, selectedDate) : false;
          const past =
            disablePast && startOfDay(date).getTime() < today.getTime();
          const holiday = date.getDay() === 0 || isKoreanPublicHoliday(date);
          const marker = markers?.[localDateKey(date)];
          return (
            <Pressable
              key={date.toISOString()}
              accessibilityRole="button"
              accessibilityState={{ selected, disabled: past }}
              disabled={past || !onSelectDate}
              className="h-10 w-[14.285%] items-center justify-center"
              onPress={() => {
                onSelectDate?.(startOfDay(date));
                if (!inMonth)
                  onChangeMonth(
                    new Date(date.getFullYear(), date.getMonth(), 1),
                  );
              }}
            >
              <View
                className={`h-8 w-8 items-center justify-center rounded-full ${selected ? "bg-[#22C55E]" : ""}`}
              >
                <Text
                  className={`text-xs font-bold ${selected ? "text-white" : !inMonth ? (holiday ? "text-[#F3A3A3] dark:text-[#7A5151]" : "text-[#C3CBC4] dark:text-[#58615A]") : holiday ? "text-[#EF4444]" : past ? "text-[#C3CBC4] dark:text-[#58615A]" : "text-[#27342B] dark:text-[#E5ECE6]"}`}
                >
                  {date.getDate()}
                </Text>
                {marker ? (
                  <View className="absolute bottom-0 flex-row gap-0.5">
                    {marker !== "completed" ? (
                      <View className="h-1 w-1 rounded-full bg-[#22C55E]" />
                    ) : null}
                    {marker !== "scheduled" ? (
                      <View className="h-1 w-1 rounded-full bg-[#3B82F6]" />
                    ) : null}
                  </View>
                ) : null}
              </View>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

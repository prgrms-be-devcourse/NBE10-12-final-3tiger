import { Ionicons } from "@expo/vector-icons";
import DateTimePicker, {
  type DateTimePickerEvent,
} from "@react-native-community/datetimepicker";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { router } from "expo-router";
import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Platform,
  Pressable,
  ScrollView,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { getMyBookmarks } from "@/api/course-api";
import {
  cancelWalkReservation,
  createWalkReservation,
  getMyWalkReservations,
} from "@/api/reservation-api";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/ui/data-state";
import {
  MonthlyCalendar,
  type CalendarMarker,
} from "@/components/calendar/monthly-calendar";
import { localDateKey } from "@/lib/korean-holidays";
import { useThemeStore } from "@/stores/theme-store";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const startOfDay = (date: Date) =>
  new Date(date.getFullYear(), date.getMonth(), date.getDate());

const parseScheduledAt = (value: string) => {
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
};

const formatReservationDate = (value: string) => {
  const date = parseScheduledAt(value);
  if (!date) return value;
  return `${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAYS[date.getDay()]}요일 · ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
};

export default function ReservationScreen() {
  const isDark = useThemeStore((state) => state.isDark);
  const queryClient = useQueryClient();
  const today = useMemo(() => startOfDay(new Date()), []);
  const [visibleMonth, setVisibleMonth] = useState(
    new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDate, setSelectedDate] = useState(today);
  const [selectedHour, setSelectedHour] = useState(18);
  const [selectedMinute, setSelectedMinute] = useState(0);
  const [timePickerOpen, setTimePickerOpen] = useState(false);
  const [selectedCourseId, setSelectedCourseId] = useState<number | null>(null);

  const bookmarksQuery = useQuery({
    queryKey: ["bookmarks", "reservation"],
    queryFn: () => getMyBookmarks({ page: 0, size: 100 }),
  });
  const reservationsQuery = useQuery({
    queryKey: ["walk-reservations"],
    queryFn: () => getMyWalkReservations({ page: 0, size: 100 }),
  });
  const reservations = reservationsQuery.data?.content ?? [];
  const bookmarkedCourses = bookmarksQuery.data?.content ?? [];

  const reservationMarkers = useMemo(
    () =>
      reservations.reduce<Record<string, CalendarMarker>>(
        (markers, reservation) => {
          const date = parseScheduledAt(reservation.scheduledAt);
          if (date) markers[localDateKey(date)] = "scheduled";
          return markers;
        },
        {},
      ),
    [reservations],
  );

  const reserveMutation = useMutation({
    mutationFn: createWalkReservation,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["walk-reservations"] });
      Alert.alert("예약 완료", "산책 일정이 예약되었습니다.");
    },
  });
  const cancelMutation = useMutation({
    mutationFn: cancelWalkReservation,
    onSuccess: () =>
      void queryClient.invalidateQueries({ queryKey: ["walk-reservations"] }),
  });

  const scheduledAt = new Date(
    selectedDate.getFullYear(),
    selectedDate.getMonth(),
    selectedDate.getDate(),
    selectedHour,
    selectedMinute,
  );
  const canReserve =
    selectedCourseId !== null &&
    scheduledAt.getTime() > Date.now() &&
    !reserveMutation.isPending;
  const selectedTime = new Date(2000, 0, 1, selectedHour, selectedMinute);
  const handleTimeChange = (event: DateTimePickerEvent, value?: Date) => {
    if (Platform.OS === "android") setTimePickerOpen(false);
    if (event.type === "dismissed" || !value) return;
    setSelectedHour(value.getHours());
    setSelectedMinute(value.getMinutes());
  };

  if (reservationsQuery.isError)
    return (
      <SafeAreaView className="flex-1 bg-[#F3FCF0] dark:bg-[#111411]">
        <ErrorState
          message={reservationsQuery.error.message}
          onRetry={() => void reservationsQuery.refetch()}
        />
      </SafeAreaView>
    );

  return (
    <SafeAreaView
      className="flex-1 bg-[#F3FCF0] dark:bg-[#111411]"
      edges={["top"]}
    >
      <ScrollView contentContainerClassName="px-5 pb-10 pt-4">
        <View className="mb-5 flex-row items-center justify-between">
          <View>
            <Text className="text-2xl font-black text-[#161D17] dark:text-[#F1F5F2]">
              산책 예약
            </Text>
            <Text className="mt-1 text-xs text-[#637064] dark:text-[#AAB5AD]">
              걷고 싶은 코스와 시간을 미리 정해보세요
            </Text>
          </View>
          <View className="h-11 w-11 items-center justify-center rounded-full bg-[#DDFBE5] dark:bg-[#24382B]">
            <Ionicons name="calendar" size={22} color="#087A3F" />
          </View>
        </View>

        <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
          <MonthlyCalendar
            visibleMonth={visibleMonth}
            selectedDate={selectedDate}
            markers={reservationMarkers}
            disablePast
            onChangeMonth={setVisibleMonth}
            onSelectDate={setSelectedDate}
          />
        </View>

        <Text className="mb-2 mt-6 text-sm font-extrabold text-[#161D17] dark:text-[#F1F5F2]">
          예약할 코스
        </Text>
        {bookmarksQuery.isPending ? (
          <View className="h-14 items-center justify-center rounded-xl bg-white dark:bg-[#1B211D]">
            <ActivityIndicator color="#087A3F" />
          </View>
        ) : bookmarksQuery.isError ? (
          <Pressable
            className="rounded-xl bg-white p-4 dark:bg-[#1B211D]"
            onPress={() => void bookmarksQuery.refetch()}
          >
            <Text className="text-center text-xs font-bold text-[#B91C1C]">
              저장한 코스를 불러오지 못했습니다 · 다시 시도
            </Text>
          </Pressable>
        ) : bookmarkedCourses.length === 0 ? (
          <Pressable
            className="h-14 items-center justify-center rounded-xl border border-dashed border-[#9DB09F] bg-white dark:bg-[#1B211D]"
            onPress={() => router.push("/(tabs)/profile/bookmark" as never)}
          >
            <Text className="text-xs font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
              먼저 산책 코스를 저장해주세요
            </Text>
          </Pressable>
        ) : (
          <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerClassName="gap-2"
          >
            {bookmarkedCourses.map((course) => {
              const selected = selectedCourseId === course.courseId;
              return (
                <Pressable
                  key={course.courseId}
                  className={`min-h-12 max-w-52 justify-center rounded-xl border px-4 ${selected ? "border-[#22C55E] bg-[#DDFBE5] dark:bg-[#24382B]" : "border-[#DDE7DE] bg-white dark:border-[#343D36] dark:bg-[#1B211D]"}`}
                  onPress={() => setSelectedCourseId(course.courseId)}
                >
                  <Text
                    numberOfLines={1}
                    className={`text-sm font-extrabold ${selected ? "text-[#087A3F] dark:text-[#86EFAC]" : "text-[#33443A] dark:text-[#F1F5F2]"}`}
                  >
                    {course.name}
                  </Text>
                </Pressable>
              );
            })}
          </ScrollView>
        )}

        <Text className="mb-2 mt-5 text-sm font-extrabold text-[#161D17] dark:text-[#F1F5F2]">
          시작 시간
        </Text>
        <View className="rounded-2xl bg-white p-4 dark:bg-[#1B211D]">
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="시작 시간 선택"
            className={`h-14 flex-row items-center rounded-xl border px-4 ${timePickerOpen ? "border-2 border-[#22C55E]" : "border-[#CBD5E1] dark:border-[#475249]"}`}
            onPress={() => setTimePickerOpen(true)}
          >
            <Ionicons name="time-outline" size={21} color="#087A3F" />
            <View className="ml-3 flex-1">
              <Text className="text-[10px] font-bold text-[#718075] dark:text-[#AAB5AD]">
                선택한 시간
              </Text>
              <Text className="mt-0.5 text-base font-black text-[#26372D] dark:text-[#F1F5F2]">
                {String(selectedHour).padStart(2, "0")}:
                {String(selectedMinute).padStart(2, "0")}
              </Text>
            </View>
            <Text className="text-xs font-extrabold text-[#087A3F] dark:text-[#86EFAC]">
              변경
            </Text>
          </Pressable>
          {timePickerOpen ? (
            <View className="mt-3 overflow-hidden rounded-xl bg-[#F8FAF8] dark:bg-[#242B26]">
              <DateTimePicker
                value={selectedTime}
                mode="time"
                display={Platform.OS === "ios" ? "spinner" : "default"}
                minuteInterval={1}
                is24Hour
                themeVariant={isDark ? "dark" : "light"}
                onChange={handleTimeChange}
              />
              {Platform.OS === "ios" ? (
                <Pressable
                  className="mx-3 mb-3 h-11 items-center justify-center rounded-full bg-[#22C55E]"
                  onPress={() => setTimePickerOpen(false)}
                >
                  <Text className="text-sm font-extrabold text-white">
                    완료
                  </Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}
        </View>

        {reserveMutation.isError ? (
          <Text className="mt-3 text-center text-xs font-bold text-[#B91C1C]">
            {reserveMutation.error.message}
          </Text>
        ) : null}
        <Button
          className="mt-5 h-[52px] rounded-xl bg-[#087A3F]"
          disabled={!canReserve}
          onPress={() =>
            selectedCourseId !== null &&
            reserveMutation.mutate({
              courseId: selectedCourseId,
              scheduledAt: scheduledAt.toISOString(),
            })
          }
        >
          <Ionicons name="calendar-outline" size={18} color="white" />
          <Text className="text-sm font-black text-white">
            {reserveMutation.isPending ? "예약 중..." : "이 일정으로 예약하기"}
          </Text>
        </Button>

        <View className="mb-3 mt-8 flex-row items-center justify-between">
          <Text className="text-lg font-black text-[#161D17] dark:text-[#F1F5F2]">
            다가오는 예약
          </Text>
          <Text className="text-xs font-bold text-[#087A3F] dark:text-[#86EFAC]">
            {reservations.length}개
          </Text>
        </View>
        {reservationsQuery.isPending ? (
          <View className="h-28 items-center justify-center rounded-2xl bg-white dark:bg-[#1B211D]">
            <ActivityIndicator color="#087A3F" />
          </View>
        ) : reservations.length === 0 ? (
          <View className="items-center rounded-2xl bg-white py-10 dark:bg-[#1B211D]">
            <Ionicons name="calendar-outline" size={32} color="#94A09A" />
            <Text className="mt-3 text-sm font-bold text-[#637064] dark:text-[#AAB5AD]">
              예정된 산책이 없어요
            </Text>
          </View>
        ) : (
          <View className="gap-2.5">
            {reservations.map((reservation) => (
              <View
                key={reservation.reservationId}
                className="flex-row items-center rounded-2xl bg-white p-4 dark:bg-[#1B211D]"
              >
                <View className="mr-3 h-11 w-11 items-center justify-center rounded-xl bg-[#DDFBE5] dark:bg-[#24382B]">
                  <Ionicons name="walk" size={21} color="#087A3F" />
                </View>
                <View className="flex-1">
                  <Text
                    numberOfLines={1}
                    className="text-sm font-black text-[#26372D] dark:text-[#F1F5F2]"
                  >
                    {reservation.courseName}
                  </Text>
                  <Text className="mt-1 text-[11px] font-medium text-[#637064] dark:text-[#AAB5AD]">
                    {formatReservationDate(reservation.scheduledAt)}
                  </Text>
                </View>
                <Button
                  variant="ghost"
                  size="sm"
                  disabled={cancelMutation.isPending}
                  onPress={() =>
                    Alert.alert("예약을 취소할까요?", reservation.courseName, [
                      { text: "유지", style: "cancel" },
                      {
                        text: "예약 취소",
                        style: "destructive",
                        onPress: () =>
                          cancelMutation.mutate(reservation.reservationId),
                      },
                    ])
                  }
                >
                  <Text className="text-xs font-extrabold text-[#B91C1C] dark:text-[#FCA5A5]">
                    취소
                  </Text>
                </Button>
              </View>
            ))}
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

const FIXED_HOLIDAYS = new Set([
  "01-01",
  "03-01",
  "05-05",
  "06-06",
  "08-15",
  "10-03",
  "10-09",
  "12-25",
]);

// 음력 명절과 대체공휴일은 연도별로 달라 별도로 관리한다.
const MOVABLE_HOLIDAYS: Record<number, Set<string>> = {
  2025: new Set([
    "01-27",
    "01-28",
    "01-29",
    "01-30",
    "03-03",
    "05-06",
    "10-05",
    "10-06",
    "10-07",
    "10-08",
  ]),
  2026: new Set([
    "02-16",
    "02-17",
    "02-18",
    "03-02",
    "05-24",
    "05-25",
    "08-17",
    "09-24",
    "09-25",
    "09-26",
    "10-05",
  ]),
  2027: new Set([
    "02-06",
    "02-07",
    "02-08",
    "02-09",
    "05-13",
    "06-07",
    "08-16",
    "09-14",
    "09-15",
    "09-16",
    "10-04",
    "10-11",
    "12-27",
  ]),
};

export const localDateKey = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;

export function isKoreanPublicHoliday(date: Date) {
  const monthDay = localDateKey(date).slice(5);
  return (
    FIXED_HOLIDAYS.has(monthDay) ||
    MOVABLE_HOLIDAYS[date.getFullYear()]?.has(monthDay) === true
  );
}

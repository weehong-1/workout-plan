// Groups weigh-ins into Monday-to-Sunday weeks. Day-to-day weight swings with
// water and food, so a week's average is a much fairer read of the trend.

export type DayWeight = { date: string; t: number; value: number };
export type WeekWeight = {
  start: number; // local midnight on the Monday
  days: DayWeight[]; // oldest first
  average: number;
  change: number | null; // vs the previous week that has weigh-ins
};

const WEEK = 7 * 86_400_000;

export function weekStart(t: number): number {
  const d = new Date(t);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d.getTime();
}

export function groupByWeek(points: DayWeight[]): WeekWeight[] {
  const weeks = new Map<number, DayWeight[]>();
  for (const p of points) {
    const key = weekStart(p.t);
    weeks.set(key, [...(weeks.get(key) ?? []), p]);
  }
  let previous: number | null = null;
  return [...weeks].sort((a, b) => a[0] - b[0]).map(([start, days]) => {
    const sorted = [...days].sort((a, b) => a.t - b.t);
    const average = sorted.reduce((sum, d) => sum + d.value, 0) / sorted.length;
    const week = { start, days: sorted, average, change: previous === null ? null : average - previous };
    previous = average;
    return week;
  });
}

/**
 * Average change per week over the last `span` calendar weeks of weekly
 * averages (a least-squares slope), so one heavy week doesn't flip the verdict.
 */
export function weeklyTrend(weeks: WeekWeight[], span = 4): { perWeek: number; weeks: number } | null {
  if (!weeks.length) return null;
  const last = weeks[weeks.length - 1].start;
  const recent = weeks.filter((w) => Math.round((last - w.start) / WEEK) < span);
  if (recent.length < 2) return null;
  const xs = recent.map((w) => Math.round((w.start - recent[0].start) / WEEK));
  const ys = recent.map((w) => w.average);
  const mx = xs.reduce((a, b) => a + b, 0) / xs.length;
  const my = ys.reduce((a, b) => a + b, 0) / ys.length;
  const num = xs.reduce((sum, x, i) => sum + (x - mx) * (ys[i] - my), 0);
  const den = xs.reduce((sum, x) => sum + (x - mx) ** 2, 0);
  return { perWeek: num / den, weeks: xs[xs.length - 1] + 1 };
}

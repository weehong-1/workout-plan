import type { SharedLink, SharedReview } from "@/lib/shared-review";

// Same metric colours as the iOS app (dark variants), so the trainer sees one
// colour per metric everywhere (docs/ios spec 3.1).
const COLOR = {
  steps: "#FF9F0A",
  stepsBar: "#FF9500",
  sleep: "#A48BFF",
  move: "#FF375F",
  weight: "#409CFF",
  weightLine: "#0A84FF",
  food: "#FFC23D",
};

function day(date: string, withYear = false): string {
  // Dates are calendar days (yyyy-mm-dd); format them as UTC so they never shift.
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric", month: "short", ...(withYear ? { year: "numeric" } : {}), timeZone: "UTC",
  }).format(new Date(`${date}T00:00:00Z`));
}

function number(value: number, digits = 0): string {
  return value.toLocaleString("en-GB", { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

function duration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h ? `${h} hr ${m} min` : `${m} min`;
}

function Card({ title, detail, children }: { title: string; detail?: React.ReactNode; children: React.ReactNode }) {
  return (
    <section className="rounded-3xl border border-[var(--line)] bg-[var(--card)] p-4">
      <div className="mb-3 flex items-baseline justify-between gap-3">
        <h2 className="text-lg font-semibold">{title}</h2>
        {detail ? <span className="text-sm text-[var(--muted)]">{detail}</span> : null}
      </div>
      {children}
    </section>
  );
}

function Figure({ label, color, value }: { label: string; color: string; value: string }) {
  return (
    <div>
      <div className="text-sm font-semibold" style={{ color }}>{label}</div>
      <div className="font-display text-2xl font-bold">{value}</div>
    </div>
  );
}

function StepsChart({ daily }: { daily: NonNullable<SharedReview["activity"]>["daily"] }) {
  const max = Math.max(1, ...daily.map((d) => d.steps));
  const width = 320;
  const height = 90;
  const gap = daily.length > 40 ? 0 : 2;
  const bar = Math.max(1, (width - gap * (daily.length - 1)) / Math.max(1, daily.length));
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="mt-4 h-24 w-full" role="img"
      aria-label={`Daily steps from ${day(daily[0].date)} to ${day(daily[daily.length - 1].date)}`}>
      {daily.map((d, i) => {
        const h = Math.max(1, (d.steps / max) * (height - 2));
        return <rect key={d.date} x={i * (bar + gap)} y={height - h} width={bar} height={h} rx={Math.min(3, bar / 2)} fill={COLOR.stepsBar} />;
      })}
    </svg>
  );
}

function WeightChart({ entries }: { entries: NonNullable<SharedReview["weight"]>["entries"] }) {
  if (entries.length < 2) return null;
  const values = entries.map((e) => e.value);
  const low = Math.min(...values);
  const high = Math.max(...values);
  const span = Math.max(high - low, 0.5);
  const width = 320;
  const height = 100;
  const points = entries.map((e, i) => {
    const x = (i / (entries.length - 1)) * (width - 8) + 4;
    const y = height - 6 - ((e.value - low) / span) * (height - 12);
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  return (
    <svg viewBox={`0 0 ${width} ${height}`} className="mt-4 h-28 w-full" role="img"
      aria-label={`Weight from ${number(entries[0].value, 1)} to ${number(entries[entries.length - 1].value, 1)}`}>
      <polyline points={points.join(" ")} fill="none" stroke={COLOR.weightLine} strokeWidth={3} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function Photo({ url, caption }: { url?: string; caption: string }) {
  return (
    <figure className="relative aspect-[3/4] overflow-hidden rounded-2xl bg-[var(--soft)]">
      {url ? (
        // Signed URLs expire after a few minutes; next/image would cache them.
        // eslint-disable-next-line @next/next/no-img-element
        <img src={url} alt={caption} className="h-full w-full object-cover" loading="lazy" referrerPolicy="no-referrer" />
      ) : null}
      <figcaption className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/70 to-transparent p-2 text-xs font-semibold text-white">{caption}</figcaption>
    </figure>
  );
}

export function SharedReviewView({ link }: { link: SharedLink }) {
  const { review, photoUrls, expiresAt } = link;
  const { activity, weight, food } = review;
  const foodByDay = new Map<string, NonNullable<SharedReview["food"]>["photos"]>();
  for (const photo of food?.photos ?? []) {
    foodByDay.set(photo.date, [...(foodByDay.get(photo.date) ?? []), photo]);
  }
  const sign = (value: number) => (value < 0 ? "−" : value > 0 ? "+" : "±");

  return (
    <div className="space-y-3 pb-10">
      <header className="mb-5 px-1">
        <p className="text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">Shared review · read only</p>
        <h1 className="font-display mt-1 text-[34px] font-bold tracking-tight">{review.period.label}</h1>
        <p className="mt-1 text-sm text-[var(--muted)]">
          {day(review.period.start, true)} – {day(review.period.end, true)}
          {review.expiresOn ? ` · link works until ${day(review.expiresOn, true)}` : expiresAt ? " · link expires" : ""}
        </p>
      </header>

      {activity ? (
        <Card title="Activity" detail="Daily averages · Apple Health">
          <div className="grid grid-cols-3 gap-3">
            <Figure label="Steps" color={COLOR.steps} value={activity.averageSteps != null ? number(activity.averageSteps) : "–"} />
            <Figure label="Sleep" color={COLOR.sleep} value={activity.averageSleepMinutes != null ? duration(activity.averageSleepMinutes) : "–"} />
            <Figure label="Move" color={COLOR.move} value={activity.averageMoveKcal != null ? `${number(activity.averageMoveKcal)} kcal` : "–"} />
          </div>
          <p className="mt-3 text-sm text-[var(--muted)]">
            Rings closed on <span className="font-semibold text-[var(--fg)]">{activity.ringsClosedDays} of {review.period.days}</span> days
          </p>
          {activity.daily.length ? <StepsChart daily={activity.daily} /> : null}
        </Card>
      ) : null}

      {weight ? (
        <Card title="Weight" detail={weight.count > 1 ? <span style={{ color: COLOR.weight }} className="font-semibold">{sign(weight.change)}{number(Math.abs(weight.change), 1)} {weight.unit}</span> : undefined}>
          <p className="text-sm text-[var(--muted)]">
            {number(weight.first.value, 1)} → {number(weight.last.value, 1)} {weight.unit} · {weight.count} weigh-in{weight.count === 1 ? "" : "s"}
          </p>
          <WeightChart entries={weight.entries} />
          {weight.firstPhoto || weight.lastPhoto ? (
            <div className="mt-3 grid grid-cols-2 gap-3">
              {weight.firstPhoto ? <Photo url={photoUrls[weight.firstPhoto.path]} caption={`First · ${day(weight.firstPhoto.date)}`} /> : null}
              {weight.lastPhoto ? <Photo url={photoUrls[weight.lastPhoto.path]} caption={`Last · ${day(weight.lastPhoto.date)}`} /> : null}
            </div>
          ) : null}
        </Card>
      ) : null}

      {food ? (
        <Card title="Food" detail={<><span className="font-semibold" style={{ color: COLOR.food }}>{food.daysWithPhotos}</span> of {review.period.days} days with photos</>}>
          {foodByDay.size === 0 ? (
            <p className="text-sm text-[var(--muted)]">No food photos in this period.</p>
          ) : (
            <div className="space-y-4">
              {[...foodByDay.entries()].map(([date, photos]) => (
                <div key={date}>
                  <h3 className="mb-2 text-sm font-semibold text-[var(--muted)]">{day(date, true)}</h3>
                  <div className="grid grid-cols-3 gap-2">
                    {photos.map((photo) => (
                      <Photo key={photo.path} url={photoUrls[photo.path]} caption={photo.label ? `${photo.time} · ${photo.label}` : photo.time} />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      ) : null}

      {!activity && !weight && !food ? (
        <p className="px-1 text-[var(--muted)]">This link doesn&apos;t include any data.</p>
      ) : null}

      <p className="px-1 pt-2 text-xs text-[var(--faint)]">
        Shared from the Workout Plan iOS app. The owner can revoke this link at any time.
      </p>
    </div>
  );
}

export function SharedLinkUnavailable() {
  return (
    <div className="px-1 py-16">
      <p className="text-sm font-semibold uppercase tracking-wider text-[var(--muted)]">Shared review</p>
      <h1 className="mt-2 text-3xl font-bold">This link is no longer available</h1>
      <p className="mt-2 text-[var(--muted)]">It may have expired or been revoked. Ask for a new link.</p>
    </div>
  );
}

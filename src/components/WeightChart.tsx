"use client";

import { useEffect, useRef, useState } from "react";
import type { Unit } from "@/lib/store";

export type WeightPoint = { date: string; t: number; value: number };

const H = 200;
const PAD = { top: 16, right: 14, bottom: 26, left: 40 };

export const shortDate = (t: number) =>
  new Date(t).toLocaleDateString(undefined, { month: "short", day: "numeric" });

// A "nice" y axis: about 4 gridlines on round steps around the data.
function yScale(values: number[]) {
  const min = Math.min(...values);
  const max = Math.max(...values);
  const pad = Math.max((max - min) * 0.15, 0.5);
  const span = max - min + pad * 2;
  const step = [0.5, 1, 2, 2.5, 5, 10, 20, 50].find((s) => span / s <= 4) ?? 100;
  const lo = Math.floor((min - pad) / step) * step;
  const hi = Math.ceil((max + pad) / step) * step;
  const ticks: number[] = [];
  for (let v = lo; v <= hi + 1e-9; v += step) ticks.push(Number(v.toFixed(2)));
  return { lo, hi, ticks, decimals: step < 1 ? 1 : 0 };
}

export function WeightChart({ points, unit }: { points: WeightPoint[]; unit: Unit }) {
  const wrap = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [active, setActive] = useState<number | null>(null);

  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setWidth(e.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useEffect(() => setActive(null), [points]);

  const { lo, hi, ticks, decimals } = yScale(points.map((p) => p.value));
  const t0 = points[0].t;
  const t1 = points[points.length - 1].t;
  const plotW = Math.max(0, width - PAD.left - PAD.right);
  const plotH = H - PAD.top - PAD.bottom;
  const x = (t: number) => PAD.left + (t1 === t0 ? plotW / 2 : ((t - t0) / (t1 - t0)) * plotW);
  const y = (v: number) => PAD.top + (1 - (v - lo) / (hi - lo)) * plotH;
  const path = points.map((p, i) => `${i ? "L" : "M"}${x(p.t).toFixed(1)},${y(p.value).toFixed(1)}`).join("");
  const showAllDots = points.length <= 40;

  const pick = (clientX: number) => {
    const rect = wrap.current!.getBoundingClientRect();
    const px = clientX - rect.left;
    let best = 0;
    points.forEach((p, i) => {
      if (Math.abs(x(p.t) - px) < Math.abs(x(points[best].t) - px)) best = i;
    });
    setActive(best);
  };

  const a = active !== null ? points[active] : null;

  return (
    <div ref={wrap} className="relative select-none" style={{ height: H }}>
      {width > 0 && (
        <svg
          width={width}
          height={H}
          role="img"
          aria-label={`Body weight chart, ${points.length} entries`}
          className="touch-pan-y"
          onPointerDown={(e) => pick(e.clientX)}
          onPointerMove={(e) => pick(e.clientX)}
          onPointerLeave={(e) => e.pointerType === "mouse" && setActive(null)}
        >
          {ticks.map((v) => (
            <g key={v}>
              <line x1={PAD.left} x2={width - PAD.right} y1={y(v)} y2={y(v)} stroke="var(--line)" strokeWidth={1} />
              <text x={PAD.left - 8} y={y(v)} dy="0.32em" textAnchor="end" fontSize={11} fill="var(--muted)" className="tabular-nums">
                {v.toFixed(decimals)}
              </text>
            </g>
          ))}
          <text x={PAD.left} y={H - 6} fontSize={11} fill="var(--muted)">
            {shortDate(t0)}
          </text>
          {t1 !== t0 && (
            <text x={width - PAD.right} y={H - 6} textAnchor="end" fontSize={11} fill="var(--muted)">
              {shortDate(t1)}
            </text>
          )}

          {a && (
            <line x1={x(a.t)} x2={x(a.t)} y1={PAD.top} y2={PAD.top + plotH} stroke="var(--muted)" strokeWidth={1} strokeDasharray="3 3" />
          )}
          <path d={path} fill="none" stroke="var(--accent)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
          {points.map((p, i) =>
            showAllDots || i === points.length - 1 || i === active ? (
              <circle
                key={p.date}
                cx={x(p.t)}
                cy={y(p.value)}
                r={i === active ? 6 : 4}
                fill="var(--accent)"
                stroke="var(--card)"
                strokeWidth={2}
              />
            ) : null,
          )}
        </svg>
      )}
      {a && (
        <div
          className={`pointer-events-none absolute -translate-x-1/2 rounded-lg border border-[var(--line)] bg-[var(--card)] px-2.5 py-1.5 text-center shadow-md ${
            y(a.value) < 70 ? "translate-y-3" : "-translate-y-[calc(100%+12px)]"
          }`}
          style={{ left: Math.min(Math.max(x(a.t), 56), width - 56), top: y(a.value) }}
        >
          <p className="text-[11px] text-[var(--muted)]">{shortDate(a.t)}</p>
          <p className="text-sm font-semibold tabular-nums">
            {a.value.toFixed(1)} {unit}
          </p>
        </div>
      )}
    </div>
  );
}

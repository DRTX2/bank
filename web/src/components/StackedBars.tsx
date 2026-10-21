import { useState } from 'react';

import { money } from '../lib/format';

export interface StackedDatum {
  label: string;
  a: number;
  b: number;
}

const W = 720;
const H = 240;
const PAD = { top: 12, right: 8, bottom: 24, left: 64 };
const GAP = 2;

/** Bar top with 4px rounded corners; the baseline end stays square. */
function topRounded(x: number, y: number, w: number, h: number, r: number): string {
  const rr = Math.min(r, w / 2, h);
  return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`;
}

/**
 * Two-series stacked bars (e.g. principal + interest per installment). Hover any column for exact values;
 * the full table is always rendered next to it by the caller.
 */
export function StackedBars({
  data,
  names,
  currency,
  title,
}: {
  data: StackedDatum[];
  names: [string, string];
  currency: string;
  title: string;
}) {
  const [hover, setHover] = useState<number | null>(null);
  const max = Math.max(...data.map((d) => d.a + d.b), 1);
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const step = plotW / data.length;
  const barW = Math.max(step - GAP, 1);
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((t) => t * max);
  const labelEvery = Math.ceil(data.length / 12);
  const hovered = hover === null ? null : data[hover];

  return (
    <figure className="relative">
      <figcaption className="mb-2 flex flex-wrap items-center justify-between gap-3 text-sm">
        <span className="font-medium">{title}</span>
        <span className="flex gap-4 text-ink-2">
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm bg-chart-1" aria-hidden />
            {names[0]}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2.5 rounded-sm bg-chart-2" aria-hidden />
            {names[1]}
          </span>
        </span>
      </figcaption>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        className="w-full"
        role="img"
        aria-label={`${title}: ${names[0]} y ${names[1]}`}
      >
        {ticks.map((t) => (
          <g key={t}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--line)" strokeWidth={1} />
            <text
              x={PAD.left - 8}
              y={y(t)}
              textAnchor="end"
              dominantBaseline="middle"
              fontSize={11}
              fill="var(--ink-2)"
              className="num"
            >
              {Math.round(t).toLocaleString('es-EC')}
            </text>
          </g>
        ))}
        {data.map((d, i) => {
          const x = PAD.left + i * step + GAP / 2;
          const hA = (d.a / max) * plotH;
          const hB = (d.b / max) * plotH;
          const dim = hover !== null && hover !== i;
          return (
            <g key={d.label} opacity={dim ? 0.45 : 1}>
              <rect
                x={x}
                y={y(d.a)}
                width={barW}
                height={Math.max(hA - (hB > 0 ? GAP / 2 : 0), 0)}
                fill="var(--chart-1)"
              />
              {hB > 0 && (
                <path
                  d={topRounded(x, y(d.a + d.b), barW, Math.max(hB - GAP / 2, 0.5), 4)}
                  fill="var(--chart-2)"
                />
              )}
              {i % labelEvery === 0 && (
                <text
                  x={x + barW / 2}
                  y={H - 6}
                  textAnchor="middle"
                  fontSize={11}
                  fill="var(--ink-2)"
                  className="num"
                >
                  {d.label}
                </text>
              )}
              <rect
                x={PAD.left + i * step}
                y={PAD.top}
                width={step}
                height={plotH}
                fill="transparent"
                onMouseEnter={() => {
                  setHover(i);
                }}
                onMouseLeave={() => {
                  setHover(null);
                }}
              />
            </g>
          );
        })}
      </svg>
      {hovered && hover !== null && (
        <div
          className="pointer-events-none absolute top-8 z-10 rounded-md border border-line bg-surface px-3 py-2 text-xs shadow-md"
          style={{
            left: `min(calc(${((PAD.left + (hover + 0.5) * step) / W) * 100}% + 8px), calc(100% - 11rem))`,
          }}
        >
          <p className="font-medium">Periodo {hovered.label}</p>
          <p className="num">
            {names[0]}: {money(hovered.a, currency)}
          </p>
          <p className="num">
            {names[1]}: {money(hovered.b, currency)}
          </p>
        </div>
      )}
    </figure>
  );
}

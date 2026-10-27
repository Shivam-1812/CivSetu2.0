import { useId, useState } from 'react';

/**
 * Small dependency-free charts. Series colours are the first categorical slots of a validated
 * palette (blue, orange: colour-blind separation checked); text always uses text colours, and
 * every chart can be read as a table.
 */
export const SERIES_COLORS = ['#2a78d6', '#eb6834'] as const;

export interface Series {
  name: string;
  values: number[];
}

/** Lines over time with a hover crosshair, legend and direct end labels. */
export function LineChart({ labels, series, height = 220 }: { labels: string[]; series: Series[]; height?: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const [asTable, setAsTable] = useState(false);
  const titleId = useId();
  const width = 640;
  const pad = { top: 12, right: 96, bottom: 28, left: 36 };
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  const niceMax = Math.ceil(max / 5) * 5 || 5;
  const x = (i: number) => pad.left + (labels.length <= 1 ? 0 : (i / (labels.length - 1)) * (width - pad.left - pad.right));
  const y = (v: number) => pad.top + (1 - v / niceMax) * (height - pad.top - pad.bottom);
  const ticks = [0, niceMax / 2, niceMax];

  const onMove = (event: React.MouseEvent<SVGRectElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    const ratio = (event.clientX - box.left) / box.width;
    setHover(Math.max(0, Math.min(labels.length - 1, Math.round(ratio * (labels.length - 1)))));
  };

  return (
    <figure className="space-y-2" aria-labelledby={titleId}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <ul className="flex gap-4 text-xs text-slate-700" id={titleId}>
          {series.map((s, i) => (
            <li key={s.name} className="flex items-center gap-1.5">
              <span className="h-0.5 w-4 rounded" style={{ background: SERIES_COLORS[i] }} />
              {s.name}
            </li>
          ))}
        </ul>
        <button type="button" className="text-xs text-slate-500 underline" onClick={() => setAsTable((v) => !v)}>
          {asTable ? 'Show chart' : 'Show as table'}
        </button>
      </div>
      {asTable ? (
        <DataTable headers={['Day', ...series.map((s) => s.name)]} rows={labels.map((label, i) => [label, ...series.map((s) => s.values[i])])} />
      ) : (
        <div className="relative">
          <svg viewBox={`0 0 ${width} ${height}`} className="h-auto w-full" role="img" aria-label={series.map((s) => s.name).join(' and ') + ' per day'}>
            {ticks.map((tick) => (
              <g key={tick}>
                <line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} stroke="#e2e8f0" strokeWidth={1} />
                <text x={pad.left - 6} y={y(tick)} dy="0.32em" textAnchor="end" className="fill-slate-500 text-[10px]">
                  {tick}
                </text>
              </g>
            ))}
            {labels.map((label, i) =>
              i === 0 || i === labels.length - 1 || i === Math.floor(labels.length / 2) ? (
                <text key={label} x={x(i)} y={height - 8} textAnchor="middle" className="fill-slate-500 text-[10px]">
                  {label}
                </text>
              ) : null,
            )}
            {series.map((s, si) => (
              <g key={s.name}>
                <polyline
                  fill="none"
                  stroke={SERIES_COLORS[si]}
                  strokeWidth={2}
                  strokeLinejoin="round"
                  strokeLinecap="round"
                  points={s.values.map((v, i) => `${x(i)},${y(v)}`).join(' ')}
                />
                {/* Direct label at the line's end, in text colour next to a coloured mark */}
                <circle cx={x(s.values.length - 1)} cy={y(s.values.at(-1) ?? 0)} r={4} fill={SERIES_COLORS[si]} stroke="#fff" strokeWidth={2} />
                <text x={x(s.values.length - 1) + 8} y={y(s.values.at(-1) ?? 0)} dy="0.32em" className="fill-slate-700 text-[11px]">
                  {s.name}: {s.values.at(-1) ?? 0}
                </text>
              </g>
            ))}
            {hover !== null && (
              <g>
                <line x1={x(hover)} x2={x(hover)} y1={pad.top} y2={height - pad.bottom} stroke="#94a3b8" strokeDasharray="3 3" />
                {series.map((s, si) => (
                  <circle key={s.name} cx={x(hover)} cy={y(s.values[hover])} r={4.5} fill={SERIES_COLORS[si]} stroke="#fff" strokeWidth={2} />
                ))}
              </g>
            )}
            <rect
              x={pad.left}
              y={pad.top}
              width={width - pad.left - pad.right}
              height={height - pad.top - pad.bottom}
              fill="transparent"
              onMouseMove={onMove}
              onMouseLeave={() => setHover(null)}
            />
          </svg>
          {hover !== null && (
            <div
              className="pointer-events-none absolute top-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs shadow"
              style={{ left: `${(x(hover) / width) * 100}%`, transform: hover > labels.length / 2 ? 'translateX(-110%)' : 'translateX(10%)' }}
            >
              <p className="font-medium text-slate-900">{labels[hover]}</p>
              {series.map((s, si) => (
                <p key={s.name} className="flex items-center gap-1.5 text-slate-700">
                  <span className="h-2 w-2 rounded-full" style={{ background: SERIES_COLORS[si] }} />
                  {s.name}: {s.values[hover]}
                </p>
              ))}
            </div>
          )}
        </div>
      )}
    </figure>
  );
}

export interface BarItem {
  label: string;
  value: number;
}

/** Horizontal bars for one measure; values are printed, so nothing depends on colour. */
export function BarList({ items, unit = '' }: { items: BarItem[]; unit?: string }) {
  const max = Math.max(1, ...items.map((item) => item.value));
  if (items.length === 0) return <p className="text-sm text-slate-500">No data for this period.</p>;
  return (
    <ul className="space-y-2">
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[minmax(0,10rem)_1fr_auto] items-center gap-3 text-sm" title={`${item.label}: ${item.value}${unit}`}>
          <span className="truncate text-slate-700">{item.label}</span>
          <span className="h-3 rounded-r bg-slate-100">
            <span
              className="block h-3 rounded-r"
              style={{ width: `${Math.max(2, (item.value / max) * 100)}%`, background: SERIES_COLORS[0] }}
            />
          </span>
          <span className="w-10 text-right font-medium text-slate-900 tabular-nums">
            {item.value}
            {unit}
          </span>
        </li>
      ))}
    </ul>
  );
}

export function DataTable({ headers, rows }: { headers: string[]; rows: (string | number)[][] }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="border-b border-slate-200 text-xs text-slate-500">
          <tr>
            {headers.map((header) => (
              <th key={header} scope="col" className="py-2 pr-4 font-medium">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {rows.map((row, i) => (
            <tr key={i}>
              {row.map((cell, j) => (
                <td key={j} className="py-2 pr-4 tabular-nums">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** A single headline number. */
export function StatTile({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-medium text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-slate-900 tabular-nums">{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

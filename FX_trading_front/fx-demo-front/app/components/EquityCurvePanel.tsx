"use client";

import { useMemo } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  type TooltipContentProps,
  XAxis,
  YAxis,
} from "recharts";

import type { EquitySnapshot } from "../../lib/marketRateTicks";

export type EquityHistoryRange = "5m" | "30m" | "1h" | "all";

type EquityCurvePanelProps = {
  error: string | null;
  history: EquitySnapshot[];
  loading: boolean;
  range: EquityHistoryRange;
  onRangeChange: (range: EquityHistoryRange) => void;
  onRetry: () => void;
};

type EquityChartPoint = {
  time: string;
  recordedAt: string;
  balance: number;
  equity: number;
  usedMargin: number | null;
  marginRatio: number | null;
};

const RANGE_LABELS: Record<EquityHistoryRange, string> = {
  "5m": "5m",
  "30m": "30m",
  "1h": "1h",
  all: "All",
};

export function EquityCurvePanel({
  error,
  history,
  loading,
  range,
  onRangeChange,
  onRetry,
}: EquityCurvePanelProps) {
  const chartData = useMemo<EquityChartPoint[]>(
    () =>
      history.map((snapshot) => ({
        time: formatTime(snapshot.recordedAt),
        recordedAt: snapshot.recordedAt,
        balance: snapshot.balance,
        equity: snapshot.equity,
        usedMargin: snapshot.usedMargin,
        marginRatio: snapshot.marginRatio,
      })),
    [history],
  );
  const latest = history.at(-1);

  return (
    <section className="flex h-full min-h-0 min-w-0 flex-col overflow-hidden border border-[#262d38] bg-[#161b22]">
      <div className="flex min-h-10 items-center justify-between gap-3 border-b border-[#262d38] bg-[#161b22] px-4 py-2">
        <div className="min-w-0">
          <h2 className="truncate font-mono text-sm font-semibold text-[#e6edf3]">Equity curve</h2>
          <div className="mt-0.5 font-mono text-[10px] uppercase text-[#768390]">
            Balance / Equity
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-3">
          <LatestValue label="Balance" value={latest?.balance} />
          <LatestValue label="Equity" value={latest?.equity} accent />
          <div className="flex border border-[#262d38] bg-[#0d1117] p-0.5">
            {(["5m", "30m", "1h", "all"] as const).map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => onRangeChange(value)}
                className={`px-2 py-1 font-mono text-[10px] uppercase transition-colors ${
                  range === value
                    ? "bg-[#1f6feb] text-white"
                    : "text-[#768390] hover:bg-[#21272f] hover:text-[#e6edf3]"
                }`}
              >
                {RANGE_LABELS[value]}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="min-h-0 flex-1 px-3 py-2">
        {loading && history.length === 0 ? (
          <PanelState label="Loading equity history..." />
        ) : error ? (
          <div className="flex h-full items-center justify-between gap-3 text-sm">
            <span className="min-w-0 break-all font-mono text-xs text-[#f0a8a4]">{error}</span>
            <button
              type="button"
              onClick={onRetry}
              className="shrink-0 border border-[#f85149]/60 px-3 py-1.5 text-xs font-medium text-[#ff9a92] hover:bg-[#f85149]/10"
            >
              Retry
            </button>
          </div>
        ) : chartData.length === 0 ? (
          <PanelState label="No equity history" />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartData} margin={{ top: 6, right: 18, bottom: 0, left: 4 }}>
              <CartesianGrid stroke="#26313a" strokeDasharray="2 5" />
              <XAxis
                dataKey="time"
                minTickGap={32}
                tick={{ fill: "#82909d", fontSize: 10 }}
                tickLine={false}
                axisLine={{ stroke: "#33404a" }}
              />
              <YAxis
                domain={["auto", "auto"]}
                orientation="right"
                tick={{ fill: "#82909d", fontSize: 10 }}
                tickFormatter={(value: number) => compactJpy(value)}
                tickLine={false}
                axisLine={false}
                width={68}
              />
              <Tooltip content={(props) => <EquityTooltip {...props} />} />
              <Legend
                verticalAlign="top"
                align="left"
                iconType="plainline"
                wrapperStyle={{ color: "#a1a1aa", fontSize: 11, paddingBottom: 2 }}
              />
              <Line
                type="linear"
                dataKey="balance"
                name="Balance"
                stroke="#adbac7"
                strokeWidth={1.7}
                dot={false}
                isAnimationActive={false}
              />
              <Line
                type="linear"
                dataKey="equity"
                name="Equity"
                stroke="#58a6ff"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  );
}

function LatestValue({
  accent,
  label,
  value,
}: {
  accent?: boolean;
  label: string;
  value?: number;
}) {
  return (
    <div className="hidden text-right font-mono sm:block">
      <div className="text-[9px] uppercase text-[#768390]">{label}</div>
      <div className={`text-[11px] font-semibold tabular-nums ${accent ? "text-[#58a6ff]" : "text-[#adbac7]"}`}>
        {value === undefined ? "--" : formatJpy(value)}
      </div>
    </div>
  );
}

function EquityTooltip({ active, payload }: TooltipContentProps) {
  if (!active || !payload?.length) {
    return null;
  }

  const point = payload[0].payload as EquityChartPoint;

  return (
    <div className="border border-[#35434e] bg-[#11181e] px-3 py-2 font-mono text-xs text-zinc-200 shadow-xl">
      <div className="mb-2 border-b border-[#35434e] pb-2 text-zinc-500">
        {new Date(point.recordedAt).toLocaleString("ja-JP")}
      </div>
      <TooltipValue label="Balance" value={formatJpy(point.balance)} color="text-[#adbac7]" />
      <TooltipValue label="Equity" value={formatJpy(point.equity)} color="text-[#58a6ff]" />
      <TooltipValue label="Used margin" value={formatOptionalJpy(point.usedMargin)} color="text-[#d29922]" />
      <TooltipValue label="Margin ratio" value={formatOptionalPercent(point.marginRatio)} color="text-[#768390]" />
    </div>
  );
}

function TooltipValue({
  color,
  label,
  value,
}: {
  color: string;
  label: string;
  value: string;
}) {
  return (
    <div className="flex min-w-48 items-center justify-between gap-5 py-0.5">
      <span className="text-zinc-500">{label}</span>
      <span className={color}>{value}</span>
    </div>
  );
}

function PanelState({ label }: { label: string }) {
  return <div className="grid h-full place-items-center text-sm text-[#768390]">{label}</div>;
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

function formatJpy(value: number): string {
  return `JPY ${new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(value)}`;
}

function formatOptionalJpy(value: number | null): string {
  return value === null ? "--" : formatJpy(value);
}

function formatOptionalPercent(value: number | null): string {
  return value === null ? "--" : `${value.toFixed(2)}%`;
}

function compactJpy(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(1)}m`;
  }
  if (abs >= 1_000) {
    return `${(value / 1_000).toFixed(0)}k`;
  }
  return value.toFixed(0);
}

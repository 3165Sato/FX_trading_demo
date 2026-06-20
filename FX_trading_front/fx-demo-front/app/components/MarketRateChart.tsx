"use client";

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
import { useMemo } from "react";

import type { MarketRateTick } from "../../lib/marketRateTicks";

type MarketRateChartProps = {
  currencyPair: string;
  ticks: MarketRateTick[];
};

type ChartPoint = {
  time: string;
  quotedAt: string;
  bid: number;
  ask: number;
  midPrice: number;
  spread: number;
};

export function MarketRateChart({
  currencyPair,
  ticks,
}: MarketRateChartProps) {
  const chartData = useMemo<ChartPoint[]>(
    () =>
      ticks.map((tick) => ({
        time: formatTime(tick.quotedAt),
        quotedAt: tick.quotedAt,
        bid: tick.bid,
        ask: tick.ask,
        midPrice: tick.midPrice,
        spread: tick.spread,
      })),
    [ticks],
  );
  const priceScale = getPriceScale(currencyPair);

  return (
    <div className="h-[390px] w-full sm:h-[470px]">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart
          data={chartData}
          margin={{ top: 14, right: 18, bottom: 4, left: 4 }}
        >
          <CartesianGrid stroke="#26313a" strokeDasharray="2 5" />
          <XAxis
            dataKey="time"
            minTickGap={42}
            tick={{ fill: "#82909d", fontSize: 11 }}
            tickLine={false}
            axisLine={{ stroke: "#33404a" }}
          />
          <YAxis
            dataKey="midPrice"
            domain={["auto", "auto"]}
            orientation="right"
            tick={{ fill: "#82909d", fontSize: 11 }}
            tickFormatter={(value: number) => value.toFixed(priceScale)}
            tickLine={false}
            axisLine={false}
            width={76}
          />
          <Tooltip content={(props) => <RateTooltip {...props} priceScale={priceScale} />} />
          <Legend
            verticalAlign="top"
            align="left"
            iconType="plainline"
            wrapperStyle={{ paddingBottom: 12, color: "#a1a1aa", fontSize: 12 }}
          />
          <Line
            type="linear"
            dataKey="bid"
            name="Bid"
            stroke="#38bdf8"
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="linear"
            dataKey="ask"
            name="Ask"
            stroke="#fb7185"
            strokeWidth={1.5}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="linear"
            dataKey="midPrice"
            name="Mid"
            stroke="#34d399"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function getPriceScale(currencyPair: string): number {
  return currencyPair.endsWith("/JPY") ? 3 : 5;
}

function RateTooltip({
  active,
  payload,
  priceScale,
}: TooltipContentProps & { priceScale: number }) {
  if (!active || !payload?.length) {
    return null;
  }

  const point = payload[0].payload as ChartPoint;

  return (
    <div className="border border-[#35434e] bg-[#11181e] px-3 py-2 font-mono text-xs text-zinc-200 shadow-xl">
      <div className="mb-2 border-b border-[#35434e] pb-2 text-zinc-500">
        {new Date(point.quotedAt).toLocaleString("ja-JP")}
      </div>
      <TooltipValue label="Bid" value={point.bid} scale={priceScale} color="text-sky-400" />
      <TooltipValue label="Ask" value={point.ask} scale={priceScale} color="text-rose-400" />
      <TooltipValue label="Mid" value={point.midPrice} scale={priceScale} color="text-emerald-400" />
      <TooltipValue label="Spread" value={point.spread} scale={priceScale} color="text-amber-300" />
    </div>
  );
}

function TooltipValue({
  label,
  value,
  scale,
  color,
}: {
  label: string;
  value: number;
  scale: number;
  color: string;
}) {
  return (
    <div className="flex min-w-44 items-center justify-between gap-5 py-0.5">
      <span className="text-zinc-500">{label}</span>
      <span className={color}>{value.toFixed(scale)}</span>
    </div>
  );
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

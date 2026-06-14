"use client";

import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
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
  midPrice: number;
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
        midPrice: tick.midPrice,
      })),
    [ticks],
  );
  const priceScale = currencyPair === "EUR/USD" ? 5 : 3;

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
          <Tooltip
            contentStyle={{
              background: "#11181e",
              border: "1px solid #35434e",
              borderRadius: 4,
              color: "#f4f7f9",
            }}
            formatter={(value) => [
              typeof value === "number" ? value.toFixed(priceScale) : value,
              "Mid",
            ]}
            labelFormatter={(_, payload) =>
              payload?.[0]?.payload?.quotedAt
                ? new Date(payload[0].payload.quotedAt).toLocaleString("ja-JP")
                : ""
            }
          />
          <Line
            type="linear"
            dataKey="midPrice"
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

function formatTime(value: string): string {
  return new Intl.DateTimeFormat("ja-JP", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(value));
}

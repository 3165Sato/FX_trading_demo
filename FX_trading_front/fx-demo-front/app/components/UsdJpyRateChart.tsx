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
import { useEffect, useMemo, useState } from "react";

import {
  fetchMarketRateTicks,
  type MarketRateTick,
} from "../../lib/marketRateTicks";

type ChartPoint = {
  time: string;
  quotedAt: string;
  midPrice: number;
};

const CURRENCY_PAIR = "USD/JPY";
const TICK_LIMIT = 300;

export function UsdJpyRateChart() {
  const [ticks, setTicks] = useState<MarketRateTick[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function loadTicks() {
      try {
        const nextTicks = await fetchMarketRateTicks(CURRENCY_PAIR, TICK_LIMIT);
        if (!active) {
          return;
        }
        setTicks(nextTicks);
        setErrorMessage(null);
      } catch (error) {
        if (!active) {
          return;
        }
        const message =
          error instanceof Error
            ? error.message
            : "Rate history could not be loaded.";
        setErrorMessage(message);
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadTicks();
    const intervalId = window.setInterval(loadTicks, 5000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  const chartData = useMemo<ChartPoint[]>(
    () =>
      ticks.map((tick) => ({
        time: new Intl.DateTimeFormat("ja-JP", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }).format(new Date(tick.quotedAt)),
        quotedAt: tick.quotedAt,
        midPrice: tick.midPrice,
      })),
    [ticks],
  );

  return (
    <section className="flex w-full flex-1 flex-col gap-5">
      <div className="flex flex-col gap-1 border-b border-zinc-200 pb-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-zinc-950">
            USD/JPY Rate Chart
          </h1>
          <p className="text-sm text-zinc-500">midPrice / latest 300 ticks</p>
        </div>
        <div className="text-sm text-zinc-500">
          {chartData.length > 0 ? `${chartData.length} points` : "No points"}
        </div>
      </div>

      {loading ? (
        <div className="flex min-h-[420px] items-center justify-center border border-zinc-200 bg-white text-sm text-zinc-500">
          Loading rate history...
        </div>
      ) : errorMessage ? (
        <div className="flex min-h-[420px] items-center justify-center border border-red-200 bg-red-50 px-6 text-sm text-red-700">
          {errorMessage}
        </div>
      ) : chartData.length === 0 ? (
        <div className="flex min-h-[420px] items-center justify-center border border-zinc-200 bg-white text-sm text-zinc-500">
          No rate history yet.
        </div>
      ) : (
        <div className="h-[460px] w-full border border-zinc-200 bg-white p-4">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={chartData}
              margin={{ top: 12, right: 24, bottom: 8, left: 8 }}
            >
              <CartesianGrid stroke="#e4e4e7" strokeDasharray="3 3" />
              <XAxis
                dataKey="time"
                minTickGap={36}
                tick={{ fill: "#71717a", fontSize: 12 }}
                tickLine={false}
              />
              <YAxis
                dataKey="midPrice"
                domain={["auto", "auto"]}
                tick={{ fill: "#71717a", fontSize: 12 }}
                tickFormatter={(value: number) => value.toFixed(3)}
                tickLine={false}
                width={72}
              />
              <Tooltip
                formatter={(value) => [
                  typeof value === "number" ? value.toFixed(5) : value,
                  "midPrice",
                ]}
                labelFormatter={(_, payload) =>
                  payload?.[0]?.payload?.quotedAt ?? ""
                }
              />
              <Line
                type="monotone"
                dataKey="midPrice"
                stroke="#0f766e"
                strokeWidth={2}
                dot={false}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </section>
  );
}

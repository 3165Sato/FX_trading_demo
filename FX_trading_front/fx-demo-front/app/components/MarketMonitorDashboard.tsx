"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  fetchLatestMarketRates,
  fetchMarketRateTicks,
  type MarketRate,
  type MarketRateTick,
} from "../../lib/marketRateTicks";
import { MarketRateCard } from "./MarketRateCard";
import { MarketRateChart } from "./MarketRateChart";

const MONITORED_PAIRS = ["USD/JPY", "EUR/JPY", "EUR/USD"];
const DEFAULT_PAIR = "USD/JPY";
const TICK_LIMIT = 300;

export function MarketMonitorDashboard() {
  const [rates, setRates] = useState<MarketRate[]>([]);
  const [ticks, setTicks] = useState<MarketRateTick[]>([]);
  const [selectedPair, setSelectedPair] = useState(DEFAULT_PAIR);
  const [rateChanges, setRateChanges] = useState<Record<string, number>>({});
  const [ratesLoading, setRatesLoading] = useState(true);
  const [ticksLoading, setTicksLoading] = useState(true);
  const [ratesError, setRatesError] = useState<string | null>(null);
  const [ticksError, setTicksError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const previousRatesRef = useRef<Map<string, number>>(new Map());

  const loadRates = useCallback(async () => {
    try {
      const nextRates = await fetchLatestMarketRates();
      const nextChanges: Record<string, number> = {};

      for (const rate of nextRates) {
        const previousMid = previousRatesRef.current.get(rate.currencyPair);
        nextChanges[rate.currencyPair] =
          previousMid === undefined ? 0 : rate.midPrice - previousMid;
      }

      previousRatesRef.current = new Map(
        nextRates.map((rate) => [rate.currencyPair, rate.midPrice]),
      );
      setRates(nextRates);
      setRateChanges(nextChanges);
      setRatesError(null);
      setLastUpdated(new Date());
    } catch (error) {
      setRatesError(getErrorMessage(error));
    } finally {
      setRatesLoading(false);
    }
  }, []);

  const loadTicks = useCallback(async () => {
    try {
      const nextTicks = await fetchMarketRateTicks(selectedPair, TICK_LIMIT);
      setTicks(nextTicks);
      setTicksError(null);
    } catch (error) {
      setTicksError(getErrorMessage(error));
    } finally {
      setTicksLoading(false);
    }
  }, [selectedPair]);

  useEffect(() => {
    const initialTimeoutId = window.setTimeout(loadRates, 0);
    const intervalId = window.setInterval(loadRates, 1000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadRates]);

  useEffect(() => {
    const initialTimeoutId = window.setTimeout(loadTicks, 0);
    const intervalId = window.setInterval(loadTicks, 5000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadTicks]);

  const selectCurrencyPair = (currencyPair: string) => {
    if (currencyPair === selectedPair) {
      return;
    }
    setSelectedPair(currencyPair);
    setTicks([]);
    setTicksLoading(true);
    setTicksError(null);
  };

  const monitoredRates = useMemo(
    () =>
      MONITORED_PAIRS.map((pair) =>
        rates.find((rate) => rate.currencyPair === pair),
      ).filter((rate): rate is MarketRate => rate !== undefined),
    [rates],
  );
  const recentTicks = useMemo(() => ticks.slice(-10).reverse(), [ticks]);
  const connected = rates.length > 0 && ratesError === null;
  const errorMessage = ratesError ?? ticksError;

  const retry = () => {
    setRatesLoading(rates.length === 0);
    setTicksLoading(ticks.length === 0);
    void loadRates();
    void loadTicks();
  };

  return (
    <main className="min-h-screen bg-[#080c10] text-zinc-100">
      <header className="border-b border-[#26313a] bg-[#0c1217]">
        <div className="mx-auto flex w-full max-w-[1500px] flex-col gap-5 px-4 py-5 sm:px-6 lg:flex-row lg:items-end lg:justify-between lg:px-8">
          <div>
            <div className="mb-2 flex items-center gap-2 font-mono text-[11px] uppercase text-emerald-400">
              <span className="h-2 w-2 bg-emerald-400" />
              Live simulation
            </div>
            <h1 className="text-2xl font-semibold text-white sm:text-3xl">
              DemoFX Market Monitor
            </h1>
            <p className="mt-1 text-sm text-zinc-500">
              Simulated FX market data dashboard
            </p>
          </div>
          <div className="font-mono text-xs text-zinc-500">
            Environment / LEARNING-DEMO
          </div>
        </div>
      </header>

      <section className="border-b border-[#26313a] bg-[#0a1014]">
        <div className="mx-auto grid w-full max-w-[1500px] grid-cols-2 divide-x divide-y divide-[#26313a] px-4 sm:px-6 md:grid-cols-5 md:divide-y-0 lg:px-8">
          <StatusItem
            label="Backend connection"
            value={connected ? "Connected" : "Disconnected"}
            accent={connected ? "positive" : "negative"}
          />
          <StatusItem
            label="Last updated"
            value={lastUpdated ? formatTime(lastUpdated.toISOString()) : "--:--:--"}
          />
          <StatusItem label="Active pairs" value={String(rates.length)} />
          <StatusItem label="Tick interval" value="5s" />
          <StatusItem label="Rate interval" value="1s" />
        </div>
      </section>

      <div className="mx-auto flex w-full max-w-[1500px] flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        {errorMessage && (
          <div className="flex flex-col gap-3 border border-rose-500/50 bg-rose-950/30 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="text-sm font-semibold text-rose-300">
                Market data connection issue
              </div>
              <div className="mt-1 break-all font-mono text-xs text-rose-200/70">
                {errorMessage}
              </div>
            </div>
            <button
              type="button"
              onClick={retry}
              className="border border-rose-400/60 px-4 py-2 text-sm font-medium text-rose-200 hover:bg-rose-400/10"
            >
              Retry
            </button>
          </div>
        )}

        <section aria-labelledby="rates-heading">
          <div className="mb-3 flex items-center justify-between">
            <h2
              id="rates-heading"
              className="text-xs font-semibold uppercase text-zinc-500"
            >
              Live rates
            </h2>
            <span className="hidden font-mono text-[11px] text-zinc-600 sm:block">
              Click a pair to inspect
            </span>
          </div>

          {ratesLoading && rates.length === 0 ? (
            <LoadingPanel label="Loading latest market rates..." />
          ) : (
            <div className="grid gap-3 md:grid-cols-3">
              {monitoredRates.map((rate) => (
                <MarketRateCard
                  key={rate.currencyPair}
                  rate={rate}
                  change={rateChanges[rate.currencyPair] ?? 0}
                  selected={selectedPair === rate.currencyPair}
                  onSelect={selectCurrencyPair}
                />
              ))}
            </div>
          )}
        </section>

        <div className="grid min-w-0 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <section className="min-w-0 border border-[#2a353e] bg-[#0e1419]">
            <div className="flex items-center justify-between border-b border-[#2a353e] px-4 py-3 sm:px-5">
              <div>
                <h2 className="font-mono text-sm font-semibold text-zinc-100">
                  {selectedPair} Mid Price
                </h2>
                <p className="mt-1 text-xs text-zinc-500">
                  Recent simulated tick history
                </p>
              </div>
              <div className="font-mono text-xs text-zinc-500">
                {ticks.length} ticks
              </div>
            </div>

            {ticksLoading && ticks.length === 0 ? (
              <LoadingPanel label={`Loading ${selectedPair} tick history...`} />
            ) : ticks.length === 0 ? (
              <EmptyPanel label="No tick history is available yet." />
            ) : (
              <div className="p-2 sm:p-4">
                <MarketRateChart currencyPair={selectedPair} ticks={ticks} />
              </div>
            )}
          </section>

          <section className="min-w-0 border border-[#2a353e] bg-[#0e1419]">
            <div className="border-b border-[#2a353e] px-4 py-3">
              <h2 className="text-sm font-semibold text-zinc-100">Tick log</h2>
              <p className="mt-1 text-xs text-zinc-500">
                Latest 10 / {selectedPair}
              </p>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full table-fixed font-mono text-xs">
                <thead className="text-left text-[10px] uppercase text-zinc-600">
                  <tr className="border-b border-[#26313a]">
                    <th className="w-[28%] px-3 py-3 font-medium">Time</th>
                    <th className="w-[24%] px-2 py-3 text-right font-medium">Bid</th>
                    <th className="w-[24%] px-2 py-3 text-right font-medium">Ask</th>
                    <th className="w-[24%] px-3 py-3 text-right font-medium">Mid</th>
                  </tr>
                </thead>
                <tbody>
                  {recentTicks.map((tick) => (
                    <TickRow key={`${tick.quotedAt}-${tick.midPrice}`} tick={tick} />
                  ))}
                </tbody>
              </table>
            </div>
            {!ticksLoading && recentTicks.length === 0 && (
              <div className="px-4 py-12 text-center text-sm text-zinc-600">
                Waiting for ticks
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}

function StatusItem({
  label,
  value,
  accent,
}: {
  label: string;
  value: string;
  accent?: "positive" | "negative";
}) {
  const valueClass =
    accent === "positive"
      ? "text-emerald-400"
      : accent === "negative"
        ? "text-rose-400"
        : "text-zinc-200";

  return (
    <div className="px-3 py-4 md:px-5">
      <div className="text-[10px] uppercase text-zinc-600">{label}</div>
      <div className={`mt-1 font-mono text-sm font-semibold ${valueClass}`}>
        {value}
      </div>
    </div>
  );
}

function TickRow({ tick }: { tick: MarketRateTick }) {
  const scale = tick.currencyPair === "EUR/USD" ? 5 : 3;
  return (
    <tr className="border-b border-[#202930] text-zinc-300 last:border-0 hover:bg-white/[0.025]">
      <td className="px-3 py-3 text-zinc-500">{formatTime(tick.quotedAt)}</td>
      <td className="px-2 py-3 text-right">{tick.bid.toFixed(scale)}</td>
      <td className="px-2 py-3 text-right">{tick.ask.toFixed(scale)}</td>
      <td className="px-3 py-3 text-right text-emerald-300">
        {tick.midPrice.toFixed(scale)}
      </td>
    </tr>
  );
}

function LoadingPanel({ label }: { label: string }) {
  return (
    <div className="flex min-h-40 items-center justify-center border border-[#2a353e] bg-[#0e1419] font-mono text-sm text-zinc-500">
      {label}
    </div>
  );
}

function EmptyPanel({ label }: { label: string }) {
  return (
    <div className="flex min-h-[470px] items-center justify-center text-sm text-zinc-600">
      {label}
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

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Market data could not be loaded.";
}

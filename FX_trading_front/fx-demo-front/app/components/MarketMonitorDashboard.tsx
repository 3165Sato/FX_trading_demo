"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  fetchLatestMarketRates,
  fetchMarketAlerts,
  fetchMarketRateTicks,
  fetchNewsEvents,
  getSpreadStats,
  triggerNewsEvent,
  type MarketRate,
  type MarketRateTick,
  type AlertSeverity,
  type MarketAlert,
  type NewsDirection,
  type NewsEvent,
  type SpreadStats,
} from "../../lib/marketRateTicks";
import { MarketRateCard } from "./MarketRateCard";
import { MarketRateChart } from "./MarketRateChart";
import { SpreadMonitorCard } from "./SpreadMonitorCard";

const DEFAULT_PAIR = "USD/JPY";
const TICK_LIMIT = 300;
const SPREAD_STATS_LIMIT = 60;
const NEWS_EVENT_LIMIT = 10;
const ALERT_LIMIT = 50;

export function MarketMonitorDashboard() {
  const [rates, setRates] = useState<MarketRate[]>([]);
  const [ticks, setTicks] = useState<MarketRateTick[]>([]);
  const [alerts, setAlerts] = useState<MarketAlert[]>([]);
  const [newsEvents, setNewsEvents] = useState<NewsEvent[]>([]);
  const [spreadStats, setSpreadStats] = useState<SpreadStats | undefined>();
  const [selectedPair, setSelectedPair] = useState(DEFAULT_PAIR);
  const [rateChanges, setRateChanges] = useState<Record<string, number>>({});
  const [ratesLoading, setRatesLoading] = useState(true);
  const [ticksLoading, setTicksLoading] = useState(true);
  const [ratesError, setRatesError] = useState<string | null>(null);
  const [ticksError, setTicksError] = useState<string | null>(null);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [newsEventsError, setNewsEventsError] = useState<string | null>(null);
  const [newsSubmittingDirection, setNewsSubmittingDirection] = useState<NewsDirection | null>(null);
  const [spreadStatsLoading, setSpreadStatsLoading] = useState(true);
  const [spreadStatsError, setSpreadStatsError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const previousRatesRef = useRef<Map<string, number>>(new Map());
  const activePair = useMemo(
    () =>
      rates.some((rate) => rate.currencyPair === selectedPair)
        ? selectedPair
        : rates[0]?.currencyPair ?? selectedPair,
    [rates, selectedPair],
  );

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
      const nextTicks = await fetchMarketRateTicks(activePair, TICK_LIMIT);
      setTicks(nextTicks);
      setTicksError(null);
    } catch (error) {
      setTicksError(getErrorMessage(error));
    } finally {
      setTicksLoading(false);
    }
  }, [activePair]);

  const loadSpreadStats = useCallback(async () => {
    try {
      const nextStats = await getSpreadStats(activePair, SPREAD_STATS_LIMIT);
      setSpreadStats(nextStats);
      setSpreadStatsError(null);
    } catch (error) {
      setSpreadStatsError(getErrorMessage(error));
    } finally {
      setSpreadStatsLoading(false);
    }
  }, [activePair]);

  const loadNewsEvents = useCallback(async () => {
    try {
      const nextEvents = await fetchNewsEvents(NEWS_EVENT_LIMIT);
      setNewsEvents(nextEvents);
      setNewsEventsError(null);
    } catch (error) {
      setNewsEventsError(getErrorMessage(error));
    }
  }, []);

  const loadAlerts = useCallback(async () => {
    try {
      const nextAlerts = await fetchMarketAlerts(ALERT_LIMIT);
      setAlerts(nextAlerts);
      setAlertsError(null);
    } catch (error) {
      setAlertsError(getErrorMessage(error));
    }
  }, []);

  useEffect(() => {
    const initialTimeoutId = window.setTimeout(loadRates, 0);
    const intervalId = window.setInterval(loadRates, 1000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadRates]);

  useEffect(() => {
    const initialTimeoutId = window.setTimeout(loadNewsEvents, 0);
    const intervalId = window.setInterval(loadNewsEvents, 5000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadNewsEvents]);

  useEffect(() => {
    const initialTimeoutId = window.setTimeout(loadAlerts, 0);
    const intervalId = window.setInterval(loadAlerts, 3000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadAlerts]);

  useEffect(() => {
    const loadSelectedMarketData = () => {
      void loadTicks();
      void loadSpreadStats();
    };
    const initialTimeoutId = window.setTimeout(loadSelectedMarketData, 0);
    const intervalId = window.setInterval(loadSelectedMarketData, 5000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadSpreadStats, loadTicks]);

  const selectCurrencyPair = (currencyPair: string) => {
    if (currencyPair === activePair) {
      return;
    }
    setSelectedPair(currencyPair);
    setTicks([]);
    setSpreadStats(undefined);
    setTicksLoading(true);
    setSpreadStatsLoading(true);
    setTicksError(null);
    setSpreadStatsError(null);
  };

  const monitoredRates = useMemo(
    () => [...rates].sort((first, second) => first.currencyPair.localeCompare(second.currencyPair)),
    [rates],
  );
  const recentTicks = useMemo(() => ticks.slice(-10).reverse(), [ticks]);
  const activeAlerts = useMemo(() => alerts.filter((alert) => alert.active), [alerts]);
  const connected = rates.length > 0 && ratesError === null;
  const errorMessage = ratesError ?? ticksError ?? spreadStatsError ?? alertsError ?? newsEventsError;

  const triggerSelectedNewsEvent = async (direction: NewsDirection) => {
    setNewsSubmittingDirection(direction);
    try {
      const event = await triggerNewsEvent(activePair, direction);
      setNewsEvents((current) => [event, ...current].slice(0, NEWS_EVENT_LIMIT));
      setNewsEventsError(null);
      void loadRates();
      void loadSpreadStats();
      void loadAlerts();
    } catch (error) {
      setNewsEventsError(getErrorMessage(error));
    } finally {
      setNewsSubmittingDirection(null);
    }
  };

  const retry = () => {
    setRatesLoading(rates.length === 0);
    setTicksLoading(ticks.length === 0);
    setSpreadStatsLoading(spreadStats === undefined);
    void loadRates();
    void loadTicks();
    void loadSpreadStats();
    void loadAlerts();
    void loadNewsEvents();
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
          <StatusItem label="Active alerts" value={String(activeAlerts.length)} accent={activeAlerts.length > 0 ? "negative" : undefined} />
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
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {monitoredRates.map((rate) => (
                <MarketRateCard
                  key={rate.currencyPair}
                  rate={rate}
                  change={rateChanges[rate.currencyPair] ?? 0}
                  selected={activePair === rate.currencyPair}
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
                  {activePair} Bid / Ask / Mid
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
              <LoadingPanel label={`Loading ${activePair} tick history...`} />
            ) : ticks.length === 0 ? (
              <EmptyPanel label="No tick history is available yet." />
            ) : (
              <div className="p-2 sm:p-4">
                <MarketRateChart currencyPair={activePair} ticks={ticks} />
              </div>
            )}
          </section>

          <aside className="flex min-w-0 flex-col gap-6">
            <AlertPanel alerts={alerts} activeCount={activeAlerts.length} />

            <NewsEventPanel
              activePair={activePair}
              events={newsEvents}
              submittingDirection={newsSubmittingDirection}
              onTrigger={triggerSelectedNewsEvent}
            />

            <SpreadMonitorCard
              currencyPair={activePair}
              error={spreadStatsError}
              loading={spreadStatsLoading}
              stats={spreadStats}
            />

            <section className="min-w-0 border border-[#2a353e] bg-[#0e1419]">
              <div className="border-b border-[#2a353e] px-4 py-3">
                <h2 className="text-sm font-semibold text-zinc-100">Tick log</h2>
                <p className="mt-1 text-xs text-zinc-500">
                  Latest 10 / {activePair}
                </p>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[350px] table-fixed font-mono text-[11px]">
                  <thead className="text-left text-[9px] uppercase text-zinc-600">
                    <tr className="border-b border-[#26313a]">
                      <th className="w-[22%] px-3 py-3 font-medium">Time</th>
                      <th className="w-[20%] px-1 py-3 text-right font-medium">Bid</th>
                      <th className="w-[20%] px-1 py-3 text-right font-medium">Ask</th>
                      <th className="w-[20%] px-1 py-3 text-right font-medium">Mid</th>
                      <th className="w-[18%] px-3 py-3 text-right font-medium">Spr</th>
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
          </aside>
        </div>
      </div>
    </main>
  );
}

function AlertPanel({
  alerts,
  activeCount,
}: {
  alerts: MarketAlert[];
  activeCount: number;
}) {
  return (
    <section className="border border-[#2a353e] bg-[#0e1419]">
      <div className="flex items-start justify-between border-b border-[#2a353e] px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold text-zinc-100">Anomaly alerts</h2>
          <p className="mt-1 text-xs text-zinc-500">Rule-based monitor</p>
        </div>
        <span
          className={`border px-2 py-1 font-mono text-[10px] uppercase ${
            activeCount > 0
              ? "border-rose-400/70 text-rose-300"
              : "border-zinc-500/50 text-zinc-400"
          }`}
        >
          Active: {activeCount}
        </span>
      </div>
      <div className="flex max-h-80 flex-col gap-2 overflow-y-auto px-4 py-3">
        {alerts.length === 0 ? (
          <div className="py-8 text-center text-xs text-zinc-600">No alerts</div>
        ) : (
          alerts.map((alert) => <AlertRow key={alert.id} alert={alert} />)
        )}
      </div>
    </section>
  );
}

function AlertRow({ alert }: { alert: MarketAlert }) {
  return (
    <div
      className={`border px-3 py-2 ${
        alert.active
          ? "border-rose-500/40 bg-rose-950/20"
          : "border-[#26313a] bg-[#0a1014]"
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="font-mono text-xs text-zinc-200">
            {alert.currencyPair} / {alert.type}
          </div>
          <div className="mt-1 text-xs text-zinc-500">{alert.message}</div>
        </div>
        <span className={`shrink-0 font-mono text-[10px] ${getAlertSeverityClass(alert.severity)}`}>
          {alert.severity}
        </span>
      </div>
      <div className="mt-2 flex items-center justify-between font-mono text-[10px] text-zinc-600">
        <span>{formatTime(alert.raisedAt)}</span>
        <span>{alert.active ? "ACTIVE" : `RESOLVED ${alert.resolvedAt ? formatTime(alert.resolvedAt) : ""}`}</span>
      </div>
    </div>
  );
}

function getAlertSeverityClass(severity: AlertSeverity): string {
  switch (severity) {
    case "CRITICAL":
      return "text-rose-300";
    case "WARNING":
      return "text-amber-300";
    case "INFO":
      return "text-sky-300";
  }
}

function NewsEventPanel({
  activePair,
  events,
  submittingDirection,
  onTrigger,
}: {
  activePair: string;
  events: NewsEvent[];
  submittingDirection: NewsDirection | null;
  onTrigger: (direction: NewsDirection) => void;
}) {
  return (
    <section className="border border-[#2a353e] bg-[#0e1419]">
      <div className="border-b border-[#2a353e] px-4 py-3">
        <h2 className="text-sm font-semibold text-zinc-100">Fictional news event</h2>
        <p className="mt-1 text-xs text-zinc-500">{activePair} manual trigger</p>
      </div>
      <div className="grid grid-cols-2 gap-px bg-[#26313a]">
        <NewsTriggerButton
          direction="UP"
          disabled={submittingDirection !== null}
          loading={submittingDirection === "UP"}
          onTrigger={onTrigger}
        />
        <NewsTriggerButton
          direction="DOWN"
          disabled={submittingDirection !== null}
          loading={submittingDirection === "DOWN"}
          onTrigger={onTrigger}
        />
      </div>
      <div className="border-t border-[#2a353e] px-4 py-3">
        <div className="mb-3 text-[10px] uppercase text-zinc-600">Recent events</div>
        <div className="flex max-h-60 flex-col gap-2 overflow-y-auto">
          {events.length === 0 ? (
            <div className="py-6 text-center text-xs text-zinc-600">No events yet</div>
          ) : (
            events.map((event) => <NewsEventRow key={event.id} event={event} />)
          )}
        </div>
      </div>
    </section>
  );
}

function NewsTriggerButton({
  direction,
  disabled,
  loading,
  onTrigger,
}: {
  direction: NewsDirection;
  disabled: boolean;
  loading: boolean;
  onTrigger: (direction: NewsDirection) => void;
}) {
  const directionClass =
    direction === "UP"
      ? "text-emerald-300 hover:bg-emerald-400/10"
      : "text-rose-300 hover:bg-rose-400/10";

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onTrigger(direction)}
      className={`bg-[#0e1419] px-4 py-4 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${directionClass}`}
    >
      <div className="font-mono text-lg font-semibold">
        {loading ? "Sending..." : direction}
      </div>
      <div className="mt-1 text-[10px] uppercase text-zinc-600">
        Jump + spread shock
      </div>
    </button>
  );
}

function NewsEventRow({ event }: { event: NewsEvent }) {
  const directionClass = event.direction === "UP" ? "text-emerald-300" : "text-rose-300";
  return (
    <div className="border border-[#26313a] bg-[#0a1014] px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <span className="font-mono text-xs text-zinc-200">{event.currencyPair}</span>
        <span className={`font-mono text-[11px] ${directionClass}`}>
          {event.direction} {event.magnitudeBps}bps
        </span>
      </div>
      <div className="mt-1 line-clamp-2 text-xs text-zinc-500">{event.headline}</div>
      <div className="mt-2 flex items-center justify-between font-mono text-[10px] text-zinc-600">
        <span>{formatTime(event.startedAt)}</span>
        <span className={event.active ? "text-amber-300" : "text-zinc-600"}>
          {event.active ? "ACTIVE" : "ENDED"}
        </span>
      </div>
    </div>
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
  const scale = getPriceScale(tick.currencyPair);
  return (
    <tr className="border-b border-[#202930] text-zinc-300 last:border-0 hover:bg-white/[0.025]">
      <td className="px-3 py-3 text-zinc-500">{formatTime(tick.quotedAt)}</td>
      <td className="px-1 py-3 text-right text-sky-300">{tick.bid.toFixed(scale)}</td>
      <td className="px-1 py-3 text-right text-rose-300">{tick.ask.toFixed(scale)}</td>
      <td className="px-1 py-3 text-right text-emerald-300">
        {tick.midPrice.toFixed(scale)}
      </td>
      <td className="px-3 py-3 text-right text-amber-200">
        {tick.spread.toFixed(scale)}
      </td>
    </tr>
  );
}

function getPriceScale(currencyPair: string): number {
  return currencyPair.endsWith("/JPY") ? 3 : 5;
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

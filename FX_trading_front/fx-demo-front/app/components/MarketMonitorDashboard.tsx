"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  fetchLatestMarketRates,
  fetchMarketAlerts,
  fetchMarketRateTicks,
  fetchOrders,
  fetchPositions,
  fetchTrades,
  fetchNewsEvents,
  getSpreadStats,
  placeMarketOrder,
  triggerNewsEvent,
  type AlertSeverity,
  type MarketAlert,
  type MarketRate,
  type MarketRateTick,
  type NewsDirection,
  type NewsEvent,
  type OrderSide,
  type OrderSummary,
  type PositionSummary,
  type SpreadStats,
  type TradeSummary,
} from "../../lib/marketRateTicks";
import { MarketRateChart } from "./MarketRateChart";
import { SpreadMonitorCard } from "./SpreadMonitorCard";

const DEFAULT_PAIR = "USD/JPY";
const TICK_LIMIT = 300;
const SPREAD_STATS_LIMIT = 60;
const NEWS_EVENT_LIMIT = 10;
const ALERT_LIMIT = 50;
const TRADE_LIMIT = 50;
const ORDER_LIMIT = 50;
const SCREEN_STORAGE_KEY = "demofx.screen";
const LEGACY_PAIR_STORAGE_KEY = "demofx.selectedPair";
const MONITOR_PAIR_STORAGE_KEY = "demofx.monitorSelectedPair";
const TRADING_PAIR_STORAGE_KEY = "demofx.tradingSelectedPair";

type Screen = "monitor" | "trading";

export function MarketMonitorDashboard() {
  const [screen, setScreen] = useState<Screen>("monitor");
  const [rates, setRates] = useState<MarketRate[]>([]);
  const [ticks, setTicks] = useState<MarketRateTick[]>([]);
  const [alerts, setAlerts] = useState<MarketAlert[]>([]);
  const [trades, setTrades] = useState<TradeSummary[]>([]);
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [positions, setPositions] = useState<PositionSummary[]>([]);
  const [newsEvents, setNewsEvents] = useState<NewsEvent[]>([]);
  const [spreadStats, setSpreadStats] = useState<SpreadStats | undefined>();
  const [monitorSelectedPair, setMonitorSelectedPair] = useState(DEFAULT_PAIR);
  const [tradingSelectedPair, setTradingSelectedPair] = useState(DEFAULT_PAIR);
  const [orderQuantity, setOrderQuantity] = useState("10000");
  const [rateChanges, setRateChanges] = useState<Record<string, number>>({});
  const [ratesLoading, setRatesLoading] = useState(true);
  const [ticksLoading, setTicksLoading] = useState(true);
  const [spreadStatsLoading, setSpreadStatsLoading] = useState(true);
  const [ratesError, setRatesError] = useState<string | null>(null);
  const [ticksError, setTicksError] = useState<string | null>(null);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [tradesError, setTradesError] = useState<string | null>(null);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [positionsError, setPositionsError] = useState<string | null>(null);
  const [orderError, setOrderError] = useState<string | null>(null);
  const [newsEventsError, setNewsEventsError] = useState<string | null>(null);
  const [spreadStatsError, setSpreadStatsError] = useState<string | null>(null);
  const [lastOrderMessage, setLastOrderMessage] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [clock, setClock] = useState("--:--:--");
  const [nowMs, setNowMs] = useState(0);
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [submittingOrderSide, setSubmittingOrderSide] = useState<OrderSide | null>(null);
  const [newsSubmittingDirection, setNewsSubmittingDirection] = useState<NewsDirection | null>(null);
  const previousRatesRef = useRef<Map<string, number>>(new Map());

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      const savedScreen = window.localStorage.getItem(SCREEN_STORAGE_KEY);
      const savedMonitorPair =
        window.localStorage.getItem(MONITOR_PAIR_STORAGE_KEY) ??
        window.localStorage.getItem(LEGACY_PAIR_STORAGE_KEY);
      const savedTradingPair = window.localStorage.getItem(TRADING_PAIR_STORAGE_KEY);
      if (savedScreen === "monitor" || savedScreen === "trading") {
        setScreen(savedScreen);
      }
      if (savedMonitorPair) {
        setMonitorSelectedPair(savedMonitorPair);
      }
      if (savedTradingPair) {
        setTradingSelectedPair(savedTradingPair);
      }
      setPreferencesLoaded(true);
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, []);

  useEffect(() => {
    if (!preferencesLoaded) {
      return;
    }
    window.localStorage.setItem(SCREEN_STORAGE_KEY, screen);
  }, [preferencesLoaded, screen]);

  useEffect(() => {
    if (!preferencesLoaded) {
      return;
    }
    window.localStorage.setItem(MONITOR_PAIR_STORAGE_KEY, monitorSelectedPair);
  }, [monitorSelectedPair, preferencesLoaded]);

  useEffect(() => {
    if (!preferencesLoaded) {
      return;
    }
    window.localStorage.setItem(TRADING_PAIR_STORAGE_KEY, tradingSelectedPair);
  }, [preferencesLoaded, tradingSelectedPair]);

  useEffect(() => {
    const updateClock = () => {
      setNowMs(Date.now());
      setClock(
        new Intl.DateTimeFormat("ja-JP", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          timeZone: "Asia/Tokyo",
        }).format(new Date()),
      );
    };
    updateClock();
    const intervalId = window.setInterval(updateClock, 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  const monitorActivePair = useMemo(
    () =>
      rates.some((rate) => rate.currencyPair === monitorSelectedPair)
        ? monitorSelectedPair
        : rates[0]?.currencyPair ?? monitorSelectedPair,
    [monitorSelectedPair, rates],
  );
  const tradingActivePair = useMemo(
    () =>
      rates.some((rate) => rate.currencyPair === tradingSelectedPair)
        ? tradingSelectedPair
        : rates[0]?.currencyPair ?? tradingSelectedPair,
    [rates, tradingSelectedPair],
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
      const nextTicks = await fetchMarketRateTicks(monitorActivePair, TICK_LIMIT);
      setTicks(nextTicks);
      setTicksError(null);
    } catch (error) {
      setTicksError(getErrorMessage(error));
    } finally {
      setTicksLoading(false);
    }
  }, [monitorActivePair]);

  const loadSpreadStats = useCallback(async () => {
    try {
      const nextStats = await getSpreadStats(monitorActivePair, SPREAD_STATS_LIMIT);
      setSpreadStats(nextStats);
      setSpreadStatsError(null);
    } catch (error) {
      setSpreadStatsError(getErrorMessage(error));
    } finally {
      setSpreadStatsLoading(false);
    }
  }, [monitorActivePair]);

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

  const loadTrades = useCallback(async () => {
    try {
      const nextTrades = await fetchTrades(undefined, TRADE_LIMIT);
      setTrades(nextTrades);
      setTradesError(null);
    } catch (error) {
      setTradesError(getErrorMessage(error));
    }
  }, []);

  const loadOrders = useCallback(async () => {
    try {
      const nextOrders = await fetchOrders(undefined, ORDER_LIMIT);
      setOrders(nextOrders);
      setOrdersError(null);
    } catch (error) {
      setOrdersError(getErrorMessage(error));
    }
  }, []);

  const loadPositions = useCallback(async () => {
    try {
      const nextPositions = await fetchPositions();
      setPositions(nextPositions);
      setPositionsError(null);
    } catch (error) {
      setPositionsError(getErrorMessage(error));
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
    const loadTradeData = () => {
      void loadTrades();
      void loadOrders();
      void loadPositions();
    };
    const initialTimeoutId = window.setTimeout(loadTradeData, 0);
    const intervalId = window.setInterval(loadTradeData, 5000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadOrders, loadPositions, loadTrades]);

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

  const selectMonitorCurrencyPair = (currencyPair: string) => {
    if (currencyPair === monitorActivePair) {
      return;
    }
    setMonitorSelectedPair(currencyPair);
    setTicks([]);
    setSpreadStats(undefined);
    setTicksLoading(true);
    setSpreadStatsLoading(true);
    setTicksError(null);
    setSpreadStatsError(null);
  };

  const selectTradingCurrencyPair = (currencyPair: string) => {
    if (currencyPair === tradingActivePair) {
      return;
    }
    setTradingSelectedPair(currencyPair);
    setOrderError(null);
    setLastOrderMessage(null);
  };

  const monitoredRates = useMemo(
    () => [...rates].sort((first, second) => first.currencyPair.localeCompare(second.currencyPair)),
    [rates],
  );
  const recentTicks = useMemo(() => ticks.slice(-10).reverse(), [ticks]);
  const activeAlerts = useMemo(() => alerts.filter((alert) => alert.active), [alerts]);
  const selectedRate = useMemo(
    () => rates.find((rate) => rate.currencyPair === tradingActivePair),
    [rates, tradingActivePair],
  );
  const connected = rates.length > 0 && ratesError === null;
  const stalled = !lastUpdated || nowMs - lastUpdated.getTime() > 8000;
  const feedStatus = connected && !stalled ? "LIVE" : "STALLED";
  const errorMessage =
    ratesError ??
    ticksError ??
    spreadStatsError ??
    alertsError ??
    tradesError ??
    ordersError ??
    positionsError ??
    newsEventsError;

  const setActiveScreen = (nextScreen: Screen) => {
    setScreen(nextScreen);
  };

  const triggerSelectedNewsEvent = async (direction: NewsDirection) => {
    setNewsSubmittingDirection(direction);
    try {
      const event = await triggerNewsEvent(monitorActivePair, direction);
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
    void loadTrades();
    void loadOrders();
    void loadPositions();
    void loadNewsEvents();
  };

  const submitMarketOrder = async (side: OrderSide) => {
    const quantity = Number(orderQuantity);
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setOrderError("Quantity must be greater than zero.");
      return;
    }

    setSubmittingOrderSide(side);
    try {
      const result = await placeMarketOrder(tradingActivePair, side, quantity);
      setOrderError(null);
      setLastOrderMessage(
        `${result.trade.side} ${formatQuantity(result.trade.quantity)} ${result.trade.currencyPair} @ ${formatPrice(result.trade.price, result.trade.currencyPair)}`,
      );
      setTrades((current) => [result.trade, ...current].slice(0, TRADE_LIMIT));
      setOrders((current) => [result.order, ...current].slice(0, ORDER_LIMIT));
      void loadTrades();
      void loadOrders();
      void loadPositions();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingOrderSide(null);
    }
  };

  return (
    <main className="flex h-dvh flex-col overflow-hidden bg-[#0d1117] text-[#e6edf3]">
      <AppHeader
        activeAlerts={activeAlerts.length}
        clock={clock}
        feedStatus={feedStatus}
        screen={screen}
        onScreenChange={setActiveScreen}
      />

      <StatusStrip
        activeAlerts={activeAlerts.length}
        connected={connected}
        lastUpdated={lastUpdated}
        pairCount={rates.length}
      />

      <div className="mx-auto flex min-h-0 w-full max-w-[1500px] flex-1 flex-col gap-3 overflow-hidden px-4 py-3 sm:px-6 lg:px-8">
        {errorMessage && <ConnectionIssue message={errorMessage} onRetry={retry} />}

        {screen === "monitor" ? (
          <MonitorScreen
            activeAlerts={activeAlerts.length}
            activePair={monitorActivePair}
            alerts={alerts}
            monitoredRates={monitoredRates}
            newsEvents={newsEvents}
            newsSubmittingDirection={newsSubmittingDirection}
            rateChanges={rateChanges}
            ratesLoading={ratesLoading}
            recentTicks={recentTicks}
            spreadStats={spreadStats}
            spreadStatsError={spreadStatsError}
            spreadStatsLoading={spreadStatsLoading}
            ticks={ticks}
            ticksLoading={ticksLoading}
            onSelectPair={selectMonitorCurrencyPair}
            onTriggerNews={triggerSelectedNewsEvent}
          />
        ) : (
          <TradingScreen
            activePair={tradingActivePair}
            orderError={orderError}
            orderQuantity={orderQuantity}
            orders={orders}
            positions={positions}
            rates={monitoredRates}
            selectedRate={selectedRate}
            submittingOrderSide={submittingOrderSide}
            lastOrderMessage={lastOrderMessage}
            trades={trades}
            onQuantityChange={setOrderQuantity}
            onSelectPair={selectTradingCurrencyPair}
            onSubmitOrder={submitMarketOrder}
          />
        )}
      </div>
    </main>
  );
}

function AppHeader({
  activeAlerts,
  clock,
  feedStatus,
  screen,
  onScreenChange,
}: {
  activeAlerts: number;
  clock: string;
  feedStatus: string;
  screen: Screen;
  onScreenChange: (screen: Screen) => void;
}) {
  return (
    <header className="z-30 h-[52px] shrink-0 border-b border-[#262d38] bg-[#0d1117]/95 backdrop-blur">
      <div className="mx-auto flex h-full w-full max-w-[1500px] items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
        <div className="flex min-w-0 items-center gap-4">
          <div className="flex items-center gap-2">
            <div className="grid h-7 w-7 place-items-center border border-[#58a6ff]/60 bg-[#101923] font-mono text-[10px] font-bold text-[#58a6ff]">
              FX
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold tracking-wide text-[#e6edf3]">
                DemoFX
              </div>
              <div className="font-mono text-[9px] text-[#768390]">DEMO</div>
            </div>
          </div>
          <nav className="flex items-center gap-1 rounded-[6px] border border-[#262d38] bg-[#161b22] p-1">
            <HeaderTab active={screen === "monitor"} label="Monitor" onClick={() => onScreenChange("monitor")} />
            <HeaderTab active={screen === "trading"} label="Trading" onClick={() => onScreenChange("trading")} />
          </nav>
        </div>

        <div className="flex shrink-0 items-center gap-3 font-mono text-[11px]">
          <span className={feedStatus === "LIVE" ? "text-[#3fb950]" : "text-[#d29922]"}>
            {feedStatus}
          </span>
          <span className="hidden text-[#768390] sm:inline">JST {clock}</span>
          <span className={activeAlerts > 0 ? "text-[#f85149]" : "text-[#768390]"}>
            ALERTS {activeAlerts}
          </span>
        </div>
      </div>
    </header>
  );
}

function HeaderTab({
  active,
  label,
  onClick,
}: {
  active: boolean;
  label: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-[5px] px-3 py-1.5 text-xs font-medium transition-colors ${
        active
          ? "bg-[#21272f] text-[#e6edf3]"
          : "text-[#768390] hover:bg-[#1a2129] hover:text-[#e6edf3]"
      }`}
    >
      {label}
    </button>
  );
}

function StatusStrip({
  activeAlerts,
  connected,
  lastUpdated,
  pairCount,
}: {
  activeAlerts: number;
  connected: boolean;
  lastUpdated: Date | null;
  pairCount: number;
}) {
  return (
    <section className="shrink-0 border-b border-[#262d38] bg-[#10151b]">
      <div className="mx-auto grid w-full max-w-[1500px] grid-cols-2 px-4 sm:px-6 md:grid-cols-5 lg:px-8">
        <StatusItem label="Backend connection" value={connected ? "Connected" : "Disconnected"} tone={connected ? "positive" : "negative"} />
        <StatusItem label="Last updated" value={lastUpdated ? formatTime(lastUpdated.toISOString()) : "--:--:--"} />
        <StatusItem label="Active pairs" value={String(pairCount)} />
        <StatusItem label="Tick interval" value="5s" />
        <StatusItem label="Rate update" value="1s" badge={activeAlerts > 0 ? `${activeAlerts} alerts` : undefined} />
      </div>
    </section>
  );
}

function MonitorScreen({
  activeAlerts,
  activePair,
  alerts,
  monitoredRates,
  newsEvents,
  newsSubmittingDirection,
  rateChanges,
  ratesLoading,
  recentTicks,
  spreadStats,
  spreadStatsError,
  spreadStatsLoading,
  ticks,
  ticksLoading,
  onSelectPair,
  onTriggerNews,
}: {
  activeAlerts: number;
  activePair: string;
  alerts: MarketAlert[];
  monitoredRates: MarketRate[];
  newsEvents: NewsEvent[];
  newsSubmittingDirection: NewsDirection | null;
  rateChanges: Record<string, number>;
  ratesLoading: boolean;
  recentTicks: MarketRateTick[];
  spreadStats?: SpreadStats;
  spreadStatsError: string | null;
  spreadStatsLoading: boolean;
  ticks: MarketRateTick[];
  ticksLoading: boolean;
  onSelectPair: (currencyPair: string) => void;
  onTriggerNews: (direction: NewsDirection) => void;
}) {
  return (
    <div className="grid min-h-0 flex-1 gap-3 overflow-hidden xl:grid-cols-[372px_minmax(0,1fr)_360px]">
      <section className="flex min-h-[420px] flex-col overflow-hidden border border-[#262d38] bg-[#161b22] xl:h-full xl:min-h-0">
        <PanelHeader title="Rate board" meta={`${monitoredRates.length} pairs`} />
        <div className="min-h-0 flex-1 overflow-y-auto">
          {ratesLoading && monitoredRates.length === 0 ? (
            <LoadingPanel label="Loading rates..." compact />
          ) : monitoredRates.length === 0 ? (
            <EmptyPanel label="No rates" compact />
          ) : (
            <div className="divide-y divide-[#202832]">
              {monitoredRates.map((rate) => (
                <RateBoardRow
                  key={rate.currencyPair}
                  change={rateChanges[rate.currencyPair] ?? 0}
                  rate={rate}
                  selected={rate.currencyPair === activePair}
                  onSelect={onSelectPair}
                />
              ))}
            </div>
          )}
        </div>
      </section>

      <div className="flex min-w-0 flex-col gap-4 xl:min-h-0">
        <section className="flex min-w-0 flex-col border border-[#262d38] bg-[#161b22] xl:min-h-0 xl:flex-1">
          <PanelHeader title={`${activePair} Bid / Ask / Mid`} meta={`${ticks.length} ticks`} />
          {ticksLoading && ticks.length === 0 ? (
            <LoadingPanel label={`Loading ${activePair} ticks...`} />
          ) : ticks.length === 0 ? (
            <EmptyPanel label="No tick history" />
          ) : (
            <div className="min-h-0 flex-1 p-3">
              <MarketRateChart currencyPair={activePair} ticks={ticks} />
            </div>
          )}
        </section>

        <TickLogPanel activePair={activePair} ticks={recentTicks} loading={ticksLoading} />
      </div>

      <aside className="flex min-w-0 flex-col gap-3 xl:h-full xl:min-h-0 xl:overflow-hidden">
        <SpreadMonitorCard
          currencyPair={activePair}
          error={spreadStatsError}
          loading={spreadStatsLoading}
          stats={spreadStats}
        />
        <NewsEventPanel
          activePair={activePair}
          events={newsEvents}
          submittingDirection={newsSubmittingDirection}
          onTrigger={onTriggerNews}
        />
        <AlertPanel alerts={alerts} activeCount={activeAlerts} />
      </aside>
    </div>
  );
}

function TradingScreen({
  activePair,
  orderError,
  orderQuantity,
  orders,
  positions,
  rates,
  selectedRate,
  submittingOrderSide,
  lastOrderMessage,
  trades,
  onQuantityChange,
  onSelectPair,
  onSubmitOrder,
}: {
  activePair: string;
  orderError: string | null;
  orderQuantity: string;
  orders: OrderSummary[];
  positions: PositionSummary[];
  rates: MarketRate[];
  selectedRate?: MarketRate;
  submittingOrderSide: OrderSide | null;
  lastOrderMessage: string | null;
  trades: TradeSummary[];
  onQuantityChange: (quantity: string) => void;
  onSelectPair: (currencyPair: string) => void;
  onSubmitOrder: (side: OrderSide) => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="flex flex-col gap-4">
      <AccountSummaryBand />

      <div className="grid gap-4 xl:grid-cols-[430px_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <PriceReferencePanel
            activePair={activePair}
            rate={selectedRate}
            rates={rates}
            onSelectPair={onSelectPair}
          />
          <MarketOrderPanel
            activePair={activePair}
            error={orderError}
            lastMessage={lastOrderMessage}
            orderQuantity={orderQuantity}
            rate={selectedRate}
            submittingSide={submittingOrderSide}
            onQuantityChange={onQuantityChange}
            onSubmit={onSubmitOrder}
          />
        </div>

        <div className="grid min-w-0 gap-4 2xl:grid-cols-2">
          <ExecutionHistoryPanel trades={trades} onSelectPair={onSelectPair} />
          <OrderHistoryPanel orders={orders} />
          <PositionsTable positions={positions} />
          <RiskPlaceholder />
        </div>
      </div>
      </div>
    </div>
  );
}

function RateBoardRow({
  change,
  rate,
  selected,
  onSelect,
}: {
  change: number;
  rate: MarketRate;
  selected: boolean;
  onSelect: (currencyPair: string) => void;
}) {
  const priceScale = getPriceScale(rate.currencyPair);
  const direction = change > 0 ? "up" : change < 0 ? "down" : "flat";
  const directionClass =
    direction === "up"
      ? "text-[#3fb950]"
      : direction === "down"
        ? "text-[#f85149]"
        : "text-[#768390]";

  return (
    <button
      type="button"
      onClick={() => onSelect(rate.currencyPair)}
      className={`grid w-full grid-cols-[92px_1fr_72px] items-center gap-3 border-l-2 px-3 py-3 text-left transition-colors ${
        selected
          ? "border-l-[#58a6ff] bg-[#101923]"
          : "border-l-transparent hover:bg-[#1b222b]"
      }`}
      aria-pressed={selected}
    >
      <div className="min-w-0">
        <div className="font-mono text-sm font-semibold text-[#e6edf3]">{rate.currencyPair}</div>
        <div className={`mt-1 font-mono text-[11px] ${directionClass}`}>
          {direction === "up" ? "▲" : direction === "down" ? "▼" : "-"} {formatSignedChange(change, priceScale)}
        </div>
      </div>
      <div className="grid grid-cols-3 gap-2 font-mono text-[11px]">
        <MiniRate label="Bid" value={rate.bid} scale={priceScale} tone="sell" />
        <MiniRate label="Ask" value={rate.ask} scale={priceScale} tone="buy" />
        <MiniRate label="Mid" value={rate.midPrice} scale={priceScale} />
      </div>
      <div className="text-right font-mono text-[11px] text-[#d29922]">
        <div>{rate.spread.toFixed(priceScale)}</div>
        <div className="mt-1 text-[9px] text-[#768390]">{formatTime(rate.quotedAt)}</div>
      </div>
    </button>
  );
}

function MiniRate({
  label,
  value,
  scale,
  tone,
}: {
  label: string;
  value: number;
  scale: number;
  tone?: "buy" | "sell";
}) {
  const color = tone === "buy" ? "text-[#4493f8]" : tone === "sell" ? "text-[#f85149]" : "text-[#e6edf3]";
  return (
    <div className="min-w-0">
      <div className="text-[9px] uppercase text-[#768390]">{label}</div>
      <div className={`truncate ${color}`}>{value.toFixed(scale)}</div>
    </div>
  );
}

function PriceReferencePanel({
  activePair,
  onSelectPair,
  rate,
  rates,
}: {
  activePair: string;
  onSelectPair: (currencyPair: string) => void;
  rate?: MarketRate;
  rates: MarketRate[];
}) {
  const scale = getPriceScale(activePair);
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <div className="flex min-h-11 items-center justify-between gap-3 border-b border-[#262d38] bg-[#161b22] px-4 py-3">
        <h2 className="min-w-0 truncate font-mono text-sm font-semibold text-[#e6edf3]">
          Price reference
        </h2>
        <label className="flex shrink-0 items-center gap-2">
          <span className="text-[10px] uppercase text-[#768390]">Pair</span>
          <select
            value={activePair}
            onChange={(event) => onSelectPair(event.target.value)}
            className="border border-[#262d38] bg-[#0d1117] px-2 py-1 font-mono text-[11px] text-[#e6edf3] outline-none hover:border-[#58a6ff] focus:border-[#58a6ff]"
          >
            {rates.length === 0 && <option value={activePair}>{activePair}</option>}
            {rates.map((marketRate) => (
              <option key={marketRate.currencyPair} value={marketRate.currencyPair}>
                {marketRate.currencyPair}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="grid grid-cols-2 gap-px bg-[#262d38]">
        <ExecutionPrice label="SELL uses Bid" value={rate?.bid} scale={scale} tone="sell" />
        <ExecutionPrice label="BUY uses Ask" value={rate?.ask} scale={scale} tone="buy" />
        <ExecutionPrice label="Mid" value={rate?.midPrice} scale={scale} />
        <ExecutionPrice label="Spread cost" value={rate?.spread} scale={scale} tone="spread" />
      </div>
      <div className="px-4 py-3 font-mono text-[11px] text-[#768390]">
        {rate ? `quoted ${formatTime(rate.quotedAt)}` : "waiting for price"}
      </div>
    </section>
  );
}

function MarketOrderPanel({
  activePair,
  error,
  lastMessage,
  orderQuantity,
  rate,
  submittingSide,
  onQuantityChange,
  onSubmit,
}: {
  activePair: string;
  error: string | null;
  lastMessage: string | null;
  orderQuantity: string;
  rate?: MarketRate;
  submittingSide: OrderSide | null;
  onQuantityChange: (quantity: string) => void;
  onSubmit: (side: OrderSide) => void;
}) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Market order" meta={activePair} />
      <div className="space-y-4 px-4 py-4">
        <label className="block">
          <span className="text-[10px] uppercase text-[#768390]">Units</span>
          <input
            value={orderQuantity}
            onChange={(event) => onQuantityChange(event.target.value)}
            inputMode="decimal"
            className="mt-2 w-full border border-[#262d38] bg-[#0d1117] px-3 py-2 font-mono text-sm text-[#e6edf3] outline-none focus:border-[#58a6ff]"
          />
        </label>
        <div className="grid grid-cols-3 gap-2">
          {["1000", "10000", "100000"].map((quantity) => (
            <button
              key={quantity}
              type="button"
              onClick={() => onQuantityChange(quantity)}
              className="border border-[#262d38] px-2 py-2 font-mono text-xs text-[#adbac7] hover:bg-[#21272f]"
            >
              {formatQuantity(Number(quantity))}
            </button>
          ))}
        </div>
        <div className="grid grid-cols-2 gap-2">
          <OrderButton
            disabled={submittingSide !== null || !rate}
            loading={submittingSide === "SELL"}
            price={rate?.bid}
            side="SELL"
            currencyPair={activePair}
            onSubmit={onSubmit}
          />
          <OrderButton
            disabled={submittingSide !== null || !rate}
            loading={submittingSide === "BUY"}
            price={rate?.ask}
            side="BUY"
            currencyPair={activePair}
            onSubmit={onSubmit}
          />
        </div>
        {error && <div className="border border-[#f85149]/40 bg-[#2a1215] px-3 py-2 font-mono text-[11px] text-[#f0a8a4]">{error}</div>}
        {lastMessage && <div className="border border-[#3fb950]/40 bg-[#102218] px-3 py-2 font-mono text-[11px] text-[#7ee787]">{lastMessage}</div>}
      </div>
    </section>
  );
}

function OrderButton({
  currencyPair,
  disabled,
  loading,
  price,
  side,
  onSubmit,
}: {
  currencyPair: string;
  disabled: boolean;
  loading: boolean;
  price?: number;
  side: OrderSide;
  onSubmit: (side: OrderSide) => void;
}) {
  const tone =
    side === "BUY"
      ? "border-[#4493f8]/60 bg-[#0f1b2b] text-[#79c0ff] hover:bg-[#132a44]"
      : "border-[#f85149]/60 bg-[#221114] text-[#ff9a92] hover:bg-[#34171b]";
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onSubmit(side)}
      className={`border px-3 py-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${tone}`}
    >
      <div className="font-mono text-sm font-semibold">{loading ? "Sending..." : side}</div>
      <div className="mt-1 font-mono text-[11px] opacity-80">
        {price === undefined ? "--" : formatPrice(price, currencyPair)}
      </div>
    </button>
  );
}

function ExecutionPrice({
  label,
  value,
  scale,
  tone,
}: {
  label: string;
  value?: number;
  scale: number;
  tone?: "buy" | "sell" | "spread";
}) {
  const color =
    tone === "buy"
      ? "text-[#4493f8]"
      : tone === "sell"
        ? "text-[#f85149]"
        : tone === "spread"
          ? "text-[#d29922]"
          : "text-[#e6edf3]";
  return (
    <div className="bg-[#161b22] px-4 py-4">
      <div className="text-[10px] uppercase text-[#768390]">{label}</div>
      <div className={`mt-1 font-mono text-lg font-semibold ${color}`}>
        {value === undefined ? "--" : value.toFixed(scale)}
      </div>
    </div>
  );
}

function TickLogPanel({
  activePair,
  loading,
  ticks,
}: {
  activePair: string;
  loading: boolean;
  ticks: MarketRateTick[];
}) {
  return (
    <section className="h-[188px] min-w-0 max-w-full overflow-hidden border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Tick log" meta={activePair} compact />
      {loading && ticks.length === 0 ? (
        <LoadingPanel label="Loading ticks..." compact />
      ) : ticks.length === 0 ? (
        <EmptyPanel label="No ticks" compact />
      ) : (
        <div className="h-[145px] min-w-0 overflow-y-auto">
          <table className="w-full table-fixed font-mono text-[10px] sm:text-[11px]">
            <thead className="sticky top-0 bg-[#161b22] text-left text-[9px] uppercase text-[#768390]">
              <tr className="border-b border-[#262d38]">
                <th className="w-[22%] px-2 py-2 font-medium sm:px-3">Time</th>
                <th className="w-[19.5%] px-1 py-2 text-right font-medium sm:px-3">Bid</th>
                <th className="w-[19.5%] px-1 py-2 text-right font-medium sm:px-3">Ask</th>
                <th className="w-[19.5%] px-1 py-2 text-right font-medium sm:px-3">Mid</th>
                <th className="w-[19.5%] px-2 py-2 text-right font-medium sm:px-3">Spread</th>
              </tr>
            </thead>
            <tbody>
              {ticks.map((tick, index) => (
                <TickRow
                  key={`${tick.quotedAt}-${tick.bid}-${tick.ask}-${tick.midPrice}-${tick.spread}-${index}`}
                  tick={tick}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function TickRow({ tick }: { tick: MarketRateTick }) {
  const scale = getPriceScale(tick.currencyPair);
  return (
    <tr className="border-b border-[#202832] text-[#adbac7] last:border-0 hover:bg-white/[0.025]">
      <td className="truncate px-2 py-2 text-[#768390] sm:px-3">{formatTime(tick.quotedAt)}</td>
      <td className="truncate px-1 py-2 text-right text-[#f85149] sm:px-3">{tick.bid.toFixed(scale)}</td>
      <td className="truncate px-1 py-2 text-right text-[#4493f8] sm:px-3">{tick.ask.toFixed(scale)}</td>
      <td className="truncate px-1 py-2 text-right text-[#3fb950] sm:px-3">{tick.midPrice.toFixed(scale)}</td>
      <td className="truncate px-2 py-2 text-right text-[#d29922] sm:px-3">{tick.spread.toFixed(scale)}</td>
    </tr>
  );
}

function AlertPanel({
  activeCount,
  alerts,
}: {
  activeCount: number;
  alerts: MarketAlert[];
}) {
  return (
    <section className="flex min-h-[180px] flex-1 flex-col overflow-hidden border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Alerts" meta={`active ${activeCount}`} />
      <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto px-3 py-2">
        {alerts.length === 0 ? (
          <div className="grid min-h-full place-items-center">
            <CompactEmpty label="No alerts" />
          </div>
        ) : (
          alerts.map((alert) => <AlertRow key={alert.id} alert={alert} />)
        )}
      </div>
    </section>
  );
}

function AlertRow({ alert }: { alert: MarketAlert }) {
  return (
    <div className={`border px-3 py-2 ${alert.active ? "border-[#f85149]/40 bg-[#2a1215]" : "border-[#262d38] bg-[#0d1117]"}`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="truncate font-mono text-xs text-[#e6edf3]">
            {alert.currencyPair} / {formatAlertType(alert.type)}
          </div>
          <div className="mt-1 line-clamp-1 text-xs text-[#768390]">{alert.message}</div>
        </div>
        <span className={`shrink-0 font-mono text-[10px] ${getAlertSeverityClass(alert.severity)}`}>
          {alert.severity}
        </span>
      </div>
      <div className="mt-1 flex items-center justify-between font-mono text-[10px] text-[#768390]">
        <span>{formatTime(alert.raisedAt)}</span>
        <span>{alert.active ? "ACTIVE" : "RESOLVED"}</span>
      </div>
    </div>
  );
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
    <section className="flex min-h-[174px] max-h-[260px] flex-col overflow-hidden border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Fictional news" meta={activePair} />
      <div className="grid flex-none grid-cols-2 gap-px bg-[#262d38]">
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
      <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto px-3 py-2">
        {events.length === 0 ? (
          <div className="grid min-h-full place-items-center">
            <CompactEmpty label="No news events" />
          </div>
        ) : (
          events.map((event) => <NewsEventRow key={event.id} event={event} />)
        )}
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
      ? "text-[#3fb950] hover:bg-[#102218]"
      : "text-[#f85149] hover:bg-[#2a1215]";

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => onTrigger(direction)}
      className={`flex min-h-[54px] flex-col justify-center bg-[#161b22] px-4 py-2 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${directionClass}`}
    >
      <div className="font-mono text-xs font-semibold">{loading ? "Sending..." : direction}</div>
      <div className="mt-1 text-[10px] uppercase text-[#768390]">demo shock</div>
    </button>
  );
}

function NewsEventRow({ event }: { event: NewsEvent }) {
  const directionClass = event.direction === "UP" ? "text-[#3fb950]" : "text-[#f85149]";
  return (
    <div className="border border-[#262d38] bg-[#0d1117] px-3 py-2">
      <div className="flex items-center justify-between gap-3">
        <span className="font-mono text-xs text-[#e6edf3]">{event.currencyPair}</span>
        <span className={`font-mono text-[11px] ${directionClass}`}>
          {event.direction} {event.magnitudeBps}bps
        </span>
      </div>
      <div className="mt-1 line-clamp-1 text-xs text-[#768390]">{event.headline}</div>
      <div className="mt-1 flex items-center justify-between font-mono text-[10px] text-[#768390]">
        <span>{formatTime(event.startedAt)}</span>
        <span className={event.active ? "text-[#d29922]" : "text-[#768390]"}>
          {event.active ? "ACTIVE" : "ENDED"}
        </span>
      </div>
    </div>
  );
}

function AccountSummaryBand() {
  return (
    <section className="grid gap-px overflow-hidden border border-[#262d38] bg-[#262d38] md:grid-cols-4">
      <AccountMetric label="Account" value="DEMO-0001" />
      <AccountMetric label="Equity" value="Coming soon" muted />
      <AccountMetric label="Margin level" value="Coming soon" muted />
      <AccountMetric label="Free margin" value="Coming soon" muted />
    </section>
  );
}

function AccountMetric({
  label,
  muted,
  value,
}: {
  label: string;
  muted?: boolean;
  value: string;
}) {
  return (
    <div className="bg-[#161b22] px-4 py-3">
      <div className="text-[10px] uppercase text-[#768390]">{label}</div>
      <div className={`mt-1 font-mono text-sm font-semibold ${muted ? "text-[#768390]" : "text-[#e6edf3]"}`}>
        {value}
      </div>
    </div>
  );
}

function ExecutionHistoryPanel({
  trades,
  onSelectPair,
}: {
  trades: TradeSummary[];
  onSelectPair: (currencyPair: string) => void;
}) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Execution history" meta={`${trades.length} fills`} />
      <div className="max-h-[330px] overflow-y-auto">
        {trades.length === 0 ? (
          <EmptyPanel label="No fills" compact />
        ) : (
          trades.slice(0, 16).map((trade) => (
            <TradeRow key={trade.id} trade={trade} onSelectPair={onSelectPair} />
          ))
        )}
      </div>
    </section>
  );
}

function TradeRow({
  trade,
  onSelectPair,
}: {
  trade: TradeSummary;
  onSelectPair: (currencyPair: string) => void;
}) {
  const sideClass = trade.side === "BUY" ? "text-[#4493f8]" : "text-[#f85149]";
  return (
    <button
      type="button"
      onClick={() => onSelectPair(trade.currencyPair)}
      className="grid w-full grid-cols-[80px_1fr_78px_92px] gap-3 border-b border-[#202832] px-3 py-3 text-left font-mono text-[11px] hover:bg-[#1b222b]"
    >
      <span className="text-[#768390]">{formatTime(trade.executedAt)}</span>
      <span className="text-[#e6edf3]">{trade.currencyPair}</span>
      <span className={sideClass}>{trade.side}</span>
      <span className="text-right text-[#adbac7]">{formatPrice(trade.price, trade.currencyPair)}</span>
    </button>
  );
}

function OrderHistoryPanel({ orders }: { orders: OrderSummary[] }) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Order history" meta={`${orders.length} orders`} />
      <div className="max-h-[330px] overflow-y-auto">
        {orders.length === 0 ? (
          <EmptyPanel label="No orders" compact />
        ) : (
          orders.slice(0, 16).map((order) => <OrderRow key={order.id} order={order} />)
        )}
      </div>
    </section>
  );
}

function OrderRow({ order }: { order: OrderSummary }) {
  const sideClass = order.side === "BUY" ? "text-[#4493f8]" : "text-[#f85149]";
  return (
    <div className="grid grid-cols-[80px_1fr_78px_92px] gap-3 border-b border-[#202832] px-3 py-3 font-mono text-[11px]">
      <span className="text-[#768390]">{formatTime(order.requestedAt)}</span>
      <span className="text-[#e6edf3]">{order.currencyPair}</span>
      <span className={sideClass}>{order.side}</span>
      <span className="text-right text-[#adbac7]">{order.status}</span>
    </div>
  );
}

function PositionsTable({ positions }: { positions: PositionSummary[] }) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Positions" meta={`${positions.length} open`} />
      <div className="grid grid-cols-6 border-b border-[#262d38] px-3 py-2 font-mono text-[10px] uppercase text-[#768390]">
        <span>Pair</span>
        <span>Side</span>
        <span className="text-right">Units</span>
        <span className="text-right">Avg Price</span>
        <span className="text-right">Current</span>
        <span className="text-right">P&L</span>
      </div>
      <div className="max-h-[260px] overflow-y-auto">
        {positions.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-[#768390]">No open positions</div>
        ) : (
          positions.map((position) => <PositionRow key={position.currencyPair} position={position} />)
        )}
      </div>
    </section>
  );
}

function PositionRow({ position }: { position: PositionSummary }) {
  const sideClass = position.side === "LONG" ? "text-[#4493f8]" : "text-[#f85149]";
  return (
    <div className="grid grid-cols-6 gap-2 border-b border-[#202832] px-3 py-3 font-mono text-[11px] last:border-b-0">
      <span className="text-[#e6edf3]">{position.currencyPair}</span>
      <span className={sideClass}>{position.side}</span>
      <span className="text-right text-[#adbac7]">{formatQuantity(position.quantity)}</span>
      <span className="text-right text-[#adbac7]">
        {formatPrice(position.averagePrice, position.currencyPair)}
      </span>
      <span className="text-right text-[#768390]">--</span>
      <span className="text-right text-[#768390]">--</span>
    </div>
  );
}

function RiskPlaceholder() {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="P&L / Margin" meta="Coming soon" />
      <div className="grid gap-px bg-[#262d38] md:grid-cols-3">
        <AccountMetric label="Unrealized P&L" value="Coming soon" muted />
        <AccountMetric label="Realized P&L" value="Coming soon" muted />
        <AccountMetric label="Loss cut" value="50%" />
      </div>
      <div className="px-4 py-6">
        <div className="h-2 bg-[#0d1117]">
          <div className="h-full w-1/3 bg-[#58a6ff]" />
        </div>
      </div>
    </section>
  );
}

function ConnectionIssue({
  message,
  onRetry,
}: {
  message: string;
  onRetry: () => void;
}) {
  return (
    <div className="flex flex-col gap-3 border border-[#f85149]/50 bg-[#2a1215] px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <div className="text-sm font-semibold text-[#ff9a92]">Market data connection issue</div>
        <div className="mt-1 break-all font-mono text-xs text-[#f0a8a4]">{message}</div>
      </div>
      <button
        type="button"
        onClick={onRetry}
        className="border border-[#f85149]/60 px-4 py-2 text-sm font-medium text-[#ff9a92] hover:bg-[#f85149]/10"
      >
        Retry
      </button>
    </div>
  );
}

function PanelHeader({
  compact,
  meta,
  title,
}: {
  compact?: boolean;
  meta?: string;
  title: string;
}) {
  return (
    <div className={`flex min-h-11 items-center justify-between gap-3 border-b border-[#262d38] bg-[#161b22] px-4 ${compact ? "py-2" : "py-3"}`}>
      <h2 className="min-w-0 truncate font-mono text-sm font-semibold text-[#e6edf3]">{title}</h2>
      {meta && <span className="shrink-0 font-mono text-[10px] uppercase text-[#768390]">{meta}</span>}
    </div>
  );
}

function StatusItem({
  badge,
  label,
  tone,
  value,
}: {
  badge?: string;
  label: string;
  tone?: "positive" | "negative";
  value: string;
}) {
  const valueClass =
    tone === "positive" ? "text-[#3fb950]" : tone === "negative" ? "text-[#f85149]" : "text-[#e6edf3]";
  return (
    <div className="border-r border-[#262d38] px-3 py-3 last:border-r-0 md:px-5">
      <div className="text-[10px] uppercase text-[#768390]">{label}</div>
      <div className={`mt-1 font-mono text-sm font-semibold ${valueClass}`}>{value}</div>
      {badge && <div className="mt-1 font-mono text-[9px] text-[#d29922]">{badge}</div>}
    </div>
  );
}

function LoadingPanel({
  compact,
  label,
}: {
  compact?: boolean;
  label: string;
}) {
  return (
    <div className={`grid place-items-center font-mono text-sm text-[#768390] ${compact ? "min-h-28" : "min-h-[470px]"}`}>
      {label}
    </div>
  );
}

function EmptyPanel({
  compact,
  label,
}: {
  compact?: boolean;
  label: string;
}) {
  return (
    <div className={`grid place-items-center text-sm text-[#768390] ${compact ? "min-h-24" : "min-h-[470px]"}`}>
      {label}
    </div>
  );
}

function CompactEmpty({ label }: { label: string }) {
  return (
    <div className="grid min-h-12 place-items-center text-sm text-[#768390]">
      {label}
    </div>
  );
}

function getAlertSeverityClass(severity: AlertSeverity): string {
  switch (severity) {
    case "CRITICAL":
      return "text-[#f85149]";
    case "WARNING":
      return "text-[#d29922]";
    case "INFO":
      return "text-[#58a6ff]";
  }
}

function getPriceScale(currencyPair: string): number {
  return currencyPair.endsWith("/JPY") ? 3 : 5;
}

function formatAlertType(type: string): string {
  return type.replaceAll("_", " ");
}

function formatPrice(value: number, currencyPair: string): string {
  return value.toFixed(getPriceScale(currencyPair));
}

function formatQuantity(value: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 4 }).format(value);
}

function formatSignedChange(value: number, scale: number): string {
  if (value === 0) {
    return (0).toFixed(scale);
  }
  return `${value > 0 ? "+" : ""}${value.toFixed(scale)}`;
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

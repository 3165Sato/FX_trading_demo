"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  cancelPositionExitOrder,
  cancelPositionOcoOrder,
  cancelPendingOrder,
  closePosition,
  fetchAccountSummary,
  fetchLatestMarketRates,
  fetchMarketAlerts,
  fetchMarketRateTicks,
  fetchOrders,
  fetchPendingOrders,
  fetchPnlSummary,
  fetchPositions,
  fetchTrades,
  fetchNewsEvents,
  getSpreadStats,
  placePositionExitOrder,
  placePositionOcoOrder,
  placeIfdOrder,
  placeIfoOrder,
  placePendingOrder,
  placeMarketOrder,
  triggerNewsEvent,
  type AccountSummary,
  type AlertSeverity,
  type ExitOrderType,
  type MarketAlert,
  type MarketRate,
  type MarketRateTick,
  type NewsDirection,
  type NewsEvent,
  type OrderSide,
  type OrderSummary,
  type OrderType,
  type PendingOrder,
  type PnlSummary,
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
  const [pendingOrders, setPendingOrders] = useState<PendingOrder[]>([]);
  const [positions, setPositions] = useState<PositionSummary[]>([]);
  const [pnlSummary, setPnlSummary] = useState<PnlSummary | null>(null);
  const [accountSummary, setAccountSummary] = useState<AccountSummary | null>(null);
  const [newsEvents, setNewsEvents] = useState<NewsEvent[]>([]);
  const [spreadStats, setSpreadStats] = useState<SpreadStats | undefined>();
  const [monitorSelectedPair, setMonitorSelectedPair] = useState(DEFAULT_PAIR);
  const [tradingSelectedPair, setTradingSelectedPair] = useState(DEFAULT_PAIR);
  const [orderType, setOrderType] = useState<OrderType>("MARKET");
  const [orderQuantity, setOrderQuantity] = useState("10000");
  const [triggerPrice, setTriggerPrice] = useState("");
  const [ifdExitType, setIfdExitType] = useState<ExitOrderType>("TP");
  const [ifdExitPrice, setIfdExitPrice] = useState("");
  const [ifoTakeProfitPrice, setIfoTakeProfitPrice] = useState("");
  const [ifoStopLossPrice, setIfoStopLossPrice] = useState("");
  const [rateChanges, setRateChanges] = useState<Record<string, number>>({});
  const [ratesLoading, setRatesLoading] = useState(true);
  const [ticksLoading, setTicksLoading] = useState(true);
  const [spreadStatsLoading, setSpreadStatsLoading] = useState(true);
  const [ratesError, setRatesError] = useState<string | null>(null);
  const [ticksError, setTicksError] = useState<string | null>(null);
  const [alertsError, setAlertsError] = useState<string | null>(null);
  const [tradesError, setTradesError] = useState<string | null>(null);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [pendingOrdersError, setPendingOrdersError] = useState<string | null>(null);
  const [positionsError, setPositionsError] = useState<string | null>(null);
  const [pnlSummaryError, setPnlSummaryError] = useState<string | null>(null);
  const [accountSummaryError, setAccountSummaryError] = useState<string | null>(null);
  const [orderError, setOrderError] = useState<string | null>(null);
  const [newsEventsError, setNewsEventsError] = useState<string | null>(null);
  const [spreadStatsError, setSpreadStatsError] = useState<string | null>(null);
  const [lastOrderMessage, setLastOrderMessage] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [clock, setClock] = useState("--:--:--");
  const [nowMs, setNowMs] = useState(0);
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [submittingOrderSide, setSubmittingOrderSide] = useState<OrderSide | null>(null);
  const [submittingIfdSide, setSubmittingIfdSide] = useState<OrderSide | null>(null);
  const [submittingIfoSide, setSubmittingIfoSide] = useState<OrderSide | null>(null);
  const [cancelingPendingOrderId, setCancelingPendingOrderId] = useState<number | null>(null);
  const [closingPositionId, setClosingPositionId] = useState<number | null>(null);
  const [exitOrderDrafts, setExitOrderDrafts] = useState<Record<number, Partial<Record<ExitOrderType, string>>>>({});
  const [submittingExitOrder, setSubmittingExitOrder] = useState<{ positionId: number; type: ExitOrderType } | null>(null);
  const [cancelingExitOrderId, setCancelingExitOrderId] = useState<number | null>(null);
  const [submittingOcoPositionId, setSubmittingOcoPositionId] = useState<number | null>(null);
  const [cancelingOcoGroupId, setCancelingOcoGroupId] = useState<string | null>(null);
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

  const loadPendingOrders = useCallback(async () => {
    try {
      const nextPendingOrders = await fetchPendingOrders("PENDING", undefined, ORDER_LIMIT);
      setPendingOrders(nextPendingOrders);
      setPendingOrdersError(null);
    } catch (error) {
      setPendingOrdersError(getErrorMessage(error));
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

  const loadPnlSummary = useCallback(async () => {
    try {
      const nextSummary = await fetchPnlSummary();
      setPnlSummary(nextSummary);
      setPnlSummaryError(null);
    } catch (error) {
      setPnlSummaryError(getErrorMessage(error));
    }
  }, []);

  const loadAccountSummary = useCallback(async () => {
    try {
      const nextSummary = await fetchAccountSummary();
      setAccountSummary(nextSummary);
      setAccountSummaryError(null);
    } catch (error) {
      setAccountSummaryError(getErrorMessage(error));
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
      void loadPendingOrders();
      void loadPositions();
      void loadPnlSummary();
      void loadAccountSummary();
    };
    const initialTimeoutId = window.setTimeout(loadTradeData, 0);
    const intervalId = window.setInterval(loadTradeData, 5000);
    return () => {
      window.clearTimeout(initialTimeoutId);
      window.clearInterval(intervalId);
    };
  }, [loadAccountSummary, loadOrders, loadPendingOrders, loadPnlSummary, loadPositions, loadTrades]);

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
    pendingOrdersError ??
    positionsError ??
    pnlSummaryError ??
    accountSummaryError ??
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
    void loadPendingOrders();
    void loadPositions();
    void loadPnlSummary();
    void loadAccountSummary();
    void loadNewsEvents();
  };

  const submitMarketOrder = async (side: OrderSide) => {
    const quantity = Number(orderQuantity);
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setOrderError("Quantity must be greater than zero.");
      return;
    }
    const nextTriggerPrice = Number(triggerPrice);
    if (orderType !== "MARKET" && (!Number.isFinite(nextTriggerPrice) || nextTriggerPrice <= 0)) {
      setOrderError("Trigger price must be greater than zero.");
      return;
    }

    setSubmittingOrderSide(side);
    try {
      if (orderType === "MARKET") {
        const result = await placeMarketOrder(tradingActivePair, side, quantity);
        setLastOrderMessage(
          `${result.trade.side} ${formatQuantity(result.trade.quantity)} ${result.trade.currencyPair} @ ${formatPrice(result.trade.price, result.trade.currencyPair)}`,
        );
        setTrades((current) => [result.trade, ...current].slice(0, TRADE_LIMIT));
        setOrders((current) => [result.order, ...current].slice(0, ORDER_LIMIT));
      } else {
        const pendingOrder = await placePendingOrder(
          tradingActivePair,
          side,
          orderType,
          quantity,
          nextTriggerPrice,
        );
        setLastOrderMessage(
          `${pendingOrder.orderType} ${pendingOrder.side} ${formatQuantity(pendingOrder.quantity)} ${pendingOrder.currencyPair} trigger ${formatPrice(pendingOrder.triggerPrice, pendingOrder.currencyPair)}`,
        );
        setPendingOrders((current) => [pendingOrder, ...current].slice(0, ORDER_LIMIT));
      }
      setOrderError(null);
      void loadTrades();
      void loadOrders();
      void loadPendingOrders();
      void loadPositions();
      void loadPnlSummary();
      void loadAccountSummary();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingOrderSide(null);
    }
  };

  const submitIfdOrder = async (side: OrderSide) => {
    const quantity = Number(orderQuantity);
    const entryPrice = Number(triggerPrice);
    const exitPrice = Number(ifdExitPrice);
    if (orderType === "MARKET") {
      setOrderError("IFD entry must be LIMIT or STOP.");
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setOrderError("Quantity must be greater than zero.");
      return;
    }
    if (!Number.isFinite(entryPrice) || entryPrice <= 0) {
      setOrderError("Entry trigger price must be greater than zero.");
      return;
    }
    if (!Number.isFinite(exitPrice) || exitPrice <= 0) {
      setOrderError("IFD exit price must be greater than zero.");
      return;
    }

    setSubmittingIfdSide(side);
    try {
      const result = await placeIfdOrder(
        tradingActivePair,
        side,
        orderType,
        quantity,
        entryPrice,
        ifdExitType,
        exitPrice,
      );
      setLastOrderMessage(
        `IFD ${result.entry.orderType} ${result.entry.side} ${formatQuantity(result.entry.quantity)} ${result.entry.currencyPair} -> ${ifdExitType} ${formatPrice(exitPrice, result.entry.currencyPair)}`,
      );
      setPendingOrders((current) => [result.entry, result.exit, ...current].slice(0, ORDER_LIMIT));
      setOrderError(null);
      void loadPendingOrders();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingIfdSide(null);
    }
  };

  const submitIfoOrder = async (side: OrderSide) => {
    const quantity = Number(orderQuantity);
    const entryPrice = Number(triggerPrice);
    const tpPrice = Number(ifoTakeProfitPrice);
    const slPrice = Number(ifoStopLossPrice);
    if (orderType === "MARKET") {
      setOrderError("IFO entry must be LIMIT or STOP.");
      return;
    }
    if (!Number.isFinite(quantity) || quantity <= 0) {
      setOrderError("Quantity must be greater than zero.");
      return;
    }
    if (!Number.isFinite(entryPrice) || entryPrice <= 0) {
      setOrderError("Entry trigger price must be greater than zero.");
      return;
    }
    if (!Number.isFinite(tpPrice) || tpPrice <= 0 || !Number.isFinite(slPrice) || slPrice <= 0) {
      setOrderError("IFO TP and SL prices must be greater than zero.");
      return;
    }

    setSubmittingIfoSide(side);
    try {
      const result = await placeIfoOrder(
        tradingActivePair,
        side,
        orderType,
        quantity,
        entryPrice,
        tpPrice,
        slPrice,
      );
      setLastOrderMessage(
        `IFO ${result.entry.orderType} ${result.entry.side} ${formatQuantity(result.entry.quantity)} ${result.entry.currencyPair} -> OCO TP ${formatPrice(tpPrice, result.entry.currencyPair)} / SL ${formatPrice(slPrice, result.entry.currencyPair)}`,
      );
      setPendingOrders((current) => [result.entry, ...result.exits, ...current].slice(0, ORDER_LIMIT));
      setOrderError(null);
      void loadPendingOrders();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingIfoSide(null);
    }
  };

  const cancelSelectedPendingOrder = async (id: number) => {
    setCancelingPendingOrderId(id);
    try {
      await cancelPendingOrder(id);
      setPendingOrders((current) => current.filter((order) => order.id !== id));
      setOrderError(null);
      void loadPendingOrders();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setCancelingPendingOrderId(null);
    }
  };

  const closeSelectedPosition = async (id: number) => {
    setClosingPositionId(id);
    try {
      const result = await closePosition(id);
      setLastOrderMessage(
        `CLOSE ${formatQuantity(result.quantity)} ${result.currencyPair} @ ${formatPrice(result.closePrice, result.currencyPair)}`,
      );
      setPositions((current) => current.filter((position) => position.id !== id));
      setTrades((current) => [result.execution.trade, ...current].slice(0, TRADE_LIMIT));
      setOrders((current) => [result.execution.order, ...current].slice(0, ORDER_LIMIT));
      setOrderError(null);
      void loadTrades();
      void loadOrders();
      void loadPositions();
      void loadPnlSummary();
      void loadAccountSummary();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setClosingPositionId(null);
    }
  };

  const updateExitOrderDraft = (positionId: number, type: ExitOrderType, value: string) => {
    setExitOrderDrafts((current) => ({
      ...current,
      [positionId]: {
        ...current[positionId],
        [type]: value,
      },
    }));
  };

  const submitPositionExitOrder = async (position: PositionSummary, type: ExitOrderType) => {
    const triggerPrice = Number(exitOrderDrafts[position.id]?.[type]);
    if (!Number.isFinite(triggerPrice) || triggerPrice <= 0) {
      setOrderError(`${type} price must be greater than zero.`);
      return;
    }

    setSubmittingExitOrder({ positionId: position.id, type });
    try {
      const exitOrder = await placePositionExitOrder(position.id, type, triggerPrice);
      setLastOrderMessage(
        `${type} set for #${position.id} ${position.currencyPair} @ ${formatPrice(exitOrder.triggerPrice, position.currencyPair)}`,
      );
      setExitOrderDrafts((current) => ({
        ...current,
        [position.id]: {
          ...current[position.id],
          [type]: "",
        },
      }));
      setOrderError(null);
      void loadPositions();
      void loadPendingOrders();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingExitOrder(null);
    }
  };

  const cancelSelectedExitOrder = async (positionId: number, exitOrderId: number) => {
    setCancelingExitOrderId(exitOrderId);
    try {
      await cancelPositionExitOrder(positionId, exitOrderId);
      setOrderError(null);
      void loadPositions();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setCancelingExitOrderId(null);
    }
  };

  const submitPositionOcoOrder = async (position: PositionSummary) => {
    const tpPrice = Number(exitOrderDrafts[position.id]?.TP);
    const slPrice = Number(exitOrderDrafts[position.id]?.SL);
    if (!Number.isFinite(tpPrice) || tpPrice <= 0 || !Number.isFinite(slPrice) || slPrice <= 0) {
      setOrderError("OCO requires both TP and SL prices greater than zero.");
      return;
    }

    setSubmittingOcoPositionId(position.id);
    try {
      const ocoOrder = await placePositionOcoOrder(position.id, tpPrice, slPrice);
      setLastOrderMessage(
        `OCO set for #${position.id} ${position.currencyPair}: TP ${formatPrice(tpPrice, position.currencyPair)} / SL ${formatPrice(slPrice, position.currencyPair)}`,
      );
      setExitOrderDrafts((current) => ({
        ...current,
        [position.id]: {
          TP: "",
          SL: "",
        },
      }));
      setOrderError(null);
      void ocoOrder;
      void loadPositions();
      void loadPendingOrders();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setSubmittingOcoPositionId(null);
    }
  };

  const cancelSelectedOcoOrder = async (positionId: number, groupId: string) => {
    setCancelingOcoGroupId(groupId);
    try {
      await cancelPositionOcoOrder(positionId, groupId);
      setOrderError(null);
      void loadPositions();
    } catch (error) {
      setOrderError(getErrorMessage(error));
    } finally {
      setCancelingOcoGroupId(null);
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
            accountSummary={accountSummary}
            activePair={tradingActivePair}
            cancelingExitOrderId={cancelingExitOrderId}
            cancelingOcoGroupId={cancelingOcoGroupId}
            cancelingPendingOrderId={cancelingPendingOrderId}
            exitOrderDrafts={exitOrderDrafts}
            ifdExitPrice={ifdExitPrice}
            ifdExitType={ifdExitType}
            ifoStopLossPrice={ifoStopLossPrice}
            ifoTakeProfitPrice={ifoTakeProfitPrice}
            orderError={orderError}
            orderQuantity={orderQuantity}
            orderType={orderType}
            orders={orders}
            pendingOrders={pendingOrders}
            positions={positions}
            pnlSummary={pnlSummary}
            rates={monitoredRates}
            selectedRate={selectedRate}
            submittingOrderSide={submittingOrderSide}
            lastOrderMessage={lastOrderMessage}
            trades={trades}
            triggerPrice={triggerPrice}
            submittingExitOrder={submittingExitOrder}
            submittingIfdSide={submittingIfdSide}
            submittingIfoSide={submittingIfoSide}
            submittingOcoPositionId={submittingOcoPositionId}
            onCancelPendingOrder={cancelSelectedPendingOrder}
            onCancelExitOrder={cancelSelectedExitOrder}
            onCancelOcoOrder={cancelSelectedOcoOrder}
            onClosePosition={closeSelectedPosition}
            onExitOrderDraftChange={updateExitOrderDraft}
            onIfdExitPriceChange={setIfdExitPrice}
            onIfdExitTypeChange={setIfdExitType}
            onIfoStopLossPriceChange={setIfoStopLossPrice}
            onIfoTakeProfitPriceChange={setIfoTakeProfitPrice}
            onQuantityChange={setOrderQuantity}
            onOrderTypeChange={setOrderType}
            onSelectPair={selectTradingCurrencyPair}
            onSubmitOrder={submitMarketOrder}
            onSubmitExitOrder={submitPositionExitOrder}
            onSubmitIfdOrder={submitIfdOrder}
            onSubmitIfoOrder={submitIfoOrder}
            onSubmitOcoOrder={submitPositionOcoOrder}
            onTriggerPriceChange={setTriggerPrice}
            closingPositionId={closingPositionId}
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
  accountSummary,
  activePair,
  cancelingExitOrderId,
  cancelingOcoGroupId,
  cancelingPendingOrderId,
  closingPositionId,
  exitOrderDrafts,
  ifdExitPrice,
  ifdExitType,
  ifoStopLossPrice,
  ifoTakeProfitPrice,
  orderError,
  orderQuantity,
  orderType,
  orders,
  pendingOrders,
  positions,
  pnlSummary,
  rates,
  selectedRate,
  submittingOrderSide,
  lastOrderMessage,
  trades,
  triggerPrice,
  submittingExitOrder,
  submittingIfdSide,
  submittingIfoSide,
  submittingOcoPositionId,
  onCancelExitOrder,
  onCancelOcoOrder,
  onCancelPendingOrder,
  onClosePosition,
  onExitOrderDraftChange,
  onIfdExitPriceChange,
  onIfdExitTypeChange,
  onIfoStopLossPriceChange,
  onIfoTakeProfitPriceChange,
  onQuantityChange,
  onOrderTypeChange,
  onSelectPair,
  onSubmitOrder,
  onSubmitExitOrder,
  onSubmitIfdOrder,
  onSubmitIfoOrder,
  onSubmitOcoOrder,
  onTriggerPriceChange,
}: {
  accountSummary: AccountSummary | null;
  activePair: string;
  cancelingExitOrderId: number | null;
  cancelingOcoGroupId: string | null;
  cancelingPendingOrderId: number | null;
  closingPositionId: number | null;
  exitOrderDrafts: Record<number, Partial<Record<ExitOrderType, string>>>;
  ifdExitPrice: string;
  ifdExitType: ExitOrderType;
  ifoStopLossPrice: string;
  ifoTakeProfitPrice: string;
  orderError: string | null;
  orderQuantity: string;
  orderType: OrderType;
  orders: OrderSummary[];
  pendingOrders: PendingOrder[];
  positions: PositionSummary[];
  pnlSummary: PnlSummary | null;
  rates: MarketRate[];
  selectedRate?: MarketRate;
  submittingOrderSide: OrderSide | null;
  lastOrderMessage: string | null;
  trades: TradeSummary[];
  triggerPrice: string;
  submittingExitOrder: { positionId: number; type: ExitOrderType } | null;
  submittingIfdSide: OrderSide | null;
  submittingIfoSide: OrderSide | null;
  submittingOcoPositionId: number | null;
  onCancelExitOrder: (positionId: number, exitOrderId: number) => void;
  onCancelOcoOrder: (positionId: number, groupId: string) => void;
  onCancelPendingOrder: (id: number) => void;
  onClosePosition: (id: number) => void;
  onExitOrderDraftChange: (positionId: number, type: ExitOrderType, value: string) => void;
  onIfdExitPriceChange: (price: string) => void;
  onIfdExitTypeChange: (type: ExitOrderType) => void;
  onIfoStopLossPriceChange: (price: string) => void;
  onIfoTakeProfitPriceChange: (price: string) => void;
  onQuantityChange: (quantity: string) => void;
  onOrderTypeChange: (orderType: OrderType) => void;
  onSelectPair: (currencyPair: string) => void;
  onSubmitOrder: (side: OrderSide) => void;
  onSubmitExitOrder: (position: PositionSummary, type: ExitOrderType) => void;
  onSubmitIfdOrder: (side: OrderSide) => void;
  onSubmitIfoOrder: (side: OrderSide) => void;
  onSubmitOcoOrder: (position: PositionSummary) => void;
  onTriggerPriceChange: (triggerPrice: string) => void;
}) {
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="flex flex-col gap-4">
      <AccountSummaryBand summary={accountSummary} />

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
            ifdExitPrice={ifdExitPrice}
            ifdExitType={ifdExitType}
            ifoStopLossPrice={ifoStopLossPrice}
            ifoTakeProfitPrice={ifoTakeProfitPrice}
            lastMessage={lastOrderMessage}
            orderQuantity={orderQuantity}
            orderType={orderType}
            rate={selectedRate}
            submittingIfdSide={submittingIfdSide}
            submittingIfoSide={submittingIfoSide}
            submittingSide={submittingOrderSide}
            triggerPrice={triggerPrice}
            onIfdExitPriceChange={onIfdExitPriceChange}
            onIfdExitTypeChange={onIfdExitTypeChange}
            onIfoStopLossPriceChange={onIfoStopLossPriceChange}
            onIfoTakeProfitPriceChange={onIfoTakeProfitPriceChange}
            onQuantityChange={onQuantityChange}
            onOrderTypeChange={onOrderTypeChange}
            onSubmit={onSubmitOrder}
            onSubmitIfd={onSubmitIfdOrder}
            onSubmitIfo={onSubmitIfoOrder}
            onTriggerPriceChange={onTriggerPriceChange}
          />
        </div>

        <div className="grid min-w-0 gap-4 2xl:grid-cols-2">
          <ExecutionHistoryPanel trades={trades} onSelectPair={onSelectPair} />
          <OrderHistoryPanel orders={orders} />
          <PendingOrdersPanel
            cancelingOrderId={cancelingPendingOrderId}
            orders={pendingOrders}
            onCancel={onCancelPendingOrder}
          />
          <PositionsTable
            cancelingExitOrderId={cancelingExitOrderId}
            cancelingOcoGroupId={cancelingOcoGroupId}
            closingPositionId={closingPositionId}
            exitOrderDrafts={exitOrderDrafts}
            positions={positions}
            submittingExitOrder={submittingExitOrder}
            submittingOcoPositionId={submittingOcoPositionId}
            onCancelExitOrder={onCancelExitOrder}
            onCancelOcoOrder={onCancelOcoOrder}
            onClose={onClosePosition}
            onExitOrderDraftChange={onExitOrderDraftChange}
            onSubmitExitOrder={onSubmitExitOrder}
            onSubmitOcoOrder={onSubmitOcoOrder}
          />
          <PnlSummaryPanel accountSummary={accountSummary} summary={pnlSummary} />
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
  ifdExitPrice,
  ifdExitType,
  ifoStopLossPrice,
  ifoTakeProfitPrice,
  lastMessage,
  orderQuantity,
  orderType,
  rate,
  submittingIfdSide,
  submittingIfoSide,
  submittingSide,
  triggerPrice,
  onIfdExitPriceChange,
  onIfdExitTypeChange,
  onIfoStopLossPriceChange,
  onIfoTakeProfitPriceChange,
  onQuantityChange,
  onOrderTypeChange,
  onSubmit,
  onSubmitIfd,
  onSubmitIfo,
  onTriggerPriceChange,
}: {
  activePair: string;
  error: string | null;
  ifdExitPrice: string;
  ifdExitType: ExitOrderType;
  ifoStopLossPrice: string;
  ifoTakeProfitPrice: string;
  lastMessage: string | null;
  orderQuantity: string;
  orderType: OrderType;
  rate?: MarketRate;
  submittingIfdSide: OrderSide | null;
  submittingIfoSide: OrderSide | null;
  submittingSide: OrderSide | null;
  triggerPrice: string;
  onIfdExitPriceChange: (price: string) => void;
  onIfdExitTypeChange: (type: ExitOrderType) => void;
  onIfoStopLossPriceChange: (price: string) => void;
  onIfoTakeProfitPriceChange: (price: string) => void;
  onQuantityChange: (quantity: string) => void;
  onOrderTypeChange: (orderType: OrderType) => void;
  onSubmit: (side: OrderSide) => void;
  onSubmitIfd: (side: OrderSide) => void;
  onSubmitIfo: (side: OrderSide) => void;
  onTriggerPriceChange: (triggerPrice: string) => void;
}) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Order ticket" meta={activePair} />
      <div className="space-y-4 px-4 py-4">
        <div>
          <span className="text-[10px] uppercase text-[#768390]">Type</span>
          <div className="mt-2 grid grid-cols-3 gap-1 border border-[#262d38] bg-[#0d1117] p-1">
            {(["MARKET", "LIMIT", "STOP"] as OrderType[]).map((type) => (
              <button
                key={type}
                type="button"
                onClick={() => onOrderTypeChange(type)}
                className={`px-2 py-2 font-mono text-[11px] transition-colors ${
                  orderType === type
                    ? "bg-[#21272f] text-[#e6edf3]"
                    : "text-[#768390] hover:bg-[#161b22] hover:text-[#e6edf3]"
                }`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>
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
        {orderType !== "MARKET" && (
          <label className="block">
            <span className="text-[10px] uppercase text-[#768390]">Trigger price</span>
            <input
              value={triggerPrice}
              onChange={(event) => onTriggerPriceChange(event.target.value)}
              inputMode="decimal"
              placeholder={rate ? formatPrice(rate.midPrice, activePair) : undefined}
              className="mt-2 w-full border border-[#262d38] bg-[#0d1117] px-3 py-2 font-mono text-sm text-[#e6edf3] outline-none focus:border-[#58a6ff]"
            />
            <div className="mt-2 font-mono text-[10px] leading-5 text-[#768390]">
              {orderType === "LIMIT"
                ? `BUY limit below Ask${rate ? ` ${formatPrice(rate.ask, activePair)}` : ""}; SELL limit above Bid${rate ? ` ${formatPrice(rate.bid, activePair)}` : ""}.`
                : `BUY stop above Ask${rate ? ` ${formatPrice(rate.ask, activePair)}` : ""}; SELL stop below Bid${rate ? ` ${formatPrice(rate.bid, activePair)}` : ""}.`}
            </div>
          </label>
        )}
        {orderType !== "MARKET" && (
          <div className="border border-[#262d38] bg-[#0d1117] p-3">
            <div className="mb-3 flex items-center justify-between gap-3">
              <span className="text-[10px] uppercase text-[#768390]">IFD exit leg</span>
              <div className="grid grid-cols-2 gap-1 border border-[#262d38] bg-[#161b22] p-1">
                {(["TP", "SL"] as ExitOrderType[]).map((type) => (
                  <button
                    key={type}
                    type="button"
                    onClick={() => onIfdExitTypeChange(type)}
                    className={`px-2 py-1 font-mono text-[10px] transition-colors ${
                      ifdExitType === type
                        ? "bg-[#21272f] text-[#e6edf3]"
                        : "text-[#768390] hover:bg-[#0d1117] hover:text-[#e6edf3]"
                    }`}
                  >
                    {type}
                  </button>
                ))}
              </div>
            </div>
            <input
              value={ifdExitPrice}
              onChange={(event) => onIfdExitPriceChange(event.target.value)}
              inputMode="decimal"
              placeholder={`${ifdExitType} price`}
              className="w-full border border-[#262d38] bg-[#0d1117] px-3 py-2 font-mono text-sm text-[#e6edf3] outline-none focus:border-[#58a6ff]"
            />
            <div className="mt-2 font-mono text-[10px] leading-5 text-[#768390]">
              IFD binds this {ifdExitType} after the entry fills. Direction is checked at fill time.
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <OrderButton
                disabled={submittingIfdSide !== null || submittingSide !== null || !rate}
                loading={submittingIfdSide === "SELL"}
                price={rate?.bid}
                side="SELL"
                currencyPair={activePair}
                labelPrefix="IFD"
                onSubmit={onSubmitIfd}
              />
              <OrderButton
                disabled={submittingIfdSide !== null || submittingSide !== null || !rate}
                loading={submittingIfdSide === "BUY"}
                price={rate?.ask}
                side="BUY"
                currencyPair={activePair}
                labelPrefix="IFD"
                onSubmit={onSubmitIfd}
              />
            </div>
          </div>
        )}
        {orderType !== "MARKET" && (
          <div className="border border-[#262d38] bg-[#0d1117] p-3">
            <div className="mb-3">
              <span className="text-[10px] uppercase text-[#768390]">IFO OCO exit pair</span>
              <div className="mt-1 font-mono text-[10px] leading-5 text-[#768390]">
                Entry fill creates TP and SL as one OCO group. If either direction is invalid at fill time, both expire.
              </div>
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <label className="block">
                <span className="text-[10px] uppercase text-[#768390]">TP price</span>
                <input
                  value={ifoTakeProfitPrice}
                  onChange={(event) => onIfoTakeProfitPriceChange(event.target.value)}
                  inputMode="decimal"
                  className="mt-2 w-full border border-[#262d38] bg-[#0d1117] px-3 py-2 font-mono text-sm text-[#e6edf3] outline-none focus:border-[#58a6ff]"
                />
              </label>
              <label className="block">
                <span className="text-[10px] uppercase text-[#768390]">SL price</span>
                <input
                  value={ifoStopLossPrice}
                  onChange={(event) => onIfoStopLossPriceChange(event.target.value)}
                  inputMode="decimal"
                  className="mt-2 w-full border border-[#262d38] bg-[#0d1117] px-3 py-2 font-mono text-sm text-[#e6edf3] outline-none focus:border-[#58a6ff]"
                />
              </label>
            </div>
            <div className="mt-3 grid grid-cols-2 gap-2">
              <OrderButton
                disabled={submittingIfoSide !== null || submittingIfdSide !== null || submittingSide !== null || !rate}
                loading={submittingIfoSide === "SELL"}
                price={rate?.bid}
                side="SELL"
                currencyPair={activePair}
                labelPrefix="IFO"
                onSubmit={onSubmitIfo}
              />
              <OrderButton
                disabled={submittingIfoSide !== null || submittingIfdSide !== null || submittingSide !== null || !rate}
                loading={submittingIfoSide === "BUY"}
                price={rate?.ask}
                side="BUY"
                currencyPair={activePair}
                labelPrefix="IFO"
                onSubmit={onSubmitIfo}
              />
            </div>
          </div>
        )}
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
  labelPrefix,
  loading,
  price,
  side,
  onSubmit,
}: {
  currencyPair: string;
  disabled: boolean;
  labelPrefix?: string;
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
      <div className="font-mono text-sm font-semibold">
        {loading ? "Sending..." : `${labelPrefix ? `${labelPrefix} ` : ""}${side}`}
      </div>
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

function AccountSummaryBand({ summary }: { summary: AccountSummary | null }) {
  return (
    <section className="grid gap-px overflow-hidden border border-[#262d38] bg-[#262d38] md:grid-cols-5">
      <AccountMetric label="Account" value={summary?.accountId ?? "DEMO-ACCOUNT-001"} />
      <AccountMetric label="Balance" value={formatOptionalJpy(summary?.balance)} />
      <AccountMetric
        label="Equity"
        tone={pnlTone(summary?.unrealizedPnl ?? null)}
        value={formatOptionalJpy(summary?.equity)}
      />
      <AccountMetric
        label="Margin level"
        tone={marginStatusTone(summary?.status)}
        value={formatOptionalPercent(summary?.marginRatio)}
      />
      <AccountMetric label="Free margin" value={formatOptionalJpy(summary?.freeMargin)} />
    </section>
  );
}

function AccountMetric({
  label,
  tone,
  value,
}: {
  label: string;
  tone?: "muted" | "positive" | "negative" | "warning";
  value: string;
}) {
  const valueClass = metricToneClass(tone);
  return (
    <div className="bg-[#161b22] px-4 py-3">
      <div className="text-[10px] uppercase text-[#768390]">{label}</div>
      <div className={`mt-1 font-mono text-sm font-semibold ${valueClass}`}>
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
  const statusLabel =
    order.source === "LOSS_CUT" ? "LOSS_CUT" : order.source === "TRIGGER" ? "TRIGGER" : order.status;
  const statusClass =
    order.source === "LOSS_CUT"
      ? "text-[#f85149]"
      : order.source === "TRIGGER"
        ? "text-[#d29922]"
        : "text-[#adbac7]";
  return (
    <div className="grid grid-cols-[80px_1fr_78px_92px] gap-3 border-b border-[#202832] px-3 py-3 font-mono text-[11px]">
      <span className="text-[#768390]">{formatTime(order.requestedAt)}</span>
      <span className="text-[#e6edf3]">{order.currencyPair}</span>
      <span className={sideClass}>{order.side}</span>
      <span className={`text-right ${statusClass}`}>{statusLabel}</span>
    </div>
  );
}

function PendingOrdersPanel({
  cancelingOrderId,
  orders,
  onCancel,
}: {
  cancelingOrderId: number | null;
  orders: PendingOrder[];
  onCancel: (id: number) => void;
}) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Pending orders" meta={`${orders.length} waiting`} />
      <div className="grid grid-cols-[76px_62px_62px_1fr_72px] border-b border-[#262d38] px-3 py-2 font-mono text-[10px] uppercase text-[#768390]">
        <span>Pair</span>
        <span>Type</span>
        <span>Side</span>
        <span className="text-right">Trigger</span>
        <span className="text-right">Action</span>
      </div>
      <div className="max-h-[260px] overflow-y-auto">
        {orders.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-[#768390]">No pending orders</div>
        ) : (
          orders.map((order) => (
            <PendingOrderRow
              key={order.id}
              canceling={cancelingOrderId === order.id}
              order={order}
              onCancel={onCancel}
            />
          ))
        )}
      </div>
    </section>
  );
}

function PendingOrderRow({
  canceling,
  order,
  onCancel,
}: {
  canceling: boolean;
  order: PendingOrder;
  onCancel: (id: number) => void;
}) {
  const sideClass = order.side === "BUY" ? "text-[#4493f8]" : "text-[#f85149]";
  const typeLabel = order.exitType ? `${order.exitType}` : order.orderType;
  const pairLabel = order.parentOrderId ? `${order.ocoGroupId ? "IFO" : "IFD"} #${order.parentOrderId}` : order.currencyPair;
  return (
    <div className="grid grid-cols-[76px_62px_62px_1fr_72px] items-center gap-2 border-b border-[#202832] px-3 py-3 font-mono text-[11px] last:border-b-0">
      <span className="truncate text-[#e6edf3]">{pairLabel}</span>
      <span className="text-[#d29922]">{typeLabel}</span>
      <span className={sideClass}>{order.side}</span>
      <span className="text-right text-[#adbac7]">{formatPrice(order.triggerPrice, order.currencyPair)}</span>
      <button
        type="button"
        disabled={canceling}
        onClick={() => onCancel(order.id)}
        className="border border-[#262d38] px-2 py-1 text-[10px] text-[#adbac7] hover:bg-[#21272f] disabled:cursor-not-allowed disabled:opacity-50"
      >
        {canceling ? "..." : "Cancel"}
      </button>
    </div>
  );
}

function PositionsTable({
  cancelingExitOrderId,
  cancelingOcoGroupId,
  closingPositionId,
  exitOrderDrafts,
  positions,
  submittingExitOrder,
  submittingOcoPositionId,
  onCancelExitOrder,
  onCancelOcoOrder,
  onClose,
  onExitOrderDraftChange,
  onSubmitExitOrder,
  onSubmitOcoOrder,
}: {
  cancelingExitOrderId: number | null;
  cancelingOcoGroupId: string | null;
  closingPositionId: number | null;
  exitOrderDrafts: Record<number, Partial<Record<ExitOrderType, string>>>;
  positions: PositionSummary[];
  submittingExitOrder: { positionId: number; type: ExitOrderType } | null;
  submittingOcoPositionId: number | null;
  onCancelExitOrder: (positionId: number, exitOrderId: number) => void;
  onCancelOcoOrder: (positionId: number, groupId: string) => void;
  onClose: (id: number) => void;
  onExitOrderDraftChange: (positionId: number, type: ExitOrderType, value: string) => void;
  onSubmitExitOrder: (position: PositionSummary, type: ExitOrderType) => void;
  onSubmitOcoOrder: (position: PositionSummary) => void;
}) {
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="Positions" meta={`${positions.length} open`} />
      <div className="grid grid-cols-[44px_72px_58px_1fr_1fr_1fr_1fr_1fr_168px_74px] gap-2 border-b border-[#262d38] px-3 py-2 font-mono text-[10px] uppercase text-[#768390]">
        <span>ID</span>
        <span>Pair</span>
        <span>Side</span>
        <span className="text-right">Units</span>
        <span className="text-right">Open</span>
        <span className="text-right">Current</span>
        <span className="text-right">P&L</span>
        <span className="text-right">Margin</span>
        <span className="text-center">TP / SL</span>
        <span className="text-right">Action</span>
      </div>
      <div className="max-h-[260px] overflow-y-auto">
        {positions.length === 0 ? (
          <div className="px-4 py-12 text-center text-sm text-[#768390]">No open positions</div>
        ) : (
          positions.map((position) => (
            <PositionRow
              key={position.id}
              cancelingExitOrderId={cancelingExitOrderId}
              cancelingOcoGroupId={cancelingOcoGroupId}
              closing={closingPositionId === position.id}
              drafts={exitOrderDrafts[position.id] ?? {}}
              position={position}
              submittingExitOrder={submittingExitOrder}
              submittingOcoPositionId={submittingOcoPositionId}
              onCancelExitOrder={onCancelExitOrder}
              onCancelOcoOrder={onCancelOcoOrder}
              onClose={onClose}
              onExitOrderDraftChange={onExitOrderDraftChange}
              onSubmitExitOrder={onSubmitExitOrder}
              onSubmitOcoOrder={onSubmitOcoOrder}
            />
          ))
        )}
      </div>
    </section>
  );
}

function PositionRow({
  cancelingExitOrderId,
  cancelingOcoGroupId,
  closing,
  drafts,
  position,
  submittingExitOrder,
  submittingOcoPositionId,
  onCancelExitOrder,
  onCancelOcoOrder,
  onClose,
  onExitOrderDraftChange,
  onSubmitExitOrder,
  onSubmitOcoOrder,
}: {
  cancelingExitOrderId: number | null;
  cancelingOcoGroupId: string | null;
  closing: boolean;
  drafts: Partial<Record<ExitOrderType, string>>;
  position: PositionSummary;
  submittingExitOrder: { positionId: number; type: ExitOrderType } | null;
  submittingOcoPositionId: number | null;
  onCancelExitOrder: (positionId: number, exitOrderId: number) => void;
  onCancelOcoOrder: (positionId: number, groupId: string) => void;
  onClose: (id: number) => void;
  onExitOrderDraftChange: (positionId: number, type: ExitOrderType, value: string) => void;
  onSubmitExitOrder: (position: PositionSummary, type: ExitOrderType) => void;
  onSubmitOcoOrder: (position: PositionSummary) => void;
}) {
  const sideClass = position.side === "LONG" ? "text-[#4493f8]" : "text-[#f85149]";
  const pnlClass = pnlToneClass(position.unrealizedPnl);
  const tpOrder = findPositionExitOrder(position, "TP");
  const slOrder = findPositionExitOrder(position, "SL");
  const ocoGroupId = tpOrder?.ocoGroupId ?? slOrder?.ocoGroupId ?? null;
  const canSubmitOco = !tpOrder && !slOrder;
  return (
    <div className="grid grid-cols-[44px_72px_58px_1fr_1fr_1fr_1fr_1fr_168px_74px] items-center gap-2 border-b border-[#202832] px-3 py-3 font-mono text-[11px] last:border-b-0">
      <span className="text-[#768390]">#{position.id}</span>
      <span className="text-[#e6edf3]">{position.currencyPair}</span>
      <span className={sideClass}>{position.side}</span>
      <span className="text-right text-[#adbac7]">{formatQuantity(position.quantity)}</span>
      <span className="text-right text-[#adbac7]">
        {formatPrice(position.averagePrice, position.currencyPair)}
      </span>
      <span className="text-right text-[#adbac7]">
        {position.currentPrice === null ? "--" : formatPrice(position.currentPrice, position.currencyPair)}
      </span>
      <span className={`text-right ${pnlClass}`}>
        {position.unrealizedPnl === null
          ? "--"
          : formatCurrencyAmount(position.quoteCurrency, position.unrealizedPnl)}
      </span>
      <span className="text-right text-[#adbac7]">{formatOptionalJpy(position.requiredMargin)}</span>
      <div className="grid gap-1">
        <ExitOrderControl
          canceling={cancelingExitOrderId === tpOrder?.id}
          cancelingOco={ocoGroupId !== null && cancelingOcoGroupId === ocoGroupId}
          draftValue={drafts.TP ?? ""}
          order={tpOrder}
          position={position}
          submitting={submittingExitOrder?.positionId === position.id && submittingExitOrder.type === "TP"}
          type="TP"
          onCancel={onCancelExitOrder}
          onCancelOco={onCancelOcoOrder}
          onChange={onExitOrderDraftChange}
          onSubmit={onSubmitExitOrder}
        />
        <ExitOrderControl
          canceling={cancelingExitOrderId === slOrder?.id}
          cancelingOco={ocoGroupId !== null && cancelingOcoGroupId === ocoGroupId}
          draftValue={drafts.SL ?? ""}
          order={slOrder}
          position={position}
          submitting={submittingExitOrder?.positionId === position.id && submittingExitOrder.type === "SL"}
          type="SL"
          onCancel={onCancelExitOrder}
          onCancelOco={onCancelOcoOrder}
          onChange={onExitOrderDraftChange}
          onSubmit={onSubmitExitOrder}
        />
        {canSubmitOco ? (
          <button
            type="button"
            disabled={submittingOcoPositionId === position.id}
            onClick={() => onSubmitOcoOrder(position)}
            className="border border-[#d29922]/60 px-1.5 py-0.5 text-[9px] uppercase text-[#d29922] hover:bg-[#d29922]/10 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submittingOcoPositionId === position.id ? "Setting" : "Set OCO"}
          </button>
        ) : ocoGroupId ? (
          <div className="text-center text-[9px] uppercase text-[#d29922]">OCO</div>
        ) : null}
      </div>
      <span className="text-right">
        <button
          type="button"
          className="border border-[#f85149]/70 px-2 py-1 text-[10px] uppercase text-[#f85149] transition-colors hover:bg-[#f85149]/10 disabled:cursor-not-allowed disabled:opacity-50"
          disabled={closing}
          onClick={() => onClose(position.id)}
        >
          {closing ? "Closing" : "Close"}
        </button>
      </span>
    </div>
  );
}

function ExitOrderControl({
  canceling,
  cancelingOco,
  draftValue,
  order,
  position,
  submitting,
  type,
  onCancel,
  onCancelOco,
  onChange,
  onSubmit,
}: {
  canceling: boolean;
  cancelingOco: boolean;
  draftValue: string;
  order?: PositionSummary["exitOrders"][number];
  position: PositionSummary;
  submitting: boolean;
  type: ExitOrderType;
  onCancel: (positionId: number, exitOrderId: number) => void;
  onCancelOco: (positionId: number, groupId: string) => void;
  onChange: (positionId: number, type: ExitOrderType, value: string) => void;
  onSubmit: (position: PositionSummary, type: ExitOrderType) => void;
}) {
  const labelClass = type === "TP" ? "text-[#3fb950]" : "text-[#f85149]";

  if (order) {
    return (
      <div className="grid grid-cols-[22px_minmax(0,1fr)_42px] items-center gap-1">
        <span className={`text-[10px] ${labelClass}`}>{type}</span>
        <span className="truncate text-right text-[#adbac7]">
          {formatPrice(order.triggerPrice, position.currencyPair)}
        </span>
        <button
          type="button"
          disabled={canceling || cancelingOco}
          onClick={() =>
            order.ocoGroupId
              ? onCancelOco(position.id, order.ocoGroupId)
              : onCancel(position.id, order.id)
          }
          className="border border-[#262d38] px-1.5 py-0.5 text-[9px] uppercase text-[#768390] hover:bg-[#21272f] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {canceling || cancelingOco ? "..." : "Off"}
        </button>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-[22px_minmax(0,1fr)_42px] items-center gap-1">
      <span className={`text-[10px] ${labelClass}`}>{type}</span>
      <input
        aria-label={`${type} price for position ${position.id}`}
        value={draftValue}
        onChange={(event) => onChange(position.id, type, event.target.value)}
        placeholder={exitOrderPlaceholder(position, type)}
        className="min-w-0 border border-[#262d38] bg-[#0d1117] px-1.5 py-0.5 text-right text-[10px] text-[#adbac7] outline-none focus:border-[#58a6ff]"
      />
      <button
        type="button"
        disabled={submitting}
        onClick={() => onSubmit(position, type)}
        className="border border-[#58a6ff]/50 px-1.5 py-0.5 text-[9px] uppercase text-[#58a6ff] hover:bg-[#58a6ff]/10 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitting ? "..." : "Set"}
      </button>
    </div>
  );
}

function findPositionExitOrder(position: PositionSummary, type: ExitOrderType) {
  return position.exitOrders.find(
    (order) =>
      order.type === type &&
      (order.status === "PENDING" || order.status === "WAITING"),
  );
}

function exitOrderPlaceholder(position: PositionSummary, type: ExitOrderType) {
  const currentPrice = position.currentPrice ?? position.averagePrice;
  if (position.side === "LONG") {
    return type === "TP"
      ? `>${formatPrice(currentPrice, position.currencyPair)}`
      : `<${formatPrice(currentPrice, position.currencyPair)}`;
  }
  return type === "TP"
    ? `<${formatPrice(currentPrice, position.currencyPair)}`
    : `>${formatPrice(currentPrice, position.currencyPair)}`;
}

function PnlSummaryPanel({
  accountSummary,
  summary,
}: {
  accountSummary: AccountSummary | null;
  summary: PnlSummary | null;
}) {
  const unrealizedRows = formatCurrencyMap(summary?.unrealizedByCurrency);
  const realizedRows = formatCurrencyMap(summary?.realizedByCurrency);
  return (
    <section className="border border-[#262d38] bg-[#161b22]">
      <PanelHeader title="P&L / Margin" meta={accountSummary?.status ?? "SAFE"} />
      <div className="grid gap-px bg-[#262d38] md:grid-cols-3">
        <PnlMetric label="Unrealized P&L" rows={unrealizedRows} />
        <PnlMetric label="Realized P&L" rows={realizedRows} />
        <AccountMetric label="Used margin" value={formatOptionalJpy(accountSummary?.usedMargin)} />
      </div>
      <MarginGauge summary={accountSummary} />
    </section>
  );
}

function MarginGauge({ summary }: { summary: AccountSummary | null }) {
  const ratio = summary?.marginRatio ?? null;
  const lossCut = summary?.lossCutThreshold ?? 50;
  const gaugeMax = 200;
  const ratioWidth = ratio === null ? 0 : Math.min(100, Math.max(0, (ratio / gaugeMax) * 100));
  const lossCutLeft = Math.min(100, Math.max(0, (lossCut / gaugeMax) * 100));
  const gaugeClass = marginStatusBarClass(summary?.status);

  return (
    <div className="px-4 py-5">
      <div className="mb-3 flex items-center justify-between gap-4">
        <div>
          <div className="text-[10px] uppercase text-[#768390]">Margin ratio</div>
          <div className={`mt-1 font-mono text-lg font-semibold ${metricToneClass(marginStatusTone(summary?.status))}`}>
            {formatOptionalPercent(ratio)}
          </div>
        </div>
        <div className="text-right font-mono text-[11px] text-[#768390]">
          <div>Loss cut {formatOptionalPercent(lossCut)}</div>
          <div>Free {formatOptionalJpy(summary?.freeMargin)}</div>
        </div>
      </div>
      <div className="relative h-3 overflow-hidden bg-[#0d1117]">
        <div className={`h-full ${gaugeClass}`} style={{ width: `${ratioWidth}%` }} />
        <div
          className="absolute top-0 h-full w-px bg-[#f85149]"
          style={{ left: `${lossCutLeft}%` }}
          aria-hidden="true"
        />
      </div>
      <div className="mt-2 flex justify-between font-mono text-[10px] text-[#768390]">
        <span>0%</span>
        <span>50%</span>
        <span>100%</span>
        <span>200%+</span>
      </div>
    </div>
  );
}

function PnlMetric({
  label,
  rows,
}: {
  label: string;
  rows: Array<{ currency: string; value: number }>;
}) {
  return (
    <div className="bg-[#161b22] px-4 py-3">
      <div className="text-[10px] uppercase text-[#768390]">{label}</div>
      <div className="mt-1 flex min-h-5 flex-col gap-1 font-mono text-sm font-semibold">
        {rows.length === 0 ? (
          <span className="text-[#768390]">--</span>
        ) : (
          rows.map((row) => (
            <span key={row.currency} className={pnlToneClass(row.value)}>
              {formatCurrencyAmount(row.currency, row.value)}
            </span>
          ))
        )}
      </div>
    </div>
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

function formatCurrencyAmount(currency: string, value: number): string {
  const fractionDigits = currency === "JPY" ? 0 : 2;
  const formatted = new Intl.NumberFormat("en-US", {
    maximumFractionDigits: fractionDigits,
    minimumFractionDigits: fractionDigits,
  }).format(Math.abs(value));
  const sign = value > 0 ? "+" : value < 0 ? "-" : "";
  return `${currency} ${sign}${formatted}`;
}

function formatOptionalJpy(value?: number | null): string {
  if (value === null || value === undefined) {
    return "--";
  }
  return `JPY ${new Intl.NumberFormat("en-US", { maximumFractionDigits: 0 }).format(value)}`;
}

function formatOptionalPercent(value?: number | null): string {
  if (value === null || value === undefined) {
    return "--";
  }
  return `${value.toFixed(2)}%`;
}

function formatCurrencyMap(
  values?: Record<string, number>,
): Array<{ currency: string; value: number }> {
  if (!values) {
    return [];
  }
  return Object.entries(values)
    .sort(([first], [second]) => first.localeCompare(second))
    .map(([currency, value]) => ({ currency, value }));
}

function formatQuantity(value: number): string {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 4 }).format(value);
}

function pnlToneClass(value: number | null): string {
  if (value === null || value === 0) {
    return "text-[#768390]";
  }
  return value > 0 ? "text-[#3fb950]" : "text-[#f85149]";
}

function pnlTone(value: number | null): "muted" | "positive" | "negative" {
  if (value === null || value === 0) {
    return "muted";
  }
  return value > 0 ? "positive" : "negative";
}

function marginStatusTone(status?: AccountSummary["status"]): "muted" | "positive" | "negative" | "warning" {
  switch (status) {
    case "DANGER":
      return "negative";
    case "WARNING":
      return "warning";
    case "SAFE":
      return "positive";
    default:
      return "muted";
  }
}

function metricToneClass(tone?: "muted" | "positive" | "negative" | "warning"): string {
  switch (tone) {
    case "positive":
      return "text-[#3fb950]";
    case "negative":
      return "text-[#f85149]";
    case "warning":
      return "text-[#d29922]";
    case "muted":
      return "text-[#768390]";
    default:
      return "text-[#e6edf3]";
  }
}

function marginStatusBarClass(status?: AccountSummary["status"]): string {
  switch (status) {
    case "DANGER":
      return "bg-[#f85149]";
    case "WARNING":
      return "bg-[#d29922]";
    case "SAFE":
      return "bg-[#3fb950]";
    default:
      return "bg-[#58a6ff]";
  }
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

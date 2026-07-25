export type MarketRate = {
  currencyPair: string;
  bid: number;
  ask: number;
  midPrice: number;
  spread: number;
  quotedAt: string;
};

export type MarketRateTick = MarketRate;

export type SpreadStatus =
  | "NORMAL"
  | "WIDE"
  | "VERY_WIDE"
  | "INSUFFICIENT_DATA";

export type SpreadStats = {
  currencyPair: string;
  bid: number;
  ask: number;
  spread: number;
  spreadPips: number | null;
  averageSpreadPips: number | null;
  minSpreadPips: number | null;
  maxSpreadPips: number | null;
  status: SpreadStatus;
  sampleCount: number;
  limit: number;
  pipScale: number | null;
  quotedAt: string;
};

export type NewsDirection = "UP" | "DOWN";

export type NewsEvent = {
  id: string;
  currencyPair: string;
  direction: NewsDirection;
  magnitudeBps: number;
  volatilityMultiplier: number;
  spreadMultiplier: number;
  durationSeconds: number;
  headline: string;
  startedAt: string;
  endsAt: string;
  active: boolean;
};

export type AlertSeverity = "INFO" | "WARNING" | "CRITICAL";

export type MarketAlert = {
  id: string;
  type: string;
  currencyPair: string;
  severity: AlertSeverity;
  message: string;
  changePips: number | null;
  raisedAt: string;
  resolvedAt: string | null;
  active: boolean;
};

export type OrderSide = "BUY" | "SELL";
export type OrderType = "MARKET" | "LIMIT" | "STOP";
export type PendingOrderStatus = "PENDING" | "WAITING" | "TRIGGERED" | "CANCELED" | "CANCELLED" | "REJECTED" | "EXPIRED";
export type ExitOrderType = "TP" | "SL";

export type TradeSummary = {
  id: number;
  orderId: number;
  currencyPair: string;
  side: OrderSide;
  quantity: number;
  price: number;
  executedAt: string;
  tradeKind?: "OPEN" | "CLOSE";
  positionId?: number | null;
  realizedPnl?: number | null;
};

export type OrderSummary = {
  id: number;
  currencyPair: string;
  side: OrderSide;
  orderType: OrderType;
  quantity: number;
  status: string;
  source: "MANUAL" | "LOSS_CUT" | "TRIGGER";
  requestedAt: string;
};

export type OrderResult = {
  order: OrderSummary;
  trade: TradeSummary;
};

export type PendingOrder = {
  id: number;
  currencyPair: string;
  side: OrderSide;
  orderType: "LIMIT" | "STOP";
  quantity: number;
  triggerPrice: number;
  status: PendingOrderStatus;
  createdAt: string;
  triggeredAt: string | null;
  resultingOrderId: number | null;
  rejectionReason: string | null;
  purpose?: "ENTRY" | "EXIT" | null;
  exitType?: ExitOrderType | null;
  targetPositionId?: number | null;
  parentOrderId?: number | null;
  ocoGroupId?: string | null;
};

export type IfdOrder = {
  entry: PendingOrder;
  exit: PendingOrder;
};

export type IfoOrder = {
  entry: PendingOrder;
  ocoGroupId: string;
  exits: PendingOrder[];
};

export type PositionExitOrder = {
  id: number;
  type: ExitOrderType | null;
  triggerPrice: number;
  status: PendingOrderStatus;
  ocoGroupId: string | null;
  createdAt: string;
  triggeredAt: string | null;
};

export type PositionOcoOrder = {
  ocoGroupId: string;
  orders: PositionExitOrder[];
};

export type PositionSummary = {
  id: number;
  currencyPair: string;
  side: "LONG" | "SHORT";
  quantity: number;
  averagePrice: number;
  quoteCurrency: string;
  currentPrice: number | null;
  unrealizedPnl: number | null;
  accruedSwap: number | null;
  updatedAt: string;
  requiredMargin: number | null;
  openedAt: string | null;
  exitOrders: PositionExitOrder[];
};

export type PositionCloseResult = {
  positionId: number;
  currencyPair: string;
  side: "LONG" | "SHORT";
  quantity: number;
  closePrice: number;
  realizedPnl: number;
  realizedSwap: number;
  realizedCurrency: string;
  closedAt: string;
  execution: OrderResult;
};

export type PnlSummary = {
  unrealizedByCurrency: Record<string, number>;
  realizedByCurrency: Record<string, number>;
};

export type AccountMarginStatus = "SAFE" | "WARNING" | "DANGER";

export type AccountSummary = {
  accountId: string;
  baseCurrency: string;
  balance: number | null;
  realizedPnl: number | null;
  unrealizedPnl: number | null;
  unrealizedSwap: number | null;
  equity: number | null;
  usedMargin: number | null;
  freeMargin: number | null;
  withdrawable: number | null;
  marginRatio: number | null;
  lossCutThreshold: number;
  status: AccountMarginStatus;
};

export type CashTransaction = {
  id: number;
  type: "DEPOSIT" | "WITHDRAWAL";
  amount: number;
  status: "ACCEPTED" | "COMPLETED" | "FAILED" | "CANCELED";
  requestedAt: string;
  completedAt: string | null;
};

export type CashOperationResult = {
  transaction: CashTransaction;
  balanceAfter: number;
};

export type EquitySnapshot = {
  recordedAt: string;
  balance: number;
  equity: number;
  usedMargin: number | null;
  marginRatio: number | null;
};

export type SwapRolloverResult = {
  days: number;
  appliedPositions: number;
  totalAccruedSwap: number;
  appliedAt: string;
};

export type PositionSwapTransferResult = {
  positionId: number;
  transferredSwap: number;
  balanceAfter: number;
  transferredAt: string;
};

export type SwapTransferAllResult = {
  transferredPositions: number;
  totalTransferredSwap: number;
  balanceAfter: number;
  transferredAt: string;
};

const REQUEST_TIMEOUT_MS = 10_000;
const MAX_REQUEST_ATTEMPTS = 3;
const RETRY_DELAY_MS = 500;

function getApiBaseUrl(): string {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL?.trim();

  if (apiBaseUrl && apiBaseUrl.toLowerCase() !== "auto") {
    return apiBaseUrl.replace(/\/$/, "");
  }

  if (typeof window !== "undefined") {
    return `${window.location.protocol}//${window.location.hostname}:8080`;
  }

  throw new Error("NEXT_PUBLIC_API_BASE_URL is not configured. Check .env.local.");
}

export async function fetchMarketRateTicks(
  currencyPair: string,
  limit = 300,
): Promise<MarketRateTick[]> {
  const params = new URLSearchParams({
    currencyPair,
    limit: String(limit),
  });
  const requestUrl = `${getApiBaseUrl()}/api/market/rates/ticks?${params.toString()}`;

  return fetchWithRetry<MarketRateTick[]>(requestUrl);
}

export async function fetchLatestMarketRates(): Promise<MarketRate[]> {
  const requestUrl = `${getApiBaseUrl()}/api/market/rates`;

  return fetchWithRetry<MarketRate[]>(requestUrl);
}

export async function getSpreadStats(
  currencyPair: string,
  limit = 60,
): Promise<SpreadStats> {
  const params = new URLSearchParams({
    currencyPair,
    limit: String(limit),
  });
  const requestUrl = `${getApiBaseUrl()}/api/market/spread/stats?${params.toString()}`;

  return fetchWithRetry<SpreadStats>(requestUrl);
}

export async function triggerNewsEvent(
  currencyPair: string,
  direction: NewsDirection,
): Promise<NewsEvent> {
  const requestUrl = `${getApiBaseUrl()}/api/market/news/events`;

  return fetchWithRetry<NewsEvent>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      currencyPair,
      direction,
      magnitudeBps: 100,
      durationSeconds: 30,
      volatilityMultiplier: 5.0,
      spreadMultiplier: 4.0,
    }),
  });
}

export async function fetchNewsEvents(limit = 10): Promise<NewsEvent[]> {
  const params = new URLSearchParams({
    limit: String(limit),
  });
  const requestUrl = `${getApiBaseUrl()}/api/market/news/events?${params.toString()}`;

  return fetchWithRetry<NewsEvent[]>(requestUrl);
}

export async function fetchMarketAlerts(limit = 50): Promise<MarketAlert[]> {
  const params = new URLSearchParams({
    limit: String(limit),
  });
  const requestUrl = `${getApiBaseUrl()}/api/market/alerts?${params.toString()}`;

  return fetchWithRetry<MarketAlert[]>(requestUrl);
}

export async function placeMarketOrder(
  currencyPair: string,
  side: OrderSide,
  quantity: number,
): Promise<OrderResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/market`;

  return fetchWithRetry<OrderResult>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      currencyPair,
      side,
      quantity,
    }),
  });
}

export async function placePendingOrder(
  currencyPair: string,
  side: OrderSide,
  orderType: "LIMIT" | "STOP",
  quantity: number,
  triggerPrice: number,
): Promise<PendingOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/pending`;

  return fetchWithRetry<PendingOrder>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      currencyPair,
      side,
      orderType,
      quantity,
      triggerPrice,
    }),
  });
}

export async function placeIfdOrder(
  currencyPair: string,
  side: OrderSide,
  orderType: "LIMIT" | "STOP",
  quantity: number,
  entryTriggerPrice: number,
  exitType: ExitOrderType,
  exitTriggerPrice: number,
): Promise<IfdOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/ifd`;

  return fetchWithRetry<IfdOrder>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      entry: {
        currencyPair,
        side,
        orderType,
        quantity,
        triggerPrice: entryTriggerPrice,
      },
      exit: {
        type: exitType,
        triggerPrice: exitTriggerPrice,
      },
    }),
  });
}

export async function placeIfoOrder(
  currencyPair: string,
  side: OrderSide,
  orderType: "LIMIT" | "STOP",
  quantity: number,
  entryTriggerPrice: number,
  takeProfitPrice: number,
  stopLossPrice: number,
): Promise<IfoOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/ifo`;

  return fetchWithRetry<IfoOrder>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      entry: {
        currencyPair,
        side,
        orderType,
        quantity,
        triggerPrice: entryTriggerPrice,
      },
      oco: {
        tp: {
          triggerPrice: takeProfitPrice,
        },
        sl: {
          triggerPrice: stopLossPrice,
        },
      },
    }),
  });
}

export async function fetchPendingOrders(
  status = "PENDING",
  currencyPair?: string,
  limit = 50,
): Promise<PendingOrder[]> {
  const params = new URLSearchParams({
    status,
    limit: String(limit),
  });
  if (currencyPair) {
    params.set("currencyPair", currencyPair);
  }
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/pending?${params.toString()}`;

  return fetchWithRetry<PendingOrder[]>(requestUrl);
}

export async function fetchPendingOrderHistory(limit = 50): Promise<PendingOrder[]> {
  const statuses: PendingOrderStatus[] = [
    "PENDING",
    "WAITING",
    "TRIGGERED",
    "CANCELED",
    "CANCELLED",
    "REJECTED",
    "EXPIRED",
  ];
  const results = await Promise.all(
    statuses.map((status) => fetchPendingOrders(status, undefined, limit)),
  );
  const byId = new Map<number, PendingOrder>();
  for (const order of results.flat()) {
    byId.set(order.id, order);
  }

  return [...byId.values()].sort(
    (first, second) =>
      new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime(),
  );
}

export async function cancelPendingOrder(id: number): Promise<PendingOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders/pending/${id}/cancel`;

  return fetchWithRetry<PendingOrder>(requestUrl, { method: "POST" });
}

export async function fetchTrades(
  currencyPair?: string,
  limit = 50,
): Promise<TradeSummary[]> {
  const params = new URLSearchParams({
    limit: String(limit),
  });
  if (currencyPair) {
    params.set("currencyPair", currencyPair);
  }
  const requestUrl = `${getApiBaseUrl()}/api/trade/trades?${params.toString()}`;

  return fetchWithRetry<TradeSummary[]>(requestUrl);
}

export async function fetchOrders(
  currencyPair?: string,
  limit = 50,
): Promise<OrderSummary[]> {
  const params = new URLSearchParams({
    limit: String(limit),
  });
  if (currencyPair) {
    params.set("currencyPair", currencyPair);
  }
  const requestUrl = `${getApiBaseUrl()}/api/trade/orders?${params.toString()}`;

  return fetchWithRetry<OrderSummary[]>(requestUrl);
}

export async function fetchPositions(
  currencyPair?: string,
): Promise<PositionSummary[]> {
  const params = new URLSearchParams();
  if (currencyPair) {
    params.set("currencyPair", currencyPair);
  }
  const query = params.toString();
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions${query ? `?${query}` : ""}`;

  return fetchWithRetry<PositionSummary[]>(requestUrl);
}

export async function closePosition(id: number): Promise<PositionCloseResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${id}/close`;

  return fetchWithRetry<PositionCloseResult>(requestUrl, { method: "POST" });
}

export async function transferPositionSwap(id: number): Promise<PositionSwapTransferResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${id}/swap-transfer`;

  return fetchWithRetry<PositionSwapTransferResult>(requestUrl, { method: "POST" });
}

export async function transferAllPositionSwaps(): Promise<SwapTransferAllResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/swap-transfer`;

  return fetchWithRetry<SwapTransferAllResult>(requestUrl, { method: "POST" });
}

export async function placePositionExitOrder(
  positionId: number,
  type: ExitOrderType,
  triggerPrice: number,
): Promise<PositionExitOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${positionId}/exit-orders`;

  return fetchWithRetry<PositionExitOrder>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      type,
      triggerPrice,
    }),
  });
}

export async function cancelPositionExitOrder(
  positionId: number,
  exitOrderId: number,
): Promise<PositionExitOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${positionId}/exit-orders/${exitOrderId}`;

  return fetchWithRetry<PositionExitOrder>(requestUrl, { method: "DELETE" });
}

export async function placePositionOcoOrder(
  positionId: number,
  tpTriggerPrice: number,
  slTriggerPrice: number,
): Promise<PositionOcoOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${positionId}/oco-orders`;

  return fetchWithRetry<PositionOcoOrder>(requestUrl, {
    method: "POST",
    body: JSON.stringify({
      tp: { triggerPrice: tpTriggerPrice },
      sl: { triggerPrice: slTriggerPrice },
    }),
  });
}

export async function cancelPositionOcoOrder(
  positionId: number,
  ocoGroupId: string,
): Promise<PositionOcoOrder> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/positions/${positionId}/oco-orders/${ocoGroupId}`;

  return fetchWithRetry<PositionOcoOrder>(requestUrl, { method: "DELETE" });
}

export async function fetchPnlSummary(): Promise<PnlSummary> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/pnl/summary`;

  return fetchWithRetry<PnlSummary>(requestUrl);
}

export async function fetchAccountSummary(): Promise<AccountSummary> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/account/summary`;

  return fetchWithRetry<AccountSummary>(requestUrl);
}

export async function fetchCashTransactions(limit = 50): Promise<CashTransaction[]> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/account/cash-transactions?limit=${limit}`;

  return fetchWithRetry<CashTransaction[]>(requestUrl);
}

export async function depositCash(amount: number): Promise<CashOperationResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/account/deposits`;

  return fetchWithRetry<CashOperationResult>(requestUrl, {
    method: "POST",
    body: JSON.stringify({ amount }),
  });
}

export async function withdrawCash(amount: number): Promise<CashOperationResult> {
  const requestUrl = `${getApiBaseUrl()}/api/trade/account/withdrawals`;

  return fetchWithRetry<CashOperationResult>(requestUrl, {
    method: "POST",
    body: JSON.stringify({ amount }),
  });
}

export async function fetchEquityHistory(
  limit = 300,
  from?: string,
): Promise<EquitySnapshot[]> {
  const params = new URLSearchParams({
    limit: String(limit),
  });
  if (from) {
    params.set("from", from);
  }
  const requestUrl = `${getApiBaseUrl()}/api/trade/account/equity-history?${params.toString()}`;

  return fetchWithRetry<EquitySnapshot[]>(requestUrl);
}

export async function triggerSwapRollover(days = 1): Promise<SwapRolloverResult> {
  const requestUrl = `${getApiBaseUrl()}/api/market/swap/rollover`;

  return fetchWithRetry<SwapRolloverResult>(requestUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ days }),
  });
}

async function fetchWithRetry<T>(
  requestUrl: string,
  init?: RequestInit,
): Promise<T> {

  for (let attempt = 1; attempt <= MAX_REQUEST_ATTEMPTS; attempt += 1) {
    try {
      return await requestJson<T>(requestUrl, init);
    } catch (error) {
      const canRetry = error instanceof TypeError && attempt < MAX_REQUEST_ATTEMPTS;

      if (!canRetry) {
        if (error instanceof TypeError) {
          throw new Error(`Could not connect to the rate API at ${requestUrl}`);
        }
        throw error;
      }

      await delay(RETRY_DELAY_MS);
    }
  }

  throw new Error(`Could not connect to the rate API at ${requestUrl}`);
}

async function requestJson<T>(
  requestUrl: string,
  init?: RequestInit,
): Promise<T> {
  const controller = new AbortController();
  const timeoutId = setTimeout(
    () => controller.abort(),
    REQUEST_TIMEOUT_MS,
  );

  try {
    const response = await fetch(requestUrl, {
      ...init,
      cache: "no-store",
      signal: controller.signal,
      headers: {
        "Content-Type": "application/json",
        ...init?.headers,
      },
    });

    if (!response.ok) {
      const message = await readErrorMessage(response);
      throw new Error(message ?? `Market API request failed (${response.status}) at ${requestUrl}`);
    }

    return response.json() as Promise<T>;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Market API request timed out at ${requestUrl}`);
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

async function readErrorMessage(response: Response): Promise<string | null> {
  try {
    const body = (await response.json()) as { message?: unknown };
    return typeof body.message === "string" ? body.message : null;
  } catch {
    return null;
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

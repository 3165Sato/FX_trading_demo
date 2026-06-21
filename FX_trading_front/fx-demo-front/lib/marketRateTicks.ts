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

export type TradeSummary = {
  id: number;
  orderId: number;
  currencyPair: string;
  side: OrderSide;
  quantity: number;
  price: number;
  executedAt: string;
};

export type OrderSummary = {
  id: number;
  currencyPair: string;
  side: OrderSide;
  orderType: string;
  quantity: number;
  status: string;
  requestedAt: string;
};

export type OrderResult = {
  order: OrderSummary;
  trade: TradeSummary;
};

export type PositionSummary = {
  currencyPair: string;
  side: "LONG" | "SHORT";
  quantity: number;
  averagePrice: number;
  updatedAt: string;
};

const REQUEST_TIMEOUT_MS = 10_000;
const MAX_REQUEST_ATTEMPTS = 3;
const RETRY_DELAY_MS = 500;

function getApiBaseUrl(): string {
  const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL;

  if (!apiBaseUrl) {
    throw new Error(
      "NEXT_PUBLIC_API_BASE_URL is not configured. Check .env.local.",
    );
  }

  return apiBaseUrl.replace(/\/$/, "");
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
      throw new Error(`Market API request failed (${response.status}) at ${requestUrl}`);
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

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

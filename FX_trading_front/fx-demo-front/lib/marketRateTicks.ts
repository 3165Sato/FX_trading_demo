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
      throw new Error(
        `Rate history request failed (${response.status}) at ${requestUrl}`,
      );
    }

    return response.json() as Promise<T>;
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(`Rate history request timed out at ${requestUrl}`);
    }
    throw error;
  } finally {
    clearTimeout(timeoutId);
  }
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

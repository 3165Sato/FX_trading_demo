export type MarketRateTick = {
  currencyPair: string;
  bid: number;
  ask: number;
  midPrice: number;
  spread: number;
  quotedAt: string;
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

  for (let attempt = 1; attempt <= MAX_REQUEST_ATTEMPTS; attempt += 1) {
    try {
      return await requestTicks(requestUrl);
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

async function requestTicks(requestUrl: string): Promise<MarketRateTick[]> {
  const controller = new AbortController();
  const timeoutId = setTimeout(
    () => controller.abort(),
    REQUEST_TIMEOUT_MS,
  );

  try {
    const response = await fetch(requestUrl, {
      cache: "no-store",
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(
        `Rate history request failed (${response.status}) at ${requestUrl}`,
      );
    }

    return response.json();
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

export type MarketRateTick = {
  currencyPair: string;
  bid: number;
  ask: number;
  midPrice: number;
  spread: number;
  quotedAt: string;
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function fetchMarketRateTicks(
  currencyPair: string,
  limit = 300,
): Promise<MarketRateTick[]> {
  const params = new URLSearchParams({
    currencyPair,
    limit: String(limit),
  });
  const response = await fetch(
    `${API_BASE_URL}/api/market/rates/ticks?${params.toString()}`,
    {
      cache: "no-store",
    },
  );

  if (!response.ok) {
    throw new Error(`Rate history request failed: ${response.status}`);
  }

  return response.json();
}

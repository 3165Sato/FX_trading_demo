import type { MarketRate } from "../../lib/marketRateTicks";

type MarketRateCardProps = {
  rate: MarketRate;
  change: number;
  selected: boolean;
  onSelect: (currencyPair: string) => void;
};

export function MarketRateCard({
  rate,
  change,
  selected,
  onSelect,
}: MarketRateCardProps) {
  const priceScale = rate.currencyPair === "EUR/USD" ? 5 : 3;
  const direction = change > 0 ? "up" : change < 0 ? "down" : "flat";
  const directionText =
    direction === "up" ? "UP" : direction === "down" ? "DOWN" : "FLAT";
  const directionClass =
    direction === "up"
      ? "text-emerald-400"
      : direction === "down"
        ? "text-rose-400"
        : "text-zinc-500";

  return (
    <button
      type="button"
      onClick={() => onSelect(rate.currencyPair)}
      className={`min-w-0 border p-4 text-left transition-colors focus:outline-none focus:ring-2 focus:ring-emerald-400/70 ${
        selected
          ? "border-emerald-400 bg-[#101d1b]"
          : "border-[#2a353e] bg-[#11171c] hover:border-[#52616c]"
      }`}
      aria-pressed={selected}
    >
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="font-mono text-base font-semibold text-zinc-100">
            {rate.currencyPair}
          </div>
          <div className="mt-1 text-[11px] uppercase text-zinc-500">
            Spot monitor
          </div>
        </div>
        <div className={`font-mono text-xs font-semibold ${directionClass}`}>
          {directionText} {formatSignedChange(change, priceScale)}
        </div>
      </div>

      <div className="mt-5 grid grid-cols-2 gap-x-5 gap-y-3 font-mono">
        <RateValue label="Bid" value={rate.bid} scale={priceScale} />
        <RateValue label="Ask" value={rate.ask} scale={priceScale} />
        <RateValue label="Mid" value={rate.midPrice} scale={priceScale} />
        <RateValue label="Spread" value={rate.spread} scale={priceScale} />
      </div>

      <div className="mt-4 border-t border-[#273139] pt-3 font-mono text-[11px] text-zinc-500">
        {new Date(rate.quotedAt).toLocaleString("ja-JP")}
      </div>
    </button>
  );
}

function RateValue({
  label,
  value,
  scale,
}: {
  label: string;
  value: number;
  scale: number;
}) {
  return (
    <div className="min-w-0">
      <div className="text-[10px] uppercase text-zinc-500">{label}</div>
      <div className="mt-1 truncate text-base text-zinc-100">
        {value.toFixed(scale)}
      </div>
    </div>
  );
}

function formatSignedChange(value: number, scale: number): string {
  if (value === 0) {
    return (0).toFixed(scale);
  }
  return `${value > 0 ? "+" : ""}${value.toFixed(scale)}`;
}

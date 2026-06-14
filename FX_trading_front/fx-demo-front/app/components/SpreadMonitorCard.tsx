import type { MarketRate, MarketRateTick } from "../../lib/marketRateTicks";

type SpreadMonitorCardProps = {
  rate?: MarketRate;
  ticks: MarketRateTick[];
  currencyPair: string;
};

export function SpreadMonitorCard({
  rate,
  ticks,
  currencyPair,
}: SpreadMonitorCardProps) {
  const scale = currencyPair === "EUR/USD" ? 5 : 3;
  const averageSpread =
    ticks.length > 0
      ? ticks.reduce((total, tick) => total + tick.spread, 0) / ticks.length
      : undefined;
  // This threshold leaves room for future variable-spread alerting.
  const spreadIsElevated =
    rate !== undefined &&
    averageSpread !== undefined &&
    averageSpread > 0 &&
    rate.spread > averageSpread * 1.5;

  return (
    <section className="border border-[#2a353e] bg-[#0e1419]">
      <div className="flex items-start justify-between border-b border-[#2a353e] px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold text-zinc-100">Spread monitor</h2>
          <p className="mt-1 text-xs text-zinc-500">{currencyPair} execution gap</p>
        </div>
        <span
          className={`border px-2 py-1 font-mono text-[10px] uppercase ${
            spreadIsElevated
              ? "border-amber-400/60 text-amber-300"
              : "border-emerald-400/40 text-emerald-400"
          }`}
        >
          {spreadIsElevated ? "Watch" : "Normal"}
        </span>
      </div>

      <div className="grid grid-cols-2 gap-px bg-[#26313a]">
        <SpreadValue
          label="Current spread"
          value={rate?.spread}
          scale={scale}
          featured
        />
        <SpreadValue label="Average spread" value={averageSpread} scale={scale} />
        <SpreadValue label="Bid / Sell" value={rate?.bid} scale={scale} />
        <SpreadValue label="Ask / Buy" value={rate?.ask} scale={scale} />
      </div>
      <div className="px-4 py-3 text-[11px] leading-5 text-zinc-600">
        BUY references Ask. SELL references Bid. Spread is Ask minus Bid.
      </div>
    </section>
  );
}

function SpreadValue({
  label,
  value,
  scale,
  featured = false,
}: {
  label: string;
  value?: number;
  scale: number;
  featured?: boolean;
}) {
  return (
    <div className="bg-[#0e1419] px-4 py-4">
      <div className="text-[10px] uppercase text-zinc-600">{label}</div>
      <div
        className={`mt-1 font-mono font-semibold ${
          featured ? "text-lg text-amber-300" : "text-sm text-zinc-200"
        }`}
      >
        {value === undefined ? "--" : value.toFixed(scale)}
      </div>
    </div>
  );
}

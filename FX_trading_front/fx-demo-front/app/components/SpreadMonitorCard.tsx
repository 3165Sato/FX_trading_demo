import type { SpreadStats, SpreadStatus } from "../../lib/marketRateTicks";

type SpreadMonitorCardProps = {
  currencyPair: string;
  error: string | null;
  loading: boolean;
  stats?: SpreadStats;
};

export function SpreadMonitorCard({
  currencyPair,
  error,
  loading,
  stats,
}: SpreadMonitorCardProps) {
  const scale = currencyPair === "EUR/USD" ? 5 : 3;
  const status = stats?.status ?? "INSUFFICIENT_DATA";

  return (
    <section className="border border-[#2a353e] bg-[#0e1419]">
      <div className="flex items-start justify-between border-b border-[#2a353e] px-4 py-3">
        <div>
          <h2 className="text-sm font-semibold text-zinc-100">Spread monitor</h2>
          <p className="mt-1 text-xs text-zinc-500">{currencyPair} execution gap</p>
        </div>
        <span
          className={`border px-2 py-1 font-mono text-[10px] uppercase ${getStatusClass(status)}`}
        >
          {formatStatus(status)}
        </span>
      </div>

      {error && (
        <div className="border-b border-rose-500/40 bg-rose-950/25 px-4 py-3 font-mono text-[11px] text-rose-200">
          {error}
        </div>
      )}

      <div className="grid grid-cols-2 gap-px bg-[#26313a]">
        <SpreadValue
          label="Current spread"
          value={stats?.spread}
          scale={scale}
          featured
        />
        <PipsValue label="Spread pips" value={stats?.spreadPips} featured />
        <PipsValue label="Avg spread" value={stats?.averageSpreadPips} />
        <PipsValue
          label="Min / Max spread"
          value={stats?.minSpreadPips}
          suffix={` / ${formatPips(stats?.maxSpreadPips)}`}
        />
        <SpreadValue label="Bid / Sell" value={stats?.bid} scale={scale} />
        <SpreadValue label="Ask / Buy" value={stats?.ask} scale={scale} />
      </div>
      <div className="flex items-center justify-between gap-3 px-4 py-3 text-[11px] leading-5 text-zinc-600">
        <span>BUY references Ask. SELL references Bid. Spread is Ask minus Bid.</span>
        <span className="shrink-0 font-mono text-zinc-500">
          {loading ? "Updating..." : `n=${stats?.sampleCount ?? 0}/${stats?.limit ?? 60}`}
        </span>
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

function PipsValue({
  label,
  value,
  suffix = " pips",
  featured = false,
}: {
  label: string;
  value?: number | null;
  suffix?: string;
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
        {formatPips(value)}
        {value === undefined || value === null ? "" : suffix}
      </div>
    </div>
  );
}

function formatPips(value?: number | null): string {
  return value === undefined || value === null ? "--" : value.toFixed(1);
}

function formatStatus(status: SpreadStatus): string {
  return status.replace("_", " ");
}

function getStatusClass(status: SpreadStatus): string {
  switch (status) {
    case "NORMAL":
      return "border-emerald-400/40 text-emerald-400";
    case "WIDE":
      return "border-amber-400/60 text-amber-300";
    case "VERY_WIDE":
      return "border-rose-400/70 text-rose-300";
    case "INSUFFICIENT_DATA":
      return "border-zinc-500/50 text-zinc-400";
  }
}

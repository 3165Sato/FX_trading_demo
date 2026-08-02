import { useMemo } from "react";

import type {
  AccountSummary,
  CashTransaction,
  EquitySnapshot,
  ExitOrderType,
  MarketAlert,
  MarketRate,
  MarketRateTick,
  NewsDirection,
  NewsEvent,
  OrderSide,
  OrderSummary,
  OrderType,
  PendingOrder,
  PnlSummary,
  PositionSummary,
  QuickCloseResult,
  QuickCloseScope,
  SpreadStats,
  TradeSummary,
} from "../../lib/marketRateTicks";
import { EquityCurvePanel, type EquityHistoryRange } from "./EquityCurvePanel";
import { MarketRateChart } from "./MarketRateChart";
import {
  AccountSummaryBand,
  AlertPanel,
  CashTransactionsPanel,
  EmptyPanel,
  ExecutionHistoryPanel,
  FutureHistoryPanel,
  LoadingPanel,
  MarketOrderPanel,
  NewsEventPanel,
  OrderHistoryPanel,
  PanelHeader,
  PendingOrdersPanel,
  PnlSummaryPanel,
  PositionDetailPanel,
  PositionsTable,
  PriceReferencePanel,
  QuickClosePanel,
  RateBoardRow,
  StatusItem,
  TickLogPanel,
  formatTime,
  type ComplexOrderKind,
  type OrderPanelMode,
  type Screen,
} from "./MarketMonitorDashboard";
import { SpreadMonitorCard } from "./SpreadMonitorCard";

export function AppHeader({
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
            <HeaderTab active={screen === "history"} label="History" onClick={() => onScreenChange("history")} />
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

export function HeaderTab({
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

export function StatusStrip({
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

export function MonitorScreen({
  activeAlerts,
  activePair,
  alerts,
  equityHistory,
  equityHistoryError,
  equityHistoryLoading,
  equityHistoryRange,
  monitoredRates,
  newsEvents,
  newsSubmittingDirection,
  rateChanges,
  ratesLoading,
  recentTicks,
  spreadStats,
  spreadStatsError,
  spreadStatsLoading,
  swapRolloverMessage,
  swapRolloverSubmitting,
  ticks,
  ticksLoading,
  onSelectPair,
  onSelectEquityHistoryRange,
  onRetryEquityHistory,
  onTriggerNews,
  onTriggerSwapRollover,
}: {
  activeAlerts: number;
  activePair: string;
  alerts: MarketAlert[];
  equityHistory: EquitySnapshot[];
  equityHistoryError: string | null;
  equityHistoryLoading: boolean;
  equityHistoryRange: EquityHistoryRange;
  monitoredRates: MarketRate[];
  newsEvents: NewsEvent[];
  newsSubmittingDirection: NewsDirection | null;
  rateChanges: Record<string, number>;
  ratesLoading: boolean;
  recentTicks: MarketRateTick[];
  spreadStats?: SpreadStats;
  spreadStatsError: string | null;
  spreadStatsLoading: boolean;
  swapRolloverMessage: string | null;
  swapRolloverSubmitting: boolean;
  ticks: MarketRateTick[];
  ticksLoading: boolean;
  onSelectPair: (currencyPair: string) => void;
  onSelectEquityHistoryRange: (range: EquityHistoryRange) => void;
  onRetryEquityHistory: () => void;
  onTriggerNews: (direction: NewsDirection) => void;
  onTriggerSwapRollover: () => void;
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

      <div className="flex min-w-0 flex-col gap-3 overflow-hidden xl:h-full xl:min-h-0">
        <div className="grid min-h-0 flex-1 grid-rows-[minmax(0,3fr)_minmax(0,2fr)] gap-3">
          <section className="flex min-w-0 min-h-0 flex-col border border-[#262d38] bg-[#161b22]">
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

          <EquityCurvePanel
            error={equityHistoryError}
            history={equityHistory}
            loading={equityHistoryLoading}
            range={equityHistoryRange}
            onRangeChange={onSelectEquityHistoryRange}
            onRetry={onRetryEquityHistory}
          />
        </div>

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
          swapRolloverMessage={swapRolloverMessage}
          swapRolloverSubmitting={swapRolloverSubmitting}
          onTrigger={onTriggerNews}
          onTriggerSwapRollover={onTriggerSwapRollover}
        />
        <AlertPanel alerts={alerts} activeCount={activeAlerts} />
      </aside>
    </div>
  );
}

export function TradingScreen({
  accountSummary,
  activePair,
  amendingExitOrderId,
  amendingPendingOrderId,
  cancelingExitOrderId,
  cancelingOcoGroupId,
  cancelingPendingOrderId,
  closingPositionId,
  exitOrderDrafts,
  exitOrderAmendment,
  ifdExitPrice,
  ifdExitType,
  ifoStopLossPrice,
  ifoTakeProfitPrice,
  complexOrderKind,
  orderError,
  orderPanelMode,
  orderQuantity,
  orderType,
  orders,
  pendingOrders,
  pendingOrderAmendment,
  positions,
  pnlSummary,
  quickCloseError,
  quickClosePair,
  quickCloseResult,
  quickCloseScope,
  quickCloseSubmitting,
  rates,
  selectedPosition,
  selectedRate,
  submittingOrderSide,
  lastOrderMessage,
  trades,
  triggerPrice,
  submittingExitOrder,
  submittingIfdSide,
  submittingIfoSide,
  submittingOcoPositionId,
  transferringAllSwaps,
  transferringSwapPositionId,
  onCancelExitOrder,
  onCancelOcoOrder,
  onCancelPendingOrder,
  onCloseExitOrderAmendment,
  onClosePendingOrderAmendment,
  onClosePosition,
  onExitOrderDraftChange,
  onExitOrderAmendmentChange,
  onIfdExitPriceChange,
  onIfdExitTypeChange,
  onIfoStopLossPriceChange,
  onIfoTakeProfitPriceChange,
  onComplexOrderKindChange,
  onQuantityChange,
  onQuickClosePairChange,
  onQuickCloseScopeChange,
  onOrderTypeChange,
  onOrderPanelModeChange,
  onSelectPair,
  onSelectPosition,
  onSelectExitOrderForAmendment,
  onSelectPendingOrderForAmendment,
  onSubmitOrder,
  onSubmitExitOrder,
  onSubmitExitOrderAmendment,
  onSubmitIfdOrder,
  onSubmitIfoOrder,
  onSubmitOcoOrder,
  onSubmitPendingOrderAmendment,
  onSubmitQuickClose,
  onPendingOrderAmendmentChange,
  onTransferAllSwaps,
  onTransferPositionSwap,
  onTriggerPriceChange,
}: {
  accountSummary: AccountSummary | null;
  activePair: string;
  amendingExitOrderId: number | null;
  amendingPendingOrderId: number | null;
  cancelingExitOrderId: number | null;
  cancelingOcoGroupId: string | null;
  cancelingPendingOrderId: number | null;
  closingPositionId: number | null;
  exitOrderDrafts: Record<number, Partial<Record<ExitOrderType, string>>>;
  exitOrderAmendment: { positionId: number; orderId: number; triggerPrice: string } | null;
  ifdExitPrice: string;
  ifdExitType: ExitOrderType;
  ifoStopLossPrice: string;
  ifoTakeProfitPrice: string;
  complexOrderKind: ComplexOrderKind;
  orderError: string | null;
  orderPanelMode: OrderPanelMode;
  orderQuantity: string;
  orderType: OrderType;
  orders: OrderSummary[];
  pendingOrders: PendingOrder[];
  pendingOrderAmendment: { id: number; quantity: string; triggerPrice: string } | null;
  positions: PositionSummary[];
  pnlSummary: PnlSummary | null;
  quickCloseError: string | null;
  quickClosePair: string;
  quickCloseResult: QuickCloseResult | null;
  quickCloseScope: QuickCloseScope;
  quickCloseSubmitting: boolean;
  rates: MarketRate[];
  selectedPosition: PositionSummary | null;
  selectedRate?: MarketRate;
  submittingOrderSide: OrderSide | null;
  lastOrderMessage: string | null;
  trades: TradeSummary[];
  triggerPrice: string;
  submittingExitOrder: { positionId: number; type: ExitOrderType } | null;
  submittingIfdSide: OrderSide | null;
  submittingIfoSide: OrderSide | null;
  submittingOcoPositionId: number | null;
  transferringAllSwaps: boolean;
  transferringSwapPositionId: number | null;
  onCancelExitOrder: (positionId: number, exitOrderId: number) => void;
  onCancelOcoOrder: (positionId: number, groupId: string) => void;
  onCancelPendingOrder: (id: number) => void;
  onCloseExitOrderAmendment: () => void;
  onClosePendingOrderAmendment: () => void;
  onClosePosition: (id: number) => void;
  onExitOrderDraftChange: (positionId: number, type: ExitOrderType, value: string) => void;
  onExitOrderAmendmentChange: (value: string) => void;
  onIfdExitPriceChange: (price: string) => void;
  onIfdExitTypeChange: (type: ExitOrderType) => void;
  onIfoStopLossPriceChange: (price: string) => void;
  onIfoTakeProfitPriceChange: (price: string) => void;
  onComplexOrderKindChange: (kind: ComplexOrderKind) => void;
  onQuantityChange: (quantity: string) => void;
  onQuickClosePairChange: (currencyPair: string) => void;
  onQuickCloseScopeChange: (scope: QuickCloseScope) => void;
  onOrderTypeChange: (orderType: OrderType) => void;
  onOrderPanelModeChange: (mode: OrderPanelMode) => void;
  onSelectPair: (currencyPair: string) => void;
  onSelectPosition: (positionId: number) => void;
  onSelectExitOrderForAmendment: (
    positionId: number,
    order: PositionSummary["exitOrders"][number],
  ) => void;
  onSelectPendingOrderForAmendment: (order: PendingOrder) => void;
  onSubmitOrder: (side: OrderSide) => void;
  onSubmitExitOrder: (position: PositionSummary, type: ExitOrderType) => void;
  onSubmitExitOrderAmendment: () => void;
  onSubmitIfdOrder: (side: OrderSide) => void;
  onSubmitIfoOrder: (side: OrderSide) => void;
  onSubmitOcoOrder: (position: PositionSummary) => void;
  onSubmitPendingOrderAmendment: () => void;
  onSubmitQuickClose: () => void;
  onPendingOrderAmendmentChange: (field: "quantity" | "triggerPrice", value: string) => void;
  onTransferAllSwaps: () => void;
  onTransferPositionSwap: (position: PositionSummary) => void;
  onTriggerPriceChange: (triggerPrice: string) => void;
}) {
  const visiblePendingOrders = useMemo(
    () => pendingOrders.filter((order) => order.status === "PENDING"),
    [pendingOrders],
  );
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <div className="flex flex-col gap-4">
      <AccountSummaryBand
        summary={accountSummary}
        transferringAllSwaps={transferringAllSwaps}
        onTransferAllSwaps={onTransferAllSwaps}
      />

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
            complexOrderKind={complexOrderKind}
            error={orderError}
            ifdExitPrice={ifdExitPrice}
            ifdExitType={ifdExitType}
            ifoStopLossPrice={ifoStopLossPrice}
            ifoTakeProfitPrice={ifoTakeProfitPrice}
            lastMessage={lastOrderMessage}
            mode={orderPanelMode}
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
            onComplexOrderKindChange={onComplexOrderKindChange}
            onModeChange={onOrderPanelModeChange}
            onQuantityChange={onQuantityChange}
            onOrderTypeChange={onOrderTypeChange}
            onSubmit={onSubmitOrder}
            onSubmitIfd={onSubmitIfdOrder}
            onSubmitIfo={onSubmitIfoOrder}
            onTriggerPriceChange={onTriggerPriceChange}
          />
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <QuickClosePanel
            error={quickCloseError}
            pair={quickClosePair}
            rates={rates}
            result={quickCloseResult}
            scope={quickCloseScope}
            submitting={quickCloseSubmitting}
            onPairChange={onQuickClosePairChange}
            onScopeChange={onQuickCloseScopeChange}
            onSubmit={onSubmitQuickClose}
          />
          <div className="grid min-w-0 gap-4 2xl:grid-cols-[minmax(0,1fr)_360px]">
            <PositionsTable
              positions={positions}
              selectedPositionId={selectedPosition?.id ?? null}
              onSelectPosition={onSelectPosition}
            />
            <PositionDetailPanel
              amendingExitOrderId={amendingExitOrderId}
              cancelingExitOrderId={cancelingExitOrderId}
              cancelingOcoGroupId={cancelingOcoGroupId}
              closingPositionId={closingPositionId}
              drafts={selectedPosition ? exitOrderDrafts[selectedPosition.id] ?? {} : {}}
              exitOrderAmendment={exitOrderAmendment}
              position={selectedPosition}
              submittingExitOrder={submittingExitOrder}
              submittingOcoPositionId={submittingOcoPositionId}
              transferringSwapPositionId={transferringSwapPositionId}
              onCancelExitOrder={onCancelExitOrder}
              onCancelOcoOrder={onCancelOcoOrder}
              onCloseExitOrderAmendment={onCloseExitOrderAmendment}
              onClose={onClosePosition}
              onExitOrderDraftChange={onExitOrderDraftChange}
              onExitOrderAmendmentChange={onExitOrderAmendmentChange}
              onSelectExitOrderForAmendment={onSelectExitOrderForAmendment}
              onSubmitExitOrder={onSubmitExitOrder}
              onSubmitExitOrderAmendment={onSubmitExitOrderAmendment}
              onSubmitOcoOrder={onSubmitOcoOrder}
              onTransferSwap={onTransferPositionSwap}
            />
          </div>
          <PendingOrdersPanel
            amendingOrderId={amendingPendingOrderId}
            cancelingOrderId={cancelingPendingOrderId}
            amendment={pendingOrderAmendment}
            orders={visiblePendingOrders}
            onCancel={onCancelPendingOrder}
            onAmendmentChange={onPendingOrderAmendmentChange}
            onCloseAmendment={onClosePendingOrderAmendment}
            onSelectAmendment={onSelectPendingOrderForAmendment}
            onSubmitAmendment={onSubmitPendingOrderAmendment}
          />
        </div>
      </div>
      </div>
    </div>
  );
}

export function HistoryScreen({
  accountSummary,
  cashActionError,
  cashActionMessage,
  cashTransactions,
  cashTransactionsError,
  cashTransactionsLoading,
  depositAmount,
  pendingOrderHistory,
  pendingOrderHistoryError,
  pendingOrderHistoryLoading,
  pnlSummary,
  pnlSummaryError,
  pnlSummaryLoading,
  trades,
  tradesError,
  tradesLoading,
  submittingCashAction,
  withdrawalAmount,
  onDepositAmountChange,
  onSelectPair,
  onSubmitCashAction,
  onWithdrawalAmountChange,
}: {
  accountSummary: AccountSummary | null;
  cashActionError: string | null;
  cashActionMessage: string | null;
  cashTransactions: CashTransaction[];
  cashTransactionsError: string | null;
  cashTransactionsLoading: boolean;
  depositAmount: string;
  pendingOrderHistory: PendingOrder[];
  pendingOrderHistoryError: string | null;
  pendingOrderHistoryLoading: boolean;
  pnlSummary: PnlSummary | null;
  pnlSummaryError: string | null;
  pnlSummaryLoading: boolean;
  trades: TradeSummary[];
  tradesError: string | null;
  tradesLoading: boolean;
  submittingCashAction: "DEPOSIT" | "WITHDRAWAL" | null;
  withdrawalAmount: string;
  onDepositAmountChange: (value: string) => void;
  onSelectPair: (currencyPair: string) => void;
  onSubmitCashAction: (type: "DEPOSIT" | "WITHDRAWAL") => void;
  onWithdrawalAmountChange: (value: string) => void;
}) {
  return (
    <div className="grid min-h-0 flex-1 gap-4 overflow-hidden xl:grid-cols-[minmax(0,1fr)_360px]">
      <div className="flex min-w-0 flex-col gap-4 overflow-hidden">
        <ExecutionHistoryPanel
          error={tradesError}
          loading={tradesLoading}
          trades={trades}
          onSelectPair={onSelectPair}
        />
        <OrderHistoryPanel
          error={pendingOrderHistoryError}
          loading={pendingOrderHistoryLoading}
          orders={pendingOrderHistory}
        />
      </div>
      <div className="flex min-w-0 flex-col gap-4 overflow-hidden">
        <PnlSummaryPanel
          accountSummary={null}
          error={pnlSummaryError}
          loading={pnlSummaryLoading}
          summary={pnlSummary}
        />
        <CashTransactionsPanel
          actionError={cashActionError}
          actionMessage={cashActionMessage}
          depositAmount={depositAmount}
          error={cashTransactionsError}
          loading={cashTransactionsLoading}
          summary={accountSummary}
          submittingAction={submittingCashAction}
          transactions={cashTransactions}
          withdrawalAmount={withdrawalAmount}
          onDepositAmountChange={onDepositAmountChange}
          onSubmit={onSubmitCashAction}
          onWithdrawalAmountChange={onWithdrawalAmountChange}
        />
        <FutureHistoryPanel />
      </div>
    </div>
  );
}

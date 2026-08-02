# 実装設計

本設計は `docs/requirements/issue-6-requirements-draft.md` を正式な入力とし、`CODEX.md`、`DESIGN.md`、既存 ADR、および現行実装に準拠する。

## 目的

Trading 画面から、利用者が指定した通貨ペアまたは既定デモ口座全体の OPEN 建玉を1操作で決済できるようにする。確認ダイアログは表示せず、決済可能な建玉を順次確定する部分成功とする。クイック全決済で作成した注文は `QUICK_CLOSE`／「クイック全決済」として通常の個別手動決済と履歴上で区別する。

既存の個別決済が行う価格決定、注文・約定作成、損益・スワップ反映、建玉 CLOSED 化、および未発動決済注文の EXPIRED 化は変更せず再利用する。

根拠:

- `docs/requirements/issue-6-requirements-draft.md`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`
- `docs/adr/ADR-0008_建玉消滅時の決済注文自動失効.md`

## 採用方針

1. **単一 API を採用する**  
   `POST /api/trade/positions/quick-close` を新設する。要件は既存個別決済 API の複数回呼び出しも許容するが、対象抽出、口座ロック、部分成功結果の集約をバックエンドへ集約できる単一 API を優先する。

2. **決済範囲は enum で表す**  
   `QuickCloseScope` に `PAIR` と `ACCOUNT` を定義する。`PAIR` の場合だけ `currencyPair` を必須とし、`ACCOUNT` では `currencyPair` を受け付けない。

3. **部分成功をトランザクション境界で保証する**  
   新設する `QuickCloseService` の公開オーケストレーションメソッドには `@Transactional` を付けない。口座ロックを保持したまま、別 Spring Bean である既存 `PositionService#closePositionForLockedAccount` を建玉ごとに呼ぶ。同メソッドの既存 `@Transactional` により1建玉ごとにコミットまたはロールバックし、ある建玉の失敗で成功済み建玉を巻き戻さない。

4. **対象集合は口座ロック内で確定する**  
   単一 API の処理開始後、既定口座ロック内で対象 OPEN 建玉 ID を `openedAt` 昇順に取得する。対象0件は 409 Conflict 相当の利用者向けエラーとし、決済処理を開始しない。

5. **個別決済ロジックを再利用する**  
   各対象 ID を `PositionService#closePositionForLockedAccount(id, OrderSource.QUICK_CLOSE)` へ渡す。価格、数量、損益、スワップ、注文、約定、建玉、決済注文の既存処理は複製しない。

6. **失敗を明細化して処理を継続する**  
   `ResponseStatusException` は理由を、その他の `RuntimeException` はログへ詳細を残して利用者向けの一般化した理由を失敗明細へ格納し、次の建玉へ進む。各失敗明細には少なくとも建玉 ID、通貨ペア、理由を含める。

7. **履歴由来を一貫して追加する**  
   `OrderSource` と `fx_orders_source_check` に `QUICK_CLOSE` を加える。注文一覧は既存 `OrderSummary.source` で識別できる。約定一覧は現行 `TradeSummaryResponse` に source がなく、History の `Source` 列も常に `--` のため、注文 ID に対応する `FxOrder.source` を `TradeSummaryResponse.source` へ加えて「クイック全決済」と表示する。既存 source も同じ経路で表示し、由来の欠落時は現行互換の `MANUAL` として扱う。

8. **UI は PositionsTable の行外へ置く**  
   Trading 画面右カラムの PositionsTable／PositionDetailPanel の上に専用 `QuickClosePanel` を置く。範囲、対象ペア、実行ボタン、実行中状態、結果を同じ領域にまとめ、一覧行へ操作を追加しない。確認ダイアログは実装しない。

9. **実行後はサーバー状態を再取得する**  
   成功・部分成功・失敗のいずれでも、建玉、注文、約定、損益サマリー、口座サマリーを再取得する。Equity 履歴は既存の定期記録・定期取得を維持する。

根拠:

- API 配置と POST 慣例: `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java`
- ロック・部分成功慣例: `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/AccountTradeLockService.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`
- UI 配置方針: `DESIGN.md`、`FX_trading_front/fx-demo-front/app/components/MarketMonitorScreens.tsx`

## 変更対象ファイル一覧

| ファイル | 変更内容 | 根拠 |
|---|---|---|
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/enums/OrderSource.java` | `QUICK_CLOSE` を追加 | 現行の注文由来 enum |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/schema/OrderSchemaInitializer.java` | `fx_orders_source_check` の許可値へ `QUICK_CLOSE` を追加 | PostgreSQL の既存 CHECK 制約更新方式 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java` | `POST /positions/quick-close` を追加し `QuickCloseService` へ委譲 | 建玉 API の既存配置先 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java` | 口座ロック内で対象 OPEN 建玉の最小情報を取得する read-only メソッドを追加 | 既定口座解決と建玉 Repository を既に保有 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/dto/TradeSummaryResponse.java` | 注文由来を表す `source` を加算的に追加 | 約定履歴の既存レスポンスに由来がない |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java` | 約定一覧取得時に注文 ID から `FxOrder.source` をまとめて解決し、約定レスポンスへ設定 | 約定一覧・注文 Repository の既存保有先 |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/trade/TradeExecutionServiceLossCutTest.java` | `TradeSummaryResponse` の source 追加に合わせて既存期待値を更新 | 同 DTO を直接構築する既存テスト |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceExitOrderTest.java` | `TradeSummaryResponse` の source 追加に合わせて既存 fixture を更新 | 同 DTO を直接構築する既存テスト |
| `FX_trading_front/fx-demo-front/lib/marketRateTicks.ts` | `QUICK_CLOSE` 型、約定 source、リクエスト・レスポンス型、API 関数を追加 | API 型と通信関数の既存集約先 |
| `FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx` | クイック全決済の状態、送信処理、結果・エラー管理、`QuickClosePanel` と約定 source 表示を追加 | Trading データ・操作状態と `ExecutionHistoryPanel` の既存管理先 |
| `FX_trading_front/fx-demo-front/app/components/MarketMonitorScreens.tsx` | `TradingScreen` の props と右カラムへの `QuickClosePanel` 配置を追加 | Trading レイアウトの既存構成先 |

変更しないファイル:

- `Position`、`FxOrder`、`Trade` などの Entity: 新しい永続フィールドは不要。
- `PositionRepository`: 既存の口座＋状態、口座＋通貨ペア＋状態の検索で対象抽出できる。
- `application.properties` / `application.yml`: 件数、閾値、タイムアウト等の新規設定は要件にない。
- `AccountTradeLockService`: 既存の reentrant な口座単位ロックをそのまま使う。

根拠: `PositionRepository.java`、`OrderSchemaInitializer.java`、`application.properties`、`AccountTradeLockService.java`。

## 新規作成ファイル一覧

| ファイル | 役割 |
|---|---|
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/enums/QuickCloseScope.java` | `PAIR` / `ACCOUNT` の入力範囲 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/QuickCloseRequest.java` | `scope` と `currencyPair` を受け取る |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/QuickCloseFailureResponse.java` | 失敗した建玉 ID、通貨ペア、理由 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/QuickCloseResponse.java` | 範囲、対象件数、成功件数、失敗件数、成功・失敗明細 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/model/QuickCloseTarget.java` | ロック内で確定した建玉 ID と通貨ペアの内部値 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/QuickCloseService.java` | 入力検証、口座ロック、対象抽出、個別決済反復、結果集約 |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/position/service/QuickCloseServiceTest.java` | 対象範囲、部分成功、由来、0件、競合のサービス単体テスト |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/trade/TradeExecutionServiceTradesTest.java` | 約定一覧への注文 source 一括解決と互換 fallback の単体テスト |

フロントエンドには現行のテストランナーがないため、本機能だけのためのテスト基盤やテストファイルは追加しない。`package.json` に test script／依存がないことを根拠とし、lint、build、手動受入確認で補う。

## クラス・モジュール構成

### バックエンド

- `PositionController`
  - HTTP 入出力だけを担当し、`QuickCloseService` に委譲する。
- `QuickCloseService`
  - 非トランザクションのオーケストレーター。
  - `AccountTradeLockService` で既定口座をロックする。
  - 入力検証、対象集合の確定、反復実行、失敗の局所化、レスポンス集約を担当する。
- `PositionService`
  - `findOpenQuickCloseTargetsForLockedAccount` で対象を read-only 取得する。
  - 既存 `closePositionForLockedAccount` で各建玉を独立トランザクションとして決済する。
  - 個別決済の戻り値を組み立てる際は、作成した注文の source を約定レスポンスにも渡す。
- `TradeExecutionService`
  - 約定一覧の `orderId` 群に対応する注文を一括取得し、`TradeSummaryResponse.source` を設定する。
  - 新規注文・ロスカットの即時レスポンスでも、作成済み `FxOrder.source` を約定レスポンスへ渡す。
- `QuickCloseScope`
  - API とサービスで共有する範囲 enum。
- `QuickCloseRequest` / `QuickCloseResponse` / `QuickCloseFailureResponse`
  - Entity を公開せず API 契約を表す。
- `QuickCloseTarget`
  - 失敗後も建玉 ID と通貨ペアを返せるよう、処理開始時の識別情報を保持する。

### フロントエンド

- `lib/marketRateTicks.ts`
  - `QuickCloseScope`、`QuickCloseRequest`、`QuickCloseFailure`、`QuickCloseResult` を定義する。
  - `OrderSource` 相当の union を `MANUAL | LOSS_CUT | TRIGGER | QUICK_CLOSE` として共通化し、`OrderSummary.source` と `TradeSummary.source` に用いる。
  - `quickClosePositions(request)` で単一 API を呼ぶ。
- `MarketMonitorDashboard`
  - scope、対象ペア、実行中、直近結果、エラーを保持する。
  - API 成功・失敗後の再取得を統括する。
  - `QuickClosePanel` を export する。
  - `ExecutionHistoryPanel` の既存 `Source` 列で `QUICK_CLOSE` を「クイック全決済」と表示する。
- `TradingScreen`
  - props を `QuickClosePanel` へ接続し、PositionsTable の上へ配置する。

根拠: `PositionController.java`、`PositionService.java`、`marketRateTicks.ts`、`MarketMonitorDashboard.tsx`、`MarketMonitorScreens.tsx`。

## メソッド・関数構成

### バックエンド

```java
// PositionController
@PostMapping("/positions/quick-close")
QuickCloseResponse quickClose(@RequestBody QuickCloseRequest request)
```

```java
// QuickCloseService: @Transactional を付けない
QuickCloseResponse quickClose(QuickCloseRequest request)
private QuickCloseResponse quickCloseForLockedAccount(QuickCloseRequest request)
private void validateRequest(QuickCloseRequest request)
private String failureReason(RuntimeException exception)
```

```java
// PositionService
@Transactional(readOnly = true)
List<QuickCloseTarget> findOpenQuickCloseTargetsForLockedAccount(
    QuickCloseScope scope,
    String currencyPair
)

// 既存メソッドを変更せず利用
@Transactional
PositionCloseResponse closePositionForLockedAccount(
    Long id,
    OrderSource source
)
```

`QuickCloseService#quickClose` は `withAccountLock(DEFAULT_ACCOUNT_NUMBER, ...)` を呼ぶだけとし、`quickCloseForLockedAccount` 内で対象を確定する。各 `closePositionForLockedAccount` 呼び出しは `QuickCloseService` から Spring proxy 経由で `PositionService` に入るため、建玉ごとのトランザクションになる。

```java
// TradeExecutionService / PositionService
TradeSummaryResponse toTradeResponse(Trade trade, OrderSource source)
```

`TradeExecutionService#getTrades` は取得した約定の `orderId` を重複排除し、`FxOrderRepository#findAllById` で注文を一括取得して ID・source の Map を作る。約定ごとの追加 SQL を避け、対応注文が取得できない場合は既存の null source 互換と同様に `MANUAL` を設定する。`TradeSummaryResponse` の末尾に `String source` を追加し、既存フィールドの意味と順序は保つ。

### フロントエンド

```ts
type QuickCloseScope = "PAIR" | "ACCOUNT";

type QuickCloseRequest = {
  scope: QuickCloseScope;
  currencyPair?: string;
};

async function quickClosePositions(
  request: QuickCloseRequest,
): Promise<QuickCloseResult>;
```

```ts
// MarketMonitorDashboard
async function submitQuickClose(): Promise<void>;
async function reloadTradeStateAfterQuickClose(): Promise<void>;
```

`QuickClosePanel` の props は scope、pair、rates、submitting、result、error と各変更・実行 callback とする。

根拠: `PositionController#closePosition`、`AccountTradeLockService#withAccountLock`、`PositionService#closePositionForLockedAccount`、`marketRateTicks#closePosition`、`MarketMonitorDashboard#closeSelectedPosition`。

## 処理フロー

1. 利用者が `QuickClosePanel` で `PAIR` または `ACCOUNT` を選ぶ。
2. `PAIR` の場合は同パネル内の通貨ペア selector で対象を選ぶ。Trading 画面の `tradingActivePair` とは独立した state を使う。
3. 実行ボタン押下後、確認ダイアログを挟まず、ボタンを disabled／実行中表示にする。
4. フロントエンドが `POST /api/trade/positions/quick-close` を1回呼ぶ。
5. Controller が request を `QuickCloseService` へ渡す。
6. Service が request の組み合わせを検証する。
7. Service が既定口座ロックを取得する。
8. ロック内で scope に応じた OPEN 建玉 ID／通貨ペアを `openedAt` 昇順で確定する。
9. 対象0件なら 409 を送出し、永続データを変更しない。
10. 各対象について `closePositionForLockedAccount(id, QUICK_CLOSE)` を順に呼ぶ。
11. 成功は `PositionCloseResponse` に追加する。失敗は当該建玉だけロールバックし、`QuickCloseFailureResponse` に追加して次へ進む。
12. 対象件数、成功件数、失敗件数、成功・失敗明細を返す。
13. UI は成功／部分成功／全体失敗を表示する。失敗があれば建玉 ID・通貨ペア・理由を必ず表示する。成功明細の決済価格・損益は専用パネルには表示せず、既存 History／P&L で確認する最小構成とする。
14. UI は建玉、注文、約定、損益サマリー、口座サマリーを再取得する。

根拠: `TradeExecutionService#liquidateAllPositionsIfStillUnsafe` の反復・継続処理、`PositionService#closePositionForLockedAccount`、`MarketMonitorDashboard#closeSelectedPosition`。

## データフロー

```text
QuickClosePanel
  └─ { scope, currencyPair? }
      └─ POST /api/trade/positions/quick-close
          └─ PositionController
              └─ QuickCloseService
                  ├─ AccountTradeLockService
                  ├─ PositionService.findOpenQuickCloseTargetsForLockedAccount
                  └─ PositionService.closePositionForLockedAccount(..., QUICK_CLOSE)
                      ├─ CurrencyPairRepository / MarketRateRepository
                      ├─ FxOrderRepository / TradeRepository
                      ├─ PositionRepository / AccountRepository
                      ├─ SwapRealizationRepository
                      └─ TriggerOrderRepository
          └─ QuickCloseResponse
              ├─ successes: PositionCloseResponse[]
              └─ failures: QuickCloseFailureResponse[]
      └─ QuickClosePanel result
      └─ positions / orders / trades / pnl / account 再取得
```

永続データの変更は既存個別決済経路に限定する。新しいテーブル・カラムは追加しない。変更される既存データは `fx_orders`、`trades`、`positions`、`accounts`、`swap_realizations`、`trigger_orders` であり、内容は現行個別決済と同じで、`fx_orders.source` だけが `QUICK_CLOSE` となる。

履歴再取得時は `trades.order_id` から `fx_orders.id` を一括参照し、注文の source を `TradeSummaryResponse.source` として返す。フロントエンドは値を表示名へ変換するだけで、由来を推測しない。

根拠: `PositionService.java` の `createExecutedOrder`、`createTrade`、`reflectRealizedPnl`、`recordRealizedSwap`、`expirePendingExitOrders`、`Trade.java` の `orderId`、`TradeExecutionService#getTrades`。

## 入力チェック

| 条件 | 結果 |
|---|---|
| request が null、scope が null | 400 Bad Request |
| scope=`PAIR` かつ currencyPair が null／blank | 400 Bad Request |
| scope=`ACCOUNT` かつ currencyPair が指定される | 400 Bad Request |
| scope=`PAIR` かつ通貨ペアが未登録または disabled | 404 Not Found。既存の通貨ペア検証に合わせる |
| 対象 OPEN 建玉が0件 | 409 Conflict。利用者向け「対象となるOPEN建玉がありません。」 |
| 対象建玉が処理時点で CLOSED | その建玉を失敗明細へ記録し、他を継続 |
| 最新レートなし | その建玉を失敗明細へ記録し、他を継続 |

通貨ペア文字列の独自正規化や大文字小文字変換は追加せず、既存の `CurrencyPairRepository#findBySymbol` と UI が使用する `USD/JPY` 形式に合わせる。

根拠: `PositionService#closePositionForLockedAccount`、`TradeExecutionService#validateRequest`、`PendingOrderRequest`、`ApiExceptionHandler`。

## エラー処理

- API 全体の入力不正・対象0件は既存 `ResponseStatusException` と `ApiExceptionHandler.ApiErrorResponse(message)` で返す。
- 対象確定後の各建玉エラーは HTTP 全体エラーにせず、`QuickCloseFailureResponse` に格納する。
- 1件以上成功し、1件以上失敗した場合も HTTP 200 とし、`successCount` / `failureCount` と明細で部分成功を表す。
- 対象が存在したが全件失敗した場合も集約結果を返す。対象ごとの理由を失わないため HTTP 200 とし、`successCount=0`、`failureCount=targetCount` で全体失敗を表す。
- `ResponseStatusException` の `reason` は失敗理由に使う。
- 予期しない `RuntimeException` はサーバーログへ stack trace と positionId を記録し、レスポンスには内部情報を含めず「決済処理に失敗しました。」とする。
- 各建玉の既存 `@Transactional` により、当該建玉の注文・約定・建玉・口座・スワップ・決済注文更新を一体でロールバックする。
- フロントエンドは API 自体のエラーを既存 `getErrorMessage` で表示し、集約レスポンスの failures は専用結果領域に建玉 ID、通貨ペア、理由を表示する。

根拠: `ApiExceptionHandler.java`、`PositionService.java`、`TradeExecutionService.java`、`MarketMonitorDashboard.tsx` の既存 error state。

## 既存機能への影響

- **個別決済**: `POST /positions/{id}/close` と `OrderSource.MANUAL` は変更しない。クイック全決済は同じサービス処理を別 source で呼ぶ。
- **ロスカット**: `LOSS_CUT` と維持率再確認は変更しない。双方が同じ `AccountTradeLockService` を使うため競合時も直列化される。
- **TP/SL/OCO/IFD/IFO**: 建玉クローズ後の PENDING／WAITING 決済注文 EXPIRED 化を既存のまま実行する。
- **注文・約定履歴**: 注文 API は既存 `OrderSummary.source` で `QUICK_CLOSE` を返す。約定 API は `TradeSummaryResponse.source` を加算し、既存 `ExecutionHistoryPanel` の `Source` 列へ「クイック全決済」を表示する。既存3値の意味は変えない。
- **損益・スワップ・証拠金**: 個別決済経路の反映を再利用し、計算式や scale は変えない。
- **Equity 履歴**: 専用記録を追加せず、既存スケジュールを維持する。
- **性能**: 建玉数に比例して個別トランザクションが発生する。単一 API でネットワーク往復を1回にし、処理中は同一口座ロックを保持する。

根拠: `PositionService.java`、`TradeExecutionService.java`、`TriggerOrderService.java`、`EquitySnapshotRecorder.java`、`ADR-0008`。

## 後方互換性

- 既存 API の URL と request は変更しない。
- ただし既存約定レスポンスには末尾フィールド `source` を加算する。JSON 利用側には加算的変更だが、Java の record コンストラクターを直接使う既存テスト fixture は追随が必要となる。
- `OrderSource` への `QUICK_CLOSE` 追加は加算的変更とし、既存値を改名・削除しない。
- `fx_orders_source_check` は `OrderSchemaInitializer` の既存方式で4値を許可するよう再作成する。既存行の値は変更しない。
- `OrderSummary.source` と新設する `TradeSummary.source` は同じ TypeScript union を使い、既存 source の表示を維持する。
- Entity・テーブル・カラムの追加削除は行わない。
- `QuickCloseResponse` は新規 API 専用とし、`PositionCloseResponse` を変更しない。
- フロントエンドの既存 `closePosition`、個別決済 UI、保存済み画面・通貨ペア localStorage key は変更しない。

根拠: `OrderSource.java`、`OrderSchemaInitializer.java`、`marketRateTicks.ts`、`PositionController.java`。

## セキュリティ上の考慮

- クライアントから accountId、数量、決済価格、建玉 ID 一覧を受け取らない。既定デモ口座とサーバー抽出結果だけを対象にする。
- `PAIR` の通貨ペアはサーバー側で登録・enabled を検証する。
- 各建玉は既存 `PositionService` で既定口座所属と OPEN 状態を再検証し、他口座 ID の直接操作経路を増やさない。
- 予期しない例外の stack trace、SQL、内部クラス名をレスポンスへ出さない。
- 現行 `SecurityConfig` の `/api/**` permitAll というデモ用方針は変更しない。認証・認可の新設は本 Issue の対象外であり、本番利用可能という意味にはしない。
- 確認ダイアログを設けないことは正式要件であるため、二重送信防止として実行中は操作を disabled にする。

根拠: `SecurityConfig.java`、`PositionService#closePositionForLockedAccount`、`ApiExceptionHandler.java`、`CODEX.md`。

## テスト設計

### バックエンド単体テスト

`QuickCloseServiceTest` を Mockito で作成する。

1. `ACCOUNT` で複数ペア・LONG/SHORT の対象を `openedAt` 順に `QUICK_CLOSE` で決済する。
2. `PAIR` で指定ペアだけを処理し、他ペアを呼ばない。
3. 1件が `ResponseStatusException` でも後続を処理し、成功・失敗件数と失敗 ID／ペア／理由を返す。
4. 予期しない RuntimeException を一般化した失敗理由へ変換し、後続を処理する。
5. 対象0件で 409 を返し、個別決済を呼ばない。
6. request null、scope null、PAIR のペア欠落、ACCOUNT の余分なペアを 400 にする。
7. 未登録・disabled ペアを 404 にする。
8. `AccountTradeLockService` 内で対象抽出と決済が行われる。

### 既存テストへの追加・回帰

- `TradeExecutionServiceLossCutTest`: `TradeSummaryResponse` fixture に source を加え、`OrderSource.LOSS_CUT` の既存期待が変わらないことを維持する。
- `TriggerOrderServiceExitOrderTest`: `TradeSummaryResponse` fixture に source を加え、建玉クローズ時の決済注文 EXPIRED 化の既存観点を回帰実行する。
- `TradeExecutionServiceTradesTest`: 複数約定の注文を一括解決し、`MANUAL`、`LOSS_CUT`、`TRIGGER`、`QUICK_CLOSE` を正しく返すこと、および注文欠落・null source を `MANUAL` とすることを確認する。
- `FxdemoApplicationTests`: `QUICK_CLOSE` を含む schema initializer 後に context が起動することを確認する。
- 必要に応じて `PositionService` のテストを新設せず、既存決済ロジックは上記回帰とクイック全決済サービスの委譲確認で保護する。

### フロントエンド検証

現行に自動テスト基盤がないため、次を lint、build、手動受入で確認する。

- scope と対象ペアを操作内で選択でき、Trading 選択ペアを変えない。
- PAIR／ACCOUNT とも確認ダイアログなしで1回だけ API を呼ぶ。
- 実行中は再操作できない。
- 全件成功、一部失敗、全件失敗、対象0件 API エラーを区別する。
- 一部失敗で建玉 ID、通貨ペア、理由を表示する。
- 実行後に建玉、注文、約定、損益、口座を再取得する。
- History に「クイック全決済」が表示される。
- 既存 source は従来の意味に対応する表示となり、由来不明時は `MANUAL` 相当の表示となる。

実行コマンド:

- バックエンド: `.\gradlew.bat test`
- フロントエンド: `npm run lint`、`npm run build`

根拠: `build.gradle`、`package.json`、既存 `TradeExecutionServiceLossCutTest.java`、`MarketMonitorDashboard.tsx`。

## 実装順序

1. `QuickCloseScope` と3つの API DTO、`QuickCloseTarget` を追加する。
2. `OrderSource.QUICK_CLOSE` と DB CHECK 制約を同時に追加する。
3. `PositionService` にロック内対象取得メソッドを追加する。
4. `QuickCloseService` を追加し、非トランザクションの口座ロック＋建玉単位トランザクションを実装する。
5. `PositionController` に単一 POST API を追加する。
6. `QuickCloseServiceTest` と既存回帰テストを実行する。
7. `TradeSummaryResponse`、`TradeExecutionService`、`PositionService` に約定 source の伝搬を追加し、既存 fixture と約定一覧テストを更新する。
8. `marketRateTicks.ts` に共通 source 型、クイック全決済の型・API 関数を追加する。
9. `MarketMonitorDashboard.tsx` に state、handler、結果パネル、約定 source 表示を追加する。
10. `MarketMonitorScreens.tsx` に props 接続と配置を追加する。
11. バックエンド test、フロントエンド lint/build、手動受入を順に実施する。

各段階で既存個別決済・ロスカットを変更せず、差分を局所化する。

根拠: 依存順は DTO／enum → service → controller → client type/API → state/UI である。

## 懸念事項

未確定の技術課題はない。調査時に懸念として挙げた事項は、次の設計判断として確定し、該当する採用方針、処理フロー、エラー処理、後方互換性、テスト設計へ反映済みである。

### Codex判断で確定した事項

1. **部分成功は非トランザクションのオーケストレーターと建玉単位トランザクションで実現する**
   - 判断内容: `QuickCloseService` の一括処理には `@Transactional` を付けず、口座ロック内から別 Spring Bean の `PositionService#closePositionForLockedAccount` を1建玉ずつ呼ぶ。各呼び出しを独立した既存 `@Transactional` 境界とし、失敗した建玉だけをロールバックする。
   - 判断理由: 正式要件の部分成功を満たしつつ、既存の個別決済処理とトランザクションを変更せず再利用できる。成功済み建玉を後続失敗で巻き戻さず、変更範囲もサービス追加と委譲に限定できる。
   - 根拠: `docs/requirements/issue-6-requirements-draft.md` の部分成功要件、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java` の `closePositionForLockedAccount` に付与された `@Transactional`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java` のロスカット継続処理。

2. **処理中は既存の口座ロックを保持し、専用の件数・時間上限は追加しない**
   - 判断内容: 対象抽出から全対象の処理完了まで `AccountTradeLockService` の同一口座ロックを保持する。初期実装ではクイック全決済専用の件数上限、タイムアウト、並列処理を設けない。
   - 判断理由: 対象集合と決済状態の競合を防ぎ、現行ロスカットと同じ直列化方式を維持できる。要件に根拠のある上限値がなく、独自上限を設けると「全決済」を満たさない場合が生じる。
   - 根拠: `AccountTradeLockService.java` の口座単位 `ReentrantLock`、`TradeExecutionService#liquidateAllPositionsIfStillUnsafe`、`PositionRepository#findByAccountIdAndStatusOrderByOpenedAtAsc`、`docs/requirements/issue-6-requirements-draft.md` の「件数・処理時間の新しい上限は設けない」。

3. **`QUICK_CLOSE` の enum と DB CHECK 制約は同一実装単位で追加する**
   - 判断内容: `OrderSource.QUICK_CLOSE` と `OrderSchemaInitializer` の `fx_orders_source_check` 許可値を同時に変更し、どちらか一方だけを先行適用しない。
   - 判断理由: Java 側だけに値を追加すると現行 DB 制約が保存を拒否する。既存の schema initializer に値を加える方法が最小変更であり、新しいカラムや移行方式を必要としない。
   - 根拠: `OrderSource.java` の既存3値、`OrderSchemaInitializer.java` の `fx_orders_source_check` 再作成処理、要件ドラフトの注文由来 `QUICK_CLOSE`。

4. **対象存在後の全件失敗も集約レスポンスとして HTTP 200 で返す**
   - 判断内容: 対象0件は 409 Conflict とする。一方、対象確定後に一部または全建玉が失敗した場合は HTTP 200 の `QuickCloseResponse` を返し、`successCount`、`failureCount`、失敗明細で結果を表す。
   - 判断理由: 全件失敗でも建玉ごとの識別情報と理由を失わず、一部失敗と同じレスポンス契約で UI が処理できる。対象0件だけは正式要件どおり「処理対象なし」の利用者向けエラーとして区別する。
   - 根拠: `docs/requirements/issue-6-requirements-draft.md` の対象0件エラー、部分成功、失敗識別情報・理由の表示要件、既存 DTO 方針を示す `CODEX.md`、`PositionCloseResponse.java`。

5. **失敗理由は既知の利用者向け理由だけを返し、予期しない例外は一般化する**
   - 判断内容: `ResponseStatusException#getReason()` が非空なら失敗理由に用い、空なら一般文言へフォールバックする。その他の `RuntimeException` は positionId と stack trace をサーバーログへ記録し、レスポンスには「決済処理に失敗しました。」のみを返す。
   - 判断理由: 既存の利用者向けエラー形式と整合し、内部例外、SQL、クラス名などの漏えいを避けながら必須の失敗理由を提供できる。
   - 根拠: `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/exception/ApiExceptionHandler.java`、`PositionService.java` の `ResponseStatusException` 文言、要件ドラフトの失敗理由表示要件。

6. **成功明細は専用パネルに追加せず、件数と既存 History／P&L を利用する**
   - 判断内容: `QuickClosePanel` は対象・成功・失敗件数と必須の失敗明細を表示する。成功建玉ごとの決済価格・損益は同パネルに重複表示せず、再取得後の既存約定履歴と P&L 表示で確認する。
   - 判断理由: 成功時の決済価格・損益表示は正式要件で実装裁量とされている。既存表示を再利用する方が UI 変更を小さく保ち、同じ情報の二重表示を避けられる。
   - 根拠: `docs/requirements/issue-6-requirements-draft.md` の人間回答8、`FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx` の `ExecutionHistoryPanel` と既存 P&L 表示、同ファイルのデータ再取得処理。

7. **API 方式は単一 API に確定する**
   - 判断内容: `POST /api/trade/positions/quick-close` を採用し、フロントエンドから既存個別決済 API を複数回呼ぶ代替方式は実装しない。
   - 判断理由: 正式要件が単一 API を優先しており、対象抽出、口座ロック、部分成功、結果集約をサーバー側の1操作として一貫させられる。複数呼び出し方式を併設しないことで実装・テスト範囲も最小化できる。
   - 根拠: `docs/requirements/issue-6-requirements-draft.md` の人間回答6と「AI判断で確定した事項」、`PositionController.java` の既存 `/api/trade/positions` 配置、`TradeExecutionService.java` のサーバー側全建玉処理。

8. **約定履歴の由来は既存注文を参照し、Entity へ重複保存しない**
   - 判断内容: `TradeSummaryResponse` に加算的な `source` フィールドを追加し、`Trade.orderId` に対応する `FxOrder.source` を約定一覧取得時に一括解決する。`Trade` Entity や DB カラムは変更しない。対応注文または source が欠ける既存データは `MANUAL` として返す。
   - 判断理由: 現行 `ExecutionHistoryPanel` の `Source` 列は常に `--` であり、履歴識別要件には source の伝搬が必要である。由来は既に注文へ保存されるため、約定への重複保存を避ける方が正規化と最小変更の両方に適う。既存の `toOrderResponse` も null source を `MANUAL` としており、同じ互換規則を適用できる。
   - 根拠: `TradeSummaryResponse.java`、`Trade.java` の `orderId`、`FxOrder.java` の source、`TradeExecutionService#getTrades` と `toOrderResponse`、`MarketMonitorDashboard.tsx` の `ExecutionHistoryPanel`、要件ドラフトの `QUICK_CLOSE` 履歴識別要件。

### 判断保留

なし。Issue、正式要件、既存実装の間に重大な矛盾は確認できず、業務値、認証・権限、データ削除、破壊的な後方互換性、または大規模な画面方針の追加判断を必要とする残存事項もない。

## 要件との対応表

| 要件 | 設計上の対応 | 主な対象ファイル |
|---|---|---|
| ペア単位の全決済 | `scope=PAIR` と独立したペア selector、サーバー側ペア抽出 | `QuickCloseRequest`、`QuickCloseService`、`QuickClosePanel` |
| 口座全体の全決済 | `scope=ACCOUNT` と口座内 OPEN 建玉抽出 | `QuickCloseService`、`PositionService` |
| 1操作、単一 API 優先 | `POST /api/trade/positions/quick-close` | `PositionController`、`marketRateTicks.ts` |
| 確認ダイアログなし | 実行ボタンから直接 API、実行中 disabled | `MarketMonitorDashboard.tsx` |
| 建玉全数量、最新決済側価格 | 既存個別決済を再利用 | `PositionService.java` |
| LONG=Bid、SHORT=Ask、scale/HALF_UP | 既存 `executionPrice` と丸めを再利用 | `PositionService.java` |
| 損益・スワップ・注文・約定・CLOSED | 既存個別決済トランザクションを再利用 | `PositionService.java` |
| 未発動決済注文 EXPIRED | 既存 `expirePendingExitOrders` を再利用 | `PositionService.java` |
| 部分成功 | 非トランザクション orchestrator＋建玉単位 transaction | `QuickCloseService`、`PositionService` |
| 対象0件は利用者向けエラー | 対象確定直後に 409 | `QuickCloseService`、`ApiExceptionHandler` |
| 失敗建玉の識別情報と理由 | failure DTO と UI 結果領域 | `QuickCloseFailureResponse`、`QuickClosePanel` |
| 成功詳細表示は任意 | 専用パネルでは省略し既存 History/P&L を利用 | `MarketMonitorDashboard.tsx` |
| 履歴上の専用識別 | 注文 source に `QUICK_CLOSE` を保存し、約定取得時に `TradeSummaryResponse.source` へ伝搬して「クイック全決済」と表示 | `OrderSource`、`OrderSchemaInitializer`、`TradeSummaryResponse`、`TradeExecutionService`、`marketRateTicks.ts`、`ExecutionHistoryPanel` |
| 競合・二重決済防止 | 既存口座ロックと OPEN 再検証 | `AccountTradeLockService`、`PositionService` |
| 実行後の再同期 | trade state の関連 loader を再実行 | `MarketMonitorDashboard.tsx` |
| Equity は既存周期 | 専用スナップショットなし | `EquitySnapshotRecorder.java`（変更なし） |
| 既存 API・Entity の互換性 | 加算的 API／enum／型変更、Entity変更なし | 上記変更対象一式 |

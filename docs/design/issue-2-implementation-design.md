# 実装設計

本設計は `docs/requirements/issue-2-requirements.md` を正式な入力とし、`CODEX.md`、`DESIGN.md` および既存 ADR に準拠する。

## 目的

取消と再発注を行わず、未発動注文を安全に訂正できるようにする。初期実装では、仕様が確定している次の範囲だけを扱う。

- 単独の通常予約注文（ENTRY、`PENDING`、親注文・OCO グループなし）のトリガー価格および数量
- OPEN 建玉に紐づく単独 TP/SL（EXIT、`PENDING`、親注文・OCO グループなし）のトリガー価格
- 上記のバックエンド API と Trading 画面からの訂正操作

TP/SL 数量変更、OCO、IFD/IFO、訂正履歴の永続化・表示は重大な仕様判断が必要なため初期実装に含めない。`WAITING` は既存 UI で表示されず、現行の複合注文生成でも子注文は `PENDING` として作られるため、互換性と最小変更を優先して初期実装の訂正対象外と確定する。対象外の注文を汎用 API から誤って訂正できないよう、サーバー側で関係属性を検証して拒否する。

## 採用方針

1. **同一注文 ID の部分更新**  
   `TriggerOrder` の `quantity` / `triggerPrice` だけを更新し、ID、作成日時、通貨ペア、売買区分、注文種別、目的、TP/SL 種別および関連 ID は維持する。`BaseEntity.updatedAt` は JPA の `@PreUpdate` により更新されるが、専用の訂正履歴とはみなさない。

2. **PATCH と既存リソース階層**  
   通常予約注文は `PATCH /api/trade/orders/pending/{id}`、単独 TP/SL は `PATCH /api/trade/positions/{positionId}/exit-orders/{exitOrderId}` とする。成功時は既存の `PendingOrderResponse` / `PositionExitOrderResponse` を再利用する。

3. **通常予約注文は真の部分更新**  
   価格のみ、数量のみ、または両方を受け付ける。JSON の「項目省略」と「明示的 null」を区別する必要があるため、通常の訂正 DTO は record ではなく Jackson の setter で入力有無フラグを保持する小さな DTO クラスとする。明示的 null は Bad Request、両項目省略も Bad Request とする。

4. **単独 TP/SL は価格だけ**  
   現行の決済処理は `PositionService.closePositionForLockedAccount` により建玉全量を決済するため、初期実装では数量をリクエスト DTO に持たせない。数量指定を含む未知プロパティは Jackson の既存設定に従うだけにせず、DTO または入力処理で拒否し、「数量訂正は未対応」と明示する。

5. **口座ロックとトランザクション**  
   公開サービスメソッドを `@Transactional` とし、既存の `AccountTradeLockService.withAccountLock(DEFAULT_ACCOUNT_NUMBER, ...)` 内で、ID により注文を再取得してから全検証・更新・保存を行う。監視の `evaluatePendingOrder` と同じロックを使うため、訂正と発動は単一アプリケーション内で直列化される。新しいロック方式や DB バージョン列は追加しない。

6. **新規登録と同じ正規化・価格方向検証**  
   保存前に `CurrencyPair.priceScale` / `quantityScale` と `RoundingMode.HALF_UP` を適用する。ENTRY は BUY=Ask / SELL=Bid を基準とする既存 `validateTriggerDirection`、EXIT は LONG=Bid / SHORT=Ask を基準とする既存 `validateExitDirection` を再利用する。

7. **UI は選択から詳細操作へ**  
   `ADR-0012` の「表は情報表示、操作は詳細パネル」を守る。Pending orders の行を選択すると詳細・訂正フォームを表示し、建玉の TP/SL は既存の建玉詳細内で訂正する。行内には操作を増殖させない。数量列は注文内容の確認に必要な情報として追加する。

8. **複合注文と履歴は判断保留、WAITING は対象外**  
   OCO の訂正単位、IFD/IFO 親子訂正、TP/SL 数量訂正、専用履歴テーブルは判断保留とし、本設計の汎用 API を流用して迂回できないようにする。`WAITING` は保留事項ではなく初期実装の対象外と確定し、409 で拒否する。

判断根拠は `TriggerOrderService.java`（登録・取消・検証・発動・親子バインド）、`TriggerOrder.java`、`AccountTradeLockService.java`、`TriggerOrderMonitor.java`、`PositionService.java`、`ADR-0009_OCOグループID方式と状態区別.md`、`ADR-0012_UI3画面分離と情報操作分離.md` である。

## 変更対象ファイル一覧

実装時に変更する既存ファイルは次のとおり。不要な Entity、Repository、設定、監視間隔の変更は行わない。

| ファイル | 変更内容 | 根拠 |
|---|---|---|
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/api/TradeController.java` | 通常予約注文の PATCH エンドポイント追加 | 既存の pending 注文 API の配置先 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java` | 単独 TP/SL の PATCH エンドポイント追加 | 既存の建玉・決済注文 API の配置先 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java` | ロック内訂正、対象関係・状態検証、正規化、方向検証、保存処理を追加 | 注文の生成・取消・監視・約定を集約している既存サービス |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java` | 建玉レスポンス内の決済注文へ `parentOrderId` をマッピング | バインド済み IFD/IFO 子を UI で識別するため |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/PositionExitOrderResponse.java` | 加算的な `parentOrderId` フィールドを追加 | 単独 TP/SL と IFD/IFO 子の編集可否を区別するため |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/exception/ApiExceptionHandler.java` | JSON 数値形式不正を既存 `ApiErrorResponse(message)` の 400 応答へ統一 | 現在は `ResponseStatusException` のみ共通形式化 |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceExitOrderTest.java` | 単独 EXIT 訂正と既存全量決済・OCO/IFD/IFO拒否の回帰観点を追加 | 既存の TP/SL、OCO、IFD/IFO のサービス単体テスト |
| `FX_trading_front/fx-demo-front/lib/marketRateTicks.ts` | 訂正リクエスト型と PATCH API 関数を追加 | API 型・通信処理の既存集約先 |
| `FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx` | 選択・入力・送信中・エラー状態、成功後の再取得処理を追加 | Trading データと注文操作の状態管理先 |
| `FX_trading_front/fx-demo-front/app/components/MarketMonitorScreens.tsx` | Pending orders 選択と詳細訂正フォームへの props 接続 | 3画面レイアウトの構成先 |
`TriggerOrder.java`、`TriggerOrderRepository.java`、`application.properties` / `application.yml` は変更しない。既存フィールドと `JpaRepository.save` で実現でき、新規設定も不要なためである。

## 新規作成ファイル一覧

| ファイル | 役割 |
|---|---|
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/dto/PendingOrderAmendRequest.java` | 通常予約注文の `triggerPrice` / `quantity` と各項目の指定有無を保持 |
| `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/PositionExitOrderAmendRequest.java` | 単独 TP/SL の訂正後 `triggerPrice` を保持 |
| `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceAmendmentTest.java` | 通常予約注文の訂正、競合、入力検証を集中的にテスト |

フロントエンドにはテストランナーがなく、`package.json` に test script やテスト依存もない。初期実装のためだけに基盤を追加せず、型検査を含む build、lint、および手動受入確認を行う。テスト基盤を導入する場合は別変更とする。

## クラス・モジュール構成

### バックエンド

- `TradeController`
  - 通常予約注文 PATCH を受け、`TriggerOrderService` に委譲する。
- `PositionController`
  - 単独 TP/SL PATCH を受け、`TriggerOrderService` に委譲する。
- `PendingOrderAmendRequest`
  - `BigDecimal quantity`、`BigDecimal triggerPrice`
  - `quantitySpecified`、`triggerPriceSpecified`
  - Jackson setter 呼出しで指定有無を記録し、省略と null を区別する。
- `PositionExitOrderAmendRequest`
  - 必須の `BigDecimal triggerPrice`
  - 未知項目、とくに `quantity` は拒否対象とする。
- `TriggerOrderService`
  - 公開メソッドでトランザクションと口座ロックを開始する。
  - locked メソッドで対象を再取得し、分類・状態・関連・入力・市場レートを検証する。
  - 既存の private 検証・レスポンス変換を再利用する。
- `ApiExceptionHandler`
  - Spring/Jackson の JSON 変換失敗を Bad Request + `ApiErrorResponse(message)` に変換する。

### フロントエンド

- `lib/marketRateTicks.ts`
  - `PendingOrderAmendment` 型
  - `amendPendingOrder(id, amendment)`
  - `amendPositionExitOrder(positionId, exitOrderId, triggerPrice)`
- `MarketMonitorDashboard`
  - 選択中注文、編集中値、送信対象 ID、訂正エラーを管理する。
  - 成功後に pending orders、positions、order history を再取得する。
- `MarketMonitorScreens` / `PendingOrdersPanel`
  - PENDING の単独 ENTRY の選択 UI と詳細フォームを表示する。
  - 一覧内の `parentOrderId` から IFD/IFO 親を識別し、単独 ENTRY だけを選択可能にする。
  - 建玉詳細では `PositionExitOrderResponse.parentOrderId` も確認し、単独 TP/SL だけに Edit 導線を表示する。OCO および IFD/IFO 子には表示しない。

## メソッド・関数構成

### バックエンド

| メソッド | シグネチャ案 | 責務 |
|---|---|---|
| `TradeController.amendPendingOrder` | `(Long id, PendingOrderAmendRequest request) -> PendingOrderResponse` | PATCH の受付と委譲 |
| `PositionController.amendExitOrder` | `(Long positionId, Long exitId, PositionExitOrderAmendRequest request) -> PositionExitOrderResponse` | 建玉階層 PATCH の受付と委譲 |
| `TriggerOrderService.amendPendingOrder` | `(Long id, PendingOrderAmendRequest request) -> PendingOrderResponse` | `@Transactional`、口座ロック開始 |
| `TriggerOrderService.amendPendingOrderLocked` | 同上 | 再取得、単独 ENTRY 判定、状態・入力・レート検証、保存 |
| `TriggerOrderService.amendExitOrder` | `(Long positionId, Long exitId, PositionExitOrderAmendRequest request) -> PositionExitOrderResponse` | `@Transactional`、口座ロック開始 |
| `TriggerOrderService.amendExitOrderLocked` | 同上 | 再取得、建玉・単独 EXIT 判定、状態・価格・レート検証、保存 |
| `validateAmendablePendingEntry` | `(TriggerOrder order) -> void` | `PENDING`、ENTRY、親・対象建玉・OCO なしを確認 |
| `validateAmendableStandaloneExit` | `(TriggerOrder order, Position position) -> void` | `PENDING`、EXIT、対象建玉一致、親・OCO なし、OPEN・口座一致を確認 |
| `normalizePositive` | 既存 scale 情報と値を受ける private helper | 正数、scale、precision を一貫して検証・正規化 |

既存 `validateTriggerDirection`、`validateExitDirection`、`toResponse`、`toExitOrderResponse`、`pendingStatuses` を必要に応じて共通利用する。ただし訂正可能状態は初期段階では `PENDING` のみであり、`pendingStatuses()` をそのまま訂正判定に使わない。

### フロントエンド

| 関数 | 責務 |
|---|---|
| `amendPendingOrder` | 指定された項目だけを JSON に含めて PATCH |
| `amendPositionExitOrder` | triggerPrice を PATCH |
| `submitPendingOrderAmendment` | 画面入力検証、二重送信防止、API 呼出し、再取得 |
| `submitExitOrderAmendment` | 建玉詳細の価格入力検証、API 呼出し、positions/pending/history 再取得 |
| `open...Editor` / `close...Editor` | サーバー値を初期値にした編集開始・破棄 |

## 処理フロー

### 通常予約注文

1. Trading の PENDING 注文を選択し、詳細フォームに現在価格・数量を表示する。
2. ユーザーが一方または両方を変更して送信する。
3. フロントエンドは空欄、非数、0 以下を弾き、変更された項目だけを PATCH する。
4. Controller が DTO を Service に渡す。
5. Service がデモ口座ロックを取得する。
6. ロック取得後、トランザクション内で注文を ID 再取得する。
7. `PENDING`、ENTRY、単独注文、デモ口座所属であることを確認する。
8. 通貨ペアと最新市場レートを取得し、指定値を正規化する。
9. 訂正後スナップショット全体で正数・precision・価格方向を検証する。
10. `quantity` / `triggerPrice` の指定項目だけを Entity に設定し、1 回保存する。
11. `PendingOrderResponse` を返す。
12. UI は pending orders、order history を再取得し、選択中の値を応答値へ更新する。

### 単独 TP/SL

1. 建玉詳細で単独 TP/SL の Edit を選択する。
2. 新価格を PATCH する。
3. Service は同じ口座ロック内で注文、建玉、通貨ペア、最新レートを再取得する。
4. 注文が対象建玉に紐づく単独 EXIT かつ `PENDING`、建玉がデモ口座の OPEN であることを確認する。
5. priceScale で正規化後、建玉方向・TP/SL 種別・決済側価格の関係を検証する。
6. triggerPrice だけを保存して `PositionExitOrderResponse` を返す。
7. UI は positions、pending orders、order history を再取得する。

### 監視との競合

訂正と `TriggerOrderMonitor -> evaluatePendingOrder` は同じデモ口座ロックを取得する。訂正が先なら保存後の値を次回監視が読む。監視が先なら状態が `TRIGGERED` 等へ遷移し、後続の訂正はロック取得後の再取得・状態検証で 409 となる。訂正処理内で即時発動はせず、既存の次回監視周期に委ねる。

## データフロー

```text
編集フォーム
  -> PATCH request DTO (指定項目のみ)
  -> Controller
  -> AccountTradeLockService
  -> TriggerOrderService
       -> TriggerOrderRepository: 注文再取得
       -> PositionRepository: EXIT の建玉確認
       -> CurrencyPairRepository: scale 確認
       -> MarketRateRepository: 現在価格確認
       -> TriggerOrderRepository.save: 同一 ID を更新
  -> 既存 Response DTO
  -> UI state 更新 + 関連一覧再取得
```

永続データは `trigger_orders.quantity` と `trigger_orders.trigger_price` の既存カラムだけを更新する。関連 ID や状態は更新しない。履歴テーブルは作らず、履歴画面は現時点の注文スナップショットを表示する。専用履歴が必要になった場合は、同一注文 ID を維持したまま別テーブルへ訂正前後値と日時を保存する案を第一候補とするが、本実装には含めない。

## 入力チェック

- 共通
  - パス ID は Spring が `Long` として解釈できること。
  - JSON が正しく、数値項目が `BigDecimal` に変換できること。
  - 正規化前の値が null でなく 0 より大きいこと。
  - `trigger_orders` の定義に収まる precision（価格・数量とも最大 19 桁）であること。
  - scale 超過は拒否せず、既存方針どおり `HALF_UP` で正規化する。
- 通常予約注文
  - `quantity` / `triggerPrice` の少なくとも一方が指定されていること。
  - 明示的 null は拒否すること。
  - 訂正後数量は `quantityScale` へ丸めた後も 0 より大きいこと。
  - 訂正後価格は `priceScale` へ丸めた後も 0 より大きいこと。
  - BUY LIMIT は Ask 未満、BUY STOP は Ask より上、SELL LIMIT は Bid より上、SELL STOP は Bid 未満という既存境界を適用する。
- 単独 TP/SL
  - `triggerPrice` は必須。
  - LONG TP は Bid より上、LONG SL は Bid 未満、SHORT TP は Ask 未満、SHORT SL は Ask より上という既存境界を適用する。
  - 数量は受け付けない。
- 対象
  - 注文状態は `PENDING` のみ。
  - 通常 API は単独 ENTRY のみ。`targetPositionId`、`parentOrderId`、`ocoGroupId` は null であり、当該 ID を `parentOrderId` として参照する子注文が存在しないこと。
  - EXIT API は `purpose=EXIT`、path の position ID と `targetPositionId` が一致し、`parentOrderId` / `ocoGroupId` が null であること。
  - 対象建玉は存在し、デモ口座に属し、`OPEN` であること。

## エラー処理

| 条件 | HTTP | メッセージ方針 |
|---|---:|---|
| 注文・建玉が存在しない、または他口座 | 404 | 対象が見つからない。口座情報は開示しない |
| 状態が PENDING でない | 409 | 未発動注文のみ訂正可能 |
| OCO、IFD/IFO、WAITING、単独でない対象 | 409 | 当該注文形態の訂正は未対応／専用操作が必要 |
| OPEN でない建玉、最新レートなし | 409 | 現在の状態では訂正不可 |
| 項目なし、null、非数、0 以下、precision 超過、価格方向不正 | 400 | 問題の項目と有効条件を示す |
| JSON 変換不能 | 400 | 入力形式が不正 |

サービスの業務エラーは `ResponseStatusException` とし、`ApiExceptionHandler` が `ApiErrorResponse(message)` に変換する。Jackson の変換例外も同形式へ寄せる。全検証完了前に Entity の setter を呼ばず、複数項目の一方が不正でも保存しない。予期しない Repository 例外はトランザクションをロールバックし、既存の一般エラー処理に委ねる。

UI は送信中に対象フォームとボタンを無効化し、二重送信を防ぐ。失敗時は編集値を保持し、API の message をフォーム近傍に表示する。通信失敗時も閉じたり成功扱いにしたりしない。

## 既存機能への影響

- `TriggerOrderService` が同じレコードを更新するため、発動・取消との競合が発生し得るが、訂正を既存口座ロックへ参加させて直列化する。
- 通常注文の数量変更後は、既存 `executeTriggeredOrder` に訂正後数量が渡る。約定ロジック自体は変更しない。
- TP/SL は価格だけを変え、全数量決済を維持する。
- 監視対象抽出、1 秒間隔、発動条件、レートシミュレーターは変更しない。
- OCO 相互取消、建玉消滅時 EXPIRED、IFD/IFO 親約定時バインドは変更しない。
- Pending orders に数量表示と選択状態が加わるが、PENDING のみを表示する既存方針を維持する。
- 成功後の既存ポーリングを待たず再取得するため表示遅延を抑える。ポーリング間隔は変更しない。

## 後方互換性

- 既存 API の URL、HTTP メソッド、リクエスト、レスポンスは変更せず、PATCH を追加するだけとする。
- `PositionExitOrderResponse` には IFD/IFO 子を UI で識別する `parentOrderId` を末尾へ追加する。JSON の加算的変更で既存フィールドの意味・名称は変えず、既存クライアントとの互換性を維持する。
- DB カラム・状態 enum・設定値を追加しない。`ddl-auto=update` によるスキーマ変更も発生させない。
- `PENDING` / `WAITING` の既存取得・取消挙動は変更しない。訂正だけをより狭い `PENDING` に限定する。
- 古い画面からの上書き検出用 version は追加せず、PENDING のまま複数クライアントが順次訂正した場合は後勝ちとする。現行 DTO は `updatedAt` / version を公開せず、既存取引処理も `AccountTradeLockService` による直列化を採るため、Issue #2 だけに楽観ロックと API 互換性変更を導入しない。

## セキュリティ上の考慮

- 本アプリは学習用で `/api/**` permitAll の開発設定に従う。実運用の認証・認可は本 Issue の対象外。
- それでも path の position ID だけを信用せず、注文と建玉の関連、デモ口座所有、目的、状態をサーバー側で再検証する（IDOR 相当の誤更新防止）。
- 通貨ペア、side、orderType、purpose、exitType、関連 ID はリクエストに含めず、過剰投稿による属性変更を防ぐ。
- 不正・極端な BigDecimal は保存前に拒否し、DB 例外やリソース浪費を抑える。
- エラーに内部 SQL、スタックトレース、他口座の存在を含めない。
- UI の検証は利便性目的であり、サーバー検証を正とする。

## テスト設計

### バックエンド単体テスト

`TriggerOrderServiceAmendmentTest` に次を追加する。

- PENDING の単独 ENTRY を価格のみ、数量のみ、両方で訂正できる。
- USD/JPY 等の pair scale で `HALF_UP` 正規化される。
- BUY/SELL × LIMIT/STOP の有効方向と境界不正を検証する。
- 項目なし、明示 null、0、負数、丸め後 0、precision 超過を 400 とし、`save` されない。
- 存在しない ID は 404。
- WAITING、TRIGGERED、CANCELLED、REJECTED、EXPIRED は 409。
- EXIT、OCO グループ所属、子注文から参照される IFD/IFO 親、IFD/IFO 子を通常 API で拒否する。
- ロック取得前は PENDING でも、ロック内再取得時に TRIGGERED なら拒否することを、制御可能な lock stub または順序検証で確認する。
- 成功応答に ID、関連情報、訂正後 quantity / triggerPrice が維持・反映される。

`TriggerOrderServiceExitOrderTest` に次を追加する。

- LONG/SHORT × TP/SL の有効価格訂正。
- 現在の Bid/Ask に対する境界不正。
- position ID 不一致、他口座、CLOSED 建玉、レートなし。
- OCO レッグ、未バインドおよびバインド済み IFD/IFO 子、WAITING を拒否。
- quantity や関連属性が変わらない。
- 訂正後価格を次回 `evaluatePendingOrder` が利用し、既存の全量決済を呼ぶ。
- 既存 OCO 相互取消、IFD/IFO バインドのテストが継続成功する。

Controller/API 層は Spring MVC テストを追加する場合、PATCH のマッピング、JSON の省略と明示 null、非数、`ApiErrorResponse(message)` を確認する。現在のテスト慣例がサービス単体中心のため、まずサービステストを必須、MVC テストは JSON presence 判定と例外ハンドラを保証する範囲で追加する。

### フロントエンド

- TypeScript/build で API 型と props の整合を確認する。
- ESLint を実行する。
- 手動受入確認:
  - Pending orders で数量が表示され、単独 ENTRY を選択して価格のみ・数量のみ・両方を変更できる。
  - 建玉詳細で単独 TP/SL 価格を変更できる。
  - OCO/IFD/IFO と WAITING に Edit が出ない。
  - 0、負数、空欄、非数を送信できない。
  - 送信中に二重操作できない。
  - 成功後に訂正値が即時反映され、再取得後も維持される。
  - 失敗時に入力値が残り、サーバーメッセージが表示される。
  - loading / empty / error、Cancel、TP/SL/OCO 登録・取消が退行しない。

### 競合・結合テスト

- 評価条件未到達の注文を訂正し、次回監視が訂正後価格・数量を参照する。
- 監視が先に発動したケースでは PATCH が 409 となり、発動結果を上書きしない。
- 訂正が先のケースでは部分値ではなく正規化済みの全訂正値を監視が参照する。

## 実装順序

1. `PendingOrderAmendRequest` / `PositionExitOrderAmendRequest` と JSON presence のテストを作る。
2. `TriggerOrderService` に通常 ENTRY 訂正を追加し、状態・関連・数値・方向・ロックの単体テストを通す。
3. `TriggerOrderService` に単独 EXIT 価格訂正を追加し、建玉関係・方向・既存全量決済の回帰テストを通す。
4. Controller の PATCH と JSON 変換エラーの共通応答を追加・検証する。
5. フロント API 型・関数を追加する。
6. Pending orders の数量表示、選択、詳細訂正フォームを追加する。
7. 建玉詳細の単独 TP/SL 訂正フォームを追加する。
8. 成功後再取得、送信中、エラー保持、loading / empty / error を確認する。
9. バックエンドテスト、フロント lint/build、手動受入、既存 OCO/IFD/IFO 回帰を順に確認する。
10. 「判断保留」に挙げた重大仕様がプロダクト判断で確定した後、複合注文・TP/SL 数量・履歴を別段階で設計・実装する。

## 懸念事項

### Codex 判断で解決し、本文へ反映した事項

1. **訂正可能状態は `PENDING` のみ**  
   判断内容: `WAITING` は初期実装の訂正対象外とし 409 で拒否する。  
   判断理由: Trading 画面は PENDING のみ表示する既存方針であり、現行の IFD/IFO 子も生成時は `PENDING` で、`WAITING` を編集する具体的導線・ユースケースがない。対象を広げるより既存 UI と最小変更を優先する。  
   根拠: `docs/adr/ADR-0012_UI3画面分離と情報操作分離.md`「待機注文パネルは PENDING のみ」、`FX_trading_front/fx-demo-front/app/components/MarketMonitorScreens.tsx` の `visiblePendingOrders`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java` の `createUnboundExitOrder`（子注文を `PENDING` で生成）。

2. **訂正後の即時発動は行わない**  
   判断内容: 訂正 API は保存済みスナップショットを返し、条件評価は既存の次回監視周期に委ねる。  
   判断理由: 即時発動を加えると PATCH の応答意味と約定タイミングが変わり、監視処理を独立スケジュールとする既存アーキテクチャから外れる。  
   根拠: `CODEX.md`「周期的な監視は独立スケジュール」、`TriggerOrderMonitor.java#evaluatePendingOrders`、`application.properties` の `demofx.pending-orders.evaluation-interval-seconds=1`。

3. **初期実装では楽観ロックを追加しない**  
   判断内容: 発動競合は既存口座ロックで直列化し、PENDING の連続訂正は後勝ちとする。version / `updatedAt` の API 公開は行わない。  
   判断理由: 現行は単一デモ口座・単一 JVM の `AccountTradeLockService` を取引直列化の基準とし、注文 DTO は更新版を公開していない。新しい DB 列と条件付き PATCH は Issue の最小範囲を超え、後方互換性にも影響する。  
   根拠: `CODEX.md`「口座ロックで直列化」、`AccountTradeLockService.java#withAccountLock`、`TriggerOrderService.java#evaluatePendingOrder`、`PendingOrderResponse.java`、`BaseEntity.java`。

4. **同一注文 ID を維持して既存レコードを更新する**  
   判断内容: 取消＋新 ID 発行は行わず、既存 `TriggerOrder` の変更可能フィールドだけを更新する。  
   判断理由: Issue の目的が取消・再発注の回避であり、新 ID 方式は既存の親・OCO・結果 ID 関係と注文状態の意味を変える。既存 Entity に更新対象フィールドが揃っている。  
   根拠: `docs/requirements/issue-2-requirements.md` の目的、`TriggerOrder.java`、`TriggerOrderRepository.java`。

5. **PATCH は項目省略を許し、明示 null を拒否する**  
   判断内容: presence フラグを持つ専用 DTO で省略と null を区別し、少なくとも一項目を必須とする。  
   判断理由: 要件の「価格のみ」「数量のみ」と null 拒否を同時に満たし、全 API に影響する Jackson 設定変更を避けるため。  
   根拠: `docs/requirements/issue-2-requirements.md` の正常系・異常系、既存 `PendingOrderRequest.java` の `BigDecimal` 方針、`ApiExceptionHandler.java`。

6. **フロント自動テスト基盤は追加しない**  
   判断内容: 初期実装は既存の lint/build と手動受入を必須とし、新しいテストランナー・依存は導入しない。  
   判断理由: 現行プロジェクトに test script とテスト依存がなく、基盤追加は注文訂正より広い設定変更になる。バックエンドの自動テストで業務ルールを担保し、UI は既存ツールで検証するのが最小変更である。  
   根拠: `FX_trading_front/fx-demo-front/package.json`、`CODEX.md`「足りない項目だけ最小追加」。

7. **単一 JVM の実行構成を前提とする**  
   判断内容: 本 Issue では分散ロックや DB ロックを追加しない。複数インスタンス化する場合は別設計とする。  
   判断理由: 既存の取引全体が JVM 内 `ReentrantLock` を前提としており、訂正だけを異なる排他方式にすると整合しない。  
   根拠: `AccountTradeLockService.java`、`CODEX.md` の既存口座ロック方針。

### 判断保留

#### 1. TP/SL の数量変更と部分決済

- 判断が必要な内容: TP/SL 数量変更を、建玉の一部を決済する新機能として扱うか、全数量決済を維持して訂正不可とするか。
- 判断が必要な理由: Issue は TP/SL の価格・数量変更を求める一方、現行は TP/SL 作成時に建玉数量を自動設定し、発動時に建玉全体を決済する。要件と既存仕様が衝突し、損益・証拠金・残存建玉・残存注文の業務ルールに波及する。
- 選択肢と影響:
  - A. 部分決済を新設する: Issue 文言を完全に満たすが、Position、損益、証拠金、OCO、建玉消滅時失効まで大幅な追加設計・実装が必要。
  - B. TP/SL は全数量決済を維持し、数量訂正を許可しない: 既存互換性と最小変更に優れるが、Issue の「TP/SL 数量変更」は未達となる。
  - C. 数量入力は許すが建玉数量と同値だけ受け付ける: API 上の入力欄は作れるが実質的な変更にならず、利用者に誤解を与える。
- 推奨案: **B**。部分決済を独立 Issue として要件化するまで、初期実装は単独 TP/SL の価格訂正だけにする。現行 `TriggerOrderService#createExitOrder` と `PositionService#closePositionForLockedAccount` の全量決済を壊さないため。

#### 2. OCO の訂正単位

- 判断が必要な内容: OCO の片側価格訂正を許すか、TP/SL 両脚を常にグループ単位で訂正するか。
- 判断が必要な理由: `ADR-0009` は取消をグループ単位とするが、訂正については規定していない。片側編集の可否は操作性と OCO の意味に直接影響し、両案に技術的実現性がある。
- 選択肢と影響:
  - A. 両脚一括訂正: OCO をセットとして扱う既存思想と整合し、全体検証・rollback が明確。ただし片側だけ変えたい操作でも両価格の入力が必要。
  - B. 片側価格訂正を許可し、ロック内で両脚を再検証: 操作は簡便だが、既存の「部分操作不可」という利用者理解を変更する。
  - C. OCO 訂正を対象外にする: 最小変更だが、要件の複合注文整合を恒久的には満たさない。
- 推奨案: **A**。`ADR-0009` のセット操作方針を訂正にも一貫して適用し、専用のグループ PATCH で原子的に更新する。

#### 3. IFD/IFO の親子訂正ルール

- 判断が必要な内容: 親 ENTRY と未バインド子 EXIT を個別に訂正可能とするか、一括訂正とするか。親数量変更時に子数量をいつ同期するか。
- 判断が必要な理由: 親約定時に子へ生成建玉 ID と数量を再設定し、価格方向を再検証する既存仕様がある。訂正粒度は複合注文の入力体験と親子の一貫性を変える。
- 選択肢と影響:
  - A. 複合注文全体を専用 API で一括訂正: 原子性と整合性が高いが、UI/API の変更量が大きい。
  - B. 親価格・数量と子価格を個別訂正し、親数量変更時は未発動子数量を同一トランザクションで同期: 柔軟だが操作経路と競合テストが増える。
  - C. 親 ENTRY だけ訂正し、子は取消・再発注: 最小だが複合注文を一体として扱う期待と合わない。
- 推奨案: **A**。複数レッグの部分成功を避け、IFO の OCO も含めて口座ロック内で一括検証・更新する。数量は親から子へ同期し、親約定時には既存どおり実建玉数量で再設定する。

#### 4. 訂正履歴の永続化と表示

- 判断が必要な内容: 訂正前後値を永続化するか、保持項目・期間・表示先をどうするか。
- 判断が必要な理由: Issue は変更履歴の学習を背景に挙げるが、保存・表示を完了条件として具体化していない。DB スキーマ、データ保持、後方互換性、History 画面仕様に影響する。
- 選択肢と影響:
  - A. 専用履歴テーブルに注文 ID、訂正前後の価格・数量、訂正日時を保存し、画面表示は後続: 追跡可能性を最小項目で確保するが DB 変更が必要。
  - B. A に操作者・理由・History 表示を加える: 学習・監査性は高いが、認証未実装のため操作者定義ができず UI 変更も大きい。
  - C. `updatedAt` とアプリケーションログのみ: 変更最小だが訂正前値を追跡できず、「変更履歴」の学習価値は限定的。
- 推奨案: **A**。ただし保持期間と削除方針はデータ管理ルールに当たるため、確定後に別段階で実装する。初期実装では履歴を保存したように見せず、同一レコード更新だけを行う。

## 要件との対応表

| 要件 | 設計上の対応 | 状態 |
|---|---|---|
| 通常予約注文の価格訂正 | pending リソースの PATCH、同一 ID 更新 | 対応 |
| 通常予約注文の数量訂正 | 省略可能な quantity、scale/正数/precision 検証 | 対応 |
| 単独 TP/SL の価格訂正 | position/exit-order リソースの PATCH | 対応 |
| TP/SL の数量訂正 | 現行全量決済と矛盾するため初期 API では明示拒否 | 判断保留（推奨: 全量決済を維持） |
| 未発動状態のサーバー再確認 | 口座ロック取得後に ID 再取得し PENDING を検証 | 対応 |
| PENDING / WAITING | 既存 UI と最小変更を優先し、訂正は PENDING のみ。WAITING は 409 | 対応方針確定 |
| 新規登録同等の入力検証 | BigDecimal、HALF_UP、pair scale、方向検証を再利用 | 対応 |
| 不変属性・関連 ID の維持 | DTO に含めず、対象分類と関連をサーバー検証 | 対応 |
| 終了状態の拒否 | PENDING 以外を 409 | 対応 |
| 監視・約定との競合防止 | `AccountTradeLockService` + `@Transactional` + 再取得 | 対応 |
| PATCH と既存リソース配下 | 2 本の PATCH URL を追加 | 対応 |
| 成功時 DTO 応答 | 既存 `PendingOrderResponse` / `PositionExitOrderResponse` 再利用 | 対応 |
| 失敗時非部分更新 | 全検証後に setter/save、例外時 rollback | 対応 |
| `ApiErrorResponse(message)` | ResponseStatusException と JSON 変換失敗を共通処理 | 対応 |
| OCO の整合 | 初期 API で拒否し既存セット挙動を維持 | 判断保留（推奨: 両脚一括） |
| IFD/IFO の親子整合 | 親・子とも初期 API で拒否し既存バインドを維持 | 判断保留（推奨: 全体一括） |
| 訂正後値を次回監視で利用 | 同一 Entity を更新し、即時評価せず次周期で再取得 | 対応 |
| Pending orders への反映 | 数量表示、選択詳細フォーム、成功後再取得 | 対応 |
| 建玉詳細への反映 | 単独 TP/SL の Edit と成功後 positions 再取得 | 対応 |
| 変更履歴 | `updatedAt` のみ。専用履歴は保持・表示方針確定後に別段階 | 判断保留（推奨: 最小履歴テーブル） |
| 既存登録・取消・OCO・IFD/IFO の維持 | API/DB/監視を変更せず回帰テスト | 対応 |
| 最低限のバックエンドテスト | 新規 amendment test と既存 exit test 拡張 | 対応 |
| 最低限のフロントテスト | 既存基盤に合わせ lint/build/手動受入。新規テスト基盤は追加しない | 対応方針確定 |

### 判断根拠となる主要既存ファイル

- `docs/requirements/issue-2-requirements.md`
- `CODEX.md`
- `DESIGN.md`
- `docs/adr/ADR-0009_OCOグループID方式と状態区別.md`
- `docs/adr/ADR-0010_IFDIFOの約定時判定と失効粒度.md`
- `docs/adr/ADR-0012_UI3画面分離と情報操作分離.md`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/domain/TriggerOrder.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/repository/TriggerOrderRepository.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/monitor/TriggerOrderMonitor.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/AccountTradeLockService.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/entity/BaseEntity.java`
- `FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/exception/ApiExceptionHandler.java`
- `FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceExitOrderTest.java`
- `FX_trading_front/fx-demo-front/lib/marketRateTicks.ts`
- `FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx`
- `FX_trading_front/fx-demo-front/app/components/MarketMonitorScreens.tsx`
- `FX_trading_front/fx-demo-front/app/globals.css`
- `FX_trading_front/fx-demo-front/package.json`

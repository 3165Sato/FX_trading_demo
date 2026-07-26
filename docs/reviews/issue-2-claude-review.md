# 現在の git diff コードレビュー

参照資料：

- docs/requirements/issue-2-requirements.md
- docs/design/issue-2-implementation-design.md

## 総合判定

**マージ不可（Critical 1件の修正が必要）**。設計・要件との整合性は総じて高く、口座ロック・DTOによる部分更新・入力検証・価格方向検証の再利用など、既存アーキテクチャに沿った実装になっている。しかし、設計自身が明記する「対象外注文（IFD/IFO）を汎用APIから誤って訂正できないよう、サーバー側で検証して拒否する」という安全策が、通常予約注文の訂正エンドポイントで実装されておらず、IFD/IFOの親ENTRY注文を汎用PATCHで訂正できてしまう。これは設計の核心的な安全策の欠落であり、修正必須と判断する。

---

## Critical

### 1. IFD/IFO の親ENTRY注文が `PATCH /api/trade/orders/pending/{id}` で訂正できてしまう
- **対象**: [TriggerOrderService.java:279-296](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java) の `validateAmendablePendingEntry`
- **問題**: このメソッドは注文自身の `targetPositionId` / `parentOrderId` / `ocoGroupId` のみを検証しており、「他の注文からこの注文が `parentOrderId` として参照されているか」を確認していない。IFD/IFOの親ENTRY注文は自分自身にはこれらのフィールドを一切持たない（`parentOrderId` を持つのは子のEXIT注文側のみ。[TriggerOrderService.java:463-474](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java), [864-887行](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java)）。そのため、IFD/IFOの親ENTRY注文は `isEntryOrder=true, targetPositionId=null, parentOrderId=null, ocoGroupId=null` となり、単独の通常予約注文と区別できず、検証をすり抜ける。
- **再現筋道**: IFD/IFOを1件作成（親ENTRY注文ID=E、子EXIT注文が `parentOrderId=E` で作成される）→ `PATCH /api/trade/orders/pending/{E}` に新しい価格・数量を送信 → 200で成功してしまう。
- **なぜ問題か**: 要件ドラフト・設計書ともに「IFD/IFOに含まれる未発動注文の訂正整合ルールは判断保留・初期実装の対象外」と明記し、設計の「目的」節では明示的に「対象外の注文を汎用APIから誤って訂正できないよう、サーバー側で関係属性を検証して拒否する」と安全策を謳っている（[docs/design/issue-2-implementation-design.md:13](docs/design/issue-2-implementation-design.md)）。しかし実装はこの安全策を満たしていない。既に `TriggerOrderRepository.findByParentOrderIdOrderByCreatedAtAsc` ([TriggerOrderRepository.java:48](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/repository/TriggerOrderRepository.java)) が存在しており、子の有無を調べる手段は既にあるにもかかわらず利用されていない。
- **フロント側も検知不可**: `PendingOrder` 型・一覧APIには「この注文が親か」を示すフィールドがなく、フロントの `amendable` 判定（[MarketMonitorDashboard.tsx:807](FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx)）も同じ理由でIFD/IFO親ENTRYを止められない。手動受入確認項目「OCO/IFD/IFOとWAITINGにEditが出ない」（[issue-2-implementation-design.md:285](docs/design/issue-2-implementation-design.md)）にも反する。
- **テストでの見落とし**: `TriggerOrderServiceAmendmentTest.rejectsCompositeParentOrder`（[TriggerOrderServiceAmendmentTest.java:156-166](FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceAmendmentTest.java)）は「対象注文自身に `parentOrderId` がセットされているケース」しか検証しておらず、実際には起こり得ないデータ状態をテストしているにすぎない。実際の脆弱シナリオ（他の注文からの被参照）はテストされていない。
- **修正方針（推測を含む）**: `validateAmendablePendingEntry` 内で `triggerOrderRepository.findByParentOrderIdOrderByCreatedAtAsc(order.getId())` 等により子注文の有無を確認し、存在する場合は409で拒否する。同様の観点で `TriggerOrderServiceExitOrderTest` 側は既に `order.getParentOrderId() != null` を注文自身でチェックしており正しく機能している（[TriggerOrderService.java:304-306](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java)）。

---

## High

### 2. 建玉詳細の単独TP/SL「Edit」ボタンがIFD/IFOバインド済み子注文でも表示されてしまう
- **対象**: [MarketMonitorDashboard.tsx:2413-2417](FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx)
- **問題**: `disabled={order.ocoGroupId !== null || order.status !== "PENDING"}` は `parentOrderId` を確認していない。IFDの親約定後、子TP/SLは `targetPositionId` が設定され建玉詳細の `exitOrders` に現れるが、`ocoGroupId` を持たないため（IFDはOCOを使わない）Editボタンが有効になってしまう。
- **実害の範囲**: バックエンドの `validateAmendableStandaloneExit` は自身の `parentOrderId != null` を正しく検証し409で拒否するため（[TriggerOrderService.java:304-306](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java)）、データ破損には至らない。ただし、ユーザーが価格を入力→保存→エラー表示、という混乱を招くUXであり、設計の手動受入確認項目「OCO/IFD/IFOとWAITINGにEditが出ない」に明確に反する。
- **注記**: `PositionExitOrderResponse` / `PositionExitOrder`型に `parentOrderId` が含まれていないため（[PositionExitOrderResponse.java](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/PositionExitOrderResponse.java)）、フロント側だけでは修正不可能。DTOへの `parentOrderId`（またはより抽象化した `amendable` フラグ）追加が必要。

---

## Medium

### 3. 設計のテスト観点に明記された異常系が実装テストから欠落している
- **対象**: `TriggerOrderServiceExitOrderTest`（追加分）、`TriggerOrderServiceAmendmentTest`
- **欠落しているケース**（設計 [issue-2-implementation-design.md:266-271](docs/design/issue-2-implementation-design.md) に明記済み）:
  - EXIT訂正: `position ID 不一致、他口座、CLOSED 建玉、レートなし` の異常系テストが1件もない。
  - EXIT訂正: `WAITING` ステータスの拒否テストがない（ENTRY側は `rejectsEveryNonPendingStatus` でWAITINGを含めて網羅しているのに対し非対称）。
  - ENTRY訂正: 存在しないID→404、他口座の注文→404 のテストがない。
- **理由**: これらは「異常系の考慮漏れ」というより「実装済みの検証ロジックに対するテストの不足」であり、実装自体（`validateAmendableStandaloneExit` のコード）は該当ケースを一応カバーしているように見えるが、テストで担保されていないため回帰時に気づけない。

### 4. `rejectsCompositeParentOrder` テストが実際には起こらないデータ状態を検証している
- **対象**: [TriggerOrderServiceAmendmentTest.java:156-166](FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/order/TriggerOrderServiceAmendmentTest.java)
- **問題**: `order.setParentOrderId(9L)` をENTRY注文自身に設定してテストしているが、実装上ENTRY注文が自分の `parentOrderId` を持つことはない（Critical項目1参照）。このテストは「複合注文を防いでいる」という誤った安心感を与える。Critical項目1の修正と合わせてテストの前提も見直すべき。

---

## Low

### 5. `normalizePositive` の `setScale` が巨大指数のBigDecimalでDoSを誘発し得る（既存パターンの踏襲）
- **対象**: [TriggerOrderService.java:341-352](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java)
- **懸念（推測）**: `value.setScale(scale, RoundingMode.HALF_UP)` は、`value` のscaleが極端に小さい（例: `"1E2000000000"` のような巨大指数）場合、内部でBigIntegerの桁数を大きく引き伸ばす処理が走り、メモリ・CPUを大量消費する可能性がある。精度チェック（`precision() > 19`）が `setScale` の**後**に行われているため、攻撃的な入力に対する防御としては手遅れになり得る。
- **既存踏襲である旨**: これは `placePendingOrder` 等の既存コード（[TriggerOrderService.java:104-105](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/service/TriggerOrderService.java)）にも同じパターンがあり、本diff固有の新規リスクではないが、公開APIのエンドポイント数が2つ増えるため露出面は広がる。本アプリはデモ・学習用途であり優先度は低いと判断。

### 6. `PositionExitOrderAmendRequest.setQuantity` の実装が値を握りつぶす
- **対象**: [PositionExitOrderAmendRequest.java:20-22](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/PositionExitOrderAmendRequest.java)
- **内容**: `setQuantity(BigDecimal ignored)` は値を保持せず「指定されたこと」だけを記録し、サービス層で400を返す設計。意図的な実装（設計書 [issue-2-implementation-design.md:27](docs/design/issue-2-implementation-design.md) と一致）だが、命名・実装意図がコードだけからは読み取りにくい。コメント無しでの可読性の観点でのみ指摘。

### 7. 設計書に記載の `globals.css` 変更が実際には行われていない
- **対象**: 設計書「変更対象ファイル一覧」（[issue-2-implementation-design.md:57](docs/design/issue-2-implementation-design.md)）は `globals.css` を変更対象としているが、実装は既存のTailwindユーティリティクラスのみで完結しており、`globals.css` は変更されていない。
- **判断**: 実害はなく、むしろ変更範囲最小化の観点では良い。設計書との形式的な不一致として記録のみ。

---

## テスト追加候補

1. **[Critical対応]** IFD/IFOの親ENTRY注文を `amendPendingOrder` で訂正しようとすると409になること（子注文が存在する場合の拒否）。
2. EXIT注文訂正: `WAITING` ステータスの決済注文を拒否すること。
3. EXIT注文訂正: 存在しない建玉ID／存在しない決済注文ID→404。
4. EXIT注文訂正: 他口座の建玉・決済注文→404（IDOR観点）。
5. EXIT注文訂正: `CLOSED` 建玉に対する訂正拒否。
6. EXIT注文訂正: 最新レート取得不可時の409。
7. ENTRY注文訂正: 存在しないID→404。
8. ENTRY注文訂正: 他口座の注文→404（IDOR観点）。
9. Controller/MVC層: 不正JSON（非数値の価格・数量）が `ApiErrorResponse` 形式の400で返ること（`HttpMessageNotReadableException` ハンドラの動作確認）。
10. （フロント）IFD/IFOバインド済み子注文・IFD/IFO親注文でEditボタンが表示されない、または表示されても送信できないことのUI確認（要バックエンドDTO拡張後）。

---

## 問題なしと判断した主な観点

- **口座ロックとトランザクション**: `amendPendingOrder`/`amendExitOrder` はいずれも `@Transactional` かつ `AccountTradeLockService.withAccountLock` 内でロック取得後に対象注文を再取得しており、既存の `evaluatePendingOrder` 等と同じロックで直列化されている（[AccountTradeLockService.java](FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/AccountTradeLockService.java) 実装確認済み）。監視処理との競合排除は設計通り。
- **価格方向検証の再利用**: 訂正時も新規登録時と同じ `validateTriggerDirection` / `validateExitDirection` を再利用しており、訂正によって新規登録では作れない不正な注文を作成できない。
- **IDOR対策（口座所有チェック）**: `validateAmendablePendingEntry` / `validateAmendableStandaloneExit` はいずれも `account.getId()` と注文・建玉の `accountId` の一致を確認しており、他口座のリソースへの誤更新は防止されている。
- **過剰投稿（mass assignment）対策**: `PendingOrderAmendRequest` / `PositionExitOrderAmendRequest` には通貨ペア・売買区分・関連ID等のフィールドが存在せず、これらを訂正リクエストで書き換えることはできない。
- **既存APIとの後方互換性**: 既存のURL・HTTPメソッド・レスポンスDTOは変更されておらず、PATCHエンドポイント追加のみで既存クライアントへの影響はない。
- **DBスキーマ**: 新規カラム・Entity・設定値は追加されておらず、`ddl-auto=update` によるスキーマ変更も発生しない。
- **失敗時の非部分更新**: すべての検証をEntityの `setter` 呼び出し前に完了させており、検証失敗時に部分的な値の書き込みは発生しない。

---

## Codex 対応結果（2026-07-27）

### 総合結果

全指摘を確認し、指摘 1〜7 をすべて対応済みとした。要件変更を必要とする指摘はなかった。修正は注文訂正機能とその設計・テストの範囲に限定した。

### 各指摘への結論

1. **Critical: IFD/IFO 親 ENTRY を汎用 PATCH で訂正できる** — **対応済み**  
   `TriggerOrderService.validateAmendablePendingEntry` で、対象 ID を `parentOrderId` として参照する子注文を `TriggerOrderRepository.findByParentOrderIdOrderByCreatedAtAsc` で検索し、存在する場合は 409 で拒否するよう修正した。テストは実際のデータ構造どおり「親を参照する子が存在する」ケースへ置き換えた。

2. **High: バインド済み IFD/IFO 子に Edit が表示される** — **対応済み**  
   `PositionExitOrderResponse` とフロントの `PositionExitOrder` に `parentOrderId` を加算的に追加し、建玉詳細の Edit を `ocoGroupId == null`、`parentOrderId == null`、`status == PENDING` の場合だけ有効にした。Pending orders の親 ENTRY は、一覧内の子の `parentOrderId` から親 ID を判定して選択不可にした。既存フィールドの変更・削除はない。

3. **Medium: 設計記載の異常系テスト不足** — **対応済み**  
   ENTRY の存在しない ID・他口座、EXIT の WAITING・存在しない注文・存在しない建玉・他口座・CLOSED 建玉・最新レートなしを追加した。

4. **Medium: `rejectsCompositeParentOrder` が非現実的な状態を検証** — **対応済み**  
   ENTRY 自身へ `parentOrderId` を設定するテストを削除し、子 EXIT が対象 ENTRY ID を `parentOrderId` として保持する実際の IFD/IFO 構造を再現するテストへ変更した。

5. **Low: 巨大指数 BigDecimal を `setScale` 前に防御していない** — **対応済み**  
   新規 PATCH API の露出増加を考慮し、`normalizePositive` で `precision`、整数桁数、過大な正 scale を `setScale` より前に検証するよう変更した。巨大な正指数・負指数のテストを追加した。既存登録処理全体のリファクタリングは指摘範囲外のため行っていない。

6. **Low: `setQuantity` が値を保持しない意図が不明瞭** — **対応済み**  
   TP/SL 数量指定の presence だけを保持し、サービスで明示的に 400 とする意図を setter にコメントした。値を保持しない動作自体は設計どおりなので変更していない。

7. **Low: 設計書にある `globals.css` が未変更** — **対応済み**  
   Tailwind ユーティリティだけで実装でき、変更範囲最小化に適うため、実装へ不要な CSS 変更を加えず、設計書の変更対象ファイル一覧から `globals.css` を削除した。

### テスト・検証結果

- `git diff --check`: 成功。
- `gradlew.bat test --tests ...TriggerOrderServiceAmendmentTest --tests ...TriggerOrderServiceExitOrderTest`: 実行環境に Java がなく、`JAVA_HOME is not set and no 'java' command could be found` で開始前に失敗。
- `npm.cmd run lint`: npm 依存を取得できておらず、`eslint is not recognized` で開始前に失敗。
- `npm.cmd run build`: npm 依存を取得できておらず、`next is not recognized` で開始前に失敗。

上記はテスト不成功ではなく実行環境未準備による未実行である。Java 21 と npm 依存を利用できる環境で再実行が必要。

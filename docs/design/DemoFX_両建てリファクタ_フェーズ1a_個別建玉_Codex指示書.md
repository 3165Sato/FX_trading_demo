# DemoFX 機能指示書: 両建てリファクタ フェーズ1a（個別建玉モデル＋生成＋個別決済）

## 1. 準拠
- 実装ルールは **CODEX.md** に従う(BigDecimal/HALF_UP、DTO返却、独立スケジュール、口座ロックで直列化、
  既存を壊さない・小ステップ、NEXT_PUBLIC_API_BASE_URL、logs非Git、着手前に既存エンティティを調査・報告)。
- UIは **DESIGN.md** に従う(カラートークン、情報密度、loading/empty/error、Trading画面のグリッド・PositionsTable枠)。
- 前提機能: 成行注文+約定(①)、評価損益(③)、証拠金・維持率(④a)、ロスカット(④b)、指値・逆指値(⑤)。
- 関連設計メモ: 「両建てリファクタ 設計メモ」を正とする。本書はそのフェーズ1のうち **1a** を扱う。

## 2. ゴール / 位置づけ
- ポジションモデルを **派生オンリードのネッティング集約 → 個別建玉(建玉ID付き)の永続化** へ移行する。
- 同一ペアで **LONG / SHORT を共存(HEDGING)** させ、同方向の買い増しも **1本化せず建玉ごとに分離保持**する。
- 建玉を **建玉IDで個別に決済(クローズ)** できるようにする(エントリーとイグジットを対で完成)。
- フェーズ1の位置づけ:
  - **1a(本書)**: 個別建玉モデル＋成行で建玉生成＋個別決済＋建玉一覧/画面複数行
  - 1b: 損益・証拠金・維持率の建玉ベース化(集計SUM/レートFIXED)
  - 1c: 全決済ロスカット
  - フェーズ2以降: NETTINGモード(反対注文FIFOオフセット)、MAX/REVALUE、決済行(TP/SL)、IFD/IFO

## 3. スコープ
### スコープ内
- 個別建玉エンティティ(建玉ID付き)の永続化
- 成行約定 → 建玉の**新規生成**(HEDGING:反対方向でも相殺せず別建玉)
- 建玉IDを指定した**個別決済**(成行クローズ)→ 実現損益確定 → 残高反映
- 建玉一覧API、PositionsTable を**建玉単位の複数行**に対応
- 既存データは**移行せずクリーン再構築**(設計メモ7)

### スコープ外(後続)
- 損益・証拠金・維持率の建玉ベース集計(1b)。本書では PositionsTable の P&L/必要証拠金列は「—」のままでよい
- 全決済ロスカット(1c)。本書ではロスカット監視の建玉ベース化は扱わない
- NETTINGモード/反対注文オフセット(フェーズ2)
- 決済行(TP/SL)・IFD/IFO

## 4. 設計概要
### 4.1 個別建玉モデル
- 建玉は **建玉ID** を持つ永続行。1ペアに複数行(LONG/SHORT、同方向複数)が共存しうる。
- 約定(Trade)1件が建玉に対応づく:
  - **新規約定** → 新しい建玉を生成(数量・建値=約定価格・サイド)。
  - **決済約定** → 対象建玉(建玉ID指定)をクローズ。
- 反対売買は **明示的な個別決済を指定しない限り、相殺せず新規建玉**になる(HEDGING)。

### 4.2 約定価格(共通ルール再掲せず・本機能の要点のみ)
- 新規: BUY → Ask、SELL → Bid。
- 個別決済: LONG建玉のクローズ → SELL=**Bid**、SHORT建玉のクローズ → BUY=**Ask**。
- 実現損益(クローズ時、quote通貨): LONG `(Bid-建値)×数量` / SHORT `(建値-Ask)×数量`。

### 4.3 状態
- 建玉ステータス: **OPEN / CLOSED**(部分決済は本書スコープ外。全数量クローズのみ。部分決済は後続で検討)。

> 部分決済(建玉の一部だけ閉じる)を入れるかは1bや後続で判断。1aは**全数量クローズ**に限定して小さく作る。

## 5. バックエンド仕様
### 5.1 既存調査(着手前に view して報告)
- 既存 `Position` の使われ方(派生オンリードの実体・参照箇所)、`Trade` / `FxOrder` / `Account`、
  これらを参照している建玉算出・評価損益・証拠金・ロスカット・指値発動の各サービス。
- **影響範囲(派生オンリードに依存している箇所)を洗い出して報告**してから着手する。

### 5.2 エンティティ / enum
- **個別建玉エンティティ**(新規 or 既存Positionを建玉単位に再定義):
  建玉ID / account / currencyPair / side(LONG/SHORT) / quantity / openPrice(建値) /
  status(OPEN/CLOSED) / openedAt / closedAt / openTradeId / closeTradeId(任意参照)。
- enum `PositionStatus { OPEN, CLOSED }`。
- `Trade` に決済か新規かを区別する手段(例 `tradeKind { OPEN, CLOSE }` または対象建玉ID参照)を additive 追加。
- **派生オンリードの集約ロジックは廃止**(建玉を正とする)。既存呼び出し元は新モデル参照に置き換え。

### 5.3 サービス
- `PositionService`(建玉ベースに再構築):
  - `openPosition(account, pair, side, qty, price)`: 新規建玉を生成。
  - `closePosition(positionId)`: 対象建玉を成行クローズ(決済価格決定→実現損益→残高反映→CLOSED)。
  - `getOpenPositions(account)`: OPEN建玉の一覧。
- 成行約定サービス(①)を、約定後に上記 open を呼ぶ流れに接続。
- **口座ロックで直列化**(発注・決済・(後続の)ロスカット・トリガー発動が競合しない)。
- 既存の評価損益/証拠金/維持率/ロスカット/指値発動が派生オンリードに依存していたら、
  1aでは**最小限のつなぎ替え**で動く状態にし、本格的な建玉ベース集計は1bで行う。

### 5.4 API
- `POST /api/trade/orders/market`(既存): 約定後に新規建玉を生成する流れへ接続(レスポンスに建玉IDを含めてよい)。
- `GET /api/trade/positions`: **OPEN建玉の一覧**(建玉ID/ペア/サイド/数量/建値/openedAt)。
  P&L・必要証拠金・現在価格は本書では含めない or null(1bで配線)。
- `POST /api/trade/positions/{id}/close`: 指定建玉を成行クローズ。実現損益を返す。
  - OPEN以外は 409。未知IDは not-found。
- DTO返却(Entity直返し禁止)。

### 5.5 エッジケース
| ケース | 期待挙動 |
|---|---|
| 反対方向の成行 | 相殺せず新規建玉(HEDGING) |
| 同方向の連続成行 | 1本化せず建玉が増える |
| 個別決済の対象がOPENでない/未知ID | 409 / not-found |
| クローズ時にレート無し | クローズ不可(理由付きで断る) |
| 数量・建値の丸め | quantityScale / priceScale で BigDecimal 丸め |
| 同時操作 | 口座ロックで直列化、冪等 |

## 6. フロントエンド仕様
- **PositionsTable を建玉単位の複数行**に対応(同一ペアでLONG/SHORT・同方向複数が並ぶ)。
  - 列: 建玉ID(短縮表示可)/ ペア / サイド(LONG=青系/SHORT=赤系)/ 数量 / 建値 / **決済ボタン**。
  - **Current / P&L / 必要証拠金 列は「—」**のまま(1bで配線)。DESIGN.md の枠・配色に従う。
- 決済ボタン → `POST /api/trade/positions/{id}/close` → 約定履歴・建玉一覧を再取得。
- 既存のポーリング方針を踏襲。loading/empty/error を実装。

## 7. この機能固有の制約
- 同方向の買い増しを**平均建値で1本化しない**(建玉ごとに分離保持。後続のTP/SLが建玉ID参照する前提)。
- 反対売買は**相殺しない**(HEDGINGがベース。NETTINGはフェーズ2)。
- 既存のネッティング期データは**移行しない(クリーン再構築)**。
- 1aでは損益・証拠金の**建玉ベース集計は作り込まない**(つなぎ替えで既存が壊れない範囲に留め、本格化は1b)。

## 8. 実装ステップ
1. 既存調査: 派生オンリードPositionとPositionに依存する全箇所を洗い出し報告。
2. 個別建玉エンティティ/enum/Trade区別フィールドを追加(additive)。クリーン再構築方針。
3. `PositionService` を建玉ベースに再構築(open/close/getOpen)。
4. 成行約定(①)を新規建玉生成に接続。口座ロックで直列化。
5. 既存の評価損益/証拠金/ロスカット/指値発動の参照を新モデルへ最小つなぎ替え(壊れない状態に)。
6. API(positions一覧 / close)を追加・接続。
7. テスト追加(9章)。
8. フロント: PositionsTable 複数行＋決済ボタン。
9. 動作確認(10章)。

## 9. テスト（受け入れ条件）
- **新規生成**: BUY 10k → LONG建玉1件(建値=その時のAsk)。
- **同方向買い増し**: さらにBUY 10k → **建玉が2件**(1本化しない)。
- **両建て共存**: 同ペアでSELL 10k → LONG2件とSHORT1件が**同時に共存**。
- **個別決済**: LONG建玉をIDでクローズ → 決済価格=Bid、実現損益=(Bid-建値)×数量、建玉はCLOSED・一覧から消える。
- **SHORT決済**: SHORT建玉クローズ → 決済価格=Ask、実現損益=(建値-Ask)×数量。
- **対象不正**: OPEN以外/未知IDのクローズ → 409/not-found。
- **冪等/直列化**: 同時クローズ・発注で二重決済しない。

## 10. 動作確認手順
### バックエンド
```
# 同方向に2回 → 建玉2件
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }
GET  /api/trade/positions                      # LONG建玉が2件

# 反対方向 → 相殺せず別建玉(両建て)
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"SELL","quantity":10000 }
GET  /api/trade/positions                      # LONG2件 + SHORT1件が共存

# 建玉IDを指定して個別決済
POST /api/trade/positions/{positionId}/close   # 実現損益が返る、その建玉はCLOSED
GET  /api/trade/positions                      # 該当建玉が一覧から消える
```
### フロントエンド
- 同ペアで買い増し・反対売買すると、PositionsTable に建玉が**複数行**で並ぶ(LONG/SHORT共存)。
- 各行の決済ボタンで個別にクローズでき、約定履歴に決済が記録される。
- Current/P&L/必要証拠金 列は「—」(1bで配線)。
- 既存画面(監視・成行・指値/逆指値・約定履歴)が壊れていない。
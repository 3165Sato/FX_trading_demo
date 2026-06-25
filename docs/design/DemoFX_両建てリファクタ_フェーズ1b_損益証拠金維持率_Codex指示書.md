# DemoFX 機能指示書: 両建てリファクタ フェーズ1b（損益・証拠金・維持率の建玉ベース化）

## 1. 準拠
- 実装ルールは **CODEX.md** に従う(BigDecimal/HALF_UP、DTO返却、独立スケジュール、口座ロック、
  既存を壊さない・小ステップ、NEXT_PUBLIC_API_BASE_URL、logs非Git、着手前に既存を調査・報告)。
- UIは **DESIGN.md** に従う(カラートークン、数値は等幅+tabular-nums、PositionsTable/PnlSummary/
  AccountSummary/MarginGauge の枠、利益=緑/損失=赤)。
- 前提機能: フェーズ1a(個別建玉モデル＝建玉ID付き永続化・HEDGING共存・個別決済)。
- 関連設計メモ: 「両建てリファクタ 設計メモ」3〜5章を正とする。本書はフェーズ1の **1b**。

## 2. ゴール / 位置づけ
- 1aで作った**個別建玉を評価**し、1aで「—」にしていた数値を配線する:
  - 建玉ごとの**含み損益**(LONG=Bid評価 / SHORT=Ask評価)。
  - 建玉ごとの**必要証拠金**(数量×レート÷レバレッジ)→ **集計ポリシーSUM** で合算。
  - **エクイティ**(残高＋含み損益)、**維持率**(エクイティ÷必要証拠金×100)。
- 差し替え可能な構造(集計ポリシー / レート評価ポリシー)を**インターフェースとして用意**し、
  本書では **SUM ＋ FIXED** のみ実装。MAX / REVALUE は後付け。
- フェーズ1の位置づけ: 1a(建玉モデル)→ **1b(評価・本書)**→ 1c(全決済ロスカット)。

## 3. スコープ
### スコープ内
- 建玉ごとの含み損益(quote通貨)算出
- 建玉ごとの必要証拠金(レート評価 **FIXED** = 建玉のopenPrice使用)
- 集計ポリシー **SUM**(差し替え可能な構造で実装)
- 基軸通貨(JPY)換算、エクイティ・余力・維持率・状態(SAFE/WARNING/DANGER)
- PositionsTable の Current/P&L/必要証拠金 列、PnlSummary、AccountSummary、MarginGauge を配線

### スコープ外(後続)
- 集計 **MAX**、レート **REVALUE**(構造だけ用意し実装は後付け)
- 全建玉一括ロスカットの建玉ベース化(1c)。本書では発注時の余力チェックの建玉ベース化までに留める
- 部分決済、決済行(TP/SL)、NETTINGモード

## 4. 設計概要
### 4.1 含み損益(建玉ごと・quote通貨)
最新レートでマーク(決済方向):
- LONG建玉: `(Bid - openPrice) × quantity`
- SHORT建玉: `(openPrice - Ask) × quantity`
- 口座の含み損益合計 = 各OPEN建玉の含み損益を基軸通貨に換算して合算。

### 4.2 必要証拠金(建玉ごと → 集計)
- **第1層(共通)**: 1建玉の必要証拠金 = `数量 × レート ÷ レバレッジ`。
  - レート評価 **FIXED(デフォルト)**: 「レート」に **建玉の openPrice(建玉時レート)** を使う。
    → 分母は建玉時に確定し、その後動かない(追加フィールド不要)。
  - レート評価 REVALUE(後付け): 「レート」に現在レートを使う(本書では実装しない)。
- **第2層(集計ポリシー)**: 口座の必要証拠金合計の出し方。
  - **SUM(デフォルト)**: 全建玉の必要証拠金を単純合算。
  - MAX(後付け): ペアごとに `max(LONG合計, SHORT合計)` を取り、ペア横断で合算。

### 4.3 基軸換算・エクイティ・維持率(④a踏襲)
- quote通貨 → 基軸(JPY)換算は既存④aの考え方を踏襲(USD:×USD/JPY、CHF:×USDJPY/USDCHF、CAD:×USDJPY/USDCAD、JPY:×1、仲値使用)。
- エクイティ = 残高 ＋ 含み損益(基軸)。残高 = 初期残高 ＋ 実現損益(基軸)。
- 必要証拠金合計(基軸) = 集計ポリシー(SUM)の結果。
- 余力 = エクイティ − 必要証拠金合計。維持率 = エクイティ ÷ 必要証拠金合計 × 100(必要証拠金0→null「—」)。
- 状態 = 閾値判定(SAFE/WARNING/DANGER)。**分子(エクイティ)は常にライブ、ポリシーが効くのは分母のみ**。

## 5. バックエンド仕様
### 5.1 既存調査(着手前に view して報告)
- 1aの建玉エンティティ/`PositionService`、④aの `AccountSummaryService`・基軸換算ユーティリティ、
  ④bの発注時余力チェックの現状。建玉ベースへ寄せる接続点を報告。

### 5.2 ポリシー構造(差し替え可能に)
- `MarginRateEvaluationPolicy`(FIXED / REVALUE): 1建玉の必要証拠金算出で使う「レート」を返す。
  本書は **FIXED**(openPrice)を実装。REVALUE は未実装の口だけ用意。
- `MarginAggregationPolicy`(SUM / MAX): OPEN建玉群 → 必要証拠金合計。本書は **SUM** を実装。MAX は口だけ。
- どちらも既定(FIXED/SUM)を設定で選べる形にし、既定値で本書の挙動になるようにする。

### 5.3 サービス
- `PositionService`(1a)を拡張: 各OPEN建玉に `currentPrice`(マーク価格)・`unrealizedPnl`・`requiredMargin` を付与。
- `AccountSummaryService`(④a)を建玉ベースに接続: 含み損益合計・必要証拠金合計(SUM)・エクイティ・余力・維持率・状態。
- 実現損益(残高反映)は1aのクローズで確定済みの値を使う。

### 5.4 API
- `GET /api/trade/positions`(拡張): 各建玉に `currentPrice` / `unrealizedPnl`(quote通貨) / `requiredMargin`(基軸) を追加。
- `GET /api/trade/pnl/summary`: 含み損益(通貨別)/ 実現損益(通貨別)。
- `GET /api/trade/account/summary`(④a拡張): balance / equity / usedMargin / freeMargin / marginRatio / status を建玉ベースで返す。
- レート無し等は null → UI「—」。DTO返却。

### 5.5 エッジケース
| ケース | 期待挙動 |
|---|---|
| 該当ペアのレート無し | currentPrice/unrealizedPnl/requiredMargin を null、UI「—」、落ちない |
| 建玉なし | usedMargin=0、marginRatio=null(「—」)、equity=balance |
| 換算レート欠落 | 当該換算 null を伝播、equity/維持率も null |
| 完全両建て(SUM) | LONG・SHORT 両方の必要証拠金を合算(設計メモの例: 10,000＋4,000＝14,000) |
| 丸め | 価格priceScale/数量quantityScale、基軸表示は通貨に応じた桁 |

## 6. フロントエンド仕様
- **PositionsTable**: 1aで「—」だった **Current / P&L / 必要証拠金** 列を配線。
  - P&L は利益=緑/損失=赤(DESIGN.md)。建玉単位の複数行のまま。
- **PnlSummary**: 含み損益・実現損益を**通貨別小計**で表示(基軸単一合計はフェーズ後続/④の換算方針に従う)。
- **AccountSummary**: 評価額(Equity)/ 維持率 / 余力 を配線。
- **MarginGauge**: 必要証拠金 / 維持率ゲージ / ロスカット閾値ライン、状態で配色。
- レートで含み損益・維持率がライブに動く(既存ポーリング方針)。loading/empty/error。

## 7. この機能固有の制約
- レート評価は **FIXED**: 必要証拠金の「レート」は建玉の **openPrice** を使う(追加フィールド不要)。
- 集計は **SUM**。ただし `MarginAggregationPolicy` / `MarginRateEvaluationPolicy` を**差し替え可能な構造**にし、
  MAX/REVALUE を後で足せるようにする(本書では実装しない)。
- ロスカットの**発動・全決済は本書で変更しない**(1c)。本書は表示・算出と発注時余力チェックの建玉ベース化まで。

## 8. 実装ステップ
1. 既存調査: 1a建玉・④aサマリ・④b余力チェックの接続点を報告。
2. ポリシーIF(`MarginRateEvaluationPolicy`/`MarginAggregationPolicy`)を定義し、FIXED/SUM を実装(既定)。
3. `PositionService` を拡張(建玉ごとの currentPrice/unrealizedPnl/requiredMargin)。
4. `AccountSummaryService` を建玉ベースに接続(含み損益合計/必要証拠金SUM/エクイティ/維持率/状態)。
5. 発注時の余力チェック(④b)を建玉ベースの必要証拠金に接続。
6. API(positions/pnl/account)を建玉ベースで返すよう拡張。
7. テスト追加(9章)。
8. フロント: PositionsTable の3列、PnlSummary、AccountSummary、MarginGauge を配線。
9. 動作確認(10章)。

## 9. テスト（受け入れ条件）
- **含み損益**: LONG建玉 openPrice155.100・Bid155.300・10k → +2,000(JPY)。SHORT openPrice155.300・Ask155.100・10k → +2,000。
- **必要証拠金(FIXED)**: openPrice155・10k・25倍 → 62,000 JPY(現在レートが動いても**分母は不変**)。
- **集計SUM(両建て)**: 同ペア LONG10k＋SHORT4k → 必要証拠金は両建玉の合算(相殺しない)。
- **エクイティ/維持率**: equity=残高+含み損益、維持率=equity÷必要証拠金×100。建玉なしで維持率=null。
- **ポリシー差し替え構造**: 既定がFIXED/SUMで、IFが分離されている(MAX/REVALUEを後付けできる)。
- **レート無し**: 当該建玉の評価値が null、画面「—」、例外で落ちない。

## 10. 動作確認手順
### バックエンド
```
# 両建てを作る(1aの機能)
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"SELL","quantity":4000 }

# 建玉ごとに currentPrice/unrealizedPnl/requiredMargin が出る
GET /api/trade/positions

# 口座サマリ:必要証拠金はSUM(両建玉合算)、エクイティ・維持率が出る
GET /api/trade/account/summary

# レートが動くと含み損益・維持率は動くが、必要証拠金(FIXED)は分母不変
GET /api/trade/account/summary
```
### フロントエンド
- PositionsTable の Current/P&L/必要証拠金 が各建玉に表示され、レートでライブに動く(利益=緑/損失=赤)。
- 完全両建てでも必要証拠金は合算(SUM)で計上される。
- AccountSummary(Equity/維持率/余力)と MarginGauge が表示され、含み損で維持率が下がり色が変わる(発動はまだしない=1c)。
- 既存画面(1aの建玉複数行・決済、監視・成行・指値/逆指値)が壊れていない。
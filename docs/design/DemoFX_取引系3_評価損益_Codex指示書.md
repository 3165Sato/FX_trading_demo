# DemoFX 機能追加指示書: 取引系③ 評価損益(含み損益)

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> 取引系①(成行注文+約定)・②(建玉=ネッティング/平均建値)が実装済みで、
> `PositionService` が Trade を畳み込んで建玉(数量・平均建値)と
> 実現損益(蓄積)を算出できる前提。
> 今回は建玉を現在レートでマークして**含み損益**を出し、
> PositionsTable の Current / P&L 列と PnlSummary を配線する。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- 価格・数量・金額は **`BigDecimal`**。丸めは HALF_UP。
- **残高・必要証拠金・Equity・維持率・ロスカットは触らない**(取引系④)。
- **口座基軸通貨への換算はしない**(④の担当)。P&L は**quote 通貨建て**で扱い、合計は**通貨ごとの小計**。
- 建玉の算出ロジック(②の畳み込み)は**再実装しない**。`PositionService` を拡張して使う。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴール

各建玉を現在レートでマークして含み損益(unrealized P&L)を算出し、
②で蓄積した実現損益(realized P&L)とあわせて表示する:
- PositionsTable: **Current 列(マーク価格)/ P&L 列(含み損益)**を配線。
- PnlSummary: **合計評価損益(unrealized)/ 実現損益(realized)**を**quote 通貨ごとの小計**で配線。

---

## 2. スコープ

### スコープ内
- 含み損益のマーク(決済方向の価格でマーク)
- PositionsTable の Current / P&L 列
- PnlSummary(unrealized / realized を通貨ごと小計)
- ②の実現損益(蓄積値)の集計・表示

### スコープ外(次チャンク以降 / 別案)
- 口座基軸通貨(JPY 等)への換算・単一合計(④)
- 残高・必要証拠金・維持率・ロスカット(④)
- AccountSummary(Equity / Margin Lv. / Free Margin)の配線(④)
- 損益のチャート/履歴グラフ

---

## 3. 設計概要

### 3.1 マーク価格(決済方向 = 最重要)
建玉は開いた側と**反対側**で決済するため、マークは決済価格を使う:
- **LONG → Bid でマーク**(売って閉じるため)
- **SHORT → Ask でマーク**(買って閉じるため)

> ①の「BUY=Ask / SELL=Bid」の裏返し。Spread を往復で払うことが見える学習ポイント。

### 3.2 含み損益(unrealized・quote 通貨建て)
最新 `MarketRate` を参照し:
- **LONG**: `(bid - averagePrice) * quantity`
- **SHORT**: `(averagePrice - ask) * quantity`

- 値の通貨はその**ペアの quote 通貨**(USD/JPY→JPY、EUR/USD→USD、USD/CHF→CHF…)。
- `BigDecimal` で計算。表示丸めは quote 通貨に応じて(例: JPY は小数 0、USD/CHF/CAD は小数 2)。

### 3.3 実現損益(realized)
- ②の畳み込みで蓄積した `realized`(quote 通貨建て)を利用。
- **重要**: 全決済で建玉一覧から消えたペアにも realized は残る。
  PnlSummary の realized は**開いている建玉だけでなく全ペアの蓄積**を集計する。

### 3.4 合計の扱い(換算しない)
- quote 通貨が混在する(JPY / USD / CHF / CAD)ため**単純合算しない**。
- PnlSummary は**通貨ごとの小計**で表示(例: `JPY +12,300 / USD +45.20`)。
- 口座基軸通貨への換算・単一合計は**④で実施**(Equity 算出時に換算が必要になるため)。
  - もし今すぐ単一合計が必要なら、quote→基軸通貨換算を追加する(USD→JPY は USD/JPY mid、
    CHF→JPY は USDJPY/USDCHF、CAD→JPY は USDJPY/USDCAD)。**ただし今回は非推奨・任意。**

---

## 4. バックエンド仕様

### 4.1 `PositionService` の拡張(②を再利用)
- ②の畳み込み結果(ペアごとの signed 数量・avg・realized)をそのまま使う。
- 各**開いている建玉**(qty != 0)について:
  - 最新 MarketRate を取得。
  - 3.1 のマーク価格(LONG=bid / SHORT=ask)を決定。
  - 3.2 で unrealized を算出。
- realized は**全ペア**(flat 含む)分を保持・集計できるようにする。

### 4.2 DTO 拡張 / 追加

#### `PositionResponse`(②の DTO を**追加フィールドで拡張**)
| 追加フィールド | 型 | 説明 |
|---|---|---|
| `quoteCurrency` | `String` | 例 `"JPY"`, `"USD"` |
| `currentPrice` | `BigDecimal`(null可) | マーク価格(LONG=bid / SHORT=ask)。レート無しは null |
| `unrealizedPnl` | `BigDecimal`(null可) | 含み損益(quote 通貨)。レート無しは null |

#### `PnlSummaryResponse`(新規)
| フィールド | 型 | 説明 |
|---|---|---|
| `unrealizedByCurrency` | `Map<String,BigDecimal>` | quote 通貨 → 含み損益合計 |
| `realizedByCurrency` | `Map<String,BigDecimal>` | quote 通貨 → 実現損益合計(全ペア) |

### 4.3 API
- 既存 `GET /api/trade/positions` を拡張: 各建玉に `quoteCurrency` / `currentPrice` / `unrealizedPnl` を含める。
- 新規 `GET /api/trade/pnl/summary`: `PnlSummaryResponse` を返す。
- DTO 返却(Entity 直返し禁止)。

### 4.4 エッジケース

| ケース | 期待挙動 |
|---|---|
| 該当ペアの最新レート無し | currentPrice / unrealizedPnl を null、UI は「—」 |
| flat ペア | 建玉一覧には出さないが realized 集計には含める |
| 開いた直後の建玉 | unrealized ≈ -(spread × qty)(Spread 往復分のマイナス。仕様通り) |
| quote 通貨が複数 | 合算せず通貨ごと小計 |
| 割り算/丸め | scale + HALF_UP を明示 |

---

## 5. フロントエンド仕様

### 5.1 PositionsTable(Current / P&L 列を配線)
- ②で「—」だった **Current 列**にマーク価格、**P&L 列**に含み損益を表示。
- P&L 配色: **利益=緑(#3fb950)/ 損失=赤(#f85149)**(値の上下色トークン)。
- 数値は等幅 + tabular-nums。通貨に応じた桁で表示。
- レート無しのセルは「—」。

### 5.2 PnlSummary(「Soon」を置き換え)
- **合計評価損益(Unrealized)**: `unrealizedByCurrency` を通貨ごとに表示(例 `JPY +12,300`)。
- **実現損益(Realized)**: `realizedByCurrency` を通貨ごとに表示。
- 各値を損益符号で配色。枠サイズは維持(レイアウト崩さない)。

### 5.3 更新タイミング
- 建玉と P&L はレートで変動するため、**既存のレート更新ポーリングに合わせて再取得**。
  (含み損益がライブで動くのが期待挙動)
- 発注後にも再取得。過剰な短間隔にはしない。

### 5.4 確認ポイント
- LONG は Bid、SHORT は Ask でマークされている。
- 開いた直後はSpread分だけマイナス。レートが有利に動くとプラスに転じる。
- 全決済後も実現損益が PnlSummary に残る。

---

## 6. 守ってほしい制約(チェック用)
- [ ] マークは決済方向(LONG=Bid / SHORT=Ask)
- [ ] unrealized は quote 通貨建て、合計は通貨ごと小計(基軸換算しない)
- [ ] realized は全ペア(flat 含む)を集計
- [ ] ②の畳み込みを再実装せず PositionService を拡張
- [ ] 残高・証拠金・Equity・維持率・ロスカットは**触っていない**(④)
- [ ] PositionsTable の Current/P&L を配線、損益で配色、レート無しは「—」
- [ ] PnlSummary の枠を維持して配線
- [ ] BigDecimal・HALF_UP、レート無しは null→「—」で固まらない
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能(監視・成行注文・建玉・レイアウト)を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)
1. `PositionService` を拡張し、開いている建玉の currentPrice / unrealizedPnl を算出。
2. `PositionResponse` に quoteCurrency / currentPrice / unrealizedPnl を追加。
3. realized を全ペア集計し、`PnlSummaryResponse` を組み立てる処理を追加。
4. `GET /api/trade/pnl/summary` を追加(positions 拡張とあわせて)。
5. ユニットテストを追加(8 章)。
6. フロント: PositionsTable の Current/P&L 配線、PnlSummary 配線。
7. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)
- **LONG 利益**: avg=155.100、bid=155.300、qty=10,000 → unrealized=+2,000(JPY)。
- **SHORT 利益**: avg=155.300、ask=155.100、qty=10,000 → unrealized=+2,000(JPY)。
- **開いた直後**: LONG を ask で建て bid でマーク → unrealized ≈ -(spread×qty)。
- **レート無し**: currentPrice/unrealizedPnl が null。
- **realized 集計**: 全決済済みペアの realized が summary に含まれる。
- **通貨小計**: JPY 系と USD 系が別キーで集計される。
- **丸め/符号**: quote 通貨に応じた桁、損益符号が正しい。

---

## 9. 動作確認手順

### バックエンド
```
# LONG を作る
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }

# 建玉 → currentPrice=bid, unrealizedPnl が出る(直後はSpread分マイナス)
GET /api/trade/positions

# サマリ → 通貨ごとの unrealized / realized
GET /api/trade/pnl/summary

# しばらく置いてレートが動くと unrealized が変化することを確認
GET /api/trade/positions
```

### フロントエンド
- Trading 画面で建玉を作る → PositionsTable の Current/P&L が表示され、ライブで動く。
- 利益=緑 / 損失=赤 で配色。
- 反対売買で決済 → PnlSummary の実現損益に反映、建玉が消えても realized は残る。
- Current/P&L 以外(Account/Margin)はまだ「—」(④)。
- 既存画面が壊れていない。

---

## 10. 次のチャンク(参考)
- **④ 証拠金・維持率・ロスカット**: MarginRule で必要証拠金を算出、
  口座基軸通貨(JPY 想定)へ P&L を**換算**して Equity = 残高 + 含み損益、
  維持率 = Equity / 必要証拠金、ロスカット閾値判定。
  AccountSummary(Equity / Margin Lv. / Free Margin)と MarginGauge を配線。
  ※基軸通貨への換算ロジックはこのチャンクで導入。

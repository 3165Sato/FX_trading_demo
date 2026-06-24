# DemoFX 機能追加指示書: 取引系④a 証拠金・維持率(算出と表示)

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> 取引系①(成行注文)②(建玉)③(評価損益)が実装済みで、
> `PositionService` が建玉・含み損益(quote 通貨)・実現損益(蓄積)を出せる前提。
> 今回は口座の証拠金まわりを**算出・表示**する(④a)。
> ロスカット**自動執行**と発注時の余力チェックは次の **④b** で行う。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実取引・実資金は扱わない。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- **既存 `Account` / `MarginRule` / `BankAccount` / `CashTransaction` を再定義しない**。
  現状を調べ、足りない項目だけ最小追加(実装ステップ1で調査・報告)。
- 価格・数量・金額は **`BigDecimal`**、丸めは HALF_UP。
- **今回は表示のみ。建玉を自動決済しない / 発注を却下しない**(④b)。
- ②③のロジック(畳み込み・含み損益)は**再実装せず** `PositionService` を拡張して使う。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴール

デフォルト口座について、基軸通貨(JPY 想定)建てで以下を算出・表示する:
- 残高(balance)/ 実現損益(基軸換算)
- 含み損益(基軸換算)/ **Equity(有効証拠金)= 残高 + 含み損益**
- **必要証拠金(used margin)** / **余力(free margin)= Equity − 必要証拠金**
- **維持率(margin ratio)= Equity / 必要証拠金 × 100**
- ロスカット閾値に対する状態(SAFE / WARNING / DANGER)※表示のみ

これを AccountSummary と MarginGauge(確保済み枠)に配線する。

---

## 2. スコープ

### スコープ内
- 基軸通貨・初期残高のシード(冪等)
- quote 通貨 → 基軸通貨(JPY)への換算
- 実現損益(基軸)を残高へ反映、含み損益(基軸)で Equity 算出
- 必要証拠金・余力・維持率の算出
- AccountSummary / MarginGauge の配線(色分け状態まで)

### スコープ外(④b / その後)
- **ロスカット自動執行(建玉の強制決済)**(④b)
- **発注時の余力チェック(証拠金不足で却下)**(④b)
- 入出金 UI、複数口座、手数料・スワップ

---

## 3. 設計概要

### 3.1 基軸通貨・残高
- 口座基軸通貨は **JPY 想定**(`Account` に通貨フィールドがあれば使用、無ければ JPY 既定・設定化)。
- デモ口座に**初期残高(例 1,000,000 JPY)を冪等にシード**
  (`Account` の残高フィールド or `CashTransaction` の入金 1 件。既存の作法に合わせる)。
- `balance` = 初期入金 + Σ(実現損益を基軸換算)。
  - 実現損益は②の畳み込み蓄積値(quote 通貨)を基軸換算して合算(全ペア・flat 含む)。

### 3.2 quote → 基軸(JPY)換算(ここで導入)
最新 `MarketRate` の **mid** を使う。本アプリの quote 通貨は {JPY, USD, CHF, CAD} に限られる:

```
toJpy(amount, quote):
  JPY → amount
  USD → amount * mid(USD/JPY)
  CHF → amount * mid(USD/JPY) / mid(USD/CHF)     // CHF/JPY = USDJPY / USDCHF
  CAD → amount * mid(USD/JPY) / mid(USD/CAD)      // CAD/JPY = USDJPY / USDCAD
```

- 換算に必要な mid が取得できない場合は **null を伝播**(Equity/維持率は null → UI は「—」)。
- 割り算は scale + HALF_UP を明示。

### 3.3 Equity / 含み損益(基軸)
- `unrealizedPnlBase` = Σ(各建玉の含み損益[③, quote] を基軸換算)。
- `equity` = `balance` + `unrealizedPnlBase`。

### 3.4 必要証拠金(used margin)
ポジションごと:
```
notionalQuote   = quantity * mid(pair)          // 例 10,000 USD × 155 = 1,550,000 JPY(USD/JPY)
requiredMarginQuote = notionalQuote / leverage
requiredMarginBase  = toJpy(requiredMarginQuote, quote)
```
- `leverage` は `MarginRule`(ペア別 or 全体)から。無ければ既定 **25 倍(証拠金率4%)**・設定化。
- `usedMargin` = Σ requiredMarginBase(開いている建玉)。
- USD/JPY 例: (10,000 × 155)/25 = **62,000 JPY**(quote=JPY=基軸、換算不要)。

### 3.5 余力・維持率・状態
- `freeMargin` = `equity` − `usedMargin`。
- `marginRatio`(%) = `usedMargin > 0 ? equity / usedMargin × 100 : null`(建玉無しは「—」)。
- ロスカット閾値 `lossCutThreshold`(既定 **50%**・設定化)、警告 `warningThreshold`(既定 100%・任意)。
- `status`:
  - `marginRatio == null` → SAFE(建玉なし)
  - `marginRatio <= lossCutThreshold` → DANGER
  - `marginRatio <= warningThreshold` → WARNING
  - それ以外 → SAFE
- **状態は表示のみ。④a では決済も発注却下もしない。**

---

## 4. バックエンド仕様

### 4.1 まず既存調査(実装前に必須)
- `Account`(通貨/残高フィールドの有無)、`MarginRule`(leverage か証拠金率か)、
  `BankAccount` / `CashTransaction`(入金で残高を表すか)を `view` して報告。
- 既存に合わせて「残高の持ち方」を 1 つに決めて明記。

### 4.2 換算ユーティリティ
- `CurrencyConverter#toBase(amount, quoteCurrency)`(3.2)。基軸通貨は設定/口座から。
- レート不足時は null を返す。

### 4.3 サービス
- `PositionService` を拡張: 各建玉に `requiredMarginBase` を付与可能に。
- `AccountSummaryService`(新規): balance / realized(base)/ unrealized(base)/ equity /
  usedMargin / freeMargin / marginRatio / status を算出。

### 4.4 DTO / API
- `PositionResponse` に `requiredMargin`(基軸, 任意)を追加(additive)。
- 新規 `AccountSummaryResponse`:

| フィールド | 型 | 説明 |
|---|---|---|
| `accountId` | `String` | 例 `"DEMO-0001"` |
| `baseCurrency` | `String` | 例 `"JPY"` |
| `balance` | `BigDecimal` | 残高(基軸) |
| `realizedPnl` | `BigDecimal` | 実現損益(基軸, 残高に反映済の内訳) |
| `unrealizedPnl` | `BigDecimal`(null可) | 含み損益(基軸) |
| `equity` | `BigDecimal`(null可) | 有効証拠金 |
| `usedMargin` | `BigDecimal` | 必要証拠金合計 |
| `freeMargin` | `BigDecimal`(null可) | 余力 |
| `marginRatio` | `BigDecimal`(null可) | 維持率(%) |
| `lossCutThreshold` | `BigDecimal` | ロスカット閾値(%) |
| `status` | `String` | SAFE / WARNING / DANGER |

- 新規 `GET /api/trade/account/summary` を追加。
- `GET /api/trade/positions` は requiredMargin を含めてよい(任意)。

### 4.5 エッジケース

| ケース | 期待挙動 |
|---|---|
| 建玉なし | usedMargin=0、marginRatio=null(「—」)、equity=balance |
| 換算レート欠落 | 当該換算 null → equity/ratio 等 null →「—」、落ちない |
| Equity マイナス(大きな含み損) | ratio が低下/マイナス、status=DANGER(表示のみ) |
| MarginRule 未設定 | 既定レバレッジ(25)を使用 |
| 割り算/丸め | scale+HALF_UP、基軸表示は通貨に応じた桁(JPY は小数0等) |

---

## 5. フロントエンド仕様

### 5.1 AccountSummary(帯・「— — —」を置き換え)
- 表示: `accountId` / **評価額 Equity** / **維持率 Margin Lv.(%)** / **余力 Free Margin**。
- 必要なら残高(balance)も。数値は等幅 + tabular-nums。

### 5.2 MarginGauge(「Soon」を置き換え)
- **必要証拠金**(usedMargin)。
- **維持率ゲージ**: 現在の維持率を可視化。**ロスカット 50% ライン**を表示。
- 状態で配色: SAFE=緑 / WARNING=amber / DANGER=赤。
- ratio が null(建玉なし)のときは「—」。

### 5.3 更新タイミング
- Equity・維持率はレートで動くため、**既存のレート更新ポーリングに合わせて再取得**(ライブ更新)。
- 発注・決済後にも再取得。過剰な短間隔にしない。

### 5.4 確認ポイント
- 建玉を持つと必要証拠金・維持率が表示され、レートでライブに動く。
- 含み損が膨らむと維持率が下がり、50% に近づくと WARNING→DANGER に色が変わる(表示のみ、まだ決済はされない)。

---

## 6. 守ってほしい制約(チェック用)
- [ ] 既存 Account/MarginRule/BankAccount/CashTransaction を調査・報告してから着手
- [ ] 基軸通貨 JPY・初期残高シードが冪等
- [ ] quote→基軸 換算は {JPY,USD,CHF,CAD} を mid で正しく
- [ ] equity = 残高 + 含み損益(基軸)、維持率 = equity/必要証拠金×100
- [ ] 必要証拠金 = 数量×mid/レバレッジ を基軸換算
- [ ] **自動決済・発注却下をしていない**(④b)
- [ ] ②③を再実装せず PositionService を拡張
- [ ] レート/換算欠落で null→「—」、落ちない
- [ ] AccountSummary / MarginGauge を枠維持で配線、状態で配色
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能(監視・成行注文・建玉・評価損益・レイアウト)を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)
1. **既存調査**: Account/MarginRule/BankAccount/CashTransaction の現状を報告。
2. 基軸通貨・初期残高の冪等シード。
3. `CurrencyConverter`(quote→基軸)を実装。
4. 必要証拠金算出を `PositionService` に追加。
5. `AccountSummaryService` で balance/equity/usedMargin/freeMargin/marginRatio/status を算出。
6. `AccountSummaryResponse` と `GET /api/trade/account/summary` を追加。
7. ユニットテストを追加(8 章)。
8. フロント: AccountSummary / MarginGauge を配線(状態で配色)。
9. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)
- **換算**: 100 USD、mid(USD/JPY)=155 → 15,500 JPY。CHF/CAD も式どおり。
- **必要証拠金**: USD/JPY 10,000・mid155・lev25 → 62,000 JPY。
- **Equity**: balance=1,000,000、unrealizedBase=+3,000 → equity=1,003,000。
- **維持率**: equity=1,003,000、usedMargin=62,000 → ≈ 1617%。
- **建玉なし**: usedMargin=0 → marginRatio=null、equity=balance。
- **status**: 50% 以下 DANGER、100% 以下 WARNING、それ超 SAFE。
- **realized 反映**: 決済済の実現損益(基軸)が balance に乗る。
- **欠落**: 換算レート無しで null になり例外で落ちない。

---

## 9. 動作確認手順

### バックエンド
```
# 建玉を作る
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }

# 口座サマリ → equity / usedMargin / freeMargin / marginRatio / status
GET /api/trade/account/summary

# レートが動くと equity・維持率が変化(ライブ)
GET /api/trade/account/summary
```

### フロントエンド
- Trading 画面に Equity / 維持率 / 余力 / 必要証拠金 が表示され、レートでライブに動く。
- 含み損が膨らむと維持率が下がり、ゲージの色が SAFE→WARNING→DANGER に変化(まだ決済はされない)。
- 建玉なしのときは維持率「—」。
- 既存画面が壊れていない。

---

## 10. 次のチャンク ④b(参考・本書スコープ外)
- **ロスカット自動執行**: 維持率 ≤ ロスカット閾値で**建玉を強制決済**(LONG→Bid 売り / SHORT→Ask 買い、
  既存の約定経路を再利用)。実現損益確定 → 残高反映 → フラット。実行をアラート化。
  評価は独立スケジュール(レート更新に合わせた周期)で行う。
- **発注時の余力チェック**: 必要証拠金が free margin を超える新規発注を却下(理由付き)。
- 強制決済の取引には loss-cut 由来の印(任意)。

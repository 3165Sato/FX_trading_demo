# DemoFX 機能追加指示書: Spread監視の強化

> このファイルは、実装担当(Codex)にそのまま渡すための指示書です。
> DemoFX の既存プロジェクト概要ドキュメントが別途与えられている前提で、
> その内容を尊重しつつ「Spread監視の強化」だけを追加します。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実在サービスの仕様は再現しない。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- 今回は**追加中心の最小差分**。既存ロジックの大改修は禁止。
- 価格・金額・Spread は **`BigDecimal`** で扱う。`double`/`float` は使わない。
- 丸めは原則 **HALF_UP**。
- API は Entity を直接返さず **DTO** で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` 配下や `*.log` は Git 管理しない(既存 `.gitignore` を尊重)。
- **作業は下記「実装ステップ」の順に小さく分けて進める**。一度に全部やらない。

---

## 1. 今回のゴール

選択中の通貨ペアについて、Spread を **pips 単位**で監視できるようにする。
具体的には、直近 Tick 履歴から以下を算出して返す API を追加し、
既存の「Spread監視カード」を拡張して表示する。

- 現在の Spread(価格 / pips)
- 直近ウィンドウの平均 / 最小 / 最大 Spread(pips)
- Spread のステータス判定(NORMAL / WIDE / VERY_WIDE / INSUFFICIENT_DATA)

### スコープ内
- バックエンド: 新 DTO・新 Service・既存 `MarketRateController` へのエンドポイント追加
- フロントエンド: 既存 Spread監視カードの表示拡張

### スコープ外(今回はやらない)
- `MarketRateSimulator` の変更(Spread を変動させる等)
- Redis 連携
- 注文・約定・建玉などの取引系機能
- 新しいチャートやページの追加

---

## 2. 設計概要

### 2.1 pips への換算

`CurrencyPair.pipScale` を使って、Spread(価格)を pips に換算する。

```
spreadPips = spread.movePointRight(pipScale)
```

- 割り算を使わないため、ゼロ除算・無限小数の問題が発生しない。
- 表示用に pips は **小数第1位で HALF_UP** に丸める。
- 期待される pipScale(シード値):
  - USD/JPY: `2`
  - EUR/JPY: `2`
  - EUR/USD: `4`
- 検算:
  - USD/JPY spread `0.003` → `movePointRight(2)` = `0.3` pips
  - EUR/USD spread `0.00003` → `movePointRight(4)` = `0.3` pips

> ⚠️ `pipScale` が null または 0 以下の場合は pips を計算できない。
> その場合は pips 系フィールドを `null` で返し、warning ログを出す(例外で落とさない)。

### 2.2 監視ウィンドウ

- 既存 Tick 取得 API と同じく `limit`(Tick 件数)でウィンドウを指定する。
- Tick は 5 秒ごとに保存されるため、`limit=60` ≒ 直近 5 分。
- `limit` の補正は既存 Tick API と同じ規則:
  - 未指定 → `60`
  - 1 未満 → `1`
  - 1000 超 → `1000`

### 2.3 ステータス判定

直近ウィンドウの平均 Spread に対する現在 Spread の比率で判定する。

```
sampleCount < 5         → INSUFFICIENT_DATA
averageSpread <= 0      → INSUFFICIENT_DATA
ratio = currentSpread / averageSpread
ratio <= 1.5            → NORMAL
ratio <= 3.0            → WIDE
ratio >  3.0            → VERY_WIDE
```

> 補足: 現状のシミュレーターは Spread をほぼ一定に保つため、
> 通常は常に `NORMAL` になる。これは想定どおり。
> Spread を変動させる機能(疑似ニュースイベント等)を後で入れたときに
> WIDE / VERY_WIDE が意味を持つ。今回はその土台を作る。

---

## 3. バックエンド仕様

### 3.1 新規 DTO: `SpreadStatsResponse`

`dto`(既存 DTO と同じパッケージ)に追加。

| フィールド | 型 | 説明 |
|---|---|---|
| `currencyPair` | `String` | 例 `"USD/JPY"` |
| `bid` | `BigDecimal` | 現在の Bid(最新 MarketRate) |
| `ask` | `BigDecimal` | 現在の Ask(最新 MarketRate) |
| `spread` | `BigDecimal` | 現在の Spread(価格、priceScale) |
| `spreadPips` | `BigDecimal` | 現在の Spread(pips、小数1位)。算出不可なら null |
| `averageSpreadPips` | `BigDecimal` | ウィンドウ平均(pips)。算出不可なら null |
| `minSpreadPips` | `BigDecimal` | ウィンドウ最小(pips)。算出不可なら null |
| `maxSpreadPips` | `BigDecimal` | ウィンドウ最大(pips)。算出不可なら null |
| `status` | `String`(enum 名) | NORMAL / WIDE / VERY_WIDE / INSUFFICIENT_DATA |
| `sampleCount` | `int` | 計算に使った Tick 件数 |
| `limit` | `int` | 補正後の limit |
| `pipScale` | `Integer` | 参照した pipScale(null 可) |
| `quotedAt` | `Instant` | 現在レートの quotedAt |

ステータスは enum `SpreadStatus { NORMAL, WIDE, VERY_WIDE, INSUFFICIENT_DATA }` を定義し、
レスポンスでは名前(文字列)で返す。

### 3.2 新規 Service: `SpreadStatsService`

`service` パッケージに新規追加(既存 Service は極力変更しない)。

責務:
1. symbol から `CurrencyPair` を解決。
2. 最新 `MarketRate` を取得(現在の bid/ask/spread/quotedAt)。
3. **既存の Tick 取得ロジックを再利用**して直近 `limit` 件の `MarketRateTick` を取得。
   - 既存 Tick API が使っている Repository メソッドをそのまま使うこと(新規クエリは極力作らない)。
4. 各 Tick の `spread` から平均 / 最小 / 最大を算出。
   - 平均は作業 scale を `priceScale + 6`、HALF_UP で計算してから pips 換算。
5. pips 換算(2.1 のルール)。
6. ステータス判定(2.3 のルール)。
7. `SpreadStatsResponse` を組み立てて返す。

### 3.3 エンドポイント(既存 `MarketRateController` にメソッド追加)

```
GET /api/market/spread/stats?currencyPair=USD/JPY&limit=60
```

- `currencyPair` は**必須**。欠落時は **400**(既存 Tick API と同じ挙動)。
- 存在しない通貨ペアの場合は、**既存 `GET /api/market/rates/latest` と同じ not-found 挙動**に合わせる。
- `limit` の補正は 2.2 のとおり。
- レスポンス例:

```json
{
  "currencyPair": "USD/JPY",
  "bid": 155.120,
  "ask": 155.123,
  "spread": 0.003,
  "spreadPips": 0.3,
  "averageSpreadPips": 0.3,
  "minSpreadPips": 0.3,
  "maxSpreadPips": 0.3,
  "status": "NORMAL",
  "sampleCount": 60,
  "limit": 60,
  "pipScale": 2,
  "quotedAt": "2026-06-20T12:34:56Z"
}
```

### 3.4 エッジケース一覧

| ケース | 期待挙動 |
|---|---|
| `currencyPair` 未指定 | 400 |
| 通貨ペアが存在しない | 既存 latest API と同じ not-found 挙動 |
| Tick が 0 件 | 現在 Spread を avg/min/max に流用、`sampleCount=0`、`status=INSUFFICIENT_DATA` |
| `sampleCount < 5` | `status=INSUFFICIENT_DATA` |
| `pipScale` が null / 0 以下 | pips 系を null、warning ログ、例外で落とさない |
| 平均算出での割り算 | scale と HALF_UP を明示し `ArithmeticException` を回避 |

---

## 4. フロントエンド仕様

### 4.1 API クライアント

- `NEXT_PUBLIC_API_BASE_URL` を使う関数 `getSpreadStats(currencyPair, limit?)` を追加。
- 既存の fetch / エラー / ローディングのパターンに合わせる。

### 4.2 既存「Spread監視カード」の拡張

既存コンポーネントを**探して特定し**、表示項目を追加する(新規ページ・新規カードは作らない)。

表示内容:
- 現在 Spread: `spread`(価格) と `spreadPips`(pips)
- 平均 / 最小 / 最大 Spread(pips)
- ステータスバッジ:
  - `NORMAL` → 緑
  - `WIDE` → 黄(amber)
  - `VERY_WIDE` → 赤
  - `INSUFFICIENT_DATA` → グレー
- pips が null の場合は「—」等のプレースホルダ表示にする。

### 4.3 更新タイミング

- 選択中通貨ペアの変更時に取得。
- 既存ダッシュボードのポーリング間隔に合わせて定期更新する(独自に過剰な短間隔ポーリングを足さない)。

---

## 5. 実装ステップ(この順で小さく進める)

1. **事前確認**: シードデータで各 `CurrencyPair.pipScale` が
   USD/JPY=2 / EUR/JPY=2 / EUR/USD=4 で入っているか確認。
   入っていない/null の場合はユーザーに報告し、勝手に大改修しない。
2. enum `SpreadStatus` と DTO `SpreadStatsResponse` を追加。
3. `SpreadStatsService` を追加(既存 Tick 取得ロジックを再利用)。
4. `MarketRateController` にエンドポイントを 1 本追加。
5. バックエンドのユニットテストを追加(7. 参照)。
6. フロントに API クライアント関数を追加。
7. 既存 Spread監視カードを拡張。
8. 動作確認(8. 参照)。

各ステップ完了ごとに、変更ファイルと差分概要を簡潔に報告すること。

---

## 6. 守ってほしい制約(再掲・チェック用)

- [ ] 既存 Entity / Repository / Service / Controller / 画面を壊していない
- [ ] `BigDecimal` 使用、`double`/`float` 不使用、丸め HALF_UP
- [ ] Entity を直接返さず DTO で返している
- [ ] フロントは `NEXT_PUBLIC_API_BASE_URL` を使用、localhost 固定なし
- [ ] `MarketRateSimulator` を変更していない
- [ ] 新規クエリを増やさず既存 Tick 取得を再利用している
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 受け入れ条件 / テスト

`SpreadStatsService`(または計算部分)のユニットテストを追加する。

- **正常系**: spread が一定の Tick 列 → avg=min=max=current、`status=NORMAL`。
- **pips 換算**: spread `0.003`・pipScale `2` → `spreadPips=0.3`。
- **WIDE 判定**: 平均に対し現在が 2 倍 → `status=WIDE`。
- **VERY_WIDE 判定**: 平均に対し現在が 4 倍 → `status=VERY_WIDE`。
- **データ不足**: Tick 4 件以下 → `status=INSUFFICIENT_DATA`。
- **pipScale 不正**: pipScale=null → pips 系が null、例外で落ちない。

---

## 8. 動作確認手順

### バックエンド
```
GET http://localhost:8080/api/market/spread/stats?currencyPair=USD/JPY
GET http://localhost:8080/api/market/spread/stats?currencyPair=EUR/USD&limit=120
GET http://localhost:8080/api/market/spread/stats            # currencyPair なし → 400 を確認
```
- `spreadPips` が pips 値(例 0.3)で返ること。
- `status` が返ること(通常 NORMAL)。

### フロントエンド
- 監視画面で通貨ペアを切り替え、Spread監視カードに pips・平均/最小/最大・ステータスバッジが表示されること。
- バックエンド停止時に Loading のまま固まらず、エラー表示になること(既存方針を踏襲)。

---

## 9. (任意)発展案 — 今回はやらない

監視を視覚的に意味あるものにするための、将来の別ステップ案。
**今回の指示には含めない。** 着手する場合は別途切り出すこと。

- 候補3(疑似ニュースイベント)で一時的に Spread を拡大させ、WIDE/VERY_WIDE を発火させる。
- Spread監視カードに直近 Spread の sparkline(Recharts)を追加。
- 平均 Spread を Redis にキャッシュ(候補4 と連動)。

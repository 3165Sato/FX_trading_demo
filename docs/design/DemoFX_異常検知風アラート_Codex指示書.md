# DemoFX 機能追加指示書: 異常検知風アラート

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> DemoFX の既存プロジェクト概要、および実装済みの
> 「平均回帰シミュレーター」「Spread監視」「疑似ニュースイベント」を前提に、
> 市場の異常を検知してアラートを出す機能を追加します。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。
- これは「**異常検知"風"**」= **ルールベース(しきい値判定)**の簡易アラート。
  機械学習や統計的異常検知の厳密な実装ではない。学習用の近似であることをコメントに明記。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- **`MarketRateSimulator` は一切変更しない**。アラート評価は独立したスケジュール処理で行う。
- データは既存の最新レート / Tick 取得ロジック・Repository を**再利用**(重いクエリを新設しない)。
- アラート状態は**スキーマを増やさずメモリ保持**(永続化は任意の発展)。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- 価格・Spread 計算が絡む箇所は `BigDecimal`(途中計算の `double` は可)。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴールと位置づけ

各通貨ペアの市場状態を定期評価し、異常をアラートとして可視化する。
既存機能と連動して「監視室」の体験を完成させる:

```
ニュースイベント発火(#3) → 価格急変 + Spread拡大
  → 異常検知が「価格急変」「Spread拡大」アラートを発火
  → アラートパネルが点灯(severity 表示)
  → 収束すると自動で「解消」される
```

### スコープ内
- ルールベース検出器(中核4種)
- ステートフルなアラート(発生/解消・重複排除・履歴)
- アラート取得 API
- フロントのアラートパネル

### スコープ外(任意の発展へ)
- 機械学習・統計的異常検知
- ボラ急増検出(任意・デフォルト OFF)
- アラートの永続化 / 通知(メール等)
- 手動 acknowledge / 消し込み UI

---

## 2. 設計概要

### 2.1 検出器(中核4種 + 任意1種)

| type | 内容 | データ源 | severity の目安 |
|---|---|---|---|
| `SPREAD_WIDE` | Spread が異常に拡大 | **既存 SpreadStatsService を再利用** | WIDE→WARNING / VERY_WIDE→CRITICAL |
| `RAPID_MOVE` | 直近の値動きが急変 | 直近 Tick | 変化幅(bps)でしきい値判定 |
| `STALE_DATA` | レート配信が停滞 | 最新レートの quotedAt | 経過秒でしきい値判定 |
| `CROSSED_QUOTE` | bid ≥ ask(板の異常) | 最新レート | 常に CRITICAL |
| `VOLATILITY_SURGE`(任意) | 直近ボラがベースラインの倍率超 | 直近 Tick | 任意・デフォルト OFF |

### 2.2 各検出ルール

#### SPREAD_WIDE
- `SpreadStatsService` の `status` をそのまま使う(しきい値ロジックを二重実装しない)。
  - `WIDE` → WARNING、`VERY_WIDE` → CRITICAL、`NORMAL`/`INSUFFICIENT_DATA` → 非発火。

#### RAPID_MOVE
- 直近 Tick から `lookbackSeconds` 前に最も近い mid を `midPast`、最新 mid を `midNow`。
- `changeBps = |midNow - midPast| / midPast * 10000`(相対変化。ペア横断でしきい値を共通化できる)
- 判定: `changeBps >= criticalBps` → CRITICAL、`>= warnBps` → WARNING、未満 → 非発火。
- メッセージには向き(UP/DOWN)と、可能なら pips 換算も併記
  (`pips = |midNow - midPast|.movePointRight(pipScale)`、pipScale 無効時は省略)。
- 十分な Tick が無い場合は非発火(エラーにしない)。

#### STALE_DATA
- `ageSeconds = now - latestRate.quotedAt`
- `ageSeconds >= criticalSeconds` → CRITICAL、`>= warnSeconds` → WARNING、未満 → 非発火。
- 通常運転(1秒更新)では発火しない。シミュレーター停止等の検知用。

#### CROSSED_QUOTE
- `bid >= ask` なら CRITICAL。通常は起こらないが板の整合性チェックとして常時評価。

#### VOLATILITY_SURGE(任意 / デフォルト OFF)
- 直近 `lookbackSeconds` の per-tick リターン標準偏差が、
  ベースライン(例: より長い窓の stdev)× `baselineMultiplier` を超えたら WARNING。

### 2.3 アラートのライフサイクル(ステートフル + 重複排除)

- キー = `(currencyPair, type)`。**有効アラートはキーごとに 1 件**。
- 評価周期ごとに各ペア×各検出器を判定し:
  - 条件 true かつ 有効アラート無し → **新規発生**(`raisedAt=now`、active=true、履歴に追加)。
  - 条件 true かつ 有効あり → 維持(severity が上がったら更新可。`message` 更新可)。
  - 条件 false かつ 有効あり → **解消**(`resolvedAt=now`、active=false、有効マップから除外。履歴に残す)。
- 履歴は件数上限つきリングバッファ(例: 直近 100 件)。

### 2.4 評価の回し方(シミュレーター非依存)

- `@Scheduled(fixedDelayString=...)` で `evaluationIntervalSeconds`(例 3秒)ごとに評価。
- 対象は `enabled=true` の CurrencyPair。
- 各検出器は既存の最新レート / Tick 取得を再利用。
- **シミュレーターのコードには触れない**(完全に独立した監視処理)。
- スレッド安全性: スケジューラと API 読み取りが同じ状態を触るため、
  `ConcurrentHashMap` 等のスレッドセーフ構造を使う。

---

## 3. バックエンド仕様

### 3.1 enum / DTO

- enum `AlertType { SPREAD_WIDE, RAPID_MOVE, STALE_DATA, CROSSED_QUOTE, VOLATILITY_SURGE }`
- enum `AlertSeverity { INFO, WARNING, CRITICAL }`
- `AlertResponse`(DTO):

| フィールド | 型 | 説明 |
|---|---|---|
| `id` | `String` | 一意 ID |
| `type` | `String`(enum 名) | 検出器種別 |
| `currencyPair` | `String` | 対象ペア |
| `severity` | `String`(enum 名) | INFO/WARNING/CRITICAL |
| `message` | `String` | 日本語の人間可読メッセージ |
| `changePips` | `BigDecimal`(任意, null 可) | RAPID_MOVE 用など |
| `raisedAt` | `Instant` | 発生時刻 |
| `resolvedAt` | `Instant`(null 可) | 解消時刻(active なら null) |
| `active` | `boolean` | 有効かどうか |

### 3.2 `AnomalyAlertService`(新規 / `service` パッケージ)

責務:
- 有効アラート: `Map<(pair,type), Alert>`(スレッドセーフ)。
- 履歴: 件数上限つきリングバッファ。
- `evaluateAll()`: 全 enabled ペア × 全 enabled 検出器を評価し、2.3 のライフサイクルを適用。
- `listAlerts(activeOnly, currencyPair?, severity?, limit)`: 新しい順で返す。
- 各検出器は private メソッドに分割。`SPREAD_WIDE` は `SpreadStatsService` を注入して再利用。

### 3.3 スケジューラ

- `@Scheduled` で `evaluationIntervalSeconds` ごとに `evaluateAll()` を呼ぶ。
- `demofx.alerts.enabled=false` のときは評価をスキップ。
- 既存のスケジューリング設定(`@EnableScheduling` 等)があれば流用、無ければ追加。

### 3.4 API(新規 `AlertController`)

```
GET /api/market/alerts?activeOnly=false&currencyPair=USD/JPY&severity=WARNING&limit=50
```
- すべて任意パラメータ。新しい順で返す。
- `activeOnly=true` で有効のみ。
- `currencyPair` / `severity` 指定でフィルタ。
- `limit` は既存 API と同様に補正(未指定 50、1 未満→1、上限あり例 200)。
- レスポンスは `AlertResponse` の配列(DTO)。

> セキュリティ: 既存 SecurityConfig の `/api/**` permitAll(開発用)に従う。

### 3.5 設定(`application.yml`)

```yaml
demofx:
  alerts:
    enabled: true
    evaluationIntervalSeconds: 3
    history:
      maxEntries: 100
    rules:
      spread:
        enabled: true
        limit: 60            # SpreadStatsService に渡す窓
      rapidMove:
        enabled: true
        lookbackSeconds: 15
        warnBps: 30
        criticalBps: 60
      staleData:
        enabled: true
        warnSeconds: 10
        criticalSeconds: 30
      crossedQuote:
        enabled: true
      volatilitySurge:       # 任意
        enabled: false
        lookbackSeconds: 60
        baselineLookbackSeconds: 600
        baselineMultiplier: 3.0
```

### 3.6 エッジケース

| ケース | 期待挙動 |
|---|---|
| Tick 不足(RAPID_MOVE/VOL) | その検出器は非発火、エラーにしない |
| Spread が INSUFFICIENT_DATA | SPREAD_WIDE 非発火 |
| pipScale 無効 | changePips を省略(bps 判定は継続) |
| 同一(pair,type)で条件継続 | 重複発生させず 1 件を維持 |
| 条件が収まった | 解消(resolvedAt 設定)し有効マップから除外 |
| `alerts.enabled=false` | 評価しない(API は空 or 既存履歴のみ) |
| 並行アクセス | スレッドセーフ構造で破綻しない |

---

## 4. フロントエンド仕様

### 4.1 アラートパネル

監視画面に「異常検知 / Alerts」パネルを追加する。

- `GET /api/market/alerts` をポーリングして表示。
- **有効(active)アラートを上部に強調表示**、解消済みは下に淡色で履歴表示。
- severity で配色: CRITICAL=赤 / WARNING=黄(amber)/ INFO=グレー or 青。
- 各行: severity バッジ / type / ペア / メッセージ / 発生時刻(/ 解消時刻)。
- 有効件数のバッジ(例: 「Active: 2」)。
- 既存のローディング・エラー方針を踏襲(失敗時に固まらない)。
- `NEXT_PUBLIC_API_BASE_URL` を使用。

### 4.2 既存機能との見え方(確認ポイント)

- ニュースイベントを発火(#3)すると、
  `RAPID_MOVE`(価格急変)と `SPREAD_WIDE`(Spread拡大)が有効化されること。
- 収束すると両アラートが自動で解消され、履歴に移ること。

---

## 5. 守ってほしい制約(チェック用)

- [ ] ルールベース(しきい値)であり、学習用の近似である旨をコメント明記
- [ ] `MarketRateSimulator` を変更していない(評価は独立スケジュール)
- [ ] 既存の最新レート / Tick 取得を再利用(重いクエリ新設なし)
- [ ] SPREAD_WIDE は SpreadStatsService を再利用(しきい値の二重実装なし)
- [ ] アラートはステートフル(発生/解消)+(pair,type)で重複排除
- [ ] 状態はスレッドセーフ構造で管理
- [ ] しきい値・周期はすべて `application.yml` 化
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存 Entity / Repository / Service / Controller / 画面を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 6. 実装ステップ(この順で小さく)

1. enum(`AlertType` / `AlertSeverity`)と DTO `AlertResponse` を追加。
2. `AnomalyAlertService`(有効マップ・履歴・evaluateAll・listAlerts)を追加。
   - まず CROSSED_QUOTE と STALE_DATA(単純)から実装して動作確認。
3. RAPID_MOVE を追加(直近 Tick 利用)。
4. SPREAD_WIDE を追加(SpreadStatsService 再利用)。
5. `application.yml` に `demofx.alerts.*` を追加し設定クラスをバインド。
6. スケジューラで `evaluateAll()` を定期実行。
7. `AlertController`(GET 一覧)を追加。
8. ユニットテストを追加(7 章)。
9. フロントにアラートパネルを追加。
10. 動作確認(8 章)。(任意)VOLATILITY_SURGE を追加。

---

## 7. テスト(受け入れ条件)

- **RAPID_MOVE**: 大きな値動きの Tick 列 → 発火し severity が正しい。小さい動き → 非発火。
- **SPREAD_WIDE マッピング**: status=WIDE→WARNING、VERY_WIDE→CRITICAL、NORMAL→非発火/解消。
- **STALE_DATA**: quotedAt が古い → 発火、新しい → 非発火。
- **CROSSED_QUOTE**: bid≥ask → CRITICAL 発火。
- **ライフサイクル**: 条件 true→発生、true 継続→重複させず 1 件、false→解消(resolvedAt 設定)。
- **しきい値設定反映**: warn/critical のしきい値が config から効く。
- **Tick 不足**: 例外を出さず非発火。

---

## 8. 動作確認手順

### バックエンド
```
# まず通常時はアラートが少ない/無いことを確認
GET /api/market/alerts?activeOnly=true

# ニュースイベントを発火(#3)
POST /api/market/news/events
{ "currencyPair": "USD/JPY", "direction": "DOWN", "magnitudeBps": 120 }

# 直後にアラートを確認 → RAPID_MOVE / SPREAD_WIDE が active になる
GET /api/market/alerts?activeOnly=true&currencyPair=USD/JPY

# 数十秒後 → 解消され active から消え、履歴に残る
GET /api/market/alerts?currencyPair=USD/JPY&limit=20
```

### フロントエンド
- ニュースイベント発火 → アラートパネルに CRITICAL/WARNING が点灯。
- 収束後にアラートが解消され履歴へ移る。
- 既存の監視画面・チャート・Spread監視カードが壊れていない。

---

## 9. (任意)発展案 — 今回は含めない

着手する場合は別ステップとして切り出すこと。

- **VOLATILITY_SURGE**: 直近ボラがベースライン × 倍率を超えたら発火(設定で有効化)。
- **手動 acknowledge**: アラートを確認済みにする操作(PATCH/POST)。
- **永続化**: アラート履歴を DB 保存し再起動後も参照可能に。
- **通貨横断アラート**: 同一通貨を含む複数ペアで同時発火した場合の集約。
- **通知連携**: Webhook 等への通知(本デモのスコープ外)。

# DemoFX 機能追加指示書: 疑似ニュースイベント(急騰・急落)

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> DemoFX の既存プロジェクト概要、および既に実装済みの
> 「平均回帰つきシミュレーター」「Spread監視」を前提に、
> 疑似ニュースイベントによる急騰・急落を追加します。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実在サービス・実在ニュースは再現しない。
  - 見出し文言も**架空の汎用文**のみ(実在の組織・人物・出来事を出さない)。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- 価格・金額・Spread は **`BigDecimal`** で保持(priceScale で HALF_UP 丸め)。
  乱数・途中計算で `double` を使うのは可。
- 平常時(イベント無し)は**既存の挙動を完全に維持**する(倍率 1.0 で素通り)。
- イベント状態は**スキーマを増やさずメモリ保持**(永続化は任意の発展)。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴールと位置づけ

選択した通貨ペアに、疑似ニュースイベントで急騰(UP)・急落(DOWN)を起こせるようにする。

この機能は既存2機能と連動して 1 つの学習ループを完成させる:

```
ニュースイベント発火
  → mid が急騰/急落(チャートが跳ねる)
  → イベント中は Spread が拡大
  → Spread監視ステータスが WIDE / VERY_WIDE になる
  → 時間経過で通常に戻り、価格は平均回帰で徐々に戻る
```

### スコープ内
- バックエンド: ニュースイベントのモデル・サービス・API・シミュレーター連携
- イベント中の **Spread 動的拡大**(これが Spread監視を発火させる)
- フロント: 手動発火コントロール + 直近イベント一覧パネル

### スコープ外(今回はやらない / 任意の発展へ)
- 通貨スコープのイベント(例: 「USD 全体が動く」)→ 9 章
- ランダム自動発生のデフォルト有効化(実装は入れるがデフォルト OFF)
- イベントの永続化(DB 保存)
- 取引系(注文・建玉・損益)

---

## 2. 設計概要

### 2.1 イベントモデル

1 つのニュースイベントは以下を持つ:

| 項目 | 説明 |
|---|---|
| `id` | 一意 ID(UUID 文字列) |
| `currencyPair` | 対象シンボル(例 `"USD/JPY"`) |
| `direction` | `UP` / `DOWN` |
| `magnitudeBps` | 発生時の価格ジャンプ幅(ベーシスポイント) |
| `volatilityMultiplier` | イベント中のボラ倍率(例 5.0) |
| `spreadMultiplier` | イベント中の Spread 倍率(例 4.0) |
| `durationSeconds` | 効果継続時間(秒) |
| `headline` | 架空の見出し文字列 |
| `startedAt` / `endsAt` | 開始・終了時刻 |
| `active` | 現在有効かどうか |

### 2.2 価格への効き方(シミュレーター連携)

既存の mid 更新式に、イベント由来の項を足す。

```
（既存）reversion = -reversionStrength * (prevMid - basePrice)
（既存）shock     = prevMid * (volBps * volMul / 10000) * z   // volMul はイベント中のみ >1
（追加）jump      = prevMid * (signedJumpBps / 10000)          // 発生時の1tickのみ
newMidRaw = prevMid + reversion + shock + jump
newMid    = priceScale で HALF_UP 丸め(BigDecimal)
```

- `signedJumpBps`: `direction=UP` なら `+magnitudeBps`、`DOWN` なら `-magnitudeBps`。
  **イベント開始 tick で 1 回だけ**適用(以降は 0)。
- `volMul`: イベント中は `volatilityMultiplier`、平常時は `1.0`。
- ジャンプ後は **既存の平均回帰**で徐々に basePrice へ戻る(「跳ねて戻る」)。

> 任意: ジャンプの一部を恒久化したい場合は `permanentFraction`(0..1)を導入し、
> basePrice を `signedJump * permanentFraction` 分だけ移動する。**デフォルト 0(全戻り)**。

### 2.3 Spread の動的化(重要 / Spread監視と連動)

- 各ペアの **基準 Spread(baseSpread)** をシミュレーター起動時に保持
  (シード済み MarketRate の spread をメモリにコピー)。
- 各 tick の Spread:
  ```
  spread = round( baseSpread * spreadMul , priceScale, HALF_UP )
  ```
  - `spreadMul`: イベント中は `spreadMultiplier`、平常時は `1.0`。
- 平常時は倍率 1.0 = baseSpread なので**既存挙動と同じ**。
- bid/ask は既存どおり `bid = mid - spread/2`, `ask = mid + spread/2`(priceScale 丸め)。
- イベント終了で spread は baseSpread に戻り、Spread監視も NORMAL に戻る。

### 2.4 クランプの緩和

- パートAの `maxDeviationBps` クランプは、**イベント中の対象ペアでは適用しない**
 (または十分大きく緩める)。これがないと急騰・急落が抑え込まれて見えない。
- イベント終了後は通常クランプに戻り、平均回帰が範囲内へ収束させる。

### 2.5 シミュレーターとの結合点

シミュレーターは各 tick で、対象ペアの「現在の効果」をサービスから受け取る。

```java
EventModifiers m = newsEventService.consumeTick(symbol, now);
// m.signedJumpBps        : 開始tickのみ非0、それ以外0
// m.volatilityMultiplier : 平常1.0
// m.spreadMultiplier     : 平常1.0
// m.clampSuppressed      : イベント中true
```

`consumeTick` の責務:
- 開始 tick を検出したら `signedJumpBps` を返し、以後そのイベントでは 0 にする。
- 継続中は倍率を返す。
- `endsAt` を過ぎていたらイベントを失効させ、中立値(0, 1.0, 1.0, false)を返す。

> スレッド安全性: API スレッドとスケジューラ(シミュレーター)スレッドから
> 同時アクセスされるため、`ConcurrentHashMap` / `AtomicReference` など
> スレッドセーフな構造を使うこと。

---

## 3. バックエンド仕様

### 3.1 DTO / enum

- enum `NewsDirection { UP, DOWN }`
- `NewsEventResponse`(2.1 のフィールドを DTO 化。`direction`/`active` 等を含む)
- 内部用 `EventModifiers`(2.5。DTO ではなく内部クラスで可)

### 3.2 `NewsEventService`(新規 / `service` パッケージ)

責務:
- 有効イベントの保持: `Map<symbol, ActiveEvent>`(**1 ペア 1 有効イベント**。
  新規発火は既存の有効イベントを**置き換える**。挙動をコメントで明記)。
- 直近イベント履歴: 件数上限つきリングバッファ(例: 直近 50 件)。
- `trigger(request)`: イベント生成 → 有効マップへ登録 → 履歴へ追加 → 生成結果を返す。
  - `magnitudeBps` / `durationSeconds` は**上限クランプ**(例: magnitude ≤ 500bps, duration ≤ 300s)。
  - `direction` 省略時はランダム、`headline` 省略時は設定のサンプルからランダム選択。
- `consumeTick(symbol, now)`: 2.5 のとおり。
- `listEvents(activeOnly, limit)`: 履歴を新しい順で返す。

### 3.3 シミュレーター改修

- `MarketRateSimulator` の tick 内で `consumeTick` を呼び、2.2〜2.4 を反映。
- baseSpread マップを起動時に初期化(seeded MarketRate.spread をコピー)。
- 平常時(中立値)で既存挙動が一致することをテストで担保(8 章)。

### 3.4 API(新規 `NewsEventController`)

#### 発火
```
POST /api/market/news/events
Content-Type: application/json

{
  "currencyPair": "USD/JPY",      // 必須。未知/無効ペアは 404 or 400
  "direction": "DOWN",            // 任意。UP/DOWN、省略時ランダム
  "magnitudeBps": 80,             // 任意。省略時は設定 defaults、上限クランプ
  "durationSeconds": 30,          // 任意。同上
  "volatilityMultiplier": 5.0,    // 任意。省略時 defaults
  "spreadMultiplier": 4.0,        // 任意。省略時 defaults
  "headline": "予想外の政策金利変更" // 任意。省略時サンプルからランダム
}
```
- レスポンス: 生成された `NewsEventResponse`(201 or 200)。
- `currencyPair` 欠落 → 400。存在しない/disabled ペア → 既存 latest API と同じ not-found 挙動。

#### 一覧
```
GET /api/market/news/events?activeOnly=false&limit=20
```
- 直近イベントを新しい順で返す(`activeOnly=true` で有効のみ)。
- `limit` は既存 API と同様に補正(未指定 20、1 未満→1、上限あり)。

> セキュリティ: 既存 SecurityConfig で `/api/**` は permitAll(開発用)。本番設定ではない点は既存方針どおり。

### 3.5 設定(`application.yml`)

```yaml
demofx:
  news:
    enabled: true
    defaults:
      magnitudeBps: 60
      durationSeconds: 30
      volatilityMultiplier: 5.0
      spreadMultiplier: 4.0
    limits:
      maxMagnitudeBps: 500
      maxDurationSeconds: 300
    auto:
      enabled: false              # ランダム自動発生(デフォルト OFF)
      probabilityPerMinute: 0.2   # 有効時のみ使用
    headlines:                    # 架空・汎用の見出しサンプル
      - "予想外の政策金利変更"
      - "重要経済指標が市場予想を大きく上回る"
      - "要人発言で相場が急変"
      - "地政学リスクの高まりが意識される"
      - "市場予想を下回る指標で失望売り"
```

### 3.6 エッジケース

| ケース | 期待挙動 |
|---|---|
| `currencyPair` 未指定 | 400 |
| 未知 / disabled ペア | 既存 latest API と同じ not-found 挙動 |
| magnitude / duration が上限超 | 上限にクランプ |
| 同一ペアで連続発火 | 既存の有効イベントを置き換え |
| イベント終了 | spread が baseSpread に戻り、Spread監視が NORMAL に戻る |
| 平常時(イベント無し) | 倍率 1.0 / jump 0 で既存挙動と完全一致 |
| 並行アクセス | スレッドセーフ構造で破綻しない |

---

## 4. フロントエンド仕様

### 4.1 ニュース発火コントロール(デモ用)

監視画面に、学習用の発火 UI を追加する。

- 対象ペア(選択中ペアを既定に)/ 方向(UP / DOWN)を選んで「発火」ボタン。
- 押下で `POST /api/market/news/events` を呼ぶ(`NEXT_PUBLIC_API_BASE_URL` 使用)。
- 連打防止に短時間の disabled を入れる程度でよい。

### 4.2 直近イベントパネル

- `GET /api/market/news/events` をポーリングし、直近イベントを一覧表示。
- 表示: 見出し / ペア / 方向(▲赤=UP・▼青=DOWN など)/ 時刻 / 有効バッジ。
- 既存のローディング・エラー方針を踏襲(失敗時に固まらない)。

### 4.3 既存機能との見え方(確認ポイント)

- 発火するとチャートが急騰/急落すること。
- **Spread監視カードのステータスが WIDE / VERY_WIDE に変わること**(前機能の発火確認)。
- 時間経過で価格が戻り、Spread・ステータスが平常へ戻ること。

---

## 5. 守ってほしい制約(チェック用)

- [ ] 見出し・文言は架空の汎用文のみ(実在の組織/人物/出来事を出さない)
- [ ] 平常時は既存挙動と完全一致(倍率 1.0・jump 0)
- [ ] 価格・Spread は `BigDecimal`・priceScale 丸め(HALF_UP)
- [ ] baseSpread を保持し、イベント中のみ拡大している
- [ ] イベント中のみクランプを緩和している
- [ ] イベント状態はスレッドセーフな構造で管理
- [ ] magnitude / duration を上限クランプしている
- [ ] 自動発生はデフォルト OFF
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存 Entity / Repository / Service / Controller / 画面を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 6. 実装ステップ(この順で小さく)

1. enum `NewsDirection` / DTO `NewsEventResponse` / 内部 `EventModifiers` を追加。
2. `NewsEventService`(有効マップ・履歴・trigger・consumeTick・listEvents)を追加。
3. `application.yml` に `demofx.news.*` を追加し設定クラスをバインド。
4. `MarketRateSimulator` を改修(baseSpread 保持 + consumeTick 反映 + クランプ緩和)。
5. `NewsEventController`(POST 発火 / GET 一覧)を追加。
6. バックエンドのユニットテストを追加(7 章)。
7. フロント: 発火コントロール + 直近イベントパネルを追加。
8. 動作確認(8 章)。
9. (任意)自動発生を実装(デフォルト OFF のまま)。

---

## 7. テスト(受け入れ条件)

- **発火 → 有効化**: trigger 後、そのペアが有効イベントを持つ。
- **ジャンプ1回**: `consumeTick` が開始 tick のみ `signedJumpBps` を返し、以降 0。
- **方向**: UP で mid 上昇、DOWN で mid 下降(vol=0・reversion=0 で決定的に検証)。
- **Spread 拡大→復帰**: イベント中 `spread = baseSpread × spreadMultiplier`、
  終了後 `baseSpread` に戻る。
- **失効**: `endsAt` 経過後、中立値を返す。
- **平常一致**: イベント無しのとき mid/Spread の挙動が既存と一致。
- **上限クランプ**: magnitude/duration が上限を超えたら上限になる。
- **置き換え**: 同一ペアで再発火すると有効イベントが置き換わる。

---

## 8. 動作確認手順

### バックエンド
```
# 急落を発火
POST /api/market/news/events
{ "currencyPair": "USD/JPY", "direction": "DOWN", "magnitudeBps": 100 }

# 直後に最新レートを数回取得 → mid が下方向に跳ね、その後戻る
GET /api/market/rates/latest?currencyPair=USD/JPY

# Spread監視が WIDE/VERY_WIDE になることを確認
GET /api/market/spread/stats?currencyPair=USD/JPY

# 一覧
GET /api/market/news/events?limit=10
```

### フロントエンド
- 発火ボタンを押す → チャートが急騰/急落 → Spread監視カードが WIDE/VERY_WIDE。
- 数十秒後に価格・Spread・ステータスが平常へ戻る。
- 直近イベントパネルに発火履歴が出る。
- 既存3〜9ペアの表示が壊れていない。

---

## 9. (任意)発展案 — 今回は含めない

着手する場合は別ステップとして切り出すこと。

- **通貨スコープのイベント**: 対象を通貨(例 USD)にし、その通貨を含む全ペアへ波及。
  方向は base/quote の位置で反転(USD 高 → USD/JPY は UP、EUR/USD は DOWN)。
- **ランダム自動発生**: `demofx.news.auto.enabled=true` で、確率的にランダムイベント発生。
- **滑らかな減衰**: spread/vol 倍率をイベント中に線形/指数で減衰させ、急拡大→収束を自然に。
- **永続化**: イベント履歴を DB 保存し、再起動後も参照可能に。
- **ジャンプの恒久化**: `permanentFraction` で basePrice を一部移動(レジームシフト表現)。

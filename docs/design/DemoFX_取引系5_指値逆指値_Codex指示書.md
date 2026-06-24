# DemoFX 機能追加指示書: 取引系⑤ 指値・逆指値(新規エントリー)

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> 取引系①〜④b が実装済み(成行約定・建玉・評価損益・証拠金/維持率・ロスカット・余力チェック)で、
> 独立スケジュールでの監視パターン(ニュース/異常検知/ロスカット)と口座ロックがある前提。
> 今回は**新規エントリーの指値・逆指値**を追加する。
> 決済用の指値/逆指値(利確・損切り/TP・SL)は**別種別**として将来の別チャンクで扱う。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- **既存 `TriggerOrder` / `FxOrder` を再定義しない**。現状を調べ、足りない項目だけ最小追加(ステップ1)。
- 価格・数量・金額は **`BigDecimal`**、丸めは HALF_UP(priceScale / quantityScale)。
- 発動時の約定は**既存の成行約定の経路を再利用**(BUY=Ask / SELL=Bid、④bの余力チェック込み)。
- 監視は**シミュレーターの外で独立スケジュール**評価(simulator は変更しない)。
- 口座のトレード生成は既存の**口座ロックで直列化**(手動発注・ロスカット・トリガー発動が競合しない)。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴールと位置づけ

「指定価格に達したら新規にエントリーする」予約注文(指値・逆指値)を出せるようにする。
価格はシミュレーターで動き、ニュースイベントで急変するため、予約注文が自動で発動する。

- **指値(LIMIT)**: 今より有利な価格で待つ(買い=下で待つ / 売り=上で待つ)。
- **逆指値(STOP)**: 今より不利な価格で待つ(ブレイク狙い。買い=上抜けで買う / 売り=下抜けで売る)。

### 取引系の位置づけ
- ① 成行 → ② 建玉 → ③ 評価損益 → ④a/④b 証拠金・ロスカット → **⑤ 指値・逆指値(新規)**(本書)
- 決済用の指値/逆指値(TP/SL、OCO、IFD 等)は**別種別**として将来チャンク。

---

## 2. スコープ

### スコープ内
- 新規エントリーの指値・逆指値の発注(予約)
- トリガー監視(独立スケジュール)→ 条件成立で成行約定(既存経路再利用)
- 予約注文の取消
- 予約注文の一覧
- フロント: 注文パネルの種別選択+トリガー価格、待機注文パネル(取消)

### スコープ外(将来の別チャンク)
- **決済用の指値/逆指値(利確・損切り / TP・SL)**(別種別として提供)
- OCO / IFD / IFD-OCO、注文の有効期限(GTC/期限切れ)
- 指値での価格指定約定(発動は成行とする)

---

## 3. 設計概要

### 3.1 注文種別とトリガー条件
約定する側の価格(BUY=Ask / SELL=Bid)で判定する。

| 種別 | 方向 | 発動条件 |
|---|---|---|
| 指値 LIMIT | 買い BUY | **Ask ≤ 指値価格**(下がってきたら買う) |
| 指値 LIMIT | 売り SELL | **Bid ≥ 指値価格**(上がってきたら売る) |
| 逆指値 STOP | 買い BUY | **Ask ≥ 逆指値価格**(上抜けで買う) |
| 逆指値 STOP | 売り SELL | **Bid ≤ 逆指値価格**(下抜けで売る) |

### 3.2 発注時の検証(向きの正しさ)
発注時点の最新レートに対し、価格の向きが種別と整合しているかを確認(不正は却下):
- 買い指値: triggerPrice < 現在 Ask
- 売り指値: triggerPrice > 現在 Bid
- 買い逆指値: triggerPrice > 現在 Ask
- 売り逆指値: triggerPrice < 現在 Bid

その他: quantity > 0、ペア有効、triggerPrice は priceScale。

### 3.3 発動 → 約定(成行で実行)
- 監視で条件成立を検出したら、**既存の成行約定経路で執行**する。
  - 約定価格 = 現在の Ask(BUY)/ Bid(SELL)。トリガー価格と多少ずれ得る(急変時のスリッページ=仕様)。
  - **④bの余力チェックを通す**。証拠金不足なら**約定せず却下**(状態 REJECTED、理由付き)。
- 約定で FxOrder(FILLED, **source=TRIGGER**)と Trade を作成 → 建玉・損益・証拠金に反映。

### 3.4 予約注文の置き場(既存 TriggerOrder を使用)
- 予約(条件付き)注文は **`TriggerOrder`** に保持する(現状を調べて整合)。
- 発動結果としての約定は **FxOrder + Trade**(既存)で表す。TriggerOrder から結果注文を辿れるとよい。

### 3.5 状態(ライフサイクル)
- `TriggerStatus`: **PENDING**(待機)→ **TRIGGERED**(発動・約定済)/ **CANCELLED**(取消)/ **REJECTED**(発動時に余力不足等)。
- 一度 TRIGGERED/CANCELLED/REJECTED になったら再発動しない(ロック内で状態確認し冪等)。

### 3.6 監視(独立スケジュール)
- `@Scheduled`(`evaluationIntervalSeconds`、例 1秒=レート更新に合わせ短め)。
- PENDING の予約注文を取得 → ペアの最新レートで 3.1 を判定 → 成立分を発動。
- 口座ロックで手動発注・ロスカットと直列化。

---

## 4. バックエンド仕様

### 4.1 まず既存調査(実装前に必須)
- `TriggerOrder`(フィールド・状態の有無)、`FxOrder`(source 拡張余地)、成行約定サービスの入口、
  口座ロックの所在を `view` して報告。

### 4.2 enum / フィールド(additive)
- `OrderType` に **LIMIT / STOP** を追加(既存 MARKET に加える)。
- `FxOrder.source` に **TRIGGER** を追加(既存 MANUAL / LOSS_CUT に加える)。
- `TriggerStatus { PENDING, TRIGGERED, CANCELLED, REJECTED }`(既存にあれば流用)。
- `TriggerOrder` の意図する項目: id / account / currencyPair / side / orderType(LIMIT|STOP) /
  quantity / triggerPrice / status / createdAt / triggeredAt / resultingOrderId(任意, FxOrderへの参照)。

### 4.3 サービス
- `TriggerOrderService`: 発注(検証+登録)、取消、一覧。
- `TriggerOrderMonitor`(@Scheduled): PENDING を走査 → 条件判定 → 成行約定経路で発動
  (ロック・状態確認で冪等、余力不足は REJECTED)。

### 4.4 API

#### 予約発注
```
POST /api/trade/orders/pending
{ "currencyPair":"USD/JPY", "side":"BUY", "orderType":"LIMIT", "quantity":10000, "triggerPrice":154.500 }
```
- 検証(3.2)に反する場合は 400(理由付き、例 `INVALID_TRIGGER_PRICE`)。
- 未知/disabled ペア → not-found 挙動。最新レート無し → 409。
- レスポンス: 作成された予約注文 DTO(status=PENDING)。

#### 取消
```
POST /api/trade/orders/pending/{id}/cancel   （または DELETE /api/trade/orders/pending/{id}）
```
- PENDING のみ取消可(他状態は 409/400)。→ CANCELLED。

#### 一覧
```
GET /api/trade/orders/pending?status=PENDING&currencyPair=&limit=50
```
- 既定は PENDING。新しい順。DTO 返却。

> 既存 `GET /api/trade/orders`(約定/注文履歴)に status フィルタを足して兼ねてもよい。

### 4.5 設定(`application.yml`)
```yaml
demofx:
  pendingOrders:
    enabled: true
    evaluationIntervalSeconds: 1
```

### 4.6 エッジケース

| ケース | 期待挙動 |
|---|---|
| 向き不正(例: 現在Askより上の買い指値) | 400(INVALID_TRIGGER_PRICE) |
| quantity ≤ 0 / ペア無効 / レート無し | 400 / not-found / 409 |
| 急変で価格がトリガーを飛び越え | 現在の Ask/Bid で約定(スリッページ=仕様) |
| 発動時に証拠金不足 | 約定せず REJECTED(理由付き) |
| 取消対象が PENDING でない | 409/400 |
| 同ペアに複数の予約注文 | それぞれ独立に監視・発動 |
| 二重発動 | ロック内で状態確認し冪等 |
| pendingOrders.enabled=false | 監視・発動しない(発注・取消は可) |

---

## 5. フロントエンド仕様

### 5.1 OrderPanel(種別選択を追加)
- 注文種別: **成行 / 指値 / 逆指値** を選べるようにする。
- 指値・逆指値を選んだら **トリガー価格入力**を表示。現在の Ask/Bid と、入力すべき向き(例:「買い指値は現在Askより下」)をヒント表示。
- 成行は `POST /api/trade/orders/market`、指値・逆指値は `POST /api/trade/orders/pending`。
- 向き不正の却下(400)はエラー表示(「指値価格が不正です」等)。

### 5.2 待機注文パネル(新規 or OrderHistory枠を活用)
- `GET /api/trade/orders/pending` をポーリング表示。
- 行: 種別(指値/逆指値)/ ペア / 売買 / 数量 / トリガー価格 / 状態 /(PENDINGに)取消ボタン。
- 取消で `…/cancel` を呼ぶ。

### 5.3 連動の見え方(確認ポイント)
- 予約注文を置く → レートがトリガーに達すると自動約定 → 約定履歴・建玉・損益・維持率に反映。
- **ニュースイベントを撃つ**と急変で予約注文が次々発動するのが見える。
- 余力不足で発動が却下されるケースが分かる。

---

## 6. 守ってほしい制約(チェック用)
- [ ] 既存 TriggerOrder/FxOrder を調査・報告してから着手、再定義しない
- [ ] トリガー判定は約定側価格(BUY=Ask / SELL=Bid)
- [ ] 発注時に向きの正しさを検証(不正は却下)
- [ ] 発動は既存成行経路を再利用、④bの余力チェックを通す、source=TRIGGER
- [ ] 監視は独立スケジュール(simulator 不変更)、口座ロックで直列化、冪等
- [ ] 状態遷移が正しい(PENDING→TRIGGERED/CANCELLED/REJECTED、再発動しない)
- [ ] 決済用の指値/逆指値(TP/SL)は**入れない**(将来の別種別)
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能(成行・建玉・損益・証拠金・ロスカット・監視)を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)
1. **既存調査**: TriggerOrder/FxOrder・成行約定の入口・口座ロックを報告。
2. enum/フィールド追加(OrderType に LIMIT/STOP、FxOrder.source に TRIGGER、TriggerStatus、triggerPrice 等)。
3. `TriggerOrderService`(発注検証・登録・取消・一覧)を実装。
4. `TriggerOrderMonitor`(@Scheduled)で PENDING を判定 → 成行経路で発動(ロック・冪等・余力チェック)。
5. API(発注 / 取消 / 一覧)と DTO を追加。
6. `application.yml` に `demofx.pendingOrders.*` を追加。
7. ユニット/結合テストを追加(8 章)。
8. フロント: OrderPanel に種別+トリガー価格、待機注文パネル(取消)。
9. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)
- **買い指値**: triggerPrice<現在Ask で発注 → Ask が指値以下に下落 → 発動・Ask約定・建玉発生。
- **売り指値**: triggerPrice>現在Bid → Bid が指値以上に上昇 → 発動。
- **買い逆指値**: triggerPrice>現在Ask → Ask が逆指値以上に上昇 → 発動。
- **売り逆指値**: triggerPrice<現在Bid → Bid が逆指値以下に下落 → 発動。
- **向き不正**: 例)買い指値で現在Ask以上 → 却下(INVALID_TRIGGER_PRICE)。
- **取消**: PENDING を取消 → CANCELLED、その後トリガーに達しても発動しない。
- **余力不足発動**: 発動時に証拠金不足 → REJECTED(約定しない)。
- **冪等**: 一度発動した注文は再発動しない。

---

## 9. 動作確認手順

### バックエンド
```
# 買い指値(現在Askより下)を置く
POST /api/trade/orders/pending
{ "currencyPair":"USD/JPY","side":"BUY","orderType":"LIMIT","quantity":10000,"triggerPrice":154.500 }

# 待機注文を確認
GET /api/trade/orders/pending

# ニュースで下落させて発動を誘発(③の機能)
POST /api/market/news/events { "currencyPair":"USD/JPY","direction":"DOWN","magnitudeBps":200,"durationSeconds":60 }

# しばらく後:待機注文がTRIGGEREDになり、約定履歴・建玉に反映
GET /api/trade/orders/pending?status=TRIGGERED
GET /api/trade/trades?currencyPair=USD/JPY
GET /api/trade/positions

# 取消の確認
POST /api/trade/orders/pending      # 別の予約を作成
POST /api/trade/orders/pending/{id}/cancel
```

### フロントエンド
- 注文パネルで指値/逆指値を選び、トリガー価格を入れて発注 → 待機注文に出る。
- ニュースを撃って急変 → 予約注文が自動発動、約定履歴・建玉・損益・維持率が動く。
- 待機注文を取消できる。向き不正はエラー表示。
- 既存画面が壊れていない。

---

## 10. 次の候補(参考・本書スコープ外)
- **決済用の指値/逆指値(TP/SL)**: 保有建玉に対する利確(指値)・損切り(逆指値)。**別種別**として提供。
- **OCO / IFD / IFD-OCO**: 複合注文。
- **有効期限**: GTC / 当日 / 期限切れ(EXPIRED)。
- **指値での価格指定約定**: 発動を成行でなく指値価格で約定。

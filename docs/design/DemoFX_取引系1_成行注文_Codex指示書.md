# DemoFX 機能追加指示書: 取引系① 成行注文 + 即時約定 + 約定履歴

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> DemoFX の既存プロジェクト概要、および実装済みの
> 「シミュレーター」「Spread監視」「ニュースイベント」「異常検知アラート」を前提に、
> **取引系の最初のチャンク**として成行注文と約定を追加します。
>
> ⚠️ 取引系の `FxOrder` / `Trade` / `Position` / `Account` / `Customer` は
> **既にエンティティとして作成済み(ほぼ未使用)**の可能性が高い。
> 勝手に再定義せず、**まず既存を調べてすり合わせる**こと(実装ステップ1)。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実在サービス・実取引は一切扱わない。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- **既存エンティティ(FxOrder/Trade/Position/Account/Customer)を再定義しない**。
  現状フィールドを調査し、足りない項目だけ**最小追加**する。
- 価格・数量・金額は **`BigDecimal`**。priceScale / quantityScale で丸め(HALF_UP)。
- 認証は無し(既存 SecurityConfig の `/api/**` permitAll に従う、開発用)。
  ユーザー識別の代わりに**デフォルトのデモ口座 1 つ**を使う。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴールと取引系ロードマップ

### 今回(チャンク①)のゴール
成行注文を出すと、その場で現在の Bid/Ask で約定し、約定履歴に残る。

学習上の核(候補 #6):
- **BUY は Ask で約定**(買うときは売り手の提示価格)
- **SELL は Bid で約定**(売るときは買い手の提示価格)
- その差(Spread)が取引コスト。

### 取引系ロードマップ(分割方針)
1. **① 成行注文 + 即時約定 + 約定履歴**(本書。候補 5/6/7)
2. ② 建玉(ネッティング・平均建値)+ 建玉表示(候補 8)
3. ③ 評価損益(含み損益)表示(候補 9)
4. ④ 証拠金・維持率・ロスカット(候補 10)
5. (将来)指値・逆指値(TriggerOrder)、建玉の決済注文

> 本書は①のみ。②以降のために**拡張しやすい形**にするが、②以降の実装は含めない。

---

## 2. スコープ

### スコープ内
- 成行注文の発注 → 現在 Bid/Ask での即時約定
- `FxOrder`(注文)と `Trade`(約定)の永続化
- 約定履歴 / 注文履歴の取得 API
- フロントの成行注文パネル + 約定履歴表示
- デフォルトのデモ口座(Customer/Account)シード

### スコープ外(次チャンク以降)
- 建玉のネッティング・平均建値(②)
- 評価損益(③)
- 証拠金・維持率・ロスカット(④)
- 指値・逆指値(TriggerOrder)
- 建玉の決済・残高(balance)増減・約定キャンセル
- 複数口座 / ユーザー認証

> 今回は **net position を追跡しない**。BUY/SELL は独立した約定として記録するだけ。
> 数量の相殺や平均建値は②で扱う。

---

## 3. 設計概要

### 3.1 約定価格(最重要・候補 #6)
- 発注時点の最新 `MarketRate` を参照。
- **BUY → 約定価格 = ask**
- **SELL → 約定価格 = bid**
- priceScale で丸め済みの値をそのまま使う。

### 3.2 即時約定モデル
- 成行注文はサーバ受信時に**同期的に約定**(デモ簡易化)。
- 1 リクエスト = 1 注文 = 1 約定(部分約定なし)。
- 処理は `@Transactional` で「注文作成 + 約定作成」を 1 トランザクションに。

### 3.3 デフォルトデモ口座
- 認証が無いため、注文/約定は**デフォルトのデモ Account** に紐づける。
- 起動時に Customer/Account を**冪等にシード**(既存初期化の作法に合わせ、無ければ作る)。
- 複数口座は扱わない(将来拡張)。

### 3.4 既存エンティティとの整合(再定義しない)
- `FxOrder` / `Trade` / `Account` / `Customer` の現状を調べ、
  下記「意図する項目」に**足りないものだけ最小追加**する。
- 既存のフィールド名・型を尊重し、命名が違う場合は既存に合わせる。

---

## 4. バックエンド仕様

### 4.1 まず既存調査(実装前に必須)
以下を `view` して**現状フィールドを報告**してから設計をすり合わせる:
- `FxOrder`, `Trade`, `Position`, `Account`, `Customer` の各エンティティ
- それらの Repository が既にあるか
- Account/Customer のシード処理が既にあるか

### 4.2 enum(無ければ追加)
- `OrderSide { BUY, SELL }`
- `OrderType { MARKET }`(将来 LIMIT/STOP 拡張用に enum 化)
- `OrderStatus { FILLED, REJECTED }`(将来 PENDING/CANCELLED 拡張可)

### 4.3 エンティティ「意図する項目」(既存に最小追加で整合)

#### FxOrder(注文)
- id
- account(ManyToOne Account)
- currencyPair(ManyToOne CurrencyPair)
- side(OrderSide)
- orderType(OrderType。今回は MARKET 固定)
- quantity(BigDecimal、quantityScale)
- status(OrderStatus)
- requestedAt(Instant)
- createdAt / updatedAt

#### Trade(約定)
- id
- order(ManyToOne FxOrder)
- account(ManyToOne Account)
- currencyPair(ManyToOne CurrencyPair)
- side(OrderSide)
- quantity(BigDecimal)
- price(BigDecimal、priceScale。BUY=ask / SELL=bid)
- executedAt(Instant)
- createdAt

> 残高(balance)・建玉(Position)は今回更新しない(②④で扱う)。

### 4.4 約定ロジック(`TradeService` または `OrderService` 新規)
1. 入力検証: `quantity > 0`、`currencyPair` 必須。
2. CurrencyPair 解決(未知/disabled → 却下)。
3. 最新 MarketRate 取得(無ければ却下)。
4. 約定価格決定: BUY=ask / SELL=bid。
5. `FxOrder`(status=FILLED)と `Trade` を作成・保存(同一トランザクション)。
6. 結果 DTO を返す。

### 4.5 API(新規 `OrderController` / `TradeController`)

#### 成行発注
```
POST /api/trade/orders/market
{ "currencyPair": "USD/JPY", "side": "BUY", "quantity": 10000 }
```
- レスポンス `OrderResultResponse`(DTO):
  - order: { id, currencyPair, side, orderType, quantity, status, requestedAt }
  - trade: { id, price, side, quantity, executedAt }
- 検証失敗:
  - quantity ≤ 0 / side 不正 → 400
  - 未知 / disabled ペア → 既存 latest API と同じ not-found 挙動
  - 最新レート無し → 409(理由メッセージ付き)
- 却下注文の永続化は**任意**(今回は未永続でよい)。

#### 約定履歴
```
GET /api/trade/trades?currencyPair=USD/JPY&limit=50
```
- 新しい順。`currencyPair` 任意フィルタ。`limit` は既存同様に補正(未指定 50、上限 200)。
- DTO 返却(Entity 直返し禁止)。

#### 注文履歴(任意)
```
GET /api/trade/orders?currencyPair=&limit=50
```
- 余力があれば。無くても可。

### 4.6 デフォルト口座シード
- 起動時に Customer/Account を冪等にシード(既存の初期化作法に合わせる)。
- 既に存在すれば作らない。
- デモ口座の解決方法(固定 ID / 既定フラグ等)を 1 つに決めてコメント明記。

### 4.7 エッジケース

| ケース | 期待挙動 |
|---|---|
| quantity ≤ 0 / 非数 | 400 |
| side 不正 | 400 |
| 未知 / disabled ペア | not-found 挙動 |
| 最新レート無し | 409(理由付き) |
| quantity が quantityScale 超の精度 | quantityScale で丸めるか 400(どちらか決めて明記) |
| 同時発注 | トランザクションで各注文が独立して整合 |
| (任意)最大数量超 | config 上限で却下 |

---

## 5. フロントエンド仕様

### 5.1 成行注文パネル(候補 #5)
監視画面に注文パネルを追加(既存画面を壊さず additive)。

- 通貨ペア(選択中ペアを既定)
- サイド: **BUY / SELL** ボタン
- 数量入力(units)
- **現在価格の提示**: BUY 側に Ask、SELL 側に Bid を表示し、
  「BUY は Ask で約定 / SELL は Bid で約定」が**目で分かる**ようにする(学習の核)。
- 発注 → `POST /api/trade/orders/market` → 結果(約定価格・数量・時刻)を表示。
- 連打防止に発注中は短時間 disabled。
- `NEXT_PUBLIC_API_BASE_URL` 使用、既存のローディング/エラー方針を踏襲。

### 5.2 約定履歴(候補 #7)
- `GET /api/trade/trades` をポーリング or 発注後に再取得。
- 表示: 時刻 / ペア / サイド(BUY 赤・SELL 青 等)/ 数量 / 約定価格。

### 5.3 確認ポイント
- BUY の約定価格 = その時の Ask、SELL = その時の Bid になっていること。
- Spread の分だけ BUY と SELL の約定価格に差が出ること(学習の核)。

---

## 6. 守ってほしい制約(チェック用)

- [ ] 既存エンティティを調査・報告してから着手した
- [ ] 既存 FxOrder/Trade/Account/Customer を再定義せず最小追加で整合
- [ ] BUY=Ask / SELL=Bid で約定している
- [ ] 価格 priceScale / 数量 quantityScale で丸め(BigDecimal、HALF_UP)
- [ ] 約定は `@Transactional` で注文+約定を一括保存
- [ ] 建玉・残高・損益・証拠金は**触っていない**(次チャンク)
- [ ] デフォルト口座シードが冪等
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能(レート配信・監視・アラート等)を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)

1. **既存調査**: FxOrder/Trade/Position/Account/Customer と Repository・シードの現状を `view` して報告。
2. enum(OrderSide/OrderType/OrderStatus)を整備(無ければ追加)。
3. エンティティを「意図する項目」に最小追加で整合(必要な Repository も)。
4. デフォルト Customer/Account の冪等シードを追加。
5. 約定サービス(発注検証 → BUY=Ask/SELL=Bid → 注文+約定保存)を実装。
6. API(POST 成行発注 / GET 約定履歴)と DTO を追加。
7. ユニットテストを追加(8 章)。
8. フロント: 注文パネル + 約定履歴を追加。
9. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)

- **BUY 約定価格 = ask**(既知の MarketRate で検証)。
- **SELL 約定価格 = bid**。
- **quantity ≤ 0** → 却下(400)。
- **未知 / disabled ペア** → not-found 挙動。
- **最新レート無し** → 409。
- **永続化**: FxOrder(status=FILLED)と Trade が作成され、price/side/quantity/executedAt が正しい。
- **丸め**: price=priceScale、quantity=quantityScale。
- **口座**: 約定がデフォルト口座に紐づく。シードが冪等(再起動で重複しない)。

---

## 9. 動作確認手順

### バックエンド
```
# BUY 発注 → Ask で約定するはず
POST /api/trade/orders/market
{ "currencyPair": "USD/JPY", "side": "BUY", "quantity": 10000 }

# SELL 発注 → Bid で約定するはず
POST /api/trade/orders/market
{ "currencyPair": "USD/JPY", "side": "SELL", "quantity": 10000 }

# 約定履歴
GET /api/trade/trades?currencyPair=USD/JPY&limit=20
```
- BUY の price が Ask、SELL の price が Bid、差が Spread になっていることを確認。

### フロントエンド
- 注文パネルで BUY/SELL を発注 → 約定価格が Ask/Bid で表示。
- 約定履歴に反映される。
- 既存の監視画面・チャート・Spread監視・アラートが壊れていない。

---

## 10. 次のチャンク(本書スコープ外・参考)

- **② 建玉**: Trade を集約してペアごとの net 数量・平均建値を算出、建玉一覧 API + 表示。
- **③ 評価損益**: 建玉 × 現在レート(決済方向の Bid/Ask)で含み損益を計算・表示。
- **④ 証拠金・維持率**: MarginRule を使い必要証拠金・維持率を算出、ロスカット判定。

着手時はそれぞれ別の指示書として切り出す。

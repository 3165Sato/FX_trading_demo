# DemoFX 実装指示書: 画面レイアウト(監視/取引 2画面)ハンドオフ

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> Claude Design で作成した 2画面ワイヤー(監視 / 取引、デスクトップ監視室風)を、
> 既存の Next.js フロントに実装するための「ワイヤー → 実装」橋渡し仕様です。
> 参考ワイヤー: `DemoFX_Wireframe__standalone_.html`(別途共有)。

---

## 0. 大前提(必ず守ること)

- これは**フロントのレイアウト再構成**。バックエンド API は既存/既設計のものを使う(新設しない)。
- 既存の画面・コンポーネント・機能を**壊さない**。既存パネルは新レイアウトに**移植/再配置**する。
- **Claude Design の生成コードは"参照"に留める**。丸ごと貼らず、既存の React/TS/Tailwind 規約に合わせて実装。
- パネルのデータは**実際のバックエンド API に配線**する(ワイヤー内のモック配列は使わない)。
- API URL は localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- ワイヤーの「**Spec / 仕様」タブはアプリに出さない**(design 用の成果物)。
- スタック: **Next.js / React / TypeScript / Tailwind CSS / Recharts**。
- 対象は**デスクトップ(~1440px)**。モバイル対応は今回スコープ外(将来検討の注記のみ)。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴール

監視(Market Monitor)と取引(Trading)の **2画面**を、共通の固定ヘッダー+タブで切り替える
監視室風レイアウトに再構成する。既存パネルを所定のグリッドに配置し、将来機能の枠も確保する。

---

## 2. すり合わせ事項(ワイヤーと実装の差分 — 重要)

| ワイヤー上の表現 | 実装での扱い |
|---|---|
| 各パネルのモックデータ配列 | **実 API に配線**(下記 4 章の対応表) |
| アラート種別 `SPREAD_SPIKE` / `RATE_STALL` 等 | 実 enum **`SPREAD_WIDE` / `RAPID_MOVE` / `STALE_DATA` / `CROSSED_QUOTE`**(+任意 `VOLATILITY_SURGE`)に合わせる |
| ニュース見出し「BOJ」「ECB」「英 CPI」等 | **ハードコードしない**。API 由来の**架空・汎用見出し**を表示(実在組織/人物を出さない) |
| 「Spec / 仕様」タブ | **実装しない**(design 専用) |
| Account / Positions / PnL / Margin | **将来枠**。静的プレースホルダ(Coming soon)で place & size のみ確保。API 配線は後チャンク |

---

## 3. 全体シェル(共通)

### 3.1 ルーティング / タブ
- 2画面: `Market Monitor` と `Trading`。固定ヘッダーのセグメントタブで切替。
- Next.js のルート(例 `/monitor`, `/trading`、既定は `/monitor`)か、共通レイアウト下のタブ切替のいずれか。
  既存のルーティング構成に合わせて選択し、方針を報告すること。

### 3.2 固定ヘッダー(52px・両画面共通・sticky)
- 左: ロゴ「DemoFX」+「DEMO」バッジ、セグメントタブ(Monitor / Trading)。
- 右: フィード状態(LIVE / STALLED)、現在時刻(JST, hh:mm:ss)、アクティブアラート件数バッジ。
- フィード状態は最新レートの新しさ(quotedAt)から導出してよい(STALLED は STALE_DATA 相当)。

### 3.3 アプリ全体の共有 state
- **選択中通貨ペア(selectedPair)は画面横断で共有**(監視のレートボード選択が、チャート・Spread・Tick・
  取引の価格リファレンス/注文パネル既定に反映)。
- 実装は Context / 軽量ストア / URL クエリのいずれか。タブ切替後も保持されること。

---

## 4. 画面① Market Monitor

### 4.1 グリッド
- ターゲット幅 ~1440px。**3カラム grid `[372px / 1fr / 360px]`**。
- 左 = RateBoard、中央 = MainChart、右 = Spread + News + Alerts(縦積み)。
- 下部に**全幅 TickLog(高さ 188px 固定)**。
- 各パネルは個別スクロール、パネルヘッダーは sticky。

### 4.2 パネル → コンポーネント → API 対応

| パネル | コンポーネント | 配線する API | 主な表示 | 状態 |
|---|---|---|---|---|
| レートボード | RateBoard | `GET /api/market/rates` | 9ペアの symbol/bid/ask/mid/spread(pips)/上下矢印、選択強調 | loading/error |
| メインチャート | MainChart | `GET /api/market/rates/ticks?currencyPair=&limit=` | bid/ask/mid 3本線(Recharts)、時間幅 1m/5m/15m/1H | loading/empty/error |
| Spread監視 | SpreadCard | `GET /api/market/spread/stats?currencyPair=&limit=` | current/avg/min/max(pips)、status バッジ | loading/error |
| ニュース | NewsPanel | `GET /api/market/news/events` / `POST /api/market/news/events` | 直近イベント(見出し/ペア/▲▼/時刻/有効バッジ)+ デモ発火コントロール | loading/empty/error |
| アラート | AlertPanel | `GET /api/market/alerts` | active 強調 + 履歴、severity 色、件数 | loading/empty/error |
| Tick ログ | TickLog | `GET /api/market/rates/ticks?currencyPair=&limit=` | time/bid/ask/mid/spread の流れ | loading/empty/error |

- RateBoard の行クリックで `selectedPair` を更新 → Chart/Spread/Tick が連動。
- NewsPanel の「Fire demo」コントロール: ペア + UP/DOWN を選び `POST` で発火(学習デモ操作)。
- ポーリング間隔は既存方針に合わせる(過剰な短間隔にしない)。

---

## 5. 画面② Trading

### 5.1 レイアウト
- 上部に **AccountSummary 帯(将来枠)**。
- 本体 **2カラム grid `[430px / 1fr]`**。
- 左 = PriceRef + OrderPanel、右 = ExecutionHistory + OrderHistory + 将来枠(Positions / PnL / Margin)。

### 5.2 パネル → コンポーネント → API 対応(現行)

| パネル | コンポーネント | 配線する API | 主な表示 | 状態 |
|---|---|---|---|---|
| 価格リファレンス | PriceRef | `GET /api/market/rates/latest?currencyPair=` | BID(SELL約定)/ ASK(BUY約定)/ mid / spread(pips)=コスト | loading/error |
| 成行注文 | OrderPanel | `POST /api/trade/orders/market` | ペア/BUY・SELL/数量(1k/10k/100k クイック)/発注/Last Fill | submitting/error |
| 約定履歴 | ExecutionHistory | `GET /api/trade/trades?currencyPair=&limit=` | time/pair/side(色)/units/fill price | loading/empty/error |
| 注文履歴(任意) | OrderHistory | `GET /api/trade/orders`(任意) | 注文一覧 / empty 表示 | loading/empty/error |

- **学習の核**: PriceRef と OrderPanel で「BUY=Ask / SELL=Bid、差=Spread=コスト」が目で分かる見せ方を維持。
- OrderPanel の既定ペアは `selectedPair`。

### 5.3 将来枠(静的プレースホルダのみ・API 配線しない)

| パネル | コンポーネント | 後チャンク | プレースホルダ表示 |
|---|---|---|---|
| 建玉テーブル | PositionsTable | 取引系② | 列(Pair/Side/Units/Avg/Current/P&L)+「Coming soon」 |
| 損益サマリ | PnlSummary | 取引系③ | 合計評価損益 / 実現損益(— — —)+「Soon」 |
| 証拠金・維持率 | MarginGauge | 取引系④ | 必要証拠金 / 維持率ゲージ / ロスカット 50%(— — —)+「Soon」 |
| 口座サマリ | AccountSummary | 取引系②〜④ | DEMO-0001 / Equity / Margin Lv. / Free Margin(— — —) |

> 重要: 後で実装したときに**レイアウトを作り直さない**よう、場所とサイズを確保しておくこと。

---

## 6. デザイントークン(ワイヤー spec 準拠)

Tailwind の theme(カスタムカラー)に登録して使うのが望ましい。

### カラー
| 役割 | HEX |
|---|---|
| 背景 base | `#0d1117` |
| パネル面 | `#161b22` |
| ボーダー | `#262d38` |
| テキスト | `#e6edf3` |
| ミュート | `#768390` |
| 上昇(価格・チャート) | `#3fb950`(緑) |
| 下落(価格)/ SELL | `#f85149`(赤) |
| BUY | `#4493f8`(青) |
| 選択 / Info | `#58a6ff` |
| Warning | `#d29922`(amber) |

> **取引サイド色(BUY=青 / SELL=赤)と価格変化色(上=緑 / 下=赤)は別系統**として扱う(混同しない)。
> アラート severity: CRITICAL=赤 / WARNING=amber / INFO=青(or 灰)。

### タイポグラフィ
- UI ラベル・見出し: sans(Helvetica Neue / system)。
- **数値(価格・pips・損益・時刻・数量): JetBrains Mono + `tabular-nums`** で桁を縦に揃える。

---

## 7. データ状態の見せ方(全データパネル共通)
- **loading**: スケルトン or ローディング表示。
- **empty**: 「∅ データがありません」等の空表示。
- **error**: 「フィード取得に失敗しました」+「再試行 Retry」。失敗で固まらない(既存方針踏襲)。

---

## 8. 守ってほしい制約(チェック用)
- [ ] 2画面 + 共通固定ヘッダー(52px, sticky)+ セグメントタブ
- [ ] selectedPair が画面横断で共有・タブ切替後も保持
- [ ] 監視グリッド `[372px/1fr/360px]` + 全幅 TickLog(188px)
- [ ] 取引グリッド `[430px/1fr]` + 上部 AccountSummary 帯
- [ ] 各パネルが**実 API**に配線(モック配列不使用)
- [ ] アラート種別は実 enum、ニュース見出しは API 由来の架空文言
- [ ] 「Spec / 仕様」タブは出していない
- [ ] 将来枠は静的プレースホルダで place & size 確保(API 配線なし)
- [ ] デザイントークン適用(サイド色と価格色を別系統に)、数値は等幅+tabular-nums
- [ ] loading/empty/error 状態を実装
- [ ] Claude Design 生成コードを丸ごと貼らず既存規約に適合
- [ ] `NEXT_PUBLIC_API_BASE_URL` 使用、localhost 固定なし
- [ ] 既存機能を壊していない

---

## 9. 実装ステップ(この順で小さく)
1. 既存フロント構成(ページ・コンポーネント・API クライアント)を `view` して現状を報告。
2. 共通シェル(固定ヘッダー + タブ + selectedPair 共有 state)を実装。既存トップを Monitor へ移植。
3. Monitor のグリッドを組み、既存パネル(RateBoard/Chart/Spread/News/Alerts/Tick)を再配置・API 配線確認。
4. すり合わせ事項(2 章)を反映(enum 名・見出し・モック排除)。
5. Trading 画面を新設(PriceRef/OrderPanel/ExecutionHistory/OrderHistory を配置・配線)。
6. 将来枠(Account/Positions/PnL/Margin)を静的プレースホルダで配置。
7. デザイントークンを Tailwind theme 化し全体適用。数値フォント(JetBrains Mono)導入。
8. loading/empty/error 状態を各パネルに実装。
9. 動作確認(10 章)。

---

## 10. 動作確認
- ヘッダーのタブで Monitor / Trading を切替でき、selectedPair が両画面で共有される。
- Monitor: レートボードで通貨ペアを選ぶと Chart/Spread/Tick が連動。ニュース発火 → Alerts/Spread が反応。
- Trading: PriceRef に Bid/Ask、OrderPanel で BUY=Ask/SELL=Bid 発注 → 約定履歴に反映。
- 将来枠が崩れず place & size を確保して表示される。
- 各パネルが API 失敗時に error 表示で固まらない。
- 既存の機能(レート配信・監視・アラート・成行注文)が壊れていない。

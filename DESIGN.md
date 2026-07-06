---
theme: dark
fonts:
  ui: "Helvetica Neue, system-ui, sans-serif"
  numeric: "JetBrains Mono, monospace" # 数値は等幅 + tabular-nums
colors:
  bg: "#0d1117" # 背景 base
  panel: "#161b22" # パネル面
  border: "#262d38" # ボーダー
  text: "#e6edf3" # テキスト
  muted: "#768390" # ミュート
  up: "#3fb950" # 上昇・利益(緑)
  down: "#f85149" # 下落・損失・SELL(赤)
  buy: "#4493f8" # BUY(青)
  selection: "#58a6ff" # 選択・Info
  warning: "#d29922" # 警告(amber)
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
radius:
  card: "12px"
  control: "8px"
layout:
  header_height: "52px"
  screens: "Monitor / Trading / History" # 3画面(C-31で2→3に再構成)
  monitor_grid: "372px 1fr 360px" # 左:RateBoard / 中:Chart+EquityCurve / 右:Spread・News・Alerts
  monitor_center_split: "6 : 4" # 中央カラム縦配分 MainChart : EquityCurve
  ticklog_height: "188px" # 監視画面下部の全幅Tickログ
  trading_grid: "430px 1fr" # 左:PriceRef・OrderPanel・MarginGauge / 右:建玉・詳細・待機注文
  history_grid: "1fr 360px" # 左:約定/注文履歴 / 右:損益サマリ・将来枠
target_width: "1440px" # デスクトップ優先(監視室風・高密度)
---

## Design Rationale

暗い背景を基調とし、金融取引の監視コンソールらしい緊張感と高い情報密度を出す。
常時多くの数値が表示される画面なので、視線移動を減らし、変化に気づきやすくすることを最優先する。

### 色の意味(厳密に守る)

- **緑(up)** は上昇・利益にのみ使う。
- **赤(down)** は下落・損失・SELL にのみ使う。
- **青(buy)** は BUY と選択状態に使う。価格の上下色(緑/赤)と取引サイド色(青/赤)は**別系統**として扱い、混同しない。
- **黄(warning)** は警告に限定する(アラートの WARNING、維持率の警戒域など)。
- 色だけに意味を持たせず、ラベルや記号(▲▼など)も併用して識別できるようにする。

### 数値表現

- 価格・pips・損益・維持率・数量・時刻などの数値は**等幅フォント + tabular-nums** で桁を縦に揃える。
- 通貨に応じた桁で表示する(例:円は小数を抑える、対ドルは小数桁を多めに)。
- 値が取得できない/対象外のセルは「—」で表す。

### 画面構成(C-31: 3画面)

機能増に伴い、役割を「見る / 撃つ / 振り返る」で3画面に分離する。共通の固定ヘッダー(52px)+
**Monitor / Trading / History** のタブ。選択中の通貨ペアは画面横断で共有する。

#### Monitor(市場監視 = 見る)

3カラム `372px / 1fr / 360px`、下部に全幅 TickLog(188px)。各パネルは個別スクロール、ヘッダーは sticky。

- 左: RateBoard(9ペアの Bid/Ask/Mid/Spread、選択強調)
- 中央: **MainChart(bid/ask/mid)** と **EquityCurve(資産曲線)** を縦に並べる(配分 6:4)。
  - EquityCurve: 残高とEquityの2本線。時間幅切替 **5分 / 30分 / 1時間 / 全期間**。
- 右: SpreadCard / NewsPanel / AlertPanel

#### Trading(取引執行 = 撃つ)

「今この瞬間の判断に必要なものだけ」。履歴系は置かない(History へ)。
上部に口座サマリ帯(コンパクト: 口座ID / Equity / 維持率 / 余力)。本体 2カラム `430px / 1fr`。

- 左: PriceRef / **OrderPanel(2モードタブ)** / MarginGauge
  - OrderPanel タブ「シンプル」: 種別(成行/指値/逆指値)+ BUY/SELL + 数量 +(指値/逆指値時)トリガー価格。
  - OrderPanel タブ「複合」: IFD / IFO を**ステップ入力**(Step1 新規条件 → Step2 決済条件 → 確認)。
- 右: **PositionsTable(情報表示に徹する)** + **建玉詳細パネル(行選択で開く)** + **PendingOrders(PENDINGのみ)**
  - PositionsTable 行: ペア/サイド/数量/建値/Current/P&L/必要証拠金 + **TP・SL・OCO の有無バッジ**(価格は出さない)。
  - 建玉詳細パネル: 選択建玉の全情報 + 紐づく決済注文(TP/SL/OCO の価格・状態) + 操作(個別決済/TP設定/SL設定/OCO設定/取消)。
  - PendingOrders: 有効な待機注文のみ。IFD/IFO は親子(`└`等)で表現。終了済(TRIGGERED/CANCELLED/EXPIRED)は出さない。

#### History(履歴・口座 = 振り返る)

2カラム `1fr / 360px`。

- 左: ExecutionHistory(約定履歴: 時刻/ペア/売買/数量/約定価格/区分(新規・決済)/由来(手動・トリガー・TP/SL・ロスカット))
  / OrderHistory(注文・予約の全状態: PENDING/TRIGGERED/CANCELLED/REJECTED/EXPIRED を色分け)
- 右: PnlSummary(通貨別の実現/含み損益) / **将来枠**(期間損益レポート C-12・入出金 C-11 の置き場を確保)

### コンポーネント方針

- パネルは角丸 `12px`・面色 `#161b22`・1pxボーダー `#262d38` で統一。
- データを扱う領域は必ず **loading / empty / error** の3状態を用意し、失敗時に固まらない
  (error はメッセージ+「再試行」)。
- ステータスは配色で即判別できるようにする(例:スプレッド NORMAL/WIDE/VERY_WIDE、
  アラート INFO/WARNING/CRITICAL、維持率 SAFE/WARNING/DANGER)。
- 予約・決済注文の状態色: 待機=ミュート / 発動=緑 / 取消=ミュート / 却下=赤 / 失効=警告色。
- 一覧の行に操作を詰め込みすぎない。**表は情報表示、操作は選択→詳細パネルに寄せる**(PositionsTable と建玉詳細の関係)。
- 学習用の操作(ニュース発火など)は、通常の取引操作と見分けがつく見せ方にする。

### 将来枠の扱い

- 未実装の機能(History の期間損益レポート・入出金など)は、実装時にレイアウトを作り直さずに済むよう、
  **場所とサイズを確保**しておく(プレースホルダ可)。

---
theme: dark
fonts:
  ui: "Helvetica Neue, system-ui, sans-serif"
  numeric: "JetBrains Mono, monospace"   # 数値は等幅 + tabular-nums
colors:
  bg: "#0d1117"          # 背景 base
  panel: "#161b22"       # パネル面
  border: "#262d38"      # ボーダー
  text: "#e6edf3"        # テキスト
  muted: "#768390"       # ミュート
  up: "#3fb950"          # 上昇・利益(緑)
  down: "#f85149"        # 下落・損失・SELL(赤)
  buy: "#4493f8"         # BUY(青)
  selection: "#58a6ff"   # 選択・Info
  warning: "#d29922"     # 警告(amber)
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
  monitor_grid: "372px 1fr 360px"   # 左:RateBoard / 中:Chart / 右:Spread・News・Alerts
  ticklog_height: "188px"           # 監視画面下部の全幅Tickログ
  trading_grid: "430px 1fr"         # 左:PriceRef・OrderPanel / 右:履歴・建玉・損益・証拠金
target_width: "1440px"              # デスクトップ優先(監視室風・高密度)
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

### レイアウト
- デスクトップ(約1440px)優先。高密度・常時表示の監視室レイアウト。モバイル対応は今回スコープ外。
- 共通の固定ヘッダー(52px)+ Market Monitor / Trading のタブ。選択中の通貨ペアは画面横断で共有する。
- **Market Monitor**: 3カラム `372px / 1fr / 360px`(左=レートボード、中=チャート、右=スプレッド/ニュース/アラート)、
  下部に全幅の Tick ログ(188px)。各パネルは個別スクロール、パネルヘッダーは sticky。
- **Trading**: 上部に口座サマリ帯、本体 2カラム `430px / 1fr`(左=価格参照・注文パネル、
  右=約定/注文履歴・建玉・損益・証拠金)。

### コンポーネント方針
- パネルは角丸 `12px`・面色 `#161b22`・1pxボーダー `#262d38` で統一。
- データを扱う領域は必ず **loading / empty / error** の3状態を用意し、失敗時に固まらない
  (error はメッセージ+「再試行」)。
- ステータスは配色で即判別できるようにする(例:スプレッド NORMAL/WIDE/VERY_WIDE、
  アラート INFO/WARNING/CRITICAL、維持率 SAFE/WARNING/DANGER)。
- 学習用の操作(ニュース発火など)は、通常の取引操作と見分けがつく見せ方にする。

### 将来枠の扱い
- 未実装の機能(口座サマリの一部、建玉/損益/証拠金などを後から足す領域)は、
  実装時にレイアウトを作り直さずに済むよう、**場所とサイズを確保**しておく。

# DemoFX 機能指示書: UI再構成(C-31)第1段階 - Trading画面の再構成

## 1. 準拠
- 実装ルールは **CODEX.md** に従う。
  - 既存を壊さない
  - 小さな差分で進める
  - API URL は `NEXT_PUBLIC_API_BASE_URL` を使う
  - DTO前提で画面へ表示する
  - loading / empty / error を用意する
  - logs / *.log は Git 管理対象外にする
- UIは **DESIGN.md** の C-31 改訂版・3画面構成を正とする。
- バックエンドAPIの追加は不要。既存の trade / market 系APIを再利用する。
- DemoFXは学習用の架空FX取引デモアプリであり、実在サービスの仕様は再現しない。

## 2. ゴール
- Trading画面を「今この瞬間の判断と発注に必要なものだけ」に絞って再構成する。
- C-31 の3画面構成に向けて、ヘッダーを `Monitor / Trading / History` の3タブにする。
- History は第2段階までプレースホルダでよい。
- 本段階では履歴系パネルは Trading に残置してよい。第2段階で History へ移設する。

## 3. スコープ
### スコープ内
- Header の3タブ化
- Trading画面の通貨ペア選択状態を Monitor と独立させる
- Trading上部の口座サマリ帯をコンパクトに表示する
- PriceReferencePanel を Trading の選択通貨ペアに連動させる
- OrderPanel を「シンプル / 複合(IFD / IFO)」の2モードタブに再構成する
- PositionsTable を情報表示中心にする
- PositionDetailPanel を新設し、行選択で建玉詳細と操作を表示する
- PendingOrdersPanel は PENDING のみ表示する

### スコープ外
- History画面の本実装
- 履歴系パネルの移設
- Monitor画面の中央カラム再調整
- バックエンドAPIの新設・変更

## 4. 画面仕様
### 4.1 Header
- タブは以下の3つとする。
  - Monitor
  - Trading
  - History
- History は第1段階ではプレースホルダでよい。
- 選択中タブは既存のダークテーマに合わせて強調する。

### 4.2 通貨ペア選択状態
- Monitor と Trading の選択通貨ペアは独立させる。
- 例:
  - `monitorSelectedPair`
  - `tradingSelectedPair`
- Monitorで選択した通貨ペアが Trading の発注対象に影響しないようにする。
- Tradingで選択した通貨ペアが Monitor のチャート表示に影響しないようにする。
- 必要に応じて `localStorage` で画面ごとの選択状態を保持する。

### 4.3 Trading レイアウト
- 上部:
  - AccountSummaryBand
- 本体:
  - 2カラム `430px / 1fr`
- 左カラム:
  - PriceReferencePanel
  - OrderPanel
- 右カラム:
  - PositionsTable
  - PositionDetailPanel
  - PendingOrdersPanel
- 履歴系パネルは第1段階では残置してよいが、第2段階で撤去する前提とする。

## 5. OrderPanel仕様
### 5.1 モード
- OrderPanel は以下の2モードを持つ。
  - シンプル
  - 複合(IFD / IFO)

### 5.2 シンプル
- 既存の成行・指値・逆指値を集約する。
- 入力項目:
  - 注文種別: MARKET / LIMIT / STOP
  - 数量
  - トリガー価格
    - LIMIT / STOP のみ
  - BUY / SELL
- 表示:
  - BUY は Ask を使う
  - SELL は Bid を使う
  - Bid と Ask の差が Spread コストであることを明示する
- API:
  - MARKET: `POST /api/trade/orders/market`
  - LIMIT / STOP: `POST /api/trade/orders/pending`

### 5.3 複合(IFD / IFO)
- 複合モードでは IFD / IFO を切り替えられるようにする。
- Entry は MARKET 不可。LIMIT / STOP のみ。

#### IFD
- Step1: 新規条件
  - LIMIT / STOP
  - BUY / SELL
  - 数量
  - トリガー価格
- Step2: 決済条件
  - TP または SL
  - 決済価格
- API:
  - `POST /api/trade/orders/ifd`

#### IFO
- Step1: 新規条件
  - LIMIT / STOP
  - BUY / SELL
  - 数量
  - トリガー価格
- Step2: OCO決済条件
  - TP価格
  - SL価格
- API:
  - `POST /api/trade/orders/ifo`

### 5.4 エラー表示
- 入力不正、向き不正、余力不足などはOrderPanel内に表示する。
- エラー時も入力値は保持する。

## 6. PositionsTable仕様
### 6.1 役割
- PositionsTable は操作ボタンを直接置かず、情報表示に徹する。
- 操作は PositionDetailPanel へ寄せる。

### 6.2 表示項目
- 通貨ペア
- サイド
  - LONG: 青系
  - SHORT: 赤系
- 数量
- 建値
- Current
- P&L
- 必要証拠金
- 決済注文バッジ
  - TP
  - SL
  - OCO
- 行クリックで選択状態にし、PositionDetailPanel を更新する。

### 6.3 データ源
- `GET /api/trade/positions`

## 7. PositionDetailPanel仕様
### 7.1 表示
- 選択中の建玉について詳細を表示する。
- 表示項目:
  - 建玉ID
  - 通貨ペア
  - サイド
  - 数量
  - 建値
  - Current
  - P&L
  - 必要証拠金
  - Opened time

### 7.2 決済注文
- 紐づく決済注文を一覧表示する。
- TP / SL / OCO の価格・状態を表示する。
- 決済注文がない場合は、設定操作へ誘導する空状態を表示する。

### 7.3 操作
- 個別決済
  - `POST /api/trade/positions/{id}/close`
- TP設定 / SL設定
  - `POST /api/trade/positions/{id}/exit-orders`
- OCO設定
  - `POST /api/trade/positions/{id}/oco-orders`
- 決済注文取消
  - 対応する取消APIを呼び出す
- 操作中はボタンを disabled にし、処理中表示を出す。

## 8. PendingOrdersPanel仕様
- Trading では PENDING のみ表示する。
- 終了済みの注文は表示しない。
- IFD / IFO の親子関係は取得できる範囲で分かる表示にする。
- 親子注文や OCO には `parentOrderId` / `ocoGroupId` を活用する。
- 取消ボタンを表示する。

## 9. 実装方針
- 既存の `MarketMonitorDashboard.tsx` の構成を活かして拡張する。
- APIクライアントは `lib/marketRateTicks.ts` の既存関数を使う。
- 状態は大きなグローバルStoreを新設せず、既存のReact stateで管理する。
- Monitor と Trading の通貨ペア選択状態は分離する。
- 注文成功後は、必要なデータを再取得する。
  - trades
  - orders
  - pendingOrders
  - positions
  - pnlSummary
  - accountSummary
- ダークテーマ、枠線、等幅数値表現は既存デザインに合わせる。

## 10. 実装ステップ
1. 既存調査
   - Headerタブ構成
   - Monitor / Trading の選択通貨ペアstate
   - 既存OrderPanel
   - 既存PositionsTable
   - pending order / position / trade API配線
2. Header を3タブ化する。
3. Monitor と Trading の通貨ペア選択stateを分離する。
4. PriceReferencePanel に Trading 用の通貨ペアセレクタを追加する。
5. OrderPanel をシンプル / 複合の2モードにする。
6. 複合モードに IFD / IFO 入力を追加する。
7. PositionsTable を情報表示中心に変更する。
8. PositionDetailPanel を追加する。
9. TP / SL / OCO / 個別決済 / 取消を既存APIへ配線する。
10. PendingOrdersPanel を PENDING のみに絞る。
11. `npm run build` で TypeScript / Next.js ビルドを確認する。

## 11. 受け入れ条件
- Header に Monitor / Trading / History の3タブが表示される。
- History はプレースホルダでもよい。
- Monitor の選択通貨ペアと Trading の選択通貨ペアが独立している。
- Trading の Price reference と Order ticket は Trading の選択通貨ペアに連動する。
- シンプルタブで MARKET / LIMIT / STOP を発注できる。
- 複合タブで IFD / IFO を入力して発注できる。
- PositionsTable には建玉情報と決済注文バッジが表示される。
- 建玉行クリックで PositionDetailPanel が更新される。
- PositionDetailPanel から個別決済、TP / SL / OCO 設定、取消ができる。
- PendingOrdersPanel には PENDING のみ表示される。
- 既存のMonitor機能が壊れていない。
- `npm run build` が成功する。

## 12. 動作確認手順
### フロントエンド
```bash
cd FX_trading_front/fx-demo-front
npm run build
npm run dev
```

### 画面確認
- `http://localhost:3000/` を開く。
- Header に `Monitor / Trading / History` が表示されることを確認する。
- Monitorで通貨ペアを変更しても、Tradingの通貨ペアが変わらないことを確認する。
- Tradingで通貨ペアを変更しても、Monitorの通貨ペアが変わらないことを確認する。
- Tradingのシンプルタブから成行・指値・逆指値が発注できることを確認する。
- Tradingの複合タブから IFD / IFO が発注できることを確認する。
- 建玉を作成し、PositionsTable に表示されることを確認する。
- 建玉行をクリックし、PositionDetailPanel に詳細が表示されることを確認する。
- TP / SL / OCO 設定と取消ができることを確認する。
- PendingOrdersPanel に PENDING の注文のみ表示されることを確認する。

## 13. 補足
- 第1段階では履歴系パネルをTradingに残置してよい。
- 第2段階で ExecutionHistory / OrderHistory / PnlSummary をHistoryへ移設し、Tradingから撤去する。
- History画面の本実装とMonitor中央カラムの6:4調整は第2・3段階の対象とする。

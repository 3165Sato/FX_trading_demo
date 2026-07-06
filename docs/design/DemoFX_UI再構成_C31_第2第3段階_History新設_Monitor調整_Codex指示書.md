# DemoFX 機能指示書: UI再構成(C-31)第2・3段階 - History新設＋履歴移設 / Monitor調整

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
- C-31 第1段階で Trading に残置していた履歴系パネルを History 画面へ移設する。
- Trading は「今この瞬間に発注・建玉操作するための画面」に絞る。
- History は「約定・注文・損益を振り返る画面」として新設する。
- Monitor 中央カラムの MainChart と EquityCurve の縦配分を 6:4 に調整する。

## 3. スコープ
### スコープ内
- History タブの実画面化
- ExecutionHistory の History への移設
- OrderHistory の History への移設
- PnlSummary の History への移設
- Trading から履歴系パネルを撤去
- History 右下に将来枠プレースホルダを追加
- Monitor 中央カラムを MainChart : EquityCurve = 6 : 4 に調整
- EquityCurve の時間幅に 1時間を追加

### スコープ外
- バックエンドAPIの新設・変更
- 注文ロジックの変更
- 建玉ロジックの変更
- History の期間損益レポートや入出金履歴の本実装

## 4. 画面仕様
### 4.1 Header
- タブは以下の3つとする。
  - Monitor
  - Trading
  - History
- 現在選択中のタブは既存デザインに合わせて強調する。

### 4.2 Trading
- Trading には以下を残す。
  - AccountSummaryBand
  - PriceReferencePanel
  - OrderPanel
  - PositionsTable
  - PositionDetailPanel
  - PendingOrdersPanel
- Trading から以下を撤去する。
  - ExecutionHistoryPanel
  - OrderHistoryPanel
  - PnlSummaryPanel
- PendingOrdersPanel は PENDING のみを表示する。
- 履歴は History に移設済みであるため、Trading には表示しない。

### 4.3 History
- History は2カラム構成とする。
  - 左: `minmax(0, 1fr)`
  - 右: `360px`
- 左カラム:
  - ExecutionHistoryPanel
  - OrderHistoryPanel
- 右カラム:
  - PnlSummaryPanel
  - FutureHistoryPanel

#### ExecutionHistoryPanel
- 表示項目:
  - 時刻
  - 通貨ペア
  - 売買
  - 数量
  - 約定価格
  - 区分
  - 由来
- データ源:
  - `GET /api/trade/trades`
- DTOに存在しない項目は `--` と表示する。

#### OrderHistoryPanel
- 予約注文の全状態を表示する。
  - PENDING
  - WAITING
  - TRIGGERED
  - CANCELED
  - CANCELLED
  - REJECTED
  - EXPIRED
- データ源:
  - 既存の pending order API を status ごとに取得し、フロント側でマージする。
- 状態色:
  - TRIGGERED: green
  - REJECTED: red
  - EXPIRED: warning
  - PENDING / WAITING / CANCELED / CANCELLED: muted
- 親子注文は、取得できる範囲で `parentOrderId` を使って分かる表示にする。

#### PnlSummaryPanel
- History では損益表示に使う。
- Trading から移設するため、History では `accountSummary` がなくても表示できるようにする。
- loading / error / empty 相当の状態を表示できるようにする。

#### FutureHistoryPanel
- 将来機能用のプレースホルダ。
- 想定機能:
  - 期間損益レポート
  - 入出金履歴
- 表示は `Coming soon` でよい。

### 4.4 Monitor
- 中央カラムを以下の縦配分にする。
  - MainChart: 6
  - EquityCurve: 4
- 実装上は `grid-rows-[minmax(0,3fr)_minmax(0,2fr)]` などを使う。
- TickLog は既存のまま下部に維持する。
- 左カラム RateBoard、右カラム Spread / News / Alerts は変更しない。
- EquityCurve の時間幅は以下を用意する。
  - 5m
  - 30m
  - 1h
  - All

## 5. 実装方針
- 既存コンポーネントをできるだけ再利用する。
- History 用に必要な props のみ拡張する。
- Trading から履歴系を消す前に、History で表示できる状態にする。
- バックエンドAPIは追加せず、既存APIに合わせてフロントで整形する。
- 画面全体のダークテーマ、枠線、等幅数値表現は維持する。
- 画面内スクロールの既存方針を壊さない。

## 6. 実装ステップ
1. 既存調査
   - Trading に残っている履歴系パネルを確認する。
   - History タブの現状を確認する。
   - Monitor 中央カラムの MainChart / EquityCurve 配置を確認する。
2. History 画面を2カラムで実装する。
3. ExecutionHistoryPanel を History に表示する。
4. OrderHistoryPanel を History に表示する。
5. PnlSummaryPanel を History に表示する。
6. Trading から ExecutionHistory / OrderHistory / PnlSummary を撤去する。
7. FutureHistoryPanel を追加する。
8. Monitor 中央カラムを 6:4 に調整する。
9. EquityCurve に 1h 範囲を追加する。
10. `npm run build` で TypeScript / Next.js ビルドを確認する。

## 7. 受け入れ条件
- Header に Monitor / Trading / History の3タブが表示される。
- History タブに約定履歴が表示される。
- History タブに注文履歴が全状態で表示される。
- History タブに損益サマリが表示される。
- History 右下に将来枠のプレースホルダが表示される。
- Trading から履歴系パネルが撤去されている。
- Trading の発注、建玉、建玉詳細、PENDING待機注文の機能が壊れていない。
- Monitor 中央カラムで MainChart と EquityCurve が 6:4 程度の配分になっている。
- EquityCurve の時間幅に 1h がある。
- `npm run build` が成功する。

## 8. 動作確認手順
### フロントエンド
```bash
cd FX_trading_front/fx-demo-front
npm run build
npm run dev
```

### 画面確認
- `http://localhost:3000/` を開く。
- Header に `Monitor / Trading / History` が表示されることを確認する。
- `History` を開き、約定履歴・注文履歴・損益サマリが表示されることを確認する。
- `Trading` を開き、履歴系パネルが表示されていないことを確認する。
- `Monitor` を開き、中央カラムの MainChart と EquityCurve の縦配分が 6:4 程度になっていることを確認する。

## 9. 補足
- 注文履歴の全状態取得は、バックエンドAPIを変更せず、既存の status 指定APIを複数回呼び出してマージする。
- 約定履歴の「由来」は現時点のDTOに明示項目がない場合 `--` とする。
- FutureHistoryPanel は C-11 / C-12 の将来実装用の場所確保であり、今回の機能本体ではない。

# DemoFX 機能指示書: スワップ手動ロールオーバーUI

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> スワップ損益機能で用意した手動ロールオーバーAPIを、Monitor画面のデモ操作から実行できるようにします。

---

## 1. 準拠
- 実装ルールは **CODEX.md** に従う。
- UIは **DESIGN.md** に従い、既存のMonitor画面のトークン、余白、枠線、等幅数値表現を踏襲する。
- フロントエンドは `NEXT_PUBLIC_API_BASE_URL` 前提の既存APIクライアントを使う。
- 既存の発注、建玉、疑似ニュース、監視処理の挙動は変更しない。

## 2. ゴール / 位置づけ
- Monitor画面の `Fictional news` と同じデモ操作枠に、手動でスワップ付与を発生させる `Rollover` ボタンを追加する。
- ユーザーがデモ操作として「1日経過」を明示的に発生させ、建玉の未実現スワップ、口座サマリ、資産曲線への反映をすぐ確認できるようにする。
- スワップ損益(C-07)の検証導線を補完するUI追加であり、バックエンド計算仕様は変更しない。

## 3. スコープ
### スコープ内
- Monitor画面右カラムの `Fictional news` パネル内に `Rollover` ボタンを追加。
- 押下時に既存API `POST /api/market/swap/rollover` を呼び出す。
- 実行中表示、実行結果表示、エラー表示を既存の画面状態管理に接続。
- 実行後に建玉、損益サマリ、口座サマリ、資産履歴を再取得する。

### スコープ外
- スワップポイント計算式の変更。
- 自動ロールオーバースケジュールの変更。
- バックエンドAPIの追加、DTO変更。
- Trading / History画面のレイアウト変更。

## 4. 画面仕様
### 4.1 配置
- Monitor画面右カラムの `Fictional news` パネル内、UP/DOWN のデモショックボタンと同じ操作枠に配置する。
- 既存パネルの高さ、枠線、背景色、フォントサイズの印象を崩さない。

### 4.2 ボタン
- ラベル: `Rollover`
- サブラベル: `swap day`
- 実行中ラベル: `Applying...`
- 色調は警告/イベント系に近いアンバー系を使用し、BUY/SELL色や損益色と混同しない。
- 疑似ニュース実行中またはロールオーバー実行中は、同枠のデモ操作ボタンを無効化する。

### 4.3 結果表示
- 成功時は同パネル内に以下の形式で結果を表示する。

```text
ROLLOVER {appliedPositions} positions / JPY {totalAccruedSwap}
```

- エラー時は既存の `ConnectionIssue` 表示に乗せる。

## 5. API仕様
### 5.1 呼び出し
既存フロントAPI関数を利用する。

```ts
triggerSwapRollover(1)
```

送信先:

```http
POST /api/market/swap/rollover
Content-Type: application/json

{ "days": 1 }
```

### 5.2 レスポンス

```ts
type SwapRolloverResult = {
  days: number;
  appliedPositions: number;
  totalAccruedSwap: number;
  appliedAt: string;
};
```

## 6. 実装ステップ
1. 既存調査: `MarketMonitorDashboard.tsx`、`MarketMonitorScreens.tsx`、`marketRateTicks.ts` のロールオーバーAPI関数とMonitor右カラム構成を確認する。
2. `MarketMonitorDashboard` にロールオーバー送信中、成功メッセージ、エラーの状態を追加する。
3. `triggerSwapRollover(1)` を呼ぶハンドラを追加する。
4. 成功後に `loadPositions`、`loadPnlSummary`、`loadAccountSummary`、`loadEquityHistory` を呼ぶ。
5. `MonitorScreen` へ必要なpropsを追加し、`NewsEventPanel` に渡す。
6. `NewsEventPanel` に `Rollover` ボタンと結果表示を追加する。
7. TypeScriptビルドで確認する。

## 7. 受け入れ条件
- Monitor画面の `Fictional news` パネルに `Rollover` ボタンが表示される。
- 押下すると `POST /api/market/swap/rollover` が `days=1` で呼ばれる。
- 実行中は `Applying...` と表示され、同枠のデモ操作が多重送信されない。
- 成功後、建玉のスワップ、口座サマリ、損益サマリ、資産曲線が更新される。
- 成功結果がパネル内に表示される。
- エラー時は既存のエラー表示で確認できる。
- `npm run build` が成功する。

## 8. 動作確認
### フロントエンド
```bash
cd FX_trading_front/fx-demo-front
npm run build
```

### 画面確認
1. バックエンドとDBを起動する。
2. フロントエンドを起動し、Monitor画面を開く。
3. `Fictional news` パネル内の `Rollover` を押す。
4. ボタンが `Applying...` になり、完了後に `ROLLOVER ...` が表示されることを確認する。
5. Trading画面の建玉スワップ、口座サマリ、Monitorの資産曲線に反映されることを確認する。

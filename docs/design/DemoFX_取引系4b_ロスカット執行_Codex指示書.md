# DemoFX 機能追加指示書: 取引系④b ロスカット執行 + 発注時余力チェック

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> 取引系①〜④a が実装済みで、`AccountSummaryService` が維持率(marginRatio)等を
> 算出でき、`PositionService` が建玉を畳み込み算出でき、①の約定経路で成行約定できる前提。
> 今回は**執行**(状態を動かす部分)を追加する:
> ロスカット自動執行 と 発注時の余力チェック。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実取引・実資金は扱わない。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- 価格・数量・金額は **`BigDecimal`**、丸めは HALF_UP。
- ①〜④a のロジック(約定経路・建玉畳み込み・含み損益・証拠金/維持率算出)は
  **再実装せず再利用**する。
- ロスカット評価は**シミュレーターの tick 内ではなく独立スケジュール**で行う(simulator は変更しない)。
- 口座のトレード生成操作は**直列化(ロック)**して、手動発注と強制決済の競合を防ぐ。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴール

- 維持率がロスカット閾値以下になったら、**全建玉を自動で強制決済**する。
- 新規成行発注時に、**余力(free margin)が足りない注文を却下**する。
- ロスカット/証拠金警告を**既存のアラートパネルに表示**する。

---

## 2. スコープ

### スコープ内
- ロスカット監視(独立スケジュール)+ 全建玉強制決済
- 発注時の余力チェック(証拠金不足で却下)
- LOSS_CUT / MARGIN_WARNING アラート(既存アラートへ統合)
- フロント: 発注却下のエラー表示、ロスカット時の挙動確認

### スコープ外(任意)
- 順次決済(SEQUENTIAL)での部分ロスカット(全決済をデフォルトとする)
- 追証 / ゼロカット / スワップ / 手数料
- 入出金 UI、複数口座

---

## 3. 設計概要

### 3.1 ロスカット監視・執行
- `@Scheduled`(`evaluationIntervalSeconds`、例 2秒)で `AccountSummaryService` を呼び維持率を評価。
- 判定:
  ```
  ratio = summary.marginRatio
  if lossCut.enabled == false: return
  if ratio == null: return                 # 建玉なし/レート欠落 → 発動しない(誤発火防止)
  if ratio <= lossCutThreshold:            # ロスカット
      lock(account):
          再取得して建玉が無ければ return    # 冪等(二重決済防止)
          再計算して ratio がまだ閾値以下なら → 全建玉を強制決済
          LOSS_CUT アラート(CRITICAL)を発生
  elif ratio <= warningThreshold:          # 危険域
      MARGIN_WARNING(WARNING)を発生/維持
  else:
      MARGIN_WARNING を解消
  ```
- **強制決済(全建玉)**: 各建玉について反対売買を①の既存約定経路で実行
  - LONG → SELL を **Bid** で / SHORT → BUY を **Ask** で(数量は建玉全量)。
  - 生成する注文に **`source = LOSS_CUT`** を付与(下記 4.2)。
  - 決済で実現損益が確定 → ④a 経由で残高に反映 → 建玉フラット。
- 決済後は建玉が無くなり ratio=null となるため**再発動しない**。

### 3.2 発注時の余力チェック(発注前)
新規成行注文を**約定する前に**投影計算で判定:
```
if marginCheckEnabled == false: 通す
positions   = 現在の建玉(② 畳み込み)
markPrice   = 現在 mid(pair)
projected   = positions に (side, qty, markPrice) を ② のセマンティクスで適用
projectedUsedMargin = Σ requiredMarginBase(projected, 現在 mid)   # ④a の式
equityNow   = summary.equity                                       # 残高 + 含み損益(基軸)
if equityNow != null and (equityNow - projectedUsedMargin) < 0:
    → 却下(INSUFFICIENT_MARGIN, 理由付き)
else:
    → 通す
```
- **決済・減少方向の注文はブロックしない**(projected の必要証拠金が下がるため自然に通る)。
- ドテン/部分決済も projected で正しく扱える。
- `equityNow` が算出不能(レート欠落)なら**通す**(一時的欠落で発注を止めない)。任意でログ。
- 却下は 409/422 + 理由。却下注文の永続化は任意。

### 3.3 アラート統合(既存 AnomalyAlertService を拡張)
- `AlertType` に **`LOSS_CUT`**(CRITICAL)、**`MARGIN_WARNING`**(WARNING)を追加。
- これらは**口座スコープ**(currencyPair を nullable 化 or `"ACCOUNT"` 等の特別キー)。
- ライフサイクルは既存どおり(発生/解消)。LOSS_CUT は執行イベントとして発生(メッセージに維持率・決済建玉を含む)。
- AlertPanel にそのまま出る(フロント側は新タイプの色/表示に対応)。

> 既存サービス拡張が大きくなりすぎる場合は、マージン監視が同じ `AlertResponse` 形式で
> アラートを供給する別経路でも可。**表示は既存 AlertPanel に集約**すること。

---

## 4. バックエンド仕様

### 4.1 まず既存調査
- ①の約定サービス(成行約定の入口)を再利用できるか、口座ロックの置き場所、
  `AnomalyAlertService` の有効アラート構造(currencyPair 必須か)を確認・報告。

### 4.2 enum / フィールド追加(additive)
- `FxOrder` に **`source { MANUAL, LOSS_CUT }`**(既定 MANUAL)を追加。
- `AlertType` に `LOSS_CUT`, `MARGIN_WARNING` を追加。
- 必要なら Alert の `currencyPair` を nullable に(口座スコープ対応)。

### 4.3 サービス
- `OrderService`(①)に**発注前チェック**(3.2)を追加。`PositionService`/`AccountSummaryService` を利用。
- `MarginMonitor`(新規 + `@Scheduled`): 3.1 を実行。強制決済は①約定経路を呼ぶ。
- 口座単位ロック(`ReentrantLock` を口座キーで保持、または DB 行ロック)で
  発注・強制決済を直列化。

### 4.4 設定(`application.yml`)
```yaml
demofx:
  margin:
    lossCut:
      enabled: true
      thresholdPercent: 50          # ④a と共有(維持率の閾値)
      warningPercent: 100           # 危険域(任意)
      evaluationIntervalSeconds: 2
      liquidation: ALL              # ALL(全建玉) | SEQUENTIAL(任意)
    order:
      marginCheckEnabled: true      # 発注時の余力チェック
```

### 4.5 API への影響
- `POST /api/trade/orders/market`: 余力不足時に **409/422 + 理由**(`INSUFFICIENT_MARGIN`)。
  既存の成功レスポンスは変更しない。
- 強制決済は内部実行(専用 API は不要)。結果は既存の建玉/約定/サマリ/アラート API に反映。

### 4.6 エッジケース

| ケース | 期待挙動 |
|---|---|
| 建玉なし / ratio=null | ロスカット発動しない |
| レート欠落で equity 算出不能 | 発注は通す(止めない)、ロスカットは発動しない |
| 強制決済中に手動発注 | ロックで直列化、整合を保つ |
| 同一サイクルでの二重発動 | ロック内で再取得・再判定し冪等 |
| 決済・減少注文 | 余力チェックで却下しない |
| Equity マイナス(ギャップ) | 現在レートで全決済(残高がマイナスになり得る、デモ仕様) |
| lossCut.enabled=false | 監視・執行しない(④a の表示のみ) |

---

## 5. フロントエンド仕様

> ④a で AccountSummary / MarginGauge / AlertPanel は配線済みのため、④b の変更は軽い。

### 5.1 OrderPanel(発注却下の表示)
- 余力不足の却下(409/422)を受けたら、**「証拠金不足で発注できません」**等のエラー表示。
- 入力は保持し、固まらない(既存のエラー方針踏襲)。

### 5.2 AlertPanel(新タイプ対応)
- `LOSS_CUT`(CRITICAL)/ `MARGIN_WARNING`(WARNING)を表示・配色。
- 口座スコープのアラート(pair が無い)を崩れず表示。

### 5.3 任意
- 危険域でのバナー表示(「MARGIN DANGER」等)。

### 5.4 確認ポイント
- 含み損で維持率が 50% 以下になると、全建玉が自動決済され、PositionsTable が空に、
  AlertPanel に LOSS_CUT、AccountSummary の維持率が「—」に戻る。
- 余力を超える発注が却下される。決済方向の発注は通る。

---

## 6. 守ってほしい制約(チェック用)
- [ ] ①〜④a を再利用(約定経路・建玉・証拠金算出を再実装しない)
- [ ] ロスカット評価は独立スケジュール(simulator 不変更)
- [ ] ratio=null では発動しない(誤強制決済の防止)
- [ ] 口座のトレード生成をロックで直列化、二重決済しない(冪等)
- [ ] 強制決済は LONG→Bid / SHORT→Ask、source=LOSS_CUT
- [ ] 発注前チェックは projected(② 畳み込み)で判定、決済方向は却下しない
- [ ] equity 算出不能時は発注を止めない
- [ ] アラートは既存 AlertPanel に集約(LOSS_CUT/MARGIN_WARNING)
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)
1. **既存調査**: 約定サービスの入口・口座ロック置き場・アラート構造を報告。
2. `FxOrder.source`(MANUAL/LOSS_CUT)を追加。
3. 発注前の余力チェック(3.2)を `OrderService` に追加(まずここだけで検証)。
4. `MarginMonitor`(@Scheduled)で維持率評価 → 全建玉強制決済(ロック・冪等)。
5. `AlertType` に LOSS_CUT/MARGIN_WARNING を追加し、監視から発生・解消。
6. `application.yml` に `demofx.margin.*` を追加。
7. ユニット/結合テストを追加(8 章)。
8. フロント: 発注却下のエラー表示、新アラートタイプ対応。
9. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)
- **余力チェック(却下)**: free margin を超える新規発注 → 却下(INSUFFICIENT_MARGIN)。
- **決済は許可**: 建玉を減らす反対売買は余力チェックで却下されない。
- **ロスカット発火**: 維持率 ≤ 50% を作る → 監視が全建玉を強制決済 → フラット・実現損益が残高反映・LOSS_CUT 発生。
- **冪等**: 同条件で二重決済しない。
- **null 安全**: ratio=null では発動しない / equity 算出不能でも発注を止めない。
- **source**: 強制決済の注文が source=LOSS_CUT。
- 監視・約定の競合はロックで直列化される(設計レベルで担保、可能ならテスト)。

---

## 9. 動作確認手順

### バックエンド
```
# 大きめの建玉を作る
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":100000 }

# 含み損を作って維持率を落とす:ニュースイベント(③)で逆方向に大きく動かす
POST /api/market/news/events { "currencyPair":"USD/JPY","direction":"DOWN","magnitudeBps":300,"durationSeconds":60 }

# サマリで維持率の低下 → ロスカット発火を確認
GET /api/trade/account/summary
GET /api/trade/positions          # 強制決済後はフラット
GET /api/market/alerts?activeOnly=false   # LOSS_CUT が出る

# 余力チェック:過大な数量で発注 → 却下されることを確認
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":99999999 }
```

### フロントエンド
- ニュースで逆行 → 維持率が下がり、50% 割れで全建玉が自動決済、PositionsTable が空に、AlertPanel に LOSS_CUT。
- 余力超の発注が「証拠金不足」で却下、決済方向は通る。
- 既存画面(監視・建玉・評価損益・証拠金表示)が壊れていない。

---

## 10. これで取引系は一巡
- ① 成行注文 → ② 建玉 → ③ 評価損益 → ④a 証拠金・維持率 → ④b ロスカット・余力チェック。
- 以降の発展候補(任意): 指値・逆指値(TriggerOrder)、決済専用 UI、入出金、
  スワップ/手数料、複数口座、約定/損益のグラフ化、Redis キャッシュ(候補4)。

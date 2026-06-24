# DemoFX 機能追加指示書: 取引系② 建玉(ネッティング・平均建値)

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> 取引系①(成行注文 + 約定)が実装済みで、約定は `Trade` として
> デフォルトのデモ口座(DEMO-0001)に記録されている前提。
> 今回は Trade を集約して**建玉(ポジション)**を算出し、
> レイアウトで確保済みの **PositionsTable 枠**に表示する。

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- **既存 `Position` エンティティを再定義しない**。現状を調べ、足りない項目だけ最小追加 or
  算出専用 DTO を使う(後述。実装ステップ1で調査・報告)。
- 価格・数量・金額は **`BigDecimal`**。priceScale / quantityScale で丸め(HALF_UP)。
- **残高・証拠金・Equity・維持率は触らない**(取引系④)。
- **評価損益(現在値マーク)・P&L 表示はやらない**(取引系③)。今回は数量と平均建値の確定まで。
- API は Entity を直接返さず DTO で返す。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` / `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。完了ごとに差分を報告する。

---

## 1. ゴール

デフォルト口座の `Trade`(約定)を集約し、通貨ペアごとの**純ポジション**を出す:
- 純数量(net units)
- サイド(LONG / SHORT)
- 平均建値(average entry price)

これを API で返し、Trading 画面の **PositionsTable**(現在「Coming soon」の枠)に流し込む。

---

## 2. スコープ

### スコープ内
- Trade を時系列で畳み込み、ペアごとのネッティング建玉を算出
- 建玉取得 API
- PositionsTable への配線(Pair / Side / Units / Avg)
- (副産物)実現損益の**蓄積のみ**(表示はしない)

### スコープ外(次チャンク以降)
- 評価損益(含み損益)= 現在値マーク、P&L 列の表示(③)
- Current 価格列の表示(③)
- 残高・必要証拠金・維持率・ロスカット(④)
- 決済専用注文 UI(成行で反対売買すれば減る、で今回は足りる)
- 両建て(ヘッジ)モデル

---

## 3. 設計概要

### 3.1 ネッティングモデル
- **1通貨ペアにつき純ポジション 1 件**(netting)。両建てはしない。
- 符号付き数量で扱うと簡潔: `BUY = +qty`、`SELL = -qty`。
  - `signed > 0` → LONG、`signed < 0` → SHORT、`0` → フラット(行を出さない)。

### 3.2 算出方法(derive-on-read)
- 建玉は**保存状態に依存せず、Trade 履歴から都度算出**する。
- デフォルト口座の Trade を **`executedAt` 昇順**で取得し、ペアごとに畳み込む。
- 既存 `Position` エンティティは**任意でスナップショット保存**に使ってよいが、
  **算出結果(derive-on-read)を正とする**(ズレ防止・学習上の透明性)。

### 3.3 畳み込みロジック(ペアごと)
状態: `signed`(符号付き数量), `avg`(平均建値), `realized`(実現損益・蓄積のみ)。
各 Trade を `t`(符号付き数量), `p`(約定価格)として順に適用:

```
if signed == 0:
    signed = t ; avg = p
elif sign(signed) == sign(t):                 # 同方向に積み増し
    avg = (|signed|*avg + |t|*p) / (|signed| + |t|)     # 加重平均, priceScale丸め
    signed = signed + t
else:                                          # 反対売買(減少 / 決済 / ドテン)
    closeQty = min(|signed|, |t|)
    # 実現損益(蓄積のみ・今回は表示しない)
    if signed > 0:   realized += (p - avg) * closeQty   # LONGを売って決済
    else:            realized += (avg - p) * closeQty    # SHORTを買って決済
    if |t| < |signed|:        # 部分決済: avg 据え置き
        signed = signed + t
    elif |t| == |signed|:     # 全決済: フラット
        signed = 0 ; avg = 0
    else:                     # ドテン: 反対サイドを約定価格で新規
        signed = sign(t) * (|t| - |signed|) ; avg = p
```

- 計算は `BigDecimal`。加重平均・avg は **priceScale で HALF_UP**。
- `realized` は**蓄積するが今回は返さなくてよい**(③で利用)。返す場合は quote 通貨建てである旨を明記。
- `signed == 0` のペアは**建玉一覧に出さない**。

### 3.4 既存エンティティ整合
- 実装前に `Position` エンティティ・Repository の現状を `view` して報告。
- derive-on-read 専用の **算出結果クラス/DTO** を用意するのが簡潔。
- `Position` を永続スナップショットに使う場合も、フィールドは最小追加に留める。

---

## 4. バックエンド仕様

### 4.1 まず既存調査(実装前に必須)
- `Position` エンティティ・Repository、デフォルト口座の解決方法、
  Trade の取得クエリ(executedAt 昇順で口座+ペア指定取得できるか)を確認・報告。

### 4.2 enum / DTO
- `PositionSide { LONG, SHORT }`(または既存 OrderSide を流用するか、現状に合わせる)。
- `PositionResponse`(DTO):

| フィールド | 型 | 説明 |
|---|---|---|
| `currencyPair` | `String` | 例 `"USD/JPY"` |
| `side` | `String`(enum 名) | LONG / SHORT |
| `quantity` | `BigDecimal` | 純数量(絶対値、quantityScale) |
| `averagePrice` | `BigDecimal` | 平均建値(priceScale) |
| `updatedAt` | `Instant`(任意) | 最終約定時刻など |

> Current 価格・評価損益は**含めない**(③で追加)。`realizedPnl` を載せるかは任意(③向け)。

### 4.3 `PositionService`(新規 / `service` パッケージ)
- `getPositions(account)`: デフォルト口座の Trade を executedAt 昇順取得 → ペアごとに 3.3 を畳み込み →
  `signed != 0` のペアだけ `PositionResponse` 化して返す。
- ペアの並び順は安定なもの(symbol 昇順 or 最終更新降順)に決めて明記。

### 4.4 API(新規 `PositionController`)
```
GET /api/trade/positions
```
- デフォルト口座の建玉一覧(フラットは除く)を返す。
- (任意)`?currencyPair=USD/JPY` で 1 ペア取得。
- DTO 返却(Entity 直返し禁止)。

### 4.5 エッジケース

| ケース | 期待挙動 |
|---|---|
| 約定が無い / 全てフラット | 空配列(PositionsTable は empty 表示) |
| 部分決済 | 数量減・平均建値据え置き |
| 全決済 | そのペアは一覧から消える |
| ドテン(反転) | 反対サイドを約定価格で新規、数量=超過分 |
| 複数ペア | ペアごとに独立して算出 |
| 加重平均の割り算 | scale + HALF_UP 明示で `ArithmeticException` 回避 |
| priceScale/quantityScale | avg=priceScale、quantity=quantityScale で丸め |

---

## 5. フロントエンド仕様

### 5.1 PositionsTable(確保済み枠に配線)
- 現在の「Coming soon」プレースホルダを、`GET /api/trade/positions` の表示に置き換える。
  **枠の位置・サイズは維持**(レイアウト崩さない)。
- 列: **Pair / Side / Units / Avg Price**(配線する)。
  **Current / P&L 列は「—」のまま**(③で配線)。
- Side 配色は既存トークン準拠: **LONG=BUY色(青)/ SHORT=SELL色(赤)**。
- 数量・価格は等幅 + tabular-nums。
- 空のときは empty 表示(「建玉がありません」等)。

### 5.2 更新タイミング
- 約定(成行発注)後に建玉を再取得。
- 既存のポーリング間隔に合わせて定期更新(過剰な短間隔にしない)。

### 5.3 確認ポイント
- 同方向の連続 BUY で平均建値が加重平均になる。
- 反対売買で数量が減り、超過すればサイドが反転する。
- 全決済でそのペアが一覧から消える。

---

## 6. 守ってほしい制約(チェック用)
- [ ] 既存 `Position` を調査・報告してから着手
- [ ] ネッティング(1ペア1建玉)・両建てなし
- [ ] Trade を executedAt 昇順で畳み込み derive-on-read で算出
- [ ] 加重平均・据え置き・ドテンのロジックが正しい
- [ ] avg=priceScale / quantity=quantityScale で BigDecimal 丸め
- [ ] 評価損益・Current 価格・残高・証拠金は**触っていない**(③④)
- [ ] PositionsTable は枠を維持して Pair/Side/Units/Avg を配線、P&L 列は「—」
- [ ] API は DTO 返却、フロントは `NEXT_PUBLIC_API_BASE_URL` 使用
- [ ] 既存機能(レート配信・監視・成行注文・レイアウト)を壊していない
- [ ] `logs/` / `*.log` をコミットしていない

---

## 7. 実装ステップ(この順で小さく)
1. **既存調査**: Position エンティティ/Repository・デフォルト口座・Trade 取得クエリの現状を報告。
2. enum(PositionSide)/ DTO(PositionResponse)を整備。
3. `PositionService`(Trade 昇順取得 → 畳み込み → フラット除外)を実装。
4. `PositionController`(GET 建玉一覧)を追加。
5. ユニットテストを追加(8 章)。
6. フロント: PositionsTable を配線(Pair/Side/Units/Avg、P&L は「—」)。
7. 動作確認(9 章)。

---

## 8. テスト(受け入れ条件)
- **積み増し**: BUY 10k @100、BUY 10k @102 → LONG 20k、avg=101。
- **部分決済**: 上から SELL 5k → LONG 15k、avg=101(据え置き)。
- **全決済**: SELL 15k → フラット(一覧から消える)。
- **ドテン**: LONG 10k から SELL 15k → SHORT 5k、avg=決済時の約定価格。
- **SHORT 側**: SELL 起点で対称に成立。
- **複数ペア**: ペアごとに独立。
- **(任意)実現損益符号**: LONG 決済で `(p-avg)`、SHORT 決済で `(avg-p)` の符号が正しい。
- **丸め**: avg=priceScale、quantity=quantityScale。

---

## 9. 動作確認手順

### バックエンド
```
# 同じペアで BUY を重ねる
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }

# 建玉確認 → LONG 20000、avg が2回の約定(Ask)の加重平均
GET /api/trade/positions

# 反対売買で減らす
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"SELL","quantity":15000 }
GET /api/trade/positions   # → LONG 5000、avg 据え置き
```

### フロントエンド
- Trading 画面で BUY/SELL を発注 → PositionsTable に Pair/Side/Units/Avg が反映。
- 反対売買で数量が減り、全決済で行が消える。ドテンでサイドが反転。
- Current / P&L 列は「—」のまま(③で配線)。
- 既存画面(Monitor / Execution・Order history 等)が壊れていない。

---

## 10. 次のチャンク(参考)
- **③ 評価損益**: 建玉 × 現在レート(決済方向 = LONG は Bid、SHORT は Ask)で含み損益を算出し、
  PositionsTable の Current / P&L 列と PnlSummary(Unrealized/Realized)を配線。
  実現損益は本書の畳み込みで蓄積した値を利用。
- **④ 証拠金・維持率**: MarginRule で必要証拠金・維持率を算出、AccountSummary と MarginGauge を配線、ロスカット判定。

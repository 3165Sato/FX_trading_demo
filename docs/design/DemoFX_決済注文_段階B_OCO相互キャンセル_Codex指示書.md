# DemoFX 機能指示書: 決済注文 段階B（OCO・TP と SL の相互キャンセル）

## 1. 準拠
- 実装ルールは **CODEX.md** に従う(BigDecimal/HALF_UP、DTO返却、独立スケジュール、口座ロックで直列化、
  既存を壊さない・小ステップ、NEXT_PUBLIC_API_BASE_URL、logs非Git、着手前に既存を調査・報告)。
- UIは **DESIGN.md** に従う(PositionsTable、状態の色分け)。
- 設計の正: **「決済注文 TP/SL → OCO → IFD/IFO 設計全体像」**(本書は段階B)。
- 前提機能: 段階A(単独 TP/SL・建玉紐づけ・建玉消滅で自動失効EXPIRED)。
  決済価格方式(利確=指定価格 / 損切り=現在値・スリッページ幅指定なし)・トリガー条件・監視は段階Aを踏襲。

## 2. ゴール / 位置づけ
- 建玉に対する **TP と SL を OCO(One-Cancels-the-Other)としてセットで仕掛け**られるようにする。
- **片方が約定したら、もう片方を自動でキャンセル**する。
- 段階の位置づけ: A(単独TP/SL)→ **B(OCO・本書)**→ C(IFD/IFO)。IFO は本OCOを内包するため、
  グループの仕組みは**段階Cで再利用できる形**にする。

## 3. スコープ
### スコープ内
- 建玉への **OCO(TP + SL のセット)** の発注・取消・一覧
- 片方約定時に、対象建玉を決済し、**同OCOグループの他方を自動 CANCELLED**
- 状態の区別: OCOによる取消=**CANCELLED** / 建玉消滅による失効=**EXPIRED**(段階A踏襲)
- PositionsTable に OCO(TP/SL ペア)の表示・操作配線

### スコープ外(後続 / 別)
- IFD / IFO(段階C)。ただしグループIDは段階Cで再利用する前提で設計する
- OCO の**片方だけの取消**(セット取消のみ。将来緩める場合は別途)
- 部分決済(全数量決済のみ)、許容スリッページ幅指定

## 4. 設計概要
### 4.1 OCOグループ(グループID方式)
- EXIT注文(段階AのTP/SL)に **`ocoGroupId`(任意)** を1つ持たせる。
  - 同じ `ocoGroupId` を持つ TP と SL が OCO として連動する。
  - 単独TP/SL(段階A)は `ocoGroupId = null`。OCOのときだけ採番してセット。
  - OCOグループは **最大2本(TP×1 + SL×1)**。親エンティティは作らない。
- グループの状態は「同グループの注文を見る」ことで判断(別途の親状態は持たない)。

> 段階C(IFO)では、新規約定後に生成した建玉へ TP+SL を bind する際、この `ocoGroupId` を採番・有効化して再利用する。

### 4.2 連動(One-Cancels-the-Other)
- 監視で OCO の一方が条件成立 → 対象建玉を決済(段階Aの決済価格方式)→ 建玉 CLOSED。
- 同時に、**同 `ocoGroupId` の他方を CANCELLED** にする(OCOによる意図的キャンセル)。
- これらは**口座ロック内で一括**実行し、整合と冪等を担保(片方約定と他方キャンセルが分断されない)。

### 4.3 状態の区別(重要)
- **CANCELLED**: OCO で片方約定により他方が取り消された(=狙い通りに消えた)。ユーザーのセット取消も CANCELLED。
- **EXPIRED**: 対象建玉が他経路(個別決済・ロスカット全決済)で消えて失効(段階A)。
- 履歴・画面で「なぜ執行されなかったか」を区別できるようにする(学習用途で意味が異なるため)。

### 4.4 整合ルール
- OCO発注は **TP と SL を同時に1リクエストで受ける**(両方そろって初めてグループ成立)。
- 片方だけの状態にしない(セット取消のみ)。建玉に既にTP/SLがある場合の扱いは 5.5 参照。

## 5. バックエンド仕様
### 5.1 既存調査(着手前に view して報告)
- 段階Aの EXIT注文(TriggerOrder + purpose=EXIT・targetPositionId)、決済監視、建玉消滅時の失効フック、
  個別決済・ロスカット全決済、口座ロックの所在。OCO連動を載せる接続点を報告。

### 5.2 enum / フィールド(additive)
- EXIT注文に **`ocoGroupId`(nullable)** を追加。
- 状態 `TriggerStatus` は既存(PENDING/TRIGGERED/CANCELLED/REJECTED/EXPIRED)を流用。
  - OCO連動の取消は **CANCELLED** を使う(新stateは増やさない)。

### 5.3 サービス
- OCO発注: 1建玉に対し TP+SL を検証(段階Aの向き検証を各々に適用)し、同一 `ocoGroupId` で2本登録。
  - TP・SL各1本制約(段階A)と整合。既存の単独TP/SLがある建玉への扱いは 5.5。
- OCO取消: グループ単位で両方を CANCELLED。
- 監視拡張: 一方 TRIGGERED → 対象建玉決済 → 同グループ他方を CANCELLED(ロック内一括・冪等)。
- 建玉消滅フック(段階A)はそのまま: 建玉が他経路で消えたら、グループの未発動注文を **EXPIRED**。
  - ※ この場合は EXPIRED(建玉消滅)であり CANCELLED(OCO約定)ではない点に注意。

### 5.4 API
- `POST /api/trade/positions/{id}/oco-orders`: OCO(TP+SL)をまとめて発注
  (body: tp{triggerPrice}, sl{triggerPrice})。向き不正・OPEN以外・重複は 4xx。
- `DELETE /api/trade/positions/{id}/oco-orders/{groupId}`(または cancel): グループ取消(両方 CANCELLED)。
- `GET /api/trade/positions`(拡張): 各建玉に OCO(グループID・TP価格・SL価格・各状態)を含める。
- DTO返却。

### 5.5 エッジケース
| ケース | 期待挙動 |
|---|---|
| OCO の TP/SL いずれかが向き不正 | グループごと却下(片方だけ登録しない) |
| 既に単独TP or SL がある建玉にOCO発注 | 重複(各1本)に抵触 → 却下(または既存を置き換えるかは要確認。既定は却下) |
| 片方約定 | 対象建玉を決済し、他方を CANCELLED(ロック内一括) |
| 建玉が他経路で消滅 | グループの未発動注文を EXPIRED(CANCELLEDではない) |
| グループ取消 | TP/SL 両方 CANCELLED(片方だけは不可) |
| 同時発動・同時操作 | 口座ロックで直列化・冪等(二重決済/二重キャンセルしない) |

## 6. フロントエンド仕様
- **PositionsTable** の建玉行に、OCO(TP価格・SL価格のペア)を表示。
  - OCO発注(TPとSLの価格を同時入力)、グループ取消(セット)。
  - 片方約定でもう片方が **CANCELLED** になり、建玉が決済されるのが見える。
  - 状態の色分け: 待機 / 発動 / **CANCELLED(OCO)** / **EXPIRED(建玉消滅)** を区別表示(DESIGN.md)。
- 段階Aの単独TP/SL UI と共存(単独 or OCO を選べる)。loading/empty/error。

## 7. この機能固有の制約
- OCOは **グループID方式**(EXIT注文に ocoGroupId を1つ持たせる。親エンティティは作らない)。
- 片方約定→他方は **CANCELLED**、建玉消滅→**EXPIRED** で**区別**する。
- OCOは **セット取消のみ**(片方だけの取消は不可)。
- TP・SL各1本(段階A)の制約と整合。全数量決済・スリッページ幅指定なしは段階A踏襲。
- `ocoGroupId` の仕組みは **段階C(IFO)で再利用**できる形にする。

## 8. 実装ステップ
1. 既存調査: 段階AのEXIT注文・監視・失効フック・ロックの接続点を報告。
2. EXIT注文に `ocoGroupId`(nullable)を追加。
3. OCO発注/取消サービス(TP+SLを同一グループで登録、セット取消)。
4. 監視拡張: 一方TRIGGERED→対象建玉決済→同グループ他方をCANCELLED(ロック内一括・冪等)。
5. 建玉消滅フックの確認: 建玉消滅時はEXPIRED(CANCELLEDと区別)。
6. API(oco発注/取消/positions拡張)を追加。
7. テスト追加(9章)。
8. フロント: PositionsTable に OCO 表示・発注・取消、状態色分け。
9. 動作確認(10章)。

## 9. テスト（受け入れ条件）
- **OCO発注**: LONG建玉に TP=上・SL=下 をOCOで発注 → 同一グループで2本がPENDING。
- **TP約定→SLキャンセル**: Bid が TP以上 → TP価格で決済・建玉CLOSED、SLが **CANCELLED**。
- **SL約定→TPキャンセル**: Bid が SL以下 → 現在Bidで決済、TPが **CANCELLED**。
- **建玉消滅→EXPIRED**: OCO仕掛け中の建玉を個別決済 or ロスカット → TP/SL とも **EXPIRED**(CANCELLEDでない)。
- **向き不正**: OCOのTP/SLいずれか不正 → グループごと却下。
- **セット取消**: グループ取消で TP/SL 両方 CANCELLED。片方だけの取消は不可。
- **冪等/直列化**: 同時発動で二重決済・二重キャンセルしない。

## 10. 動作確認手順
### バックエンド
```
# 建玉を作る
POST /api/trade/orders/market { "currencyPair":"USD/JPY","side":"BUY","quantity":10000 }

# その建玉にOCO(TP+SL)をまとめて発注
POST /api/trade/positions/{positionId}/oco-orders { "tp":{"triggerPrice":156.000}, "sl":{"triggerPrice":154.000} }
GET  /api/trade/positions      # 建玉行にOCO(TP/SL・同一グループ)が表示

# ニュースで上昇→TP約定・SLはCANCELLED
POST /api/market/news/events { "currencyPair":"USD/JPY","direction":"UP","magnitudeBps":150,"durationSeconds":60 }
GET  /api/trade/positions      # 建玉CLOSED、SL=CANCELLED
GET  /api/trade/trades         # 決済約定

# 別の建玉でOCO仕掛け中にロスカット→TP/SLはEXPIRED(区別)
```
### フロントエンド
- 建玉行で OCO(TPとSL)を同時に仕掛けられる。
- 価格到達で片方約定→建玉決済→もう片方が CANCELLED になるのが見える。
- 建玉がロスカット等で消えた場合は EXPIRED として区別表示される。
- グループ取消で両方まとめて取り消せる。既存画面が壊れていない。
```
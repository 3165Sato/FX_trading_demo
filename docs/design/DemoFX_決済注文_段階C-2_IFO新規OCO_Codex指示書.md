# DemoFX 機能指示書: 決済注文 段階C-2（IFO・新規＋OCO の合成注文）

## 1. 準拠
- 実装ルールは **CODEX.md** に従う(BigDecimal/HALF_UP、DTO返却、独立スケジュール、口座ロックで直列化、
  既存を壊さない・小ステップ、NEXT_PUBLIC_API_BASE_URL、logs非Git、着手前に既存を調査・報告)。
- UIは **DESIGN.md** に従う(注文パネル、待機注文/予約一覧、PositionsTable、状態の色分け)。
- 設計の正: **「決済注文 TP/SL → OCO → IFD/IFO 設計全体像」**(本書は段階C のうち **IFO**)。
- 前提機能: 段階B(OCO・グループID方式・片方約定で他方CANCELLED)、段階C-1(IFD・親子バインド・
  約定時に向き最終判定・不正なら子のみEXPIRED・親約定前取消で子もCANCELLED)。

## 2. ゴール / 位置づけ
- **IFO(If Done OCO)** = 新規注文(指値/逆指値)＋ OCO(TPとSLの両方)の合成注文。
- 「この価格で新規エントリーできたら、約定したら利確と損切りのOCOを自動でセットする」を1セットで予約。
- 段階の位置づけ: A → B(OCO)→ C-1(IFD)→ **C-2 IFO(本書)**。**これで決済注文系が完成**。
- 新規概念はほぼ無く、**C-1の親子バインド** と **BのOCO** の組み合わせ。

## 3. スコープ
### スコープ内
- IFO の発注・取消・一覧(親=新規注文 ＋ 子=OCO(TP+SL))
- 親が約定 → 建玉生成 → 子のOCO(2本)を建玉にバインドして有効化
- 約定時の向き最終判定: **OCOの片方でも不正ならOCOごと失効(両方EXPIRED)**(新規約定は成立)
- バインド後のOCO挙動は **段階Bを再利用**(片方約定で他方CANCELLED 等)

### スコープ外(後続 / 別)
- 部分決済(全数量決済のみ)、許容スリッページ幅指定
- 決済注文系はこれで完成。以降は別ロードマップ(NETTING、MAX/REVALUE、スワップ等)

## 4. 設計概要
### 4.1 親子モデル(C-1の拡張)
- **親** = 新規注文(指値・逆指値, purpose=ENTRY)。
- **子** = OCO(TP＋SLの2本, purpose=EXIT, 同一 `ocoGroupId`)。C-1の子が1本→2本になった形。
- 親子の関連は C-1 同様 `parentOrderId`。子2本は段階Bの `ocoGroupId` で束ねる。
- 子2本は親約定までは **未バインド**(対象建玉未確定)。

### 4.2 状態遷移
```
親(新規): PENDING --約定--> 建玉生成 ──┐
                                        ├─→ OCO(TP+SL)の向きを約定時価格で判定
子(OCO 2本): 未バインド ────────────────┘     ├ 両方正当 → 2本を建玉にバインドして有効化(同一ocoGroupId, PENDING)
                                              └ 片方でも不正 → OCOごと失効(両方EXPIRED)、新規約定は成立
親(新規): PENDING --取消--> 子(OCO 2本)も CANCELLED
```
- バインド後は段階BのOCOと同一: 片方約定→対象建玉決済→他方CANCELLED。建玉消滅→両方EXPIRED。

### 4.3 約定時の向き判定(確定)
- 親約定時の建玉時価格で、OCOのTPとSLの**両方**の向きを判定(段階Bの向き条件)。
  - **両方正当** → OCO2本を建玉にバインドして有効化。
  - **片方でも不正** → **OCOごと失効(TP・SLとも EXPIRED)**。新規約定は成立(建玉は保持、決済注文は付かない)。
- C-1の「不正なら子のみEXPIRED・新規は成立」と整合(IFOでは"子=OCO単位"で失効)。

### 4.4 約定時の決済価格(段階A/B踏襲)
- バインド後の発動: 利確(TP)=指定価格 / 損切り(SL)=トリガー後の現在値(スリッページ幅指定なし)。

## 5. バックエンド仕様
### 5.1 既存調査(着手前に view して報告)
- C-1のIFD(親子・バインド・約定時判定・親取消で子CANCELLED)、段階BのOCO(ocoGroupId・相互キャンセル)、
  親約定フックの差し込み点、口座ロックの所在。IFOで再利用する箇所を報告。

### 5.2 enum / フィールド
- 追加は最小(C-1/Bで導入済みを再利用): 子に `parentOrderId`(C-1)＋ `ocoGroupId`(B)。
- 新stateは増やさない(PENDING/TRIGGERED/CANCELLED/REJECTED/EXPIRED を流用)。

### 5.3 サービス
- IFO発注: 親(新規)＋子OCO(TP+SL, 同一ocoGroupId)を**1リクエスト**で受け、関連づけて登録(子は未バインド)。
  - 親は新規指値・逆指値の登録経路を再利用。子OCOは段階Bの構成を未バインドで保持。
- 親約定フック(C-1を拡張):
  1. 親約定 → 建玉生成(既存)。
  2. OCOのTP/SL **両方の向きを約定時価格で判定**。
  3. 両方正当 → OCO2本に建玉をバインド・有効化(PENDING)。
  4. 片方でも不正 → **OCO2本ともEXPIRED**(新規約定は成立)。
- 親取消(PENDING中) → 子OCO 2本とも CANCELLED。
- バインド後のOCO連動・決済・失効は**段階Bを再利用**(新規ロジックを作らない)。
- 口座ロックで直列化・冪等。

### 5.4 API
- `POST /api/trade/orders/ifo`: IFO発注
  ```
  {
    "entry": { "currencyPair":"USD/JPY","side":"BUY","orderType":"LIMIT","quantity":10000,"triggerPrice":154.500 },
    "oco":   { "tp":{"triggerPrice":156.000}, "sl":{"triggerPrice":153.000} }
  }
  ```
  - 親の向き検証(既存)に反したら却下。子OCOは発注時は軽い妥当性のみ(向きの最終判定は約定時)。
- 取消: 親(PENDING)取消で子OCOも CANCELLED(C-1の取消を流用 or IFO用)。
- `GET /api/trade/orders/pending`(拡張): IFO の親子＋OCOグループを表示。
- DTO返却。

### 5.5 エッジケース
| ケース | 期待挙動 |
|---|---|
| 親約定・OCO両方正当 | TP/SL を建玉にバインドして有効化(同一ocoGroupId) |
| 親約定・OCO片方が不正 | **OCOごと失効(両方EXPIRED)**、新規約定は成立 |
| 親が約定前に取消 | OCO 2本とも CANCELLED |
| 親約定時に余力不足 | 既存の新規約定の余力チェックに従う(約定しなければOCOもCANCELLED) |
| バインド後に片方約定 | 対象建玉決済→他方CANCELLED(段階B) |
| バインド後に建玉が他経路で消滅 | OCO 2本とも EXPIRED(段階B) |
| 同時操作・二重発動 | 口座ロックで直列化・冪等 |

## 6. フロントエンド仕様
- **注文パネル**に IFO 入力を追加: 新規条件(指値/逆指値＋数量＋トリガー価格)＋ OCO(TP価格・SL価格)。
  - 子の向きは「約定時に判定、片方でも不正ならOCOごと失効」する旨をヒント表示。
- **待機注文パネル**: IFO の親子＋OCOグループを表示(親=新規待機、子=未バインドのOCO)。
- 親約定でOCOが建玉にバインドされ、PositionsTable に TP/SL ペアが出る。以降は段階BのOCO挙動。
- OCOごと失効した場合は両方 EXPIRED 表示(建玉は残る)。
- 状態の色分け(待機/未バインド/発動/CANCELLED/EXPIRED)は DESIGN.md。loading/empty/error。

## 7. この機能固有の制約
- IFO の子は **OCO(TP＋SL 2本, 同一ocoGroupId)**。1本は IFD(C-1)。
- 約定時にOCOの **片方でも不正ならOCOごと失効(両方EXPIRED)**、新規約定は成立(ロールバックしない)。
- 親が約定前に取消 → OCO 2本とも CANCELLED。
- バインド後は **段階B(OCO)を再利用**(相互キャンセル・決済価格方式・建玉消滅失効)。
- 新規概念・新stateを増やさない(C-1とBの組み合わせ)。

## 8. 実装ステップ
1. 既存調査: C-1のバインド処理・BのOCO・親約定フック・ロックの再利用点を報告。
2. IFO発注サービス(親＋子OCO2本を関連づけて登録、未バインド)。
3. 親約定フックを拡張: OCO両方の向きを約定時価格で判定 → 両方正当=バインド有効化 / 片方でも不正=OCOごとEXPIRED。
4. 親取消で子OCO 2本ともCANCELLED。
5. バインド後はBのOCO挙動を再利用(連動・決済・失効)。
6. API(ifo発注 / 取消 / pending拡張)を追加。
7. テスト追加(9章)。
8. フロント: 注文パネルのIFO入力、待機注文の親子＋OCO表示。
9. 動作確認(10章)。

## 9. テスト（受け入れ条件）
- **IFO発注**: 新規指値＋OCO(TP+SL)を1リクエストで発注 → 親PENDING、子OCO2本は未バインド(同一グループ)。
- **親約定→OCOバインド**: 親約定で建玉生成 → TP/SL2本が建玉にバインドされ PENDING、PositionsTableにOCO表示。
- **片方不正でOCO失効**: 約定時にTPかSLの向きが不正 → 新規約定は成立(建玉あり)、**OCO2本ともEXPIRED**。
- **親約定前の取消**: 親をPENDINGで取消 → OCO2本とも CANCELLED(建玉なし)。
- **バインド後のOCO連動**: バインドされたOCOの片方約定 → 対象建玉決済、他方CANCELLED(段階B)。
- **冪等/直列化**: 同時約定・操作で二重バインド・二重決済しない。

## 10. 動作確認手順
### バックエンド
```
# IFO発注
POST /api/trade/orders/ifo {
  "entry": { "currencyPair":"USD/JPY","side":"BUY","orderType":"LIMIT","quantity":10000,"triggerPrice":154.500 },
  "oco":   { "tp":{"triggerPrice":156.000}, "sl":{"triggerPrice":153.000} }
}
GET  /api/trade/orders/pending      # 親(新規待機)と子OCO(未バインド・同一グループ)

# 下落させ親を約定 → OCOが建玉にバインド
POST /api/market/news/events { "currencyPair":"USD/JPY","direction":"DOWN","magnitudeBps":150,"durationSeconds":60 }
GET  /api/trade/positions           # 建玉生成、TP/SL(OCO)がバインドされ表示

# 上昇させTP約定 → 建玉決済・SLはCANCELLED(段階B)
POST /api/market/news/events { "currencyPair":"USD/JPY","direction":"UP","magnitudeBps":250,"durationSeconds":60 }
GET  /api/trade/positions           # 建玉CLOSED
GET  /api/trade/trades              # 決済約定
```
### フロントエンド
- 注文パネルで「新規(指値/逆指値)＋OCO(TP+SL)」をまとめて発注できる。
- 親約定でOCOが建玉にバインドされ、PositionsTable に TP/SL ペアが出る。
- 約定時にOCOの片方でも不正なら、建玉は残りOCOは両方EXPIRED表示。
- 親を約定前に取り消すとOCOも消える。既存画面が壊れていない。
```

---
これで決済注文系(A 単独TP/SL → B OCO → C-1 IFD → C-2 IFO)が完成。
当初の注文体系の2軸(新規/決済 × 指値/逆指値)とその合成注文(IFD/IFO)が一通り揃う。
# DemoFX 機能追加指示書: シミュレーター現実化 + 通貨ペア追加

> このファイルは実装担当(Codex)にそのまま渡すための指示書です。
> DemoFX の既存プロジェクト概要ドキュメントが別途与えられている前提で、
> その内容を尊重しつつ、以下の 2 点を追加します。
> - パートA: レートシミュレーターを「平均回帰つきランダムウォーク」にして自然な動きにする
> - パートB: 通貨ペアを追加する

---

## 0. 大前提(必ず守ること)

- DemoFX は **学習用の架空FXデモアプリ**。実在サービスの仕様は再現しない。
- 既存の Entity / Repository / Service / Controller / フロント画面を**壊さない**。
- 価格・金額・Spread は **`BigDecimal`** で保持する。`double`/`float` で価格を保持しない。
  - 乱数生成や途中計算で `double` を使うのは可。ただし**保存する価格は必ず `BigDecimal` で priceScale に丸める(HALF_UP)**。
- 既存の挙動はデフォルト設定で再現できるようにし、**新挙動は設定で調整可能**にする。
- API は Entity を直接返さず DTO で返す(今回 API 追加は無いが原則維持)。
- フロントは API URL を localhost 固定にせず **`NEXT_PUBLIC_API_BASE_URL`** を使う。
- `logs/` 配下や `*.log` は Git 管理しない。
- **作業は「実装ステップ」の順に小さく分けて進める**。一度に全部やらない。
- 各ステップ完了ごとに、変更ファイルと差分概要を簡潔に報告する。

---

## パートA: シミュレーターの現実化(平均回帰つきランダムウォーク)

### A-1. ゴール

`MarketRateSimulator` の **mid 更新ロジックだけ**を、平均回帰つきランダムウォークに置き換える。
基準価格から際限なく離れず、価格に比例した自然な揺れになるようにする。

**今回は Spread は従来どおり固定**(Spread 変動・スパイクは今回やらない)。
そのため、bid/ask の導出・丸め・Tick 保存(5秒)・enabled 判定・quotedAt 更新は**現状維持**。

### A-2. スコープ外(今回はやらない)

- Spread の変動 / 拡大
- 突発的なスパイク・疑似ニュースイベント
- 時間帯(セッション)による変動の変化
- 新しい API・新しい画面

### A-3. 更新モデル(1 tick = 1 秒、通貨ペアごと)

各 tick で mid を次のように更新する。

```
prevMid    : 直前の mid
basePrice  : そのペアの基準価格(平均回帰の中心)
deviation  = prevMid - basePrice
reversion  = -reversionStrength * deviation              // 基準へ引き戻す
z          = 標準正規乱数 (Random.nextGaussian())
shock      = prevMid * (volatilityBps / 10000.0) * z     // 価格に比例したランダム変動
newMidRaw  = prevMid + reversion + shock
(任意) maxDeviationBps が設定されていれば
           basePrice ± (basePrice * maxDeviationBps / 10000) の範囲に clamp する
newMid     = newMidRaw を priceScale で HALF_UP 丸め(BigDecimal)
```

- `reversion + shock` の計算は `double` で行ってよいが、**最終的な `newMid` は `BigDecimal` で priceScale 丸め**。
- 既存どおり:
  - `bid = newMid - spread / 2`(priceScale 丸め HALF_UP)
  - `ask = newMid + spread / 2`(priceScale 丸め HALF_UP)
  - `spread` は変更しない(固定)
  - `quotedAt` を更新
- 乱数は `java.util.Random`(または `ThreadLocalRandom`)の `nextGaussian()` を使用。

### A-4. パラメータ設定(`application.yml`)

コードに数値を埋め込まず、設定から読む。`@ConfigurationProperties` でバインドするのが望ましい。

```yaml
demofx:
  simulator:
    default:
      volatilityBps: 1.5        # 1tick(1秒)あたりのリターン標準偏差(ベーシスポイント)
      reversionStrength: 0.01   # 0..1。基準価格へ引き戻す強さ(1tickあたり)
      maxDeviationBps: 300      # 任意。基準価格から±3%を超えないよう clamp(0/未設定なら clamp 無効)
    pairs:
      - symbol: "USD/JPY"
        basePrice: 155.1215
      - symbol: "EUR/JPY"
        basePrice: 168.2525
      - symbol: "EUR/USD"
        basePrice: 1.085015
      # 追加ペア(パートB)もここに追記する
```

挙動ルール:

- ペアごとの `volatilityBps` / `reversionStrength` / `maxDeviationBps` は任意。
  未指定なら `default` の値を使う。
- `basePrice` 未指定のペアは、**シミュレーター起動時点の mid を基準価格として採用**し、
  メモリ上の `Map<symbol, basePrice>` に保持する(以後その値を基準に回帰させる)。
- 既存挙動の再現: `reversionStrength: 0` にすれば単純ランダムウォーク、
  `volatilityBps: 0` にすれば(回帰のみで)ほぼ動かない、という調整が効くこと。

> パラメータ値は学習用の目安。動きが激しすぎ/地味すぎる場合は
> `volatilityBps` と `reversionStrength` で調整する(コード変更不要)。

### A-5. パートA 実装ステップ

1. `application.yml` に `demofx.simulator.*` を追加し、バインド用の設定クラスを作成。
2. `MarketRateSimulator` の mid 更新部分のみ A-3 のモデルに差し替え。
   - bid/ask 導出・Spread 固定・丸め・Tick 保存・enabled 判定・quotedAt は触らない。
3. 基準価格マップの初期化(config 優先、無ければ起動時 mid)。
4. ユニットテスト追加(A-6)。
5. 起動して動作確認(A-7)。

### A-6. パートA テスト(受け入れ条件)

- **回帰の検証**: `volatilityBps=0`, `reversionStrength>0`, `prevMid` を base より上に設定 →
  更新後の mid が base に近づく(deviation の絶対値が減る)こと。
- **不動の検証**: `volatilityBps=0`, `reversionStrength=0` → mid が変化しないこと。
- **丸めの検証**: 更新後 mid が priceScale 桁に HALF_UP 丸めされていること。
- **bid/ask 維持**: `bid = mid - spread/2`, `ask = mid + spread/2`, spread 不変であること。
- **clamp の検証**(有効時): mid が `basePrice ± maxDeviationBps` を超えないこと。
- 乱数そのものはテストしない。`nextGaussian` を注入可能にするか、`volatilityBps=0` で決定的に検証する。

### A-7. パートA 動作確認

- `./gradlew bootRun` で起動し、しばらく放置。
- `GET /api/market/rates` を数回叩き、mid が滑らかに上下しつつ基準価格付近に留まることを確認。
- 監視画面のチャートが自然に揺れること(暴走して画面外へ行かないこと)。

---

## パートB: 通貨ペアの追加

### B-1. ゴール

`MarketDataInitializer` のシードに通貨ペアを追加する。
既存の「重複登録しない」挙動を維持したまま、追加ペアの `CurrencyPair` と初期 `MarketRate` を作成する。

### B-2. 追加するペア(デフォルト案。自由に増減可)

| symbol | base | quote | priceScale | pipScale | 初期 mid(目安) | spread(目安) |
|---|---|---|---|---|---|---|
| GBP/USD | GBP | USD | 5 | 4 | 1.27000 | 0.00004 |
| GBP/JPY | GBP | JPY | 3 | 2 | 197.000 | 0.008 |
| AUD/USD | AUD | USD | 5 | 4 | 0.65000 | 0.00004 |
| AUD/JPY | AUD | JPY | 3 | 2 | 100.830 | 0.007 |
| USD/CHF | USD | CHF | 5 | 4 | 0.88000 | 0.00004 |
| USD/CAD | USD | CAD | 5 | 4 | 1.37000 | 0.00005 |

ルール:
- **JPY が quote のペア**: `priceScale=3`, `pipScale=2`(既存 USD/JPY・EUR/JPY と統一)。
- **それ以外のメジャーペア**: `priceScale=5`, `pipScale=4`(既存 EUR/USD と統一)。
- `quantityScale` は**既存ペアと同じ値**を使う(新たに発明しない。既存 USD/JPY 等の値に合わせる)。
- `enabled = true`(シミュレーター対象にするため)。
- bid/ask は明示値を持たず、**既存と同じ式で導出**して丸める:
  - `bid = mid - spread/2`, `ask = mid + spread/2`, priceScale で HALF_UP。
- 初期 mid はクロスレートがおおむね整合する目安値(例: GBP/JPY ≒ USD/JPY × GBP/USD)。
  **学習用なので正確性は不要。自由に調整してよい。**

> さらに増やしたい場合の候補: EUR/GBP(base EUR / quote GBP, scale 5/4),
> NZD/USD(scale 5/4), CHF/JPY(scale 3/2)。同じルールで追加できる。

### B-3. シミュレーター設定への追記(パートA と連動)

追加した各ペアを `application.yml` の `demofx.simulator.pairs` にも追記し、
`basePrice` を初期 mid と同じ値にする(未設定でも起動時 mid を基準に動くが、
再起動後の基準を安定させたいので明示推奨)。

```yaml
      - symbol: "GBP/USD"
        basePrice: 1.27000
      - symbol: "GBP/JPY"
        basePrice: 197.000
      # ... 追加ペア分
```

ペアごとにボラを変えたい場合は `volatilityBps` を個別指定(任意)。

### B-4. フロントエンドの確認(重要)

既存フロントの表示対象ペアが **`/api/market/rates` 由来で動的か、ハードコードか**を確認する。

- **動的(API のレスポンスからペア一覧を生成)している場合**: 追加対応は不要(自動で増える)。
- **ハードコード(USD/JPY, EUR/JPY, EUR/USD 固定)の場合**:
  - 可能なら **API 由来でペア一覧を生成する方式に変更**するのが望ましい(今後ペアを足すたびに修正不要になる)。
  - 大改修を避けたい場合は、**最小限としてハードコード配列に追加ペアを足す**だけでもよい。
  - どちらにするかは実装前に判断し、報告すること。

### B-5. パートB 実装ステップ

1. `MarketDataInitializer` に追加ペアの `CurrencyPair` シードを追記(重複登録しない既存ロジックを維持)。
2. 追加ペアの初期 `MarketRate` シードを追記(bid/ask は式で導出)。
3. `application.yml` の simulator 設定に追加ペアを追記。
4. フロントのペア一覧が動的かハードコードか確認し、B-4 に従って対応。
5. 起動して動作確認(B-6)。

### B-6. パートB 動作確認

- 起動後 `GET /api/market/rates` に追加ペアが含まれること。
- **再起動しても重複登録されない**こと(初期化の冪等性)。
- 各追加ペアの mid がシミュレーターで動くこと(enabled=true の確認)。
- フロントの監視画面に追加ペアが表示されること(B-4 の対応後)。
- 既存3ペアの表示・チャートが壊れていないこと。

---

## 守ってほしい制約(チェック用)

- [ ] `MarketRateSimulator` は mid 更新ロジックのみ変更、Spread は固定のまま
- [ ] 価格は `BigDecimal` で priceScale 丸め(HALF_UP)、価格を `double` で保持していない
- [ ] パラメータは `application.yml` 化、コードに数値を直書きしていない
- [ ] 既存挙動が設定値で再現できる(reversionStrength=0 で単純ランダムウォーク等)
- [ ] 初期化は冪等(再起動で重複登録しない)
- [ ] 追加ペアの scale ルールを守っている(JPY=3/2, その他=5/4)
- [ ] `quantityScale` は既存ペアの値に合わせている
- [ ] フロントのペア一覧の対応方針を確認・報告している
- [ ] 既存 Entity / Repository / Service / Controller / 画面を壊していない
- [ ] フロントは `NEXT_PUBLIC_API_BASE_URL` を使用、localhost 固定なし
- [ ] `logs/` / `*.log` をコミットしていない

---

## 推奨実装順

1. **パートA**(既存3ペアで完結・検証できる)を先に実装・確認。
2. その後 **パートB**(ペア追加 + simulator 設定追記 + フロント確認)。

各パート内も上記ステップ順に小さく進め、完了ごとに差分を報告すること。

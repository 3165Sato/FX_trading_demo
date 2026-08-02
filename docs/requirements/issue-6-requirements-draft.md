# 要件ドラフト

## Issueの要約

GitHub Issue #6「クイック全決済」は、相場急変時の手仕舞いを目的として、利用者がペア単位または口座全体の OPEN 建玉を1操作で全決済できるようにする要望である。Issue にコメントはなく、最低限の完了条件は「特になし」とされている。

## 背景

現行システムには、選択した建玉を1件ずつ手動決済する機能と、証拠金維持率が閾値以下の場合に口座内の全 OPEN 建玉を決済する自動ロスカットがある。急変時に利用者が複数建玉を手仕舞うには個別操作を繰り返す必要があり、操作中にもレートが変動し得る。

自動ロスカットは口座ロック内で個別決済処理を順に再利用し、決済できない建玉があっても残りの決済を継続する。この既存経路はクイック全決済の業務処理に近いが、発動条件、注文由来、失敗時の扱い、および利用者操作の有無が異なる。

## 目的

- 利用者が、指定した通貨ペアの全 OPEN 建玉を1回の操作で決済できるようにする。
- 利用者が、デモ口座全体の全 OPEN 建玉を1回の操作で決済できるようにする。
- 個別決済と同じ価格、損益、スワップ、注文・約定、建玉状態、および紐づく決済注文の整合性を維持する。
- 急変時に、建玉ごとの決済操作を繰り返す負担を減らす。

## 現状仕様

- デモ口座は `DEMO-0001` の単一既定口座を前提としている。
- `GET /api/trade/positions` は既定口座の建玉を返し、任意の `currencyPair` クエリで通貨ペアを絞り込める。
- Trading 画面では建玉一覧から1件を選択し、建玉詳細パネルの `Close position` 操作で `POST /api/trade/positions/{id}/close` を呼び出す。
- 個別決済は口座ロックを取得し、対象が既定口座の OPEN 建玉であること、通貨ペアが有効であること、最新レートが存在することを確認する。
- 個別決済の決済側と価格は LONG が SELL/Bid、SHORT が BUY/Ask である。価格と数量は通貨ペアの scale に従い `HALF_UP` で丸める。
- 個別決済では建玉全数量を決済し、成行の注文・決済約定を作成し、建玉を CLOSED にする。実現損益と未実現スワップを口座へ反映し、スワップ実現履歴を記録する。
- 建玉の決済時には、その建玉に紐づく PENDING または WAITING の決済注文を EXPIRED にする。
- 手動の個別決済は注文由来 `MANUAL`、自動ロスカットは `LOSS_CUT`、トリガー注文による決済は `TRIGGER` として記録される。
- 自動ロスカットは証拠金維持率を口座ロック取得後に再確認し、閾値以下の場合に全 OPEN 建玉を順次個別決済する。
- 自動ロスカット中に一部建玉の決済が `ResponseStatusException` で失敗した場合、その建玉をスキップして他の建玉の決済を継続し、成功した約定だけを結果として返す。
- フロントエンドの個別決済成功後は、画面内の建玉・注文・約定を更新し、建玉、損益サマリー、口座サマリーなどを再取得する。失敗時は注文エラー表示へメッセージを出す。
- ペア単位または口座全体を利用者操作で決済する専用 API、集約レスポンス、および UI 操作は存在しない。

## 変更後仕様

- 利用者は決済範囲として「通貨ペア」または「口座全体」を指定し、対象範囲の全 OPEN 建玉に対してクイック全決済を実行できる。
- ペア単位では、実行時点で既定口座に属し、指定通貨ペアと一致する OPEN 建玉だけを対象とする。
- ペア単位の通貨ペアはクイック全決済の操作内で別途選択できるものとし、Trading 画面で選択中の通貨ペアには固定しない。
- 口座全体では、実行時点で既定口座に属する全通貨ペアの OPEN 建玉を対象とする。
- 各建玉は全数量を、実行時に取得した当該通貨ペアの最新の決済側価格で決済する。
- 各建玉の決済は、現行個別決済と同じ損益計算、スワップ実現、注文・約定作成、建玉 CLOSED 化、および未発動決済注文の EXPIRED 化を満たす。
- API 呼び出し方式は、可能であれば対象範囲を1回で受け付ける単一 API を優先する。性能上問題がなければ、フロントエンドから既存個別決済 API を対象建玉ごとに複数回呼ぶ方式も許容する。
- 単一 API 方式では、サーバーは既定口座のロックを取得した後に対象となる OPEN 建玉を抽出し、その対象集合を今回の処理対象とする。複数回呼び出し方式では、各個別決済 API の既存の口座ロックと OPEN 状態検証を維持する。
- 処理中に他の注文、トリガー約定、ロスカット、または別の決済操作と競合して二重決済を起こさないよう、既存の口座単位の直列化を必須とする。
- クイック全決済による各注文は、通常の個別手動決済と履歴上で区別する。注文由来の値は既存値と衝突しない `QUICK_CLOSE`、画面の表示名は「クイック全決済」とし、バックエンドの enum・DB制約・フロントエンド型・履歴表示で一貫させる。
- 実行結果から、少なくとも対象範囲、対象件数、成功件数、失敗件数、および各建玉の成功・失敗を利用者が判別できる。
- 一部建玉の決済に失敗しても、決済可能な建玉の成功結果は確定し、失敗した建玉を残して処理を継続する。
- 対象となる OPEN 建玉が0件の場合は利用者向けエラーとし、既存データは変更しない。
- 実行完了後、成否にかかわらず建玉一覧、約定履歴、注文履歴、損益サマリー、および口座サマリーを再取得する。Equity 履歴は既存の定期記録・5秒間隔の再取得に委ね、クイック全決済専用の即時スナップショット記録は追加しない。

## 利用者の操作

1. 利用者は Trading 画面でクイック全決済の操作を選ぶ。
2. 利用者は「通貨ペア」または「口座全体」の決済範囲を指定する。通貨ペアを選んだ場合は、クイック全決済操作内で対象ペアを選択する。
3. 利用者が実行すると、ペア単位・口座全体のどちらも確認ダイアログを挟まず直ちに処理を開始する。
4. システムは実行中の状態を表示し、同一操作の連打を防止する。
5. 完了後、システムは成功、一部失敗、または失敗を区別して結果を表示し、関連データを再取得する。対象0件の場合は利用者向けエラーを表示する。

確認ダイアログを表示しないため、確認時の対象件数・通貨ペア・数量・概算損益の表示要件は設けない。操作部品は `DESIGN.md` の方針に従い PositionsTable の各行には詰め込まず、Trading 画面内の専用操作領域へ配置する。具体的なレイアウトとボタン文言は実装設計に委ねる。

## 入力

- 必須: 決済範囲
  - ペア単位
  - 口座全体
- ペア単位の場合に必須: 通貨ペア。既存の通貨ペア表現は `USD/JPY` などの文字列である。
- 口座: 現行どおり既定デモ口座をサーバー側で特定し、クライアントから任意の口座 ID は受け取らない。
- 数量、決済価格、建玉 ID 一覧: クライアントから受け取らない。サーバーが口座ロック取得後の OPEN 建玉と実行時点の最新レートから決定する。
- 単一 API 方式を採用する場合の入力は、決済範囲を明示する値と、ペア単位の場合だけ通貨ペアを持つリクエスト DTO とする。任意の口座 ID や建玉 ID 一覧は持たせない。
- 複数回呼び出し方式を採用する場合は、既存個別決済 API の建玉 ID 以外の入力仕様を変更しない。

## 出力

- 全体結果
  - 実行した範囲
  - ペア単位の場合の通貨ペア
  - 対象建玉件数
  - 成功件数
  - 失敗件数
- 建玉ごとの結果
  - 建玉 ID
  - 失敗時は、対象建玉を識別できる情報と失敗理由を必須とする
  - 成功時の決済価格、実現損益、実現スワップ、決済日時、注文・約定などを画面に表示するかは実装判断とする
- 対象がない場合の利用者向けエラー

単一 API 方式では専用の集約レスポンス DTO で全体結果と建玉ごとの結果を返す。複数回呼び出し方式ではフロントエンドが各 `PositionCloseResponse` と各エラーを集約し、同じ画面要件を満たす。一部失敗は成功分を確定した結果として表現し、失敗した対象建玉の識別情報と失敗理由を必ず含める。

## 正常系

- 選択通貨ペアに同方向・反対方向を含む複数の OPEN 建玉がある場合、対象ペアの建玉だけが全数量決済される。
- 複数通貨ペアに OPEN 建玉がある場合、口座全体の操作ですべての対象建玉が全数量決済される。
- LONG 建玉は Bid、SHORT 建玉は Ask を基準とする実行時の最新価格で決済される。
- 各決済について成行注文と CLOSE 約定が作成され、実現損益・実現スワップが口座へ反映される。
- 決済された建玉は CLOSED となり、決済日時と決済約定 ID が記録される。
- 決済された建玉に紐づく PENDING/WAITING の TP、SL、OCO、IFD/IFO 子注文を含む決済注文が既存規則に従って EXPIRED となる。
- 全件成功後、画面から対象建玉が消え、注文・約定履歴、損益、証拠金、Equity、余力などが最新状態になる。
- 一部失敗時は、決済可能な建玉の成功結果を確定し、失敗した建玉は OPEN のまま残す。画面には対象建玉の識別情報と失敗理由を表示する。

## 異常系

- ペア単位の入力で通貨ペアが未指定、不正な形式、未登録、または無効である場合、入力エラーまたは対象外として扱い、無関係な建玉を決済しない。
- 対象建玉の通貨ペアについて最新レートが取得できない場合、その建玉を決済価格なしで決済しない。
- 対象 OPEN 建玉が0件の場合は利用者向けエラーとし、注文、約定、建玉、口座残高、および決済注文を変更しない。
- 対象抽出後に別処理で建玉が CLOSED になった場合、二重に注文・約定・損益を作成しない。
- 同じクイック全決済操作が連打または再送された場合、各決済で口座ロックと OPEN 状態検証を行い、すでに CLOSED の建玉を再決済しない。初期要件では専用の冪等キーを追加しない。
- クイック全決済と個別決済、トリガー決済、またはロスカットが競合した場合、口座ロック下で直列化し、各建玉の決済は最大1回とする。
- 途中のDB更新が失敗した場合に、建玉、注文、約定、口座残高、スワップ実現、および決済注文の状態を不整合にしない。
- 一部建玉だけ決済不能な場合も、他の決済可能な建玉の処理を継続して成功結果を確定する。失敗した建玉はロールバックまたは未更新の状態に保ち、利用者に全件成功と誤認させない。
- 通信失敗などでクライアントが結果を受け取れない場合は、建玉・注文・約定を再取得して実際の状態を表示する。再送された場合も、各決済の OPEN 状態検証により決済済み建玉を再処理しない。

## 対象範囲

- 既定デモ口座の OPEN 建玉に対するペア単位の全決済
- 既定デモ口座の OPEN 建玉に対する口座全体の全決済
- 採用する API 方式に応じた対象建玉抽出、既存の口座ロック、各建玉の全数量決済、および結果集約
- 個別決済と同等の注文・約定・損益・スワップ・建玉・決済注文ライフサイクルの更新
- Trading 画面からの実行操作、実行中表示、結果表示、および関連データの再取得
- 正常系、対象0件エラー、入力不正、競合、一部または全体失敗のテスト

## 対象外

- 部分決済、決済数量の利用者指定
- 指値・逆指値による一括決済
- 複数口座の選択または任意口座 ID を指定する機能
- 新規注文、待機中の新規予約注文そのものの一括取消
- ロスカット閾値、判定間隔、証拠金計算、レートシミュレーターの変更
- 通貨ペア、価格・数量 scale、損益計算、スワップ計算の既存ルール変更
- 実口座・実資金・外部ブローカーへの発注
- History 画面の新設または画面全体の再構成

## 既存機能への影響

- 個別決済: 一括処理が既存の個別決済ロジックを利用する場合も、既存 API と1件単位の結果を維持する必要がある。
- ロスカット: 全 OPEN 建玉を口座ロック内で順次決済する既存経路と責務が重なる。手動実行には証拠金維持率の閾値判定を適用しない一方、競合時の二重決済を防ぐ必要がある。
- トリガー注文: 一括決済中の TP/SL/OCO 発動と競合し得る。建玉が先に閉じられた場合は、既存規則どおり未発動の決済注文を EXPIRED とする。
- 注文・約定履歴: 各建玉の決済分だけ注文・約定が増え、注文由来 `QUICK_CLOSE`、表示名「クイック全決済」で通常の個別手動決済と区別される。既存 enum、DB制約、フロントエンド型、履歴の由来表示への追加が必要になる。
- 口座情報: 各建玉の実現損益と実現スワップが残高、Equity、証拠金、維持率、余力へ反映される。
- フロントエンド: 建玉、注文、約定、損益サマリー、口座サマリーは実行後に再取得する。Equity 履歴は既存の定期再取得を継続する。
- 性能: 口座全体の建玉件数に比例して注文・約定・口座更新・決済注文失効が発生し、その間は同一口座の取引が直列化される。

## 制約事項

- 学習用の架空 FX デモであり、実資金・実取引を扱わない。
- 現行実装は単一の既定デモ口座を前提とする。
- 金額・価格・数量は `BigDecimal` を使用し、丸めは `HALF_UP`、価格と数量は通貨ペアごとの scale に従う。
- 決済側価格は LONG=Bid、SHORT=Ask とする。
- API は Entity を直接返さず DTO を使用する。
- 口座の取引、発注、強制決済、トリガー発動は口座ロックで直列化する。
- 建玉のクローズと紐づく未発動決済注文の失効は、既存 ADR に従って口座ロック内で整合的に行う。
- フロントエンドの API ベース URL は `NEXT_PUBLIC_API_BASE_URL` を使用する。
- UI は `DESIGN.md` の Trading 画面、情報表示は表、操作は選択後の詳細領域へ寄せる方針、および loading/empty/error の状態設計と整合させる。

## テスト観点

- ペア単位で対象ペアの複数 OPEN 建玉だけが閉じ、他ペアの OPEN 建玉は残る。
- 口座全体で複数ペア・LONG/SHORT の全 OPEN 建玉が閉じる。
- 各建玉が全数量で決済され、LONG は Bid、SHORT は Ask、各 scale と `HALF_UP` が適用される。
- 各建玉について注文と CLOSE 約定が1件ずつ作成され、実現損益と実現スワップが正しく反映される。
- 決済された各建玉の PENDING/WAITING の単独 TP/SL、OCO、IFD/IFO 子注文が EXPIRED になる。
- 対象0件では永続データが変更されず、利用者向けエラーになる。
- 不正、未登録、無効な通貨ペアで他の建玉が決済されない。
- 一部通貨ペアの最新レート欠損時に、決済価格なしの注文・約定が作られず、他の決済可能な建玉は決済される。
- 同時のクイック全決済、個別決済、TP/SL 発動、ロスカットでも二重決済されない。
- 同一操作の連打、HTTP 再送、および決済済み建玉との競合時に、各決済の口座ロックと OPEN 状態検証によって注文・約定・損益が重複しない。
- DB処理失敗時に、建玉、注文、約定、口座、スワップ履歴、決済注文の状態が採用したトランザクション境界どおりになる。
- 結果の対象件数、成功件数、失敗件数、および明細が実際の更新件数と一致する。
- UI が確認ダイアログを表示せずに処理を開始し、実行中の再操作を抑止し、成功、一部失敗、全体失敗、対象0件エラーを区別して表示する。
- ペア単位では、Trading 画面の選択中ペアとは独立して、クイック全決済操作内で選択したペアだけが対象になる。
- 注文・約定履歴で `QUICK_CLOSE` が「クイック全決済」と表示され、`MANUAL`、`LOSS_CUT`、`TRIGGER` と衝突しない。
- 一部失敗時に、失敗した対象建玉の識別情報と失敗理由が必ず表示される。成功建玉の決済価格・損益等は、採用した画面仕様に従う。
- 実行後に建玉、注文、約定、損益、口座が再取得され、Equity 履歴は既存の定期記録・定期再取得で更新される。
- 既存の個別決済、ロスカット、TP/SL/OCO/IFD/IFO、スワップ振替が回帰しない。

## 確認できた事実

- Issue #6 のタイトルは「クイック全決済」。本文には背景として「急変時の手仕舞い。ロスカットの手動版として整合性が良い」、実現したいこととして「ペア単位/口座全体の建玉を1操作で全決済」、最低限の完了条件として「特になし」と記載されている。コメントは0件。（GitHub Issue #6 本文、Issue #6 comments）
- 現行 UI の Trading 画面は PositionsTable と選択建玉の詳細パネルを持ち、操作は詳細側へ寄せる方針である。建玉詳細には個別決済が定義されている。（`DESIGN.md`）
- 個別決済 API は `POST /api/trade/positions/{id}/close` であり、ペア単位・口座全体の専用決済 API はない。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java`）
- 建玉一覧 API は任意の `currencyPair` を受け、リポジトリには既定口座の OPEN 建玉を全件または通貨ペア別に取得する問い合わせがある。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/repository/PositionRepository.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`）
- 個別決済は既定口座のロックを取り、対象建玉の所属と OPEN 状態、有効な通貨ペア、最新レートを検証し、全数量を決済する。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`）
- 個別決済は注文・CLOSE 約定を作成し、建玉を CLOSED に更新し、実現損益・実現スワップを反映し、PENDING/WAITING の決済注文を EXPIRED にする。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`）
- 建玉消滅時は未発動の決済注文を EXPIRED とし、建玉クローズと失効を口座ロック内で一括して整合させる判断が採用済みである。（`docs/adr/ADR-0008_建玉消滅時の決済注文自動失効.md`）
- ロスカットは口座ロック内で維持率を再確認し、全 OPEN 建玉を列挙して個別決済処理を `LOSS_CUT` 由来で呼ぶ。1件の決済失敗時も他建玉を継続する。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`）
- ロスカットについて、閾値回復時は決済しない、閾値割れ時は全 OPEN 建玉を処理する、1件失敗しても残りを処理する単体テストがある。（`FX_trading_backend/fxdemo/src/test/java/com/example/fx/demo/backend/trade/TradeExecutionServiceLossCutTest.java`）
- 注文由来の現行 enum は `MANUAL`、`LOSS_CUT`、`TRIGGER` である。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/enums/OrderSource.java`）
- フロントエンドは個別決済 API の関数を持ち、成功後に建玉を画面状態から除外して注文・約定を反映し、建玉、損益サマリー、口座サマリー等を再取得する。（`FX_trading_front/fx-demo-front/lib/marketRateTicks.ts`、`FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx`）
- デモ口座、基軸通貨、対象通貨ペア、レバレッジ、ロスカット閾値などの全体前提が文書化されている。（`README.md`）

## 推測

- 「ロスカットの手動版」という表現から、証拠金維持率に関係なく利用者が明示的に実行し、既存の全建玉ロスカット処理に近い対象抽出・直列化・個別決済を利用することが期待されている可能性が高い。

## 未確定事項

- なし。従前の確認事項は正式な人間回答により解決済みである。

## 人間への確認事項

- なし。

## 人間回答で確定した事項

1. 一部建玉の決済に失敗しても、決済可能な建玉を先に確定する部分成功とする。
2. クイック全決済は通常の個別手動決済と履歴上で区別し、注文由来を `QUICK_CLOSE`、表示名を「クイック全決済」とする。
3. 口座全体・ペア単位のいずれも確認ダイアログを表示しない。
4. 確認ダイアログを表示しないため、確認時の対象件数・通貨ペア・数量・概算損益の表示要件は設けない。
5. ペア単位の対象通貨ペアはクイック全決済操作内で別途選択可能とし、Trading 画面の選択中ペアに固定しない。
6. 単一 API 呼び出しを優先するが、性能上問題がなければフロントエンドから既存個別決済 API を複数回呼ぶ方式も許容する。
7. 対象 OPEN 建玉が0件の場合は利用者向けエラーとする。
8. 一部失敗時は、失敗した対象建玉の識別情報と失敗理由を必ず表示する。決済価格・損益等の表示は実装判断とする。

## AI判断で確定した事項

### 1. API 呼び出し方式は単一 API を優先する

- 判断内容: ペア単位と口座全体を1回で受け付ける単一 API を優先する。ただし、性能上問題がなければフロントエンドから既存個別決済 API を複数回呼ぶ方式も許容する。単一 API を新設する場合、破壊的な状態変更であるため HTTP メソッドは `POST` とし、具体的な URL 名は実装設計で既存の `/api/trade/positions` 配下に定める。
- 判断理由: 単一 API は対象抽出・口座ロック・結果集約を一か所で扱いやすいため望ましい。一方、正式な人間回答により、性能上問題がなければ既存 API の複数回呼び出しも許容された。
- 根拠: `PositionController` の状態変更 API は `POST` を使用し、`TradeExecutionService#liquidateAllPositionsIfMarginRatioAtOrBelow` は口座ロック内で全建玉処理を行う。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/api/PositionController.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`）

### 2. 数量と決済価格はサーバーが決める

- 判断内容: 単一 API 方式の入力 DTO は決済範囲と、ペア単位の場合の通貨ペアだけを持つ。複数回呼び出し方式では既存 API の建玉 ID を使用する。どちらも口座 ID、数量、決済価格はクライアントから受け取らない。
- 判断理由: 現行は単一の既定口座をサーバー側で特定し、建玉一覧も口座全体または通貨ペアで抽出できる。画面に表示した建玉 ID 一覧を送ると、表示後に作成・決済された建玉とのずれが生じる。数量と価格は既存個別決済がサーバー上の建玉全数量と最新レートから決めており、クライアント指定を許すと現行ルールを変えてしまう。
- 根拠: `DemoTradingAccountInitializer.DEFAULT_ACCOUNT_NUMBER`、`PositionRepository` の口座・通貨ペア別検索、`PositionService#closePositionForLockedAccount`。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/config/DemoTradingAccountInitializer.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/repository/PositionRepository.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`）

### 3. 採用方式にかかわらず既存の口座ロックと OPEN 状態検証を維持する

- 判断内容: 単一 API 方式では口座ロック取得後に対象 OPEN 建玉を抽出する。複数回呼び出し方式では、各個別決済 API が既存どおり口座ロックを取得し、対象建玉の OPEN 状態を検証する。
- 判断理由: 単一 API では対象集合をロック内で確定できる。複数回呼び出しでは操作全体を単一ロックにできないため、各決済の既存検証によって二重決済を防ぐ。
- 根拠: `AccountTradeLockService#withAccountLock`、`TradeExecutionService#liquidateAllPositionsIfMarginRatioAtOrBelow` と `#liquidateAllPositionsIfStillUnsafe`。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/AccountTradeLockService.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`）

### 4. 専用の冪等キーは初期要件に追加しない

- 判断内容: 連打・HTTP 再送時は、各決済で口座ロックと OPEN 状態検証を行い、すでに CLOSED の建玉を再決済しない。クイック全決済専用の冪等キーは設けない。
- 判断理由: 現行の個別決済 API に冪等キーの仕組みはなく、OPEN 状態検証と口座ロックで二重決済を防いでいる。クイック全決済でも同じ規則を適用でき、新しい永続データやクライアント管理を追加する必要がない。
- 根拠: `PositionService#closePosition` と `#closePositionForLockedAccount` は口座ロック、既定口座所属、`PositionStatus.OPEN` を確認してから決済する。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/service/PositionService.java`）

### 5. 対象0件は利用者向けエラーとする

- 判断内容: 対象となる OPEN 建玉が0件の場合は利用者向けエラーを返し、永続データを変更しない。
- 判断理由: 正式な人間回答により、処理対象なしを正常終了ではなく利用者向けエラーとして扱うことが確定した。
- 根拠: 人間回答7。既存の対象抽出は `PositionRepository` の OPEN 状態検索を使用できる。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/repository/PositionRepository.java`）

### 6. 採用方式に応じて結果を集約し、失敗明細を必須とする

- 判断内容: 単一 API 方式では専用 DTO を使用し、複数回呼び出し方式ではフロントエンドが既存 `PositionCloseResponse` と各エラーを集約する。どちらも対象件数・成功件数・失敗件数を示し、失敗した対象建玉の識別情報と失敗理由を必ず表示する。成功建玉の決済価格・損益等の表示は実装判断とする。
- 判断理由: 正式な人間回答により両方の API 方式が許容され、一部失敗時に必須となる画面情報が確定した。集約位置は異なっても、利用者へ提供する失敗情報は同じである。
- 根拠: `PositionCloseResponse`、`SwapTransferAllResponse`、および API は DTO を返すという `CODEX.md` の規則。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/PositionCloseResponse.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/dto/SwapTransferAllResponse.java`、`CODEX.md`）

### 7. 注文由来 `QUICK_CLOSE` で個別手動決済と区別する

- 判断内容: クイック全決済で作成する各注文は `QUICK_CLOSE` 由来とし、History では「クイック全決済」と表示する。
- 判断理由: 正式な人間回答により通常の個別手動決済との識別が必須となり、具体値はドラフト作成者に委ねられた。既存 enum の大文字スネークケースと衝突せず、表示名と意味が一貫する値として定める。
- 根拠: 人間回答2。既存の値は `MANUAL`、`LOSS_CUT`、`TRIGGER` である。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/common/enums/OrderSource.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/order/schema/OrderSchemaInitializer.java`）

### 8. Equity 履歴に専用の即時記録処理は追加しない

- 判断内容: クイック全決済後は建玉、注文、約定、損益サマリー、口座サマリーを再取得する。Equity 履歴は現行の定期記録と5秒間隔の画面再取得に委ね、専用の即時スナップショットは作成しない。
- 判断理由: 個別決済は Equity スナップショットを直接記録せず、バックエンドの定期レコーダーが履歴を作成する。クイック全決済だけ記録タイミングを変えると既存の履歴粒度を変更するため、Issue の範囲を超える。
- 根拠: `EquitySnapshotRecorder#recordDefaultAccountSnapshot` の定期実行、`MarketMonitorDashboard` の `loadEquityHistory` 5秒間隔実行、および個別決済成功処理。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/account/service/EquitySnapshotRecorder.java`、`FX_trading_front/fx-demo-front/app/components/MarketMonitorDashboard.tsx`）

### 9. 件数・処理時間の新しい上限は設けない

- 判断内容: 初期要件ではクイック全決済専用の件数上限や処理時間上限を追加せず、既定口座の対象 OPEN 建玉を処理する。
- 判断理由: 現行ロスカットは全 OPEN 建玉を上限なしで列挙し、Issue や設定にも上限の業務ルールはない。根拠のない数値上限は全決済できないケースを新たに作るため設定しない。
- 根拠: `TradeExecutionService#liquidateAllPositionsIfStillUnsafe`、`PositionRepository#findByAccountIdAndStatusOrderByOpenedAtAsc`、`application.properties` のロスカット設定。（`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/trade/service/TradeExecutionService.java`、`FX_trading_backend/fxdemo/src/main/java/com/example/fx/demo/backend/position/repository/PositionRepository.java`、`FX_trading_backend/fxdemo/src/main/resources/application.properties`）

## 残存する未確定事項

- なし。正式な人間回答と上記の実装裁量により解決済みである。

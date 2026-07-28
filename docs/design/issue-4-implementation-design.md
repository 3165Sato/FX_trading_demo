# Issue #4 PR作成自動化 実装設計書

## 1. 背景・目的

GitHub Issue #4「PRタイトル・本文の生成およびDraft PR作成を自動化する」と
`docs/requirements/issue-4-requirements-draft.md` を正式な入力とする。

現行運用では、Claude Code等が生成したPRタイトルと本文を `.tmp/` に保存した後、利用者が
PowerShell上でpush、既存PRの確認、Draft PR作成または更新、ブラウザ表示を個別に行っている。
このため、タイトルの文字化け、コマンドの実行漏れ、既存PRの誤判定といったリスクがある。

本機能の目的は、次の1コマンドで、安全かつ再実行可能なPR作成・更新フローを提供することである。

```powershell
.\scripts\create-pr.ps1 -IssueNumber 2
```

あわせて、GitHub画面からPRを作成する場合にも本文の記載粒度が揃うよう、リポジトリ共通の
PRテンプレートを設ける。

本書は実装方針を定める設計書であり、本書作成時点ではスクリプト、テンプレートその他の
実装コードは変更しない。

## 2. スコープ

### 2.1 対象

- `.github/pull_request_template.md` の新規追加
- `scripts/create-pr.ps1` の新規追加
- `-IssueNumber` に対応するタイトル・本文ファイルの検証とUTF-8読み込み
- 実行環境、Gitリポジトリ、認証、ブランチの事前検証
- 現在ブランチの `origin` へのpush
- 同一リポジトリ・同一head/baseのOPENなPRの一意判定
- PRがない場合のDraft PR作成
- PRが1件ある場合のタイトル・本文更新
- PR番号・URLの表示とブラウザ起動
- Windows PowerShell 5.1およびPowerShell 7での動作確認

### 2.2 対象外

- PRタイトル・本文そのものの生成
- Issue本文やコメントからのPR本文生成・追記
- ブランチ作成・切替、stage、commit、rebase、merge、force push
- PRのReady/Draft状態、base/head、ラベル、assignee、reviewer、project、milestoneの変更
- CI待機、マージ、リリース
- Issueの更新・クローズ
- GitHub Actionsによる自動化
- `.tmp/` の共有Git除外設定
- バックエンド、フロントエンドの変更

## 3. 現状調査結果

### 3.1 リポジトリ構成

- GitHubリポジトリは `3165Sato/FX_trading_demo`、リモート名は `origin` である。
- 調査時の `origin/HEAD` は `origin/main` を指し、デフォルトブランチは `main` である。
- 調査時の作業ブランチは `feature/issue-4-pr-automation` である。
- 主な構成は次のとおりである。

```text
.
├── FX_trading_backend/fxdemo/       # Spring Bootバックエンド
├── FX_trading_front/fx-demo-front/  # Next.jsフロントエンド
├── docs/                            # 要件、設計、レビュー、ADR
├── .tmp/                            # ローカルPR入力例（Git管理外）
├── CODEX.md
├── DESIGN.md
└── README.md
```

- `CODEX.md` は、着手前の現状調査、小さいステップ、既存機能を壊さない最小追加を求めている。
- Issue単位のブランチとして `feature/issue-2-order-change` と
  `feature/issue-4-pr-automation` が確認できるが、ブランチ命名規則を明文化した文書はない。

### 3.2 既存の `.github` 設定

- `.github/` ディレクトリは存在しない。
- PRテンプレート、Issueテンプレート、GitHub Actions、CODEOWNERS等は存在しない。
- Git履歴にも、これらのGitHub関連設定やPR作成自動化コードは確認できない。
- よって `.github/pull_request_template.md` は、このリポジトリで最初のPRテンプレートとなる。

### 3.3 既存PowerShellスクリプト

- リポジトリ直下に `scripts/` ディレクトリは存在しない。
- 追跡済みファイルおよびGit履歴にPowerShellスクリプトは確認できない。
- `gh pr create`、`gh pr edit`、`gh pr view` を呼び出す既存コードもない。
- よって `scripts/create-pr.ps1` は、最初のリポジトリ共通PowerShell運用スクリプトとなる。

### 3.4 既存PR運用フロー

- `.tmp/issue-2-pr-title.txt` と `.tmp/issue-2-pr-body.md` がローカルの入力実例として存在する。
  タイトルは1行、本文は複数行Markdownで、Issue #4の命名規則と一致する。
- `.tmp/` はリポジトリ共有の `.gitignore` ではなく、調査した作業コピー固有の
  `.git/info/exclude` で除外されている。別cloneでは未追跡ファイルとして表示され得る。
- GitHub上の既存PR #3は、`feature/issue-2-order-change` から `main` へのPRとして作成され、
  マージ済みである。本文には概要、背景、主な変更、変更対象、テスト、レビュー結果、
  影響範囲、残課題・確認事項、チェックリスト、`Closes #2` が記載されている。
- Issue #4によると、従来は入力ファイル作成後にpushとDraft PR作成をPowerShellで個別に行い、
  既存PRの有無に応じて利用者が操作を切り替えていた。
- 標準化されたPRテンプレートや、PR作成・更新の再実行可能なコマンドは現状存在しない。

### 3.5 正式入力から確定した要点

- GitHub Issue #4はOPEN、コメントは0件であり、追加条件はない。
- Issue #4は、認証確認、ブランチ安全確認、UTF-8読み込み、push、既存PR判定、
  Draft PR作成または更新、ブラウザ表示の自動化を要求している。
- 要件ドラフトは、Windows PowerShell 5.1とPowerShell 7の両対応、厳密なUTF-8検証、
  デフォルトブランチを推測しないこと、PR検索の一意性、既存PRのレビュー準備状態維持、
  ブラウザ起動失敗のみ警告扱いとすることを追加で確定している。

## 4. 実装方針・アーキテクチャ

### 4.1 全体方針

本機能は、GitHub側の標準入力を定める「静的PRテンプレート」と、ローカルからのPR操作を
調停する「PowerShellオーケストレーションスクリプト」の2要素で構成する。

```text
.tmpの入力ファイル
        │
        ▼
scripts/create-pr.ps1
  ├─ ローカル検証（副作用なし）
  ├─ gitによる現在ブランチのpush
  └─ ghによるPR検索・作成/更新・表示
        │
        ▼
GitHub Pull Request

.github/pull_request_template.md
        └─ GitHub画面から手動作成する場合の本文標準
```

テンプレートはスクリプト入力へ自動結合しない。自動結合すると、入力本文をそのまま送るという
要件に反し、再実行時に重複するためである。

### 4.2 `scripts/create-pr.ps1` の責務分割

単一ファイル内で、処理段階ごとに小さな関数へ分割する。関数名は実装時に調整可能だが、
責務は次の単位を維持する。

| 責務 | 設計内容 |
|---|---|
| エラー終了 | 段階名、原因、対応方法を標準エラーへ出し、非0で終了する |
| 外部コマンド実行 | 実行ファイルと引数を配列として渡し、終了コードと出力を回収する |
| リポジトリルート解決 | `git rev-parse --show-toplevel` を使用し、カレントディレクトリに依存しない |
| UTF-8読み込み | BOM有無を許容し、不正バイトを例外にする厳密なUTF-8デコーダーを使う |
| Git状態取得 | 現在ブランチ、dirty状態、`origin`、`origin/HEAD` を取得する |
| GitHub状態取得 | `gh` のJSON出力からリポジトリ名、デフォルトブランチ、PR候補を取得する |
| PR操作 | 新規作成または既存1件のタイトル・本文更新だけを行う |
| 結果表示 | 操作種別、PR番号、URL、ブラウザ表示結果を出力する |

スクリプト先頭には `CmdletBinding()` と、1以上の整数に制約した必須
`[int] $IssueNumber` パラメーターを定義する。PowerShell 5.1で利用できない構文・APIは使わない。

### 4.3 外部コマンド呼び出し

- `git`、`gh` の存在確認は `Get-Command -CommandType Application` 相当で行う。
- GitおよびGitHub CLIの固定最低バージョンは設けない。バージョン番号ではなく、実装で使用する
  コマンドとオプション（`gh repo view --json`、`gh pr list --json`、
  `gh pr create --draft --body-file`、`gh pr edit --body-file`、`gh pr view --web`）を
  利用できることを、各コマンドのhelp出力等によりpush前に確認する。必須機能がない場合は、
  CLIの更新方法を示して非0終了し、人向け出力の解析や機能縮退にはフォールバックしない。
- 外部コマンドは、タイトルや本文を含むコマンド文字列を組み立てて `Invoke-Expression` へ
  渡してはならない。
- タイトルは1引数として渡し、本文は `gh` の `--body-file` へ検証済みファイルパスを渡す。
  これにより引用符、バッククォート、`$`、日本語、改行をPowerShell式として評価しない。
- 各外部コマンド直後に終了コードを確認し、失敗時は以後の副作用を中止する。
- JSONを返せる `gh` コマンドでは `--json` を使用し、表示用テキストを正規表現で解析しない。
- 外部コマンドとの文字列受け渡しでは、スクリプト開始時に現在の
  `$OutputEncoding` と `[Console]::OutputEncoding` を退避し、BOMなしUTF-8へ一時設定する。
  `finally` で元の値へ戻し、利用者環境のグローバル設定を残留変更しない。JSONの構文解析失敗や
  置換文字を検出した場合は、文字化けした値で処理を続けずpush前または該当操作前に停止する。
  この方式をWindows PowerShell 5.1とPowerShell 7の双方で受け入れテストする。

### 4.4 リポジトリ・デフォルトブランチの特定

1. `git rev-parse --show-toplevel` でリポジトリルートを得る。
2. `git remote get-url origin` が成功することを確認する。
3. `git symbolic-ref --quiet --short HEAD` で現在ブランチを得る。取得できなければ
   detached HEADとして停止する。
4. `git symbolic-ref --quiet --short refs/remotes/origin/HEAD` の
   `origin/<name>` からデフォルトブランチを得る。
5. 4で取得できない場合のみ、`gh repo view --json nameWithOwner,defaultBranchRef` 相当の
   構造化出力から取得する。
6. いずれでも取得できない、空、複数解釈になる場合は停止し、`main` へ推測しない。
7. `gh repo view` から得た `nameWithOwner` を以後の全PR操作へ `--repo` で明示する。
   `origin` が示すリポジトリと一致しない場合は、別リポジトリへの操作を避けるため停止する。
8. 現在ブランチとデフォルトブランチが一致する場合は、push前に停止する。

### 4.5 入力ファイルとUTF-8

リポジトリルートから次を組み立てる。

- `.tmp/issue-<IssueNumber>-pr-title.txt`
- `.tmp/issue-<IssueNumber>-pr-body.md`

両方がコンテナではない通常ファイルとして存在することをpush前に確認する。

Windows PowerShell 5.1の `Get-Content -Encoding UTF8` は不正バイトを置換して読み進める可能性が
あるため、実装では `.NET` の `UTF8Encoding` を例外送出モードで使用する。BOMは
`StreamReader` 相当で検出し、BOMあり・なしの両方を受理する。読み込み失敗時は対象パスを示して
停止する。

- タイトル: 先頭・末尾の改行を除いた後、非空かつ実質1行であることを確認する。
  空白のみ、内部に複数の非空タイトル行がある場合は停止する。
- 本文: 改行を保持し、空白のみでないことを確認する。
- 本文を読み込んで検証した後も、`gh --body-file` には元の検証済みファイルを指定し、
  シェル引数への複数行埋め込みと再エンコードを避ける。

### 4.6 push設計

- 事前検証をすべて終えた後にのみpushする。
- dirtyな作業ツリーの場合は、未コミット変更がpushされないことを警告して継続する。
- `origin` と現在ブランチを常に明示し、upstreamの有無にかかわらず別ブランチを更新しない。
- upstreamが未設定なら `git push --set-upstream origin <current-branch>`、設定済みなら
  `git push origin <current-branch>` 相当を実行する。
- upstreamが設定済みでも、その設定先に暗黙依存せず現在ブランチ名を明示する。
- stage、commit、rebase、forceオプションは実行しない。

### 4.7 既存PRの一意判定

push成功後、`gh pr list` 相当をOPEN状態、取得済みbase、現在headで絞り、
PR番号、URL、状態、base ref、head ref、headリポジトリをJSONで取得する。
CLIの絞り込み結果を信用し切らず、スクリプト側でも次を全件検証する。

- 状態がOPEN
- baseが取得済みデフォルトブランチ
- headが現在ブランチ
- headリポジトリが現在の `nameWithOwner`（forkではない）

判定結果ごとの動作は次のとおりとする。

| 件数 | 動作 |
|---:|---|
| 0件 | Draft PRを新規作成する |
| 1件 | そのPRのタイトル・本文だけを更新する |
| 2件以上 | PRを変更せず、候補番号を表示して非0終了する |

CLOSED/MERGEDの過去PRおよびbase違いのOPEN PRは更新対象にしない。baseの自動変更もしない。

### 4.8 PR作成・更新

新規作成では、`--repo`、`--base`、`--head`、`--draft`、`--title`、
`--body-file` をすべて明示する。対話入力は行わず、ラベル等の追加メタデータは設定しない。

既存更新ではPR番号を明示し、`--title` と `--body-file` のみを更新する。Draft/Ready状態、
base/head、reviewer、label、assignee等は取得・再設定せず、既存値を維持する。

更新操作は外部API上で完全なトランザクションにはならない。タイトルと本文の一部だけが更新された
可能性がある場合は成功扱いにせず、PR番号と失敗段階を表示して非0終了する。再実行すれば同じPRへ
望ましいタイトル・本文を再適用できる設計とする。

### 4.9 PRテンプレート

`.github/pull_request_template.md` は、次の見出しをこの順序で持つ。

1. 概要
2. 背景
3. 主な変更
4. テスト
5. レビュー結果
6. 影響範囲
7. 残課題・確認事項
8. チェックリスト
9. 関連Issue

チェックリストには少なくとも、変更内容の自己確認、テスト結果の記載、関連ドキュメントの更新、
残課題の明記を含める。関連Issue欄には `Closes #<Issue番号>` 等を記入できるプレースホルダーを
置くが、自動クローズを強制しない。

## 5. 変更・追加予定ファイル

| パス | 種別 | 予定内容 |
|---|---|---|
| `.github/pull_request_template.md` | 新規 | GitHub画面用の標準PR本文テンプレート |
| `scripts/create-pr.ps1` | 新規 | 検証、push、PR作成/更新、ブラウザ表示を行うPowerShellスクリプト |

本Issueの実装では既存ファイルを変更せず、Pester等のテスト基盤やテスト専用ファイルも追加しない。
検証は第8章の手動受け入れ試験と静的確認で行う。自動テスト基盤の導入は、本機能の実装に必須では
なく、依存関係と変更範囲を増やすため別Issueとする。

## 6. 処理フロー

1. `IssueNumber` を検証する。
2. `git` と `gh` の実行可否を確認する。
3. `gh auth status` 相当で認証を確認する。
4. Gitワークツリーとリポジトリルートを確認する。
5. `origin`、現在ブランチ、detached HEADでないことを確認する。
6. `origin/HEAD`、次にGitHub情報からデフォルトブランチを取得する。
7. GitHubリポジトリと `origin` の一致を確認する。
8. 現在ブランチがデフォルトブランチでないことを確認する。
9. タイトル・本文ファイルの存在、厳密なUTF-8、内容を検証する。
10. dirtyなら警告する。
11. 現在ブランチを `origin` へpushし、必要ならupstreamを設定する。
12. 同一リポジトリ・同一head/baseのOPEN PRを検索・再検証する。
13. 0件ならDraft PRを作成し、1件ならタイトル・本文を更新する。複数なら停止する。
14. 対象PRの番号とURLを表示する。
15. 対象PRを既定ブラウザで開く。
16. ブラウザ起動のみ失敗した場合は警告とURLを表示し、終了コード0とする。

外部状態の変化は、原則として `push → PR作成/更新 → ブラウザ表示` の順でのみ発生する。
push前に判定可能な失敗は、すべて手順1〜10で検出する。

## 7. エラーハンドリング方針

### 7.1 終了コード

- 必須パラメーター、ツール、認証、Git状態、入力、push、PR検索、PR作成・更新の失敗:
  非0
- 全必須処理成功: 0
- PR作成・更新成功後のブラウザ起動だけの失敗: 警告を出して0

### 7.2 ログ

- 各段階の開始・成功を、認証確認、ブランチ確認、入力検証、push、PR検索、
  作成/更新、ブラウザ表示の粒度で標準出力へ出す。
- 警告はdirty状態やブラウザ起動失敗など、継続可能な状態に限定する。
- エラーは「停止した段階」「対象ファイル/ブランチ/PR」「原因」「利用者が取る対応」を
  標準エラーへ出す。
- 認証トークン、環境変数の秘密値、認証コマンドの機密出力は表示しない。

### 7.3 段階別の扱い

| 失敗 | 副作用・後続処理 |
|---|---|
| ツール不在・認証失敗 | push前に停止 |
| リポジトリ外・detached HEAD・デフォルトブランチ上 | push前に停止 |
| デフォルトブランチ取得不能・origin不整合 | 推測せずpush前に停止 |
| 入力欠落・空・複数行タイトル・不正UTF-8 | push前に停止 |
| push失敗 | PR検索・変更・ブラウザ表示を行わない |
| PR検索失敗・候補複数 | PRを変更しない |
| PR作成失敗 | URL表示・ブラウザ表示を行わず非0 |
| PR更新の全部または一部失敗 | PR番号と失敗内容を示し非0。再実行可能とする |
| ブラウザ起動失敗 | PR操作済みであることとURLを警告し0 |

## 8. テスト観点

### 8.1 静的確認

- 両追加ファイルがUTF-8である。
- PRテンプレートの9見出しが指定順で存在する。
- チェックリストがGitHub Markdownタスクリスト形式である。
- スクリプトが `Invoke-Expression`、force push、stage、commit、rebase等を使用しない。
- タイトル・本文がシェルコマンド文字列へ埋め込まれない。
- `git diff --check` が成功する。

### 8.2 パラメーター・ローカル検証

- `IssueNumber` が未指定、0、負数、整数以外の場合に副作用なく非0終了する。
- リポジトリルート、サブディレクトリのどちらから実行しても同じ `.tmp/` を参照する。
- リポジトリ外、detached HEAD、デフォルトブランチ、`origin` 不在、
  デフォルトブランチ取得不能でpush前に停止する。
- `git`/`gh` 不在、`gh` 未認証・認証無効でpush前に停止する。
- dirtyな作業ツリーは警告するが、stage/commitせず継続する。

### 8.3 入力・文字コード

- UTF-8 BOMあり・なしのタイトルと本文をWindows PowerShell 5.1、PowerShell 7の双方で読める。
- 日本語、引用符、バッククォート、`$`、複数行Markdownが欠落・展開・文字化けしない。
- ファイル欠落、ディレクトリによる同名偽装、空、空白のみ、不正UTF-8で停止する。
- タイトル前後の改行は許容し、複数の実質的なタイトル行は拒否する。
- 本文の改行がGitHub上で入力ファイルと一致する。

### 8.4 Git・PR正常系

- upstream未設定なら設定付きで現在ブランチをpushできる。
- upstream設定済みでも別名ブランチを更新せず、現在ブランチをpushする。
- 対象PRがない場合、正しいbase/headのDraft PRを作成する。
- 同じ入力で再実行してもPRを増やさず、同一PRを更新する。
- 入力を変更して再実行すると同一PRのタイトル・本文が更新される。
- Ready for reviewの既存PRでもReady状態とその他メタデータを維持する。
- 成功時に作成/更新の区別、PR番号、URLを表示し、ブラウザを開く。

### 8.5 Git・PR異常系

- push失敗後にPR検索・作成・更新を行わない。
- CLOSED/MERGEDの過去PRは更新せず、新規作成を妨げない。
- 同じheadでもbase違いのOPEN PRを更新しない。
- 同名ブランチを持つforkのPRを更新しない。
- 同一リポジトリ・同一head/baseのOPEN PR候補が複数なら、どれも変更しない。
- PR検索・作成・更新のAPI/認証/通信失敗を非0として扱う。
- 更新の一部失敗を成功表示せず、PR番号と失敗段階を表示する。
- ブラウザ起動だけが失敗した場合、URLを表示して終了コード0とする。

実GitHubを使用する結合確認はテスト用ブランチで行い、`main` や無関係な既存PRを変更しない。
本Issueでは既存のPowerShellテスト基盤がないため、第8章を正式な手動受け入れ試験とする。
試験結果には、実行したPowerShellの種類とバージョン、Git/GitHub CLIのバージョン、各観点の
成否を記録する。Pester等の自動テスト基盤は導入しない。

## 9. 確定した設計判断と判断保留

### 9.1 Git/GitHub CLIは固定最低バージョンではなく機能検出とする

- **判断内容**: 固定の最低バージョンを新設せず、使用するコマンドとオプションの利用可否を
  push前に検証する。利用できない場合はCLI更新を案内して停止する。
- **理由**: Issue #4は最低バージョンを要求しておらず、バージョン固定は既存利用環境を
  不必要に狭める。一方、未対応CLIで処理を開始することは安全でないため、必要機能の直接確認が
  互換性と安全性を両立する。
- **根拠**: GitHub Issue #4のツール・認証確認要件、
  `docs/requirements/issue-4-requirements-draft.md` のFR-03、FR-11、NFR-05。

### 9.2 テストは既存依存を増やさない手動受け入れ試験とする

- **判断内容**: 本IssueではPester等を導入せず、第8章の静的確認と手動受け入れ試験を正式な
  検証方法とする。PowerShell 5.1/7の双方と実GitHubを使う観点は結果を記録する。
- **理由**: リポジトリにはPowerShellスクリプトもテスト基盤もなく、自動テスト基盤の追加は
  Issueが指定する2成果物を超えて依存・変更範囲を広げる。外部Git/GitHub状態とブラウザ起動を
  含むため、初期実装では要件に列挙された手動受け入れ試験が最小かつ直接的である。
- **根拠**: `docs/requirements/issue-4-requirements-draft.md` のNFR-06、
  現在の `scripts/` 不在という調査結果、`CODEX.md` の最小追加方針。

### 9.3 外部コマンドの文字コード設定はスクリプト内に局所化する

- **判断内容**: `$OutputEncoding` と `[Console]::OutputEncoding` を実行中だけBOMなしUTF-8へ
  設定し、`finally` で復元する。構造化出力の解析失敗や置換文字はエラーとし、5.1/7双方で
  文字コード試験を行う。
- **理由**: グローバル設定の恒久変更を避けつつ、OSやPowerShellの既定コードページへの依存を
  除ける。入力ファイルについて確定済みの厳密UTF-8方針とも整合する。
- **根拠**: GitHub Issue #4のタイトル文字化け事象、
  `docs/requirements/issue-4-requirements-draft.md` のFR-05、NFR-01、NFR-02。

### 9.4 `.tmp/` の共有除外は変更しない

- **判断内容**: `.gitignore` 等へ `.tmp/` を追加しない。共有除外が必要な場合は別Issueとする。
- **理由**: 共有除外は全利用者のGit運用へ影響し、Issue #4が指定するPRテンプレートと
  PR作成スクリプトの実装には必須でない。既存作業コピーでは `.git/info/exclude` により
  現行運用が成立している。
- **根拠**: `docs/requirements/issue-4-requirements-draft.md` のスコープ外事項および
  「`.tmp/` の共有除外は本Issueで変更しない」という確定事項、`.git/info/exclude`。

### 9.5 判断保留

なし。上記はいずれもIssue、要件ドラフト、既存ファイルから、既存互換性、安全性、
変更範囲の最小化を基準に確定できる。セキュリティ・GitHub権限の変更、既存運用の大幅変更、
破壊的Git操作、仕様矛盾を伴う判断は本設計に含まれていない。

## 10. 受け入れ基準

- 指定の2ファイルだけで、PRテンプレート標準化と1コマンドのPR作成・更新フローが実現される。
- push前に判定可能な異常は外部状態を変えずに停止する。
- 日本語を含む入力がWindows PowerShell 5.1、PowerShell 7の双方で文字化けしない。
- 同一条件での再実行が重複PRを作らず、既存PRのタイトル・本文へ収束する。
- 既存PRのDraft/Ready状態とその他メタデータを変更しない。
- 失敗段階と復旧方法が利用者に分かり、秘密情報をログへ出さない。
- 第8章の必須テスト観点を満たす。

## 11. 参照

- GitHub Issue #4「PRタイトル・本文の生成およびDraft PR作成を自動化する」
- `docs/requirements/issue-4-requirements-draft.md`
- `CODEX.md`
- `.tmp/issue-2-pr-title.txt`（Git管理外の既存入力例）
- `.tmp/issue-2-pr-body.md`（Git管理外の既存入力例）
- GitHub PR #3（既存PR本文・運用実例）

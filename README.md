# DemoFX

学習用の架空FX取引デモアプリです。FX取引システムの一般概念(通貨ペア / Bid・Ask・Mid /
スプレッド / レート配信 / Tick履歴 / チャート / 注文 / 約定 / 建玉 / 証拠金 / ロスカット など)を
学ぶことを目的としています。実在サービスの仕様・社内システム・実業務の内部設計は再現せず、
実資金・実取引も一切扱いません。

---

## このドキュメントの役割

リポジトリには 3 つの「正(source of truth)」ドキュメントと、機能ごとの設計書があります。
**目的のものを 1 か所だけ見れば良い**ように役割を分けています。

| ファイル | 役割 | 主な読者 |
|---|---|---|
| `README.md` | プロジェクト全体の説明・起動方法・ドキュメント案内(本書) | 人間 |
| `DESIGN.md` | UI・見た目・画面思想の**正**(カラー/余白/角丸/情報密度/コンポーネント方針) | 人間 / Codex |
| `CODEX.md` | Codex に守らせる実装ルールの**正**(共通の前提・規約) | Codex / 人間 |
| `docs/design/` | 機能ごとの指示書・機能設計書(Claudeが作成) | Codex / 人間 |

- **フロントエンドを変更するときは必ず `DESIGN.md` を参照**し、UIの色・余白・角丸・情報密度・
  コンポーネント方針は `DESIGN.md` を正とします。
- **実装の共通ルールは `CODEX.md` を正**とします。機能指示書では共通ルールを再掲せず、
  これらを参照します。

---

## 技術スタック

### バックエンド
- Java 21 / Spring Boot 4.0.6 / Gradle
- Spring Data JPA / Spring Security
- PostgreSQL / Redis(導入済み・本格利用は保留)
- Logback(SQLログは `logs/sql.log`、アプリログは `logs/application.log`)

### フロントエンド
- Next.js / React / TypeScript
- Tailwind CSS / Recharts

### インフラ
- Docker Compose(PostgreSQL:5432 / Redis:6379)

---

## 主要な前提・初期値

| 項目 | 値 |
|---|---|
| 基軸通貨 | JPY |
| レバレッジ | 25倍(必要証拠金率 4%) |
| ロスカット閾値 | 維持率 50% |
| デモ口座 | DEMO-0001 |
| 初期残高 | 1,000,000 JPY |
| 通貨ペア | 9ペア(USD/JPY, EUR/JPY, EUR/USD, GBP/USD, GBP/JPY, AUD/USD, AUD/JPY, USD/CHF, USD/CAD) |
| ポジションモデル | ネッティング(両建てなし)。※両建て対応は今後の土台づくりで検討中 |

---

## 主な機能(実装済み)

- レートシミュレーター(平均回帰つきランダムウォーク・通貨ペア別ボラティリティ)
- スプレッド監視(pips換算・NORMAL / WIDE / VERY_WIDE)
- 疑似ニュースイベント(急騰・急落のインパルス+ボラ増幅+スプレッド拡大)
- 異常検知風アラート(SPREAD_WIDE / RAPID_MOVE / STALE_DATA / CROSSED_QUOTE)
- 2画面レイアウト(Market Monitor / Trading)
- 取引系: 成行注文(BUY=Ask / SELL=Bid)→ 建玉(ネッティング)→ 評価損益 →
  証拠金・維持率 → ロスカット・余力チェック
- 指値・逆指値(新規エントリー)

詳細は `docs/design/` の各機能設計書、および設計書ワークブックを参照。

---

## ディレクトリ構成(例)

```
.
├─ README.md            # 本書(全体説明)
├─ DESIGN.md            # UI・見た目の正
├─ CODEX.md             # 実装ルールの正
├─ docs/
│   └─ design/          # 機能ごとの指示書・設計書
├─ backend/             # Spring Boot
├─ frontend/            # Next.js
└─ docker-compose.yml
```

---

## 起動方法

### 1. Docker(DB / Redis)
プロジェクト直下で:
```
docker compose up -d
```
- PostgreSQL: `demofx` / `demofx_user` / `demofx_password`(例)/ 5432
- Redis: 6379

### 2. バックエンド
Spring Boot プロジェクト直下で:
```
./gradlew bootRun
# Windows PowerShell:
.\gradlew bootRun
```

### 3. フロントエンド
Next.js プロジェクト直下で:
```
npm run dev
```

### LAN内の別端末から見る場合
- フロントの API ベースURLは固定せず環境変数を使う:
  ```
  NEXT_PUBLIC_API_BASE_URL=http://<サーバPCのIP>:8080
  ```
- Next.js の `allowedDevOrigins` に該当IPを許可する。

---

## 注意

- 本アプリは**学習用の架空デモ**。実在サービス・実資金・実取引は扱わない。
- `logs/` 配下および `*.log` は Git 管理対象外。
- 開発用の Spring Security 設定(`/api/**` permitAll 等)は**本番用ではない**。

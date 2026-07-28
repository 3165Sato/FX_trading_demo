<#
.SYNOPSIS
現在の作業ブランチをpushし、対応するPull Requestを作成または更新します。

.DESCRIPTION
Issue番号に対応する次のUTF-8ファイルをリポジトリルートの .tmp から読み込みます。

  .tmp/issue-<Issue番号>-pr-title.txt
  .tmp/issue-<Issue番号>-pr-body.md

同一リポジトリ、同一head/baseのOpen PRがなければDraft PRを作成し、1件あれば
タイトルと本文だけを更新します。ブランチ作成、commit、force push、PRのReady化、
マージ、承認、Issue更新は行いません。

.PARAMETER IssueNumber
入力ファイル名に使用する1以上のIssue番号です。

.EXAMPLE
.\scripts\create-pr.ps1 -IssueNumber 2

.EXAMPLE
Get-Help .\scripts\create-pr.ps1 -Full

.NOTES
前提:
  - GitおよびGitHub CLI (gh) がインストール済みであること
  - gh auth login によりGitHubへ認証済みであること
  - originが対象GitHubリポジトリを指していること

終了コード:
  0: PRの作成または更新に成功
     （PR操作成功後のブラウザ起動失敗は警告のみで0）
  1: パラメーター、事前検証、push、PR検索、PR作成または更新の失敗
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$IssueNumber
)

$ErrorActionPreference = 'Stop'
$script:GitCommand = $null
$script:GhCommand = $null

function Write-Step {
    param([Parameter(Mandatory = $true)][string]$Message)

    Write-Host ("[INFO] {0}" -f $Message)
}

function Write-WarningMessage {
    param([Parameter(Mandatory = $true)][string]$Message)

    [Console]::Error.WriteLine(("[WARN] {0}" -f $Message))
}

function Stop-CreatePr {
    param(
        [Parameter(Mandatory = $true)][string]$Stage,
        [Parameter(Mandatory = $true)][string]$Message
    )

    throw ("[{0}] {1}" -f $Stage, $Message)
}

function Format-NativeOutput {
    param([string]$Output)

    $text = $Output.Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        return '(詳細出力なし)'
    }

    return $text
}

function ConvertTo-NativeArgument {
    param([AllowEmptyString()][string]$Argument)

    if ($Argument.Length -gt 0 -and $Argument -notmatch '[\s"]') {
        return $Argument
    }

    $builder = New-Object System.Text.StringBuilder
    [void]$builder.Append('"')
    $backslashCount = 0

    foreach ($character in $Argument.ToCharArray()) {
        if ($character -eq '\') {
            $backslashCount++
            continue
        }

        if ($character -eq '"') {
            [void]$builder.Append((('\' * (($backslashCount * 2) + 1)) -join ''))
            [void]$builder.Append('"')
        }
        else {
            if ($backslashCount -gt 0) {
                [void]$builder.Append((('\' * $backslashCount) -join ''))
            }
            [void]$builder.Append($character)
        }
        $backslashCount = 0
    }

    if ($backslashCount -gt 0) {
        [void]$builder.Append((('\' * ($backslashCount * 2)) -join ''))
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$Stage,
        [switch]$AllowFailure
    )

    $process = New-Object System.Diagnostics.Process
    $processStartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processStartInfo.FileName = $Command
    $processStartInfo.Arguments = (($Arguments | ForEach-Object {
        ConvertTo-NativeArgument -Argument $_
    }) -join ' ')
    $processStartInfo.UseShellExecute = $false
    $processStartInfo.CreateNoWindow = $true
    $processStartInfo.RedirectStandardOutput = $true
    $processStartInfo.RedirectStandardError = $true
    $strictUtf8 = New-Object System.Text.UTF8Encoding($false, $true)
    $processStartInfo.StandardOutputEncoding = $strictUtf8
    $processStartInfo.StandardErrorEncoding = $strictUtf8
    $process.StartInfo = $processStartInfo

    try {
        if (-not $process.Start()) {
            Stop-CreatePr -Stage $Stage -Message (
                "コマンドを開始できませんでした: {0}" -f $Command
            )
        }

        $standardOutputTask = $process.StandardOutput.ReadToEndAsync()
        $standardErrorTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $standardOutput = $standardOutputTask.GetAwaiter().GetResult()
        $standardError = $standardErrorTask.GetAwaiter().GetResult()
        $commandExitCode = $process.ExitCode
    }
    catch {
        Stop-CreatePr -Stage $Stage -Message (
            "コマンドの実行またはUTF-8出力の読み取りに失敗しました: {0} ({1})" -f
            $Command,
            $_.Exception.Message
        )
    }
    finally {
        $process.Dispose()
    }

    $result = [pscustomobject]@{
        ExitCode = $commandExitCode
        Text     = $standardOutput.Trim()
        ErrorText = $standardError.Trim()
    }

    if (($commandExitCode -ne 0) -and (-not $AllowFailure)) {
        $errorDetail = Format-NativeOutput -Output $standardError
        if ([string]::IsNullOrWhiteSpace($standardError)) {
            $errorDetail = Format-NativeOutput -Output $standardOutput
        }
        Stop-CreatePr -Stage $Stage -Message (
            "コマンドが失敗しました: {0} {1}{2}{3}" -f
            $Command,
            ($Arguments -join ' '),
            [Environment]::NewLine,
            $errorDetail
        )
    }

    return $result
}

function Assert-GhOptions {
    $checks = @(
        @{
            Arguments = @('repo', 'view', '--help')
            Options   = @('--json')
            Label     = 'gh repo view'
        },
        @{
            Arguments = @('pr', 'list', '--help')
            Options   = @('--json', '--state', '--base', '--head')
            Label     = 'gh pr list'
        },
        @{
            Arguments = @('pr', 'create', '--help')
            Options   = @('--draft', '--title', '--body-file', '--base', '--head')
            Label     = 'gh pr create'
        },
        @{
            Arguments = @('pr', 'edit', '--help')
            Options   = @('--title', '--body-file')
            Label     = 'gh pr edit'
        },
        @{
            Arguments = @('pr', 'view', '--help')
            Options   = @('--json', '--web')
            Label     = 'gh pr view'
        }
    )

    foreach ($check in $checks) {
        $helpResult = Invoke-NativeCommand `
            -Command $script:GhCommand `
            -Arguments $check.Arguments `
            -Stage 'ツール確認'

        foreach ($option in $check.Options) {
            if ($helpResult.Text.IndexOf($option, [StringComparison]::Ordinal) -lt 0) {
                Stop-CreatePr -Stage 'ツール確認' -Message (
                    "{0} が必須オプション {1} に対応していません。GitHub CLIを更新してください。" -f
                    $check.Label,
                    $option
                )
            }
        }
    }
}

function ConvertFrom-JsonStrict {
    param(
        [Parameter(Mandatory = $true)][string]$Json,
        [Parameter(Mandatory = $true)][string]$Stage
    )

    if ([string]::IsNullOrWhiteSpace($Json)) {
        Stop-CreatePr -Stage $Stage -Message 'GitHub CLIからJSONが返されませんでした。'
    }
    if ($Json.IndexOf([char]0xFFFD) -ge 0) {
        Stop-CreatePr -Stage $Stage -Message 'GitHub CLIの出力に文字コード変換エラーが検出されました。'
    }

    try {
        return ($Json | ConvertFrom-Json)
    }
    catch {
        Stop-CreatePr -Stage $Stage -Message (
            "GitHub CLIのJSON出力を解析できませんでした。GitHub CLIを更新して再実行してください。詳細: {0}" -f
            $_.Exception.Message
        )
    }
}

function Read-Utf8FileStrict {
    param(
        [Parameter(Mandatory = $true)][string]$LiteralPath,
        [Parameter(Mandatory = $true)][string]$Label
    )

    $utf8 = New-Object System.Text.UTF8Encoding($false, $true)
    $reader = $null

    try {
        $reader = New-Object System.IO.StreamReader($LiteralPath, $utf8, $true)
        return $reader.ReadToEnd()
    }
    catch {
        Stop-CreatePr -Stage '入力検証' -Message (
            "{0}をUTF-8として読み込めません: {1} ({2})" -f
            $Label,
            $LiteralPath,
            $_.Exception.Message
        )
    }
    finally {
        if ($null -ne $reader) {
            $reader.Dispose()
        }
    }
}

function Get-OriginRepositoryName {
    param([Parameter(Mandatory = $true)][string]$RemoteUrl)

    $normalizedUrl = $RemoteUrl.Trim().TrimEnd('/')
    $match = [regex]::Match(
        $normalizedUrl,
        '(?:[:/])(?<owner>[^/:]+)/(?<repo>[^/]+?)(?:\.git)?$',
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )

    if (-not $match.Success) {
        Stop-CreatePr -Stage 'リポジトリ確認' -Message (
            "origin URLからGitHubリポジトリ名を判定できません: {0}" -f $RemoteUrl
        )
    }

    return ("{0}/{1}" -f $match.Groups['owner'].Value, $match.Groups['repo'].Value)
}

function Get-HeadRepositoryName {
    param([Parameter(Mandatory = $true)][object]$PullRequest)

    if (($null -ne $PullRequest.headRepository) -and
        (-not [string]::IsNullOrWhiteSpace([string]$PullRequest.headRepository.nameWithOwner))) {
        return [string]$PullRequest.headRepository.nameWithOwner
    }

    $owner = $null
    if ($null -ne $PullRequest.headRepositoryOwner) {
        $owner = [string]$PullRequest.headRepositoryOwner.login
        if ([string]::IsNullOrWhiteSpace($owner)) {
            $owner = [string]$PullRequest.headRepositoryOwner
        }
    }

    $repositoryName = $null
    if ($null -ne $PullRequest.headRepository) {
        $repositoryName = [string]$PullRequest.headRepository.name
    }

    if ((-not [string]::IsNullOrWhiteSpace($owner)) -and
        (-not [string]::IsNullOrWhiteSpace($repositoryName))) {
        return ("{0}/{1}" -f $owner, $repositoryName)
    }

    return $null
}

function Invoke-CreatePr {
    Write-Step 'GitおよびGitHub CLIを確認しています。'
    $gitApplication = Get-Command git -CommandType Application -ErrorAction SilentlyContinue
    if ($null -eq $gitApplication) {
        Stop-CreatePr -Stage 'ツール確認' -Message 'Gitが見つかりません。Gitをインストールし、PATHを確認してください。'
    }
    $script:GitCommand = $gitApplication.Source

    $ghApplication = Get-Command gh -CommandType Application -ErrorAction SilentlyContinue
    if ($null -eq $ghApplication) {
        Stop-CreatePr -Stage 'ツール確認' -Message (
            'GitHub CLI (gh) が見つかりません。GitHub CLIをインストールし、PATHを確認してください。'
        )
    }
    $script:GhCommand = $ghApplication.Source
    Assert-GhOptions

    Write-Step 'GitHub CLIの認証状態を確認しています。'
    $authResult = Invoke-NativeCommand `
        -Command $script:GhCommand `
        -Arguments @('auth', 'status') `
        -Stage '認証確認' `
        -AllowFailure
    if ($authResult.ExitCode -ne 0) {
        Stop-CreatePr -Stage '認証確認' -Message (
            'GitHub CLIの認証が無効です。認証情報を出力せず停止しました。gh auth login を実行して再認証してください。'
        )
    }

    Write-Step 'Gitリポジトリと現在ブランチを確認しています。'
    $rootResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @('rev-parse', '--show-toplevel') `
        -Stage 'リポジトリ確認'
    $repositoryRoot = $rootResult.Text.Trim()
    if ([string]::IsNullOrWhiteSpace($repositoryRoot)) {
        Stop-CreatePr -Stage 'リポジトリ確認' -Message 'Gitリポジトリルートを取得できませんでした。'
    }

    $branchResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @('symbolic-ref', '--quiet', '--short', 'HEAD') `
        -Stage 'ブランチ確認' `
        -AllowFailure
    if (($branchResult.ExitCode -ne 0) -or [string]::IsNullOrWhiteSpace($branchResult.Text)) {
        Stop-CreatePr -Stage 'ブランチ確認' -Message (
            'detached HEAD、または現在ブランチを取得できない状態です。作業ブランチへ切り替えてください。'
        )
    }
    $currentBranch = $branchResult.Text.Trim()

    $originResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @('remote', 'get-url', 'origin') `
        -Stage 'リポジトリ確認' `
        -AllowFailure
    if (($originResult.ExitCode -ne 0) -or [string]::IsNullOrWhiteSpace($originResult.Text)) {
        Stop-CreatePr -Stage 'リポジトリ確認' -Message (
            'リモート origin が存在しません。対象GitHubリポジトリをoriginとして設定してください。'
        )
    }
    $originRepository = Get-OriginRepositoryName -RemoteUrl $originResult.Text

    $repoResult = Invoke-NativeCommand `
        -Command $script:GhCommand `
        -Arguments @('repo', 'view', '--json', 'nameWithOwner,defaultBranchRef') `
        -Stage 'リポジトリ確認'
    $repoInfo = ConvertFrom-JsonStrict -Json $repoResult.Text -Stage 'リポジトリ確認'
    $repositoryName = [string]$repoInfo.nameWithOwner
    if ([string]::IsNullOrWhiteSpace($repositoryName)) {
        Stop-CreatePr -Stage 'リポジトリ確認' -Message 'GitHubリポジトリ名を取得できませんでした。'
    }
    if (-not [string]::Equals($originRepository, $repositoryName, [StringComparison]::OrdinalIgnoreCase)) {
        Stop-CreatePr -Stage 'リポジトリ確認' -Message (
            "origin ({0}) とGitHub CLIが解決したリポジトリ ({1}) が一致しません。" -f
            $originRepository,
            $repositoryName
        )
    }

    $defaultBranch = $null
    $originHeadResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @('symbolic-ref', '--quiet', '--short', 'refs/remotes/origin/HEAD') `
        -Stage 'デフォルトブランチ確認' `
        -AllowFailure
    if (($originHeadResult.ExitCode -eq 0) -and
        $originHeadResult.Text.StartsWith('origin/', [StringComparison]::Ordinal)) {
        $defaultBranch = $originHeadResult.Text.Substring('origin/'.Length).Trim()
    }
    if ([string]::IsNullOrWhiteSpace($defaultBranch) -and ($null -ne $repoInfo.defaultBranchRef)) {
        $defaultBranch = [string]$repoInfo.defaultBranchRef.name
    }
    if ([string]::IsNullOrWhiteSpace($defaultBranch)) {
        Stop-CreatePr -Stage 'デフォルトブランチ確認' -Message (
            'origin/HEADとGitHubリポジトリ情報のどちらからもデフォルトブランチを取得できません。' +
            '固定値へ推測せず停止しました。'
        )
    }
    if ([string]::Equals($currentBranch, $defaultBranch, [StringComparison]::Ordinal)) {
        Stop-CreatePr -Stage 'ブランチ確認' -Message (
            "現在ブランチはデフォルトブランチ '{0}' です。デフォルトブランチへのpushは禁止されています。" -f
            $defaultBranch
        )
    }

    Write-Step 'PRタイトル・本文ファイルを検証しています。'
    $titlePath = Join-Path $repositoryRoot ('.tmp/issue-{0}-pr-title.txt' -f $IssueNumber)
    $bodyPath = Join-Path $repositoryRoot ('.tmp/issue-{0}-pr-body.md' -f $IssueNumber)

    foreach ($inputFile in @(
        @{ Path = $titlePath; Label = 'PRタイトルファイル' },
        @{ Path = $bodyPath; Label = 'PR本文ファイル' }
    )) {
        if (-not (Test-Path -LiteralPath $inputFile.Path -PathType Leaf)) {
            Stop-CreatePr -Stage '入力検証' -Message (
                "{0}が通常ファイルとして存在しません: {1}" -f $inputFile.Label, $inputFile.Path
            )
        }
    }

    $titleContent = Read-Utf8FileStrict -LiteralPath $titlePath -Label 'PRタイトルファイル'
    $bodyContent = Read-Utf8FileStrict -LiteralPath $bodyPath -Label 'PR本文ファイル'
    $title = $titleContent.Trim([char[]]"`r`n")
    if ([string]::IsNullOrWhiteSpace($title)) {
        Stop-CreatePr -Stage '入力検証' -Message "PRタイトルが空です: $titlePath"
    }
    if ($title -match '[\r\n]') {
        Stop-CreatePr -Stage '入力検証' -Message (
            "PRタイトルは1行で指定してください。複数行が含まれています: $titlePath"
        )
    }
    if ([string]::IsNullOrWhiteSpace($bodyContent)) {
        Stop-CreatePr -Stage '入力検証' -Message "PR本文が空です: $bodyPath"
    }

    $statusResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @('-C', $repositoryRoot, 'status', '--porcelain') `
        -Stage '作業ツリー確認'
    if (-not [string]::IsNullOrWhiteSpace($statusResult.Text)) {
        Write-WarningMessage (
            '未コミット変更があります。pushされるのはコミット済みの内容だけです。stageやcommitは行いません。'
        )
    }

    Write-Step ("現在ブランチ '{0}' をoriginへpushしています。" -f $currentBranch)
    $upstreamResult = Invoke-NativeCommand `
        -Command $script:GitCommand `
        -Arguments @(
            '-C',
            $repositoryRoot,
            'rev-parse',
            '--abbrev-ref',
            '--symbolic-full-name',
            '@{upstream}'
        ) `
        -Stage 'push' `
        -AllowFailure
    if ($upstreamResult.ExitCode -eq 0) {
        [void](Invoke-NativeCommand `
            -Command $script:GitCommand `
            -Arguments @('-C', $repositoryRoot, 'push', 'origin', $currentBranch) `
            -Stage 'push')
    }
    else {
        [void](Invoke-NativeCommand `
            -Command $script:GitCommand `
            -Arguments @('-C', $repositoryRoot, 'push', '--set-upstream', 'origin', $currentBranch) `
            -Stage 'push')
    }

    Write-Step '現在ブランチに対応するOpen PRを検索しています。'
    $prListResult = Invoke-NativeCommand `
        -Command $script:GhCommand `
        -Arguments @(
            'pr',
            'list',
            '--repo', $repositoryName,
            '--state', 'open',
            '--base', $defaultBranch,
            '--head', $currentBranch,
            '--limit', '100',
            '--json',
            'number,url,state,baseRefName,headRefName,headRepository,headRepositoryOwner'
        ) `
        -Stage 'PR検索'
    $prList = @(ConvertFrom-JsonStrict -Json $prListResult.Text -Stage 'PR検索')
    $matchingPullRequests = @(
        $prList | Where-Object {
            $headRepositoryName = Get-HeadRepositoryName -PullRequest $_
            ([string]$_.state -eq 'OPEN') -and
            ([string]$_.baseRefName -ceq $defaultBranch) -and
            ([string]$_.headRefName -ceq $currentBranch) -and
            (-not [string]::IsNullOrWhiteSpace($headRepositoryName)) -and
            [string]::Equals(
                $headRepositoryName,
                $repositoryName,
                [StringComparison]::OrdinalIgnoreCase
            )
        }
    )

    if ($matchingPullRequests.Count -gt 1) {
        $candidateNumbers = ($matchingPullRequests | ForEach-Object { "#$($_.number)" }) -join ', '
        Stop-CreatePr -Stage 'PR検索' -Message (
            "同一リポジトリ・同一head/baseのOpen PRが複数見つかりました: {0}。どのPRも変更していません。" -f
            $candidateNumbers
        )
    }

    $operation = $null
    $pullRequestNumber = $null
    $pullRequestUrl = $null

    if ($matchingPullRequests.Count -eq 0) {
        Write-Step '対象PRがないためDraft PRを作成しています。'
        [void](Invoke-NativeCommand `
            -Command $script:GhCommand `
            -Arguments @(
                'pr',
                'create',
                '--repo', $repositoryName,
                '--base', $defaultBranch,
                '--head', $currentBranch,
                '--draft',
                '--title', $title,
                '--body-file', $bodyPath
            ) `
            -Stage 'PR作成')

        $createdPrResult = Invoke-NativeCommand `
            -Command $script:GhCommand `
            -Arguments @(
                'pr',
                'view',
                $currentBranch,
                '--repo', $repositoryName,
                '--json', 'number,url'
            ) `
            -Stage 'PR作成結果確認'
        $createdPr = ConvertFrom-JsonStrict -Json $createdPrResult.Text -Stage 'PR作成結果確認'
        $pullRequestNumber = [int]$createdPr.number
        $pullRequestUrl = [string]$createdPr.url
        $operation = '作成'
    }
    else {
        $existingPr = $matchingPullRequests[0]
        $pullRequestNumber = [int]$existingPr.number
        $pullRequestUrl = [string]$existingPr.url
        Write-Step ("既存PR #{0} のタイトルと本文を更新しています。" -f $pullRequestNumber)
        [void](Invoke-NativeCommand `
            -Command $script:GhCommand `
            -Arguments @(
                'pr',
                'edit',
                [string]$pullRequestNumber,
                '--repo', $repositoryName,
                '--title', $title,
                '--body-file', $bodyPath
            ) `
            -Stage ("PR #{0} 更新" -f $pullRequestNumber))
        $operation = '更新'
    }

    if (($pullRequestNumber -le 0) -or [string]::IsNullOrWhiteSpace($pullRequestUrl)) {
        Stop-CreatePr -Stage '結果確認' -Message 'PR番号またはURLを取得できませんでした。'
    }

    Write-Host ("[SUCCESS] PRを{0}しました: #{1} {2}" -f $operation, $pullRequestNumber, $pullRequestUrl)
    Write-Step 'PRを既定ブラウザで開いています。'
    $browserResult = Invoke-NativeCommand `
        -Command $script:GhCommand `
        -Arguments @(
            'pr',
            'view',
            [string]$pullRequestNumber,
            '--repo', $repositoryName,
            '--web'
        ) `
        -Stage 'ブラウザ表示' `
        -AllowFailure
    if ($browserResult.ExitCode -ne 0) {
        Write-WarningMessage (
            "PRの{0}は完了しましたが、ブラウザを開けませんでした。手動で開いてください: {1}" -f
            $operation,
            $pullRequestUrl
        )
    }
}

$originalOutputEncoding = $OutputEncoding
$originalConsoleOutputEncoding = [Console]::OutputEncoding
$originalGhNoUpdateNotifier = [Environment]::GetEnvironmentVariable('GH_NO_UPDATE_NOTIFIER', 'Process')
$scriptExitCode = 0
$consoleEncodingChanged = $false

try {
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $OutputEncoding = $utf8NoBom
    try {
        [Console]::OutputEncoding = $utf8NoBom
        $consoleEncodingChanged = $true
    }
    catch {
        Stop-CreatePr -Stage '文字コード設定' -Message (
            'コンソールの出力エンコーディングをUTF-8へ設定できません。' +
            '対話的なWindows PowerShellまたはPowerShellコンソールから再実行してください。' +
            "詳細: $($_.Exception.Message)"
        )
    }
    [Environment]::SetEnvironmentVariable('GH_NO_UPDATE_NOTIFIER', '1', 'Process')
    Invoke-CreatePr
}
catch {
    [Console]::Error.WriteLine(("[ERROR] {0}" -f $_.Exception.Message))
    $scriptExitCode = 1
}
finally {
    $OutputEncoding = $originalOutputEncoding
    if ($consoleEncodingChanged) {
        try {
            [Console]::OutputEncoding = $originalConsoleOutputEncoding
        }
        catch {
            Write-WarningMessage (
                'コンソールの出力エンコーディングを元に戻せませんでした。' +
                "新しいコンソールを開いてください。詳細: $($_.Exception.Message)"
            )
        }
    }
    [Environment]::SetEnvironmentVariable(
        'GH_NO_UPDATE_NOTIFIER',
        $originalGhNoUpdateNotifier,
        'Process'
    )
}

exit $scriptExitCode

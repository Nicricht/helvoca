# Helvoca C1: one-time Google Drive OAuth authorization (Windows PowerShell).
# NO customer dump, key, password or OAuth token is printed or sent to ChatGPT.
# OAuth access is limited to files created by this app (drive.file).
# The encrypted manual backups in the original folder stay untouched.
param(
    [string]$ProjectId = '41988ee6-f9f6-48eb-b726-abb973bb5740',
    [string]$EnvironmentId = '8bbc755a-58b5-46e8-8227-eefa4adbbce4',
    [string]$ServiceId = '42bfe173-2f69-4605-ae63-a144a4a6e1dd',
    [switch]$FreshAccount,
    [string]$ExpectedEmail = ''
)
$ErrorActionPreference = 'Continue'
# Native rclone and icacls may emit benign stderr on Windows PowerShell 5.1.
# Each external command must be checked via LASTEXITCODE or safe failure code.
$script:stage = 'START'
Set-StrictMode -Version Latest

function Assert-NativeSuccess([string]$Code) {
    if ($LASTEXITCODE -ne 0) { throw $Code }
}
function Secure-Directory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        [void](New-Item -ItemType Directory -Path $Path -Force)
    }
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $Path '/inheritance:r' '/grant:r' "$($identity):(OI)(CI)F" *> $null
    Assert-NativeSuccess 'C1_OAUTH_PRIVATE_DIRECTORY_ACL_FAILED'
}
function Secure-File([string]$Path) {
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $Path '/inheritance:r' '/grant:r' "$($identity):F" *> $null
    Assert-NativeSuccess 'C1_OAUTH_PRIVATE_FILE_ACL_FAILED'
}
function Set-RailwayVar([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { throw 'C1_OAUTH_EMPTY_VARIABLE' }
    # Railway CLI documents --stdin, so secrets never appear as process args.
    $Value | & railway variable set $Name --stdin --project $ProjectId --environment $EnvironmentId --service $ServiceId --skip-deploys
    Assert-NativeSuccess 'C1_RAILWAY_VARIABLE_UPDATE_FAILED'
}
try {
    if (-not (Get-Command railway -ErrorAction SilentlyContinue)) {
        throw 'C1_RAILWAY_CLI_REQUIRED'
    }
    if ($env:OS -ne 'Windows_NT') { throw 'C1_WINDOWS_REQUIRED' }
    $script:stage = 'LOCAL_PRIVATE_STORAGE'
    $private = Join-Path $env:USERPROFILE 'HelvocaPrivate\oauth'
    Secure-Directory $private
    $conf = Join-Path $private 'rclone.conf'
    if (-not (Test-Path -LiteralPath $conf)) { [IO.File]::WriteAllText($conf, '') }
    Secure-File $conf

    $script:stage = 'PORTABLE_RCLONE'
    $found = Get-Command rclone -ErrorAction SilentlyContinue
    $rcloneExe = if ($found) { $found.Source } else { '' }
    if (-not $rcloneExe) {
        $version = 'v1.75.2'
        $name = "rclone-$version-windows-amd64.zip"
        $work = Join-Path $env:TEMP 'HelvocaC1PortableRclone'
        if (-not (Test-Path $work)) { [void](New-Item -ItemType Directory -Path $work) }
        $zip = Join-Path $work $name
        $checksums = Join-Path $work 'SHA256SUMS'
        if (-not (Test-Path $zip)) {
            Invoke-WebRequest -UseBasicParsing "https://downloads.rclone.org/$version/$name" -OutFile $zip
        }
        Invoke-WebRequest -UseBasicParsing "https://downloads.rclone.org/$version/SHA256SUMS" -OutFile $checksums
        $escaped = [regex]::Escape($name)
        $checksumText = [IO.File]::ReadAllText($checksums)
        $match = [regex]::Match($checksumText, "(?m)^([0-9a-fA-F]{64})[ ]+[*]?$escaped[ ]*$")
        if (-not $match.Success) { throw 'C1_RCLONE_OFFICIAL_SHA_NOT_FOUND' }
        $expected = $match.Groups[1].Value.ToLowerInvariant()
        $got = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($got -ne $expected) { throw 'C1_RCLONE_DOWNLOAD_SHA256_MISMATCH' }
        Expand-Archive -LiteralPath $zip -DestinationPath $work -Force
        $rcloneExe = (Get-ChildItem $work -Recurse -Filter rclone.exe -File | Select-Object -First 1 -ExpandProperty FullName)
        if (-not $rcloneExe) { throw 'C1_RCLONE_PORTABLE_NOT_FOUND' }
    }

    $script:stage = 'OAUTH_CONFIG'
    if ([string]::IsNullOrWhiteSpace($ExpectedEmail)) { throw 'C1_EXPECTED_INSTITUTIONAL_EMAIL_REQUIRED' }
    $ExpectedEmail = $ExpectedEmail.Trim().ToLowerInvariant()
    if ($ExpectedEmail -notmatch '^[^@\s]+@duocuc\.cl$') { throw 'C1_EXPECTED_EMAIL_NOT_INSTITUTIONAL' }
    # FreshAccount never overwrites an existing remote; a new browser login is mandatory.
    $remote = if ($FreshAccount) { 'institutional_c1_' + [guid]::NewGuid().ToString('N').Substring(0,12) } else { 'institutional' }
    $existing = & $rcloneExe --config $conf listremotes
    Assert-NativeSuccess 'C1_RCLONE_CONFIG_READ_FAILED'
    if ($existing -notcontains "$($remote):") {
        Write-Host 'C1: Autoriza con tu cuenta INSTITUCIONAL en el navegador.'
        Write-Host 'C1: La app solo puede gestionar archivos creados por ella (drive.file).'
        Write-Host 'C1: Si Google bloquea el OAuth compartido de rclone, deberas usar un cliente OAuth propio.'
        # IMPORTANT: default rclone config create prints OAuth tokens; suppress it.
        & $rcloneExe --config $conf config create $remote drive scope=drive.file config_is_local=true --no-output 1>$null 2>$null
        Assert-NativeSuccess 'C1_GOOGLE_OAUTH_NOT_AUTHORIZED'
        Secure-File $conf
    }

    # Check user identity with minimum-scope drive.file BEFORE ANY Drive write.
    $script:stage = 'GOOGLE_ACCOUNT_VERIFY'
    $oauthConfText = [IO.File]::ReadAllText($conf)
    $remoteSection = [regex]::Match($oauthConfText, '(?ms)^\[' + [regex]::Escape($remote) + '\]\s*\r?\n(?<body>.*?)(?=^\[|\z)')
    if (-not $remoteSection.Success) { throw 'C1_OAUTH_REMOTE_SECTION_NOT_FOUND' }
    $identityTokenMatch = [regex]::Match($remoteSection.Groups['body'].Value, '(?m)^[ \t]*token[ \t]*=[ \t]*(\{[^\r\n]+\})[ \t]*$')
    if (-not $identityTokenMatch.Success) { throw 'C1_OAUTH_TOKEN_NOT_FOUND_LOCALLY' }
    $identityToken = $identityTokenMatch.Groups[1].Value | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($identityToken.access_token)) { throw 'C1_OAUTH_ACCESS_TOKEN_MISSING' }
    try {
        $apiResult = Invoke-RestMethod -Uri 'https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)' -Method Get -Headers @{ Authorization = 'Bearer ' + $identityToken.access_token } -TimeoutSec 30 -ErrorAction Stop
    } catch { throw 'C1_OAUTH_DRIVE_IDENTITY_UNVERIFIABLE' }
    if ($null -eq $apiResult.user -or [string]::IsNullOrWhiteSpace($apiResult.user.emailAddress)) { throw 'C1_OAUTH_DRIVE_EMAIL_MISSING' }
    if ($apiResult.user.emailAddress.Trim().ToLowerInvariant() -ne $ExpectedEmail) {
        throw 'C1_OAUTH_ACCOUNT_MISMATCH'
    }
    Write-Host 'C1_GOOGLE_ACCOUNT_INSTITUTIONAL_VERIFIED'
    # drive.file needs an app-created folder. The browser-created manual
    # HelvocaBackups folder is neither renamed nor modified.
    $script:stage = 'DRIVE_FOLDER'
    $folderName = 'HelvocaBackups-Automaticos'
    & $rcloneExe --config $conf mkdir "$($remote):$folderName" *> $null
    Assert-NativeSuccess 'C1_OAUTH_DRIVE_FOLDER_CREATE_BLOCKED'
    $entriesJson = & $rcloneExe --config $conf lsjson "$($remote):" --dirs-only
    Assert-NativeSuccess 'C1_OAUTH_DRIVE_LIST_FAILED'
    $entries = @($entriesJson | ConvertFrom-Json)
    $folder = @($entries | Where-Object { $_.Name -eq $folderName -and $_.IsDir })
    if ($folder.Count -ne 1 -or [string]::IsNullOrWhiteSpace($folder[0].ID)) {
        throw 'C1_OAUTH_DRIVE_FOLDER_ID_UNVERIFIED'
    }

    # One tiny nonsensitive probe; this is NOT customer data.
    $script:stage = 'OAUTH_READBACK_TEST'
    $markerPath = Join-Path $private 'c1-check.tmp'
    [IO.File]::WriteAllText($markerPath, 'HELVOCA_C1_INSTITUTIONAL_OAUTH_PROBE')
    Secure-File $markerPath
    $markerName = "C1_OAUTH_TEST_$([guid]::NewGuid().ToString('N')).txt"
    $destination = "$($remote):$folderName/$markerName"
    try {
        & $rcloneExe --config $conf copyto --immutable $markerPath $destination *> $null
        Assert-NativeSuccess 'C1_OAUTH_MARKER_UPLOAD_FAILED'
        $readback = & $rcloneExe --config $conf cat $destination
        Assert-NativeSuccess 'C1_OAUTH_MARKER_READBACK_FAILED'
        if (($readback -join '') -ne 'HELVOCA_C1_INSTITUTIONAL_OAUTH_PROBE') {
            throw 'C1_OAUTH_MARKER_MISMATCH'
        }
    } finally { Remove-Item -LiteralPath $markerPath -ErrorAction SilentlyContinue }

    $script:stage = 'LOCAL_OAUTH_TOKEN'
    # Extract only the verified remote's token; never use the old remote token.
    $rawConf = [IO.File]::ReadAllText($conf)
    $verifiedSection = [regex]::Match($rawConf, '(?ms)^\[' + [regex]::Escape($remote) + '\]\s*\r?\n(?<body>.*?)(?=^\[|\z)')
    if (-not $verifiedSection.Success) { throw 'C1_OAUTH_REMOTE_SECTION_NOT_FOUND' }
    $tokenMatch = [regex]::Match($verifiedSection.Groups['body'].Value, '(?m)^[ \t]*token[ \t]*=[ \t]*(\{[^\r\n]+\})[ \t]*$')
    if (-not $tokenMatch.Success) { throw 'C1_OAUTH_TOKEN_NOT_FOUND_LOCALLY' }
    $oauthToken = $tokenMatch.Groups[1].Value
    $tokenParsed = $oauthToken | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($tokenParsed.refresh_token)) {
        throw 'C1_OAUTH_REFRESH_TOKEN_MISSING'
    }

    # Set only the dedicated empty backup service; no deployment or cron.
    $script:stage = 'RAILWAY_VARIABLE_UPLOAD'
    Set-RailwayVar 'RCLONE_CONFIG_INSTITUTIONAL_TOKEN' $oauthToken
    Set-RailwayVar 'RCLONE_CONFIG_INSTITUTIONAL_ROOT_FOLDER_ID' $folder[0].ID
    Set-RailwayVar 'RCLONE_CONFIG_INSTITUTIONAL_SCOPE' 'drive.file'
    Write-Host 'C1_INSTITUTIONAL_DRIVE_OAUTH_AND_RAILWAY_VARIABLES_PREPARED'
    Write-Host 'No backups were executed, and Railway cron remains disabled.'
    Write-Host 'Never share your local rclone.conf or any OAuth token.'
} catch {
    $code = $_.Exception.Message
    if ($code -notmatch '^C1_[A-Z0-9_]+$') {
        # Only the failing stage is emitted; raw exceptions may contain OAuth secrets.
        $code = 'C1_OAUTH_UNEXPECTED_' + $script:stage
    }
    Write-Host "C1_OAUTH_BLOCKED: $code"
    Write-Host 'No backup/production deployment requested. Do not paste OAuth details.'
    exit 2
}

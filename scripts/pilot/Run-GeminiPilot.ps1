# RecepVoz Gemini document import: private Windows pilot launcher.
# Normal invocation is OFFLINE. -Send performs at most three explicit Free Tier
# API requests, AFTER a current, human-confirmed no-billing preflight.
[CmdletBinding()]
param(
    [switch]$Configure,
    [switch]$Send
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $env:LOCALAPPDATA) {
    throw 'Este asistente está diseñado para Windows, con LOCALAPPDATA disponible.'
}
$privateRoot = Join-Path $env:LOCALAPPDATA 'RecepVoz\GeminiImportPilot'
$protectedKey = Join-Path $privateRoot 'pilot-key.dpapi.txt'
$projectId = 'gen-lang-client-0812582341'
$python = (Get-Command python -ErrorAction Stop).Source

if ($Configure) {
    New-Item -Path $privateRoot -ItemType Directory -Force | Out-Null
    Write-Host 'Configuración PRIVADA: la clave se cifra para tu usuario Windows (DPAPI).'
    Write-Host 'Usa SOLO la clave del proyecto personal RecepVoz-Gemini-Pilot.'
    $secret = Read-Host 'Introduce la clave de Gemini (no aparecerá)' -AsSecureString
    if ($secret.Length -lt 20) {
        throw 'Clave demasiado corta. No se guardó ningún valor.'
    }
    ConvertFrom-SecureString -SecureString $secret |
        Set-Content -LiteralPath $protectedKey -Encoding UTF8
    $secret.Dispose()
    Write-Host 'Clave cifrada guardada fuera del repositorio. Nunca la pegues en GitHub ni en el chat.'
    if (-not $Send) {
        Write-Host 'Para una prueba real: .\Run-GeminiPilot.ps1 -Send'
        return
    }
}

$generator = Join-Path $PSScriptRoot 'fixture_factory.py'
$runner = Join-Path $PSScriptRoot 'benchmark_batch.py'
if (-not (Test-Path -LiteralPath $generator) -or
    -not (Test-Path -LiteralPath $runner)) {
    throw 'Faltan los scripts del repositorio. Ejecuta desde scripts\pilot.'
}

& $python -c 'import PIL' *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Preparando Pillow para imágenes sintéticas locales...'
    & $python -m pip install --disable-pip-version-check 'Pillow==11.3.0'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar Pillow.' }
}

$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$runFolder = Join-Path $privateRoot ('run-' + $runId + '-' + [guid]::NewGuid().ToString('N').Substring(0,6))
$images = Join-Path $runFolder 'images'
$receipts = Join-Path $runFolder 'results'
& $python $generator --out $images
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron crear los menús ficticios.' }
$manifest = Join-Path $images 'manifest.json'

if (-not $Send) {
    & $python $runner $manifest --out $receipts
    if ($LASTEXITCODE -ne 0) { throw 'Falló la validación offline.' }
    Write-Host 'OK: menús creados y validados, SIN solicitudes externas.'
    Write-Host "Archivos: $runFolder"
    return
}

if (-not (Test-Path -LiteralPath $protectedKey)) {
    throw 'Todavía no hay clave cifrada. Ejecuta primero: .\Run-GeminiPilot.ps1 -Configure'
}
Write-Host 'IMPORTANTE: Google AI Studio debe mostrar el proyecto personal, NIVEL GRATUITO y SIN facturación.'
Write-Host 'Solo se enviarán hasta 3 documentos FICTICIOS; cero publicaciones y cero reintentos.'
$consent = Read-Host 'Si acabas de verificar esos tres puntos, escribe CONFIRMO'
if ($consent -cne 'CONFIRMO') {
    throw 'Cancelado: faltó la confirmación humana de nivel gratuito y ausencia de facturación.'
}

$secure = Get-Content -LiteralPath $protectedKey -Raw | ConvertTo-SecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:GEMINI_PILOT_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $secure.Dispose()
}
try {
    $env:GEMINI_PILOT_APPROVED = 'yes'
    $env:GEMINI_PILOT_PERSONAL_PROJECT = 'yes'
    $env:GEMINI_PILOT_FREE_TIER_CONFIRMED = 'yes'
    $env:GEMINI_PILOT_NO_BILLING = 'yes'
    $env:GEMINI_PILOT_SYNTHETIC_ONLY = 'yes'
    $env:GEMINI_PILOT_PROJECT_NAME = 'RecepVoz-Gemini-Pilot'
    $env:GEMINI_PILOT_PROJECT_ID = $projectId
    & $python $runner $manifest --out $receipts --send
    if ($LASTEXITCODE -ne 0) {
        throw "NO-GO. Revisa el reporte privado en $receipts. No reintentes el mismo lote."
    }
    Write-Host 'Prueba REAL completada. Verifica también el uso del proyecto en Google AI Studio.'
    Write-Host "Reportes privados: $receipts"
} finally {
    Remove-Item Env:GEMINI_PILOT_API_KEY -ErrorAction SilentlyContinue
    foreach ($name in @('GEMINI_PILOT_APPROVED','GEMINI_PILOT_PERSONAL_PROJECT',
        'GEMINI_PILOT_FREE_TIER_CONFIRMED','GEMINI_PILOT_NO_BILLING',
        'GEMINI_PILOT_SYNTHETIC_ONLY','GEMINI_PILOT_PROJECT_NAME',
        'GEMINI_PILOT_PROJECT_ID')) {
        Remove-Item ('Env:' + $name) -ErrorAction SilentlyContinue
    }
}

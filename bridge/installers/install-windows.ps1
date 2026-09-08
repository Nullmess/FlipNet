# Installe la passerelle sur Windows.
param(
    [ValidateSet("auto", "usb", "ble")]
    [string]$Transport = "usb"
)

$ErrorActionPreference = "Stop"
$BridgeDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$InstallRoot = Join-Path $env:LOCALAPPDATA "FlipNet"
$VenvDir = Join-Path $InstallRoot "venv"
$LogDir = Join-Path $InstallRoot "logs"
$LogFile = Join-Path $LogDir "bridge.log"

$ExistingTask = Get-ScheduledTask -TaskName "FlipNet Bridge" -ErrorAction SilentlyContinue
if ($null -ne $ExistingTask) {
    Stop-ScheduledTask -TaskName "FlipNet Bridge" -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
}

New-Item -ItemType Directory -Force -Path $InstallRoot, $LogDir | Out-Null

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py -3 -m venv $VenvDir
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python -m venv $VenvDir
} else {
    throw "Python 3.11+ est introuvable. Installe Python puis relance ce script."
}

$Python = Join-Path $VenvDir "Scripts\python.exe"
$PythonW = Join-Path $VenvDir "Scripts\pythonw.exe"

& $Python -c "import sys; raise SystemExit(0 if sys.version_info >= (3,11) else 1)"
if ($LASTEXITCODE -ne 0) {
    throw "FlipNet Bridge requiert Python 3.11 ou plus recent."
}

& $Python -m pip install --upgrade pip
& $Python -m pip install --upgrade --force-reinstall $BridgeDir

$Arguments = "-m flipnet_bridge --transport $Transport --service-mode --log-file `"$LogFile`""
$Action = New-ScheduledTaskAction -Execute $PythonW -Argument $Arguments
$Trigger = New-ScheduledTaskTrigger -AtLogOn
$Settings = New-ScheduledTaskSettingsSet `
    -RestartCount 999 `
    -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero) `
    -MultipleInstances IgnoreNew

Register-ScheduledTask `
    -TaskName "FlipNet Bridge" `
    -Action $Action `
    -Trigger $Trigger `
    -Settings $Settings `
    -Description "FlipNet USB/BLE Internet bridge for Flipper Zero" `
    -Force | Out-Null

Start-ScheduledTask -TaskName "FlipNet Bridge"

Write-Host ""
Write-Host "FlipNet Bridge installe et lance." -ForegroundColor Green
Write-Host "Version :" -NoNewline
& $Python -m flipnet_bridge --version
Write-Host "Logs    : $LogFile"
Write-Host "Etat    : Get-ScheduledTask -TaskName 'FlipNet Bridge'"
Write-Host "Aucun port TCP de gestion n est ouvert."

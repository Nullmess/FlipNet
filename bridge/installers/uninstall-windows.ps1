# Désinstalle la passerelle sur Windows.
$ErrorActionPreference = "Stop"
Unregister-ScheduledTask -TaskName "FlipNet Bridge" -Confirm:$false -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force (Join-Path $env:LOCALAPPDATA "FlipNet\venv") -ErrorAction SilentlyContinue
Write-Host "FlipNet Bridge desinstalle. Les journaux et la configuration ont ete conserves."

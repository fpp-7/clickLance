param([Parameter(Mandatory=$true)][string]$FfmpegBinDirectory)
$ErrorActionPreference = 'Stop'
$gatewayRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$pythonPath = Join-Path $gatewayRoot '.venv/Scripts/python.exe'
$launcher = Join-Path $PSScriptRoot 'run-gateway.ps1'
$ffmpegRoot = (Resolve-Path -LiteralPath $FfmpegBinDirectory).Path
if (!(Test-Path -LiteralPath $pythonPath)) { throw 'Crie o ambiente virtual primeiro.' }
if (!(Test-Path -LiteralPath (Join-Path $ffmpegRoot 'ffmpeg.exe')) -or
    !(Test-Path -LiteralPath (Join-Path $ffmpegRoot 'ffprobe.exe'))) {
    throw 'FFmpeg/ffprobe não encontrados no diretório informado.'
}
$taskAction = New-ScheduledTaskAction -Execute 'powershell.exe' -WorkingDirectory $gatewayRoot `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$launcher`" -FfmpegBinDirectory `"$ffmpegRoot`""
$taskTrigger = New-ScheduledTaskTrigger -AtStartup
$taskSettings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
Register-ScheduledTask -TaskName 'ClickLanceGateway' -Action $taskAction -Trigger $taskTrigger `
    -Settings $taskSettings -User 'SYSTEM' -RunLevel Highest -Force | Out-Null
Write-Output 'Tarefa criada. Execute Start-ScheduledTask ClickLanceGateway para iniciar agora.'

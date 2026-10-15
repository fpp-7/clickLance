param([Parameter(Mandatory=$true)][string]$FfmpegBinDirectory)
$ErrorActionPreference = 'Stop'
$gatewayRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ffmpegRoot = (Resolve-Path -LiteralPath $FfmpegBinDirectory).Path
if (!(Test-Path -LiteralPath (Join-Path $ffmpegRoot 'ffmpeg.exe')) -or
    !(Test-Path -LiteralPath (Join-Path $ffmpegRoot 'ffprobe.exe'))) {
    throw 'Informe o diretório contendo ffmpeg.exe e ffprobe.exe.'
}
$env:Path = "$ffmpegRoot;$env:Path"
Set-Location -LiteralPath $gatewayRoot
& (Join-Path $gatewayRoot '.venv/Scripts/python.exe') -X utf8 (Join-Path $gatewayRoot 'main.py')
exit $LASTEXITCODE

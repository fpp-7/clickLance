# Optional supervisor. Configure a Windows scheduled task explicitly if desired.
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$gatewayPython = Join-Path $PSScriptRoot '.venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $gatewayPython)) {
    throw 'Crie o ambiente virtual antes de iniciar o supervisor.'
}
while ($true) {
    & $gatewayPython (Join-Path $PSScriptRoot 'main.py')
    if ($LASTEXITCODE -eq 0) { break }
    Write-Warning 'Gateway encerrou com erro; nova tentativa em 10 segundos.'
    Start-Sleep -Seconds 10
}

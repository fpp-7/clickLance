[CmdletBinding()]
param([string]$PythonExecutable = '', [switch]$DownloadBinaries, [switch]$KeepData)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'postgres-runtime.ps1')
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$backendRoot = Join-Path $repoRoot 'backend_cloud'
$gatewayRoot = Join-Path $repoRoot 'gateway_captura'
if ([string]::IsNullOrWhiteSpace($PythonExecutable)) {
    $gatewayPython = Join-Path $gatewayRoot '.venv/Scripts/python.exe'
    $PythonExecutable = if (Test-Path -LiteralPath $gatewayPython) { $gatewayPython } else { 'python' }
}
$PythonExecutable = if (Test-Path -LiteralPath $PythonExecutable) { (Resolve-Path -LiteralPath $PythonExecutable).Path } else { $PythonExecutable }
& $PythonExecutable -c "import requests, boto3, serial, dotenv; from zoneinfo import ZoneInfo; ZoneInfo('America/Sao_Paulo')"
if ($LASTEXITCODE -ne 0) { throw 'Python precisa das dependencias de gateway_captura/requirements.txt e tzdata (Windows).' }
$runtime = Get-TestPostgresRuntime -PythonExecutable $PythonExecutable -DownloadBinaries:$DownloadBinaries
$cluster = $null; $server = $null; $smokeExitCode = 1
$environmentNames = @('CLICKLANCE_TEST_BACKEND_URL','CLICKLANCE_TEST_ADMIN_TOKEN','CLICKLANCE_TEST_LOCAL_STORAGE_DIR')
$previousEnvironment = @{}
foreach ($name in $environmentNames) { $previousEnvironment[$name] = [System.Environment]::GetEnvironmentVariable($name, 'Process') }

try {
    Push-Location $backendRoot
    try { & mvn '-q' '-DskipTests' package; if ($LASTEXITCODE -ne 0) { throw 'Falha ao empacotar backend.' } }
    finally { Pop-Location }
    $cluster = New-TestPostgres -RuntimeRoot $runtime
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start(); $apiPort = $listener.LocalEndpoint.Port; $listener.Stop()
    $adminToken = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $jwtSecret = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $storageSecret = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $storageDirectory = Join-Path $cluster.RunRoot 'videos'
    New-Item -ItemType Directory -Path $storageDirectory -Force | Out-Null
    $jar = Join-Path $backendRoot 'target/backend-cloud-0.1.0-SNAPSHOT.jar'
    $java = (Get-Command java -ErrorAction Stop).Source
    $javaArguments = @('-jar', ('"' + $jar + '"'), '--server.address=127.0.0.1', ('--server.port=' + $apiPort), ('--spring.datasource.url=' + $cluster.JdbcUrl), '--spring.datasource.username=clicklance_test', '--spring.datasource.password=', '--spring.datasource.driver-class-name=org.postgresql.Driver', ('--clicklance.security.admin-token=' + $adminToken), ('--clicklance.security.jwt-secret=' + $jwtSecret), '--clicklance.storage.mode=local', ('--clicklance.storage.signing-secret=' + $storageSecret), ('--clicklance.storage.local-directory="' + $storageDirectory + '"'), '--clicklance.purge.enabled=false')
    $server = Start-Process -FilePath $java -ArgumentList $javaArguments -WorkingDirectory $backendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $cluster.RunRoot 'backend.log') -RedirectStandardError (Join-Path $cluster.RunRoot 'backend-error.log')
    $baseUrl = 'http://127.0.0.1:' + $apiPort
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($server.HasExited) { throw ('Backend encerrou antes do healthcheck; logs em ' + $cluster.RunRoot) }
        try {
            $health = Invoke-RestMethod -Uri ($baseUrl + '/actuator/health') -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    if (-not $ready) { throw ('Backend nao ficou pronto; logs em ' + $cluster.RunRoot) }
    $env:CLICKLANCE_TEST_BACKEND_URL = $baseUrl
    $env:CLICKLANCE_TEST_ADMIN_TOKEN = $adminToken
    $env:CLICKLANCE_TEST_LOCAL_STORAGE_DIR = $storageDirectory
    Push-Location $gatewayRoot
    try { & $PythonExecutable -m unittest discover -s tests -p test_backend_contract.py -v; $smokeExitCode = $LASTEXITCODE }
    finally { Pop-Location }
} finally {
    foreach ($name in $environmentNames) { [System.Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], 'Process') }
    try {
        if ($null -ne $server -and -not $server.HasExited) {
            # Only terminate the java instance created above.
            try { $server.Kill() } catch { if (-not $server.HasExited) { throw } }
            if (-not $server.WaitForExit(15000)) { throw 'Backend de teste nao encerrou.' }
        }
    } finally {
        if ($null -ne $cluster) { Stop-TestPostgres -Cluster $cluster -KeepData:($KeepData -or $smokeExitCode -ne 0) }
    }
}
if ($smokeExitCode -ne 0) { throw ('Smoke gateway/backend falhou com codigo ' + $smokeExitCode) }
Write-Output 'Smoke Python -> Spring Boot -> PostgreSQL -> video privado aprovado; processos de teste encerrados.'

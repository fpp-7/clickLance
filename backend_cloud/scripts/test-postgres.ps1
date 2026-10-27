[CmdletBinding()]
param([string]$Tests = '*', [string]$PythonExecutable = 'python', [switch]$DownloadBinaries, [switch]$KeepData)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'postgres-runtime.ps1')
$backendRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtime = Get-TestPostgresRuntime -PythonExecutable $PythonExecutable -DownloadBinaries:$DownloadBinaries
$cluster = $null
$testExitCode = 1
$oldTestUrl = $env:CLICKLANCE_POSTGRES_TEST_URL
$oldTestUser = $env:CLICKLANCE_POSTGRES_TEST_USER
$oldTestPassword = $env:CLICKLANCE_POSTGRES_TEST_PASSWORD
try {
    $cluster = New-TestPostgres -RuntimeRoot $runtime
    $url = $cluster.JdbcUrl
    $env:CLICKLANCE_POSTGRES_TEST_URL = $url
    $env:CLICKLANCE_POSTGRES_TEST_USER = 'clicklance_test'
    $env:CLICKLANCE_POSTGRES_TEST_PASSWORD = ''
    Push-Location $backendRoot
    try {
        & mvn '-q' ("-Dtest=$Tests") ("-Dspring.datasource.url=$url") '-Dspring.datasource.username=clicklance_test' '-Dspring.datasource.password=' '-Dspring.datasource.driver-class-name=org.postgresql.Driver' test
        $testExitCode = $LASTEXITCODE
    } finally { Pop-Location }
} finally {
    $env:CLICKLANCE_POSTGRES_TEST_URL = $oldTestUrl
    $env:CLICKLANCE_POSTGRES_TEST_USER = $oldTestUser
    $env:CLICKLANCE_POSTGRES_TEST_PASSWORD = $oldTestPassword
    if ($null -ne $cluster) { Stop-TestPostgres -Cluster $cluster -KeepData:($KeepData -or $testExitCode -ne 0) }
}
if ($testExitCode -ne 0) { throw ('Testes falharam com codigo ' + $testExitCode) }
Write-Output 'Suite validada contra PostgreSQL real; cluster descartavel encerrado.'

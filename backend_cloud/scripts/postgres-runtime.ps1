# Shared helpers for disposable Windows PostgreSQL test instances. No Windows service is installed.
$ErrorActionPreference = 'Stop'

function Get-TestPostgresRuntime {
    param([string]$PythonExecutable = 'python', [switch]$DownloadBinaries)
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $postgresRoot = Join-Path $repoRoot '.tools/postgres'
    $runtimeRoot = Join-Path $postgresRoot 'runtime'
    if (-not (Test-Path -LiteralPath (Join-Path $runtimeRoot 'bin/initdb.exe'))) {
        if (-not $DownloadBinaries) { throw 'Binarios ausentes. Use -DownloadBinaries (Windows x64, Python 3.10+).' }
        New-Item -ItemType Directory -Path $postgresRoot -Force | Out-Null
        $archive = Join-Path $postgresRoot 'postgres-17.7.0.jar'
        Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/io/zonky/test/postgres/embedded-postgres-binaries-windows-amd64/17.7.0/embedded-postgres-binaries-windows-amd64-17.7.0.jar' -OutFile $archive
        $expectedHash = 'C488D0A4C94B1FCB525E6503C93FC7A0023092AB33762719C7283418F7BDDD15'
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expectedHash) { throw 'Checksum invalido no PostgreSQL de testes.' }
        & $PythonExecutable (Join-Path $PSScriptRoot 'extract_postgres.py') $archive $runtimeRoot | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao extrair PostgreSQL de testes.' }
    }
    return $runtimeRoot
}

function New-TestPostgres {
    param([Parameter(Mandatory = $true)][string]$RuntimeRoot)
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $runRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ('.tools/postgres/runs/' + [guid]::NewGuid().ToString('N'))))
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $cluster = [pscustomobject]@{ RunRoot = $runRoot; DataRoot = (Join-Path $runRoot 'data'); PgCtl = (Join-Path $RuntimeRoot 'bin/pg_ctl.exe'); Port = 0; JdbcUrl = '' }
    try {
        & (Join-Path $RuntimeRoot 'bin/initdb.exe') -D $cluster.DataRoot -U clicklance_test --auth=trust --encoding=UTF8 --no-locale -c 'shared_buffers=16MB' -c 'max_connections=20' | ForEach-Object { Write-Host $_ }
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao inicializar cluster PostgreSQL descartavel.' }
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
        $listener.Start(); $cluster.Port = $listener.LocalEndpoint.Port; $listener.Stop()
        $cluster.JdbcUrl = 'jdbc:postgresql://127.0.0.1:' + $cluster.Port + '/postgres'
        $startArgs = @('-D', ('"' + $cluster.DataRoot + '"'), '-l', ('"' + (Join-Path $runRoot 'postgres.log') + '"'), '-o', ('"-h 127.0.0.1 -p ' + $cluster.Port + ' -c shared_buffers=16MB -c max_connections=20"'), '-w', '-t', '30', 'start')
        # Wait only for pg_ctl. Start-Process -Wait would wait for its server child too.
        $startProcess = Start-Process -FilePath $cluster.PgCtl -ArgumentList $startArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runRoot 'start.log') -RedirectStandardError (Join-Path $runRoot 'start-error.log')
        if (-not $startProcess.WaitForExit(35000) -or $startProcess.ExitCode -ne 0) { throw ('PostgreSQL nao iniciou. Logs em ' + $runRoot) }
        return $cluster
    } catch { Stop-TestPostgres -Cluster $cluster -KeepData; throw }
}

function Stop-TestPostgres {
    param([Parameter(Mandatory = $true)]$Cluster, [switch]$KeepData)
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    $runsRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot '.tools/postgres/runs'))
    $resolvedRun = (Resolve-Path -LiteralPath $Cluster.RunRoot).Path
    if (-not $resolvedRun.StartsWith($runsRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or -not ([System.IO.Path]::GetFileName($resolvedRun) -match '^[a-f0-9]{32}$')) { throw 'Diretorio temporario fora da raiz permitida.' }
    $shutdownOk = $true
    if (Test-Path -LiteralPath (Join-Path $Cluster.DataRoot 'postmaster.pid')) {
        $stopArgs = @('-D', ('"' + $Cluster.DataRoot + '"'), '-m', 'fast', '-w', '-t', '15', 'stop')
        $stopProcess = Start-Process -FilePath $Cluster.PgCtl -ArgumentList $stopArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $resolvedRun 'stop.log') -RedirectStandardError (Join-Path $resolvedRun 'stop-error.log')
        $shutdownOk = $stopProcess.WaitForExit(20000) -and $stopProcess.ExitCode -eq 0
    }
    if (-not $shutdownOk) { Write-Warning ('Cluster de teste precisa de encerramento: ' + $Cluster.DataRoot) }
    if ($shutdownOk -and -not $KeepData) { Remove-Item -LiteralPath $resolvedRun -Recurse -Force }
    else { Write-Host ('Dados e logs de teste preservados em ' + $resolvedRun) }
    if (-not $shutdownOk) { throw 'Falha ao encerrar cluster PostgreSQL da tarefa.' }
}

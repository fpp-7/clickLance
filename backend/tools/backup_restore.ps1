param(
    [Parameter(Mandatory)][ValidateSet('Backup','Restore')][string]$Mode,
    [Parameter(Mandatory)][string]$PgBin,
    [Parameter(Mandatory)][string]$Archive,
    [string]$DestinationDatabase
)
$ErrorActionPreference = 'Stop'
foreach ($key in @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($key,'Process'))) {
        throw "Configure $key no ambiente do processo; nao use segredos em argumentos."
    }
}
$taskArchivePath = [IO.Path]::GetFullPath($Archive)
$taskPreviousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD','Process')
$taskWatch = [Diagnostics.Stopwatch]::StartNew()
try {
    $env:PGPASSWORD = $env:DB_PASSWORD
    $taskConnection = @('-h',$env:DB_HOST,'-p',$env:DB_PORT,'-U',$env:DB_USER,'--no-password')
    if ($Mode -eq 'Backup') {
        if (Test-Path -LiteralPath $taskArchivePath) { throw 'Arquivo de destino ja existe; use outro caminho.' }
        & (Join-Path $PgBin 'pg_dump.exe') @taskConnection --format=custom --no-owner --no-privileges --file $taskArchivePath --dbname $env:DB_NAME
        if ($LASTEXITCODE -ne 0) { throw 'Backup falhou; nao considere o arquivo parcial como recuperavel.' }
        & (Join-Path $PgBin 'pg_restore.exe') --list $taskArchivePath > $null
        if ($LASTEXITCODE -ne 0) { throw 'Arquivo de backup nao possui catalogo legivel.' }
        Write-Output "Backup criado em $taskArchivePath. Proteja o arquivo: inclui hashes, referencias e configuracao restrita."
    } else {
        if (!(Test-Path -LiteralPath $taskArchivePath -PathType Leaf)) { throw 'Arquivo de backup ausente.' }
        if ($DestinationDatabase -notmatch '^[a-z][a-z0-9_]*_restore_[a-z0-9_]+$' -or $DestinationDatabase -eq $env:DB_NAME) {
            throw 'Destino deve ser um banco novo isolado, com nome *_restore_*, diferente da origem.'
        }
        # createdb recusa banco existente. Sem --clean, DROP DATABASE ou sobrescrita.
        & (Join-Path $PgBin 'createdb.exe') @taskConnection --template=template0 --encoding=UTF8 $DestinationDatabase
        if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel criar destino vazio. Nenhum banco existente sera sobrescrito.' }
        & (Join-Path $PgBin 'pg_restore.exe') @taskConnection --exit-on-error --single-transaction --no-owner --no-privileges --dbname $DestinationDatabase $taskArchivePath
        if ($LASTEXITCODE -ne 0) { throw 'Restauracao falhou. Destino permanece isolado; nao iniciar worker nele.' }
        Write-Output "Restauracao concluida em $DestinationDatabase; mantenha PURGE_ENABLED=false e SMTP sem credenciais ate validar."
    }
    $taskWatch.Stop()
    Write-Output ('Duracao: {0:N3} segundos.' -f $taskWatch.Elapsed.TotalSeconds)
} finally {
    [Environment]::SetEnvironmentVariable('PGPASSWORD',$taskPreviousPassword,'Process')
}

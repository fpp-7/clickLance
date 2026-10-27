# ClickLance — Backend Cloud

API Java 21 / Spring Boot 4.1.2 com PostgreSQL, JDBC e migrations Flyway. Implementa as quatro sprints US-01 a US-12: cadastros/configuração, ingestão, catálogo e biblioteca com expiração. O contrato vigente está em [contrato_api_mvp.md](../docs/contrato_api_mvp.md).

O backend usa JDBC com transações e locks explícitos; não usa JPA. A primeira entrega tem um administrador global de operação por `X-Admin-Token`. Os jogadores têm conta global e sessão JWT. Papéis administrativos por empresa ainda não estão implementados.

## Desenvolvimento local

Requisitos: JDK 21, Maven 3.6.3+ e PostgreSQL 15+ (17 usado na validação). Docker é opcional. No PowerShell, dentro desta pasta:

```powershell
$env:DATABASE_PASSWORD = 'local-only-password'
docker compose up -d postgres
mvn spring-boot:run '-Dspring-boot.run.profiles=local'
```

O Docker Desktop precisa estar ativo para o primeiro comando. Pode usar um PostgreSQL existente informando `DATABASE_URL`, `DATABASE_USER` e `DATABASE_PASSWORD` de uma base dedicada. A migration cria o schema; não apontar testes para base de produção.

O perfil `local` limita a API a `127.0.0.1:8080` e usa segredos explícitos de desenvolvimento. O token administrativo nesse perfil é `admin-local-only-01234567890123456789`, salvo se sobrescrito por `ADMIN_TOKEN`. Não publicar esse perfil.

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
$admin = @{ 'X-Admin-Token' = 'admin-local-only-01234567890123456789' }
$empresa = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/empresas -Headers $admin -ContentType application/json -Body '{"nome":"Arena piloto","dias_retencao_padrao":2}'
$quadraBody = @{ empresa_id=$empresa.id; nome='Quadra 1'; duracao_segmento_segundos=10; segmentos_concatenar=12 } | ConvertTo-Json
$quadra = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/quadras -Headers $admin -ContentType application/json -Body $quadraBody
$dispositivoBody = @{ id='disp_piloto'; quadra_id=$quadra.id; descricao='Botão piloto' } | ConvertTo-Json
$dispositivo = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/dispositivos -Headers $admin -ContentType application/json -Body $dispositivoBody
```

O token do dispositivo aparece somente na criação. Guarde-o de forma segura no gateway. No modo local os vídeos devem estar em `data/videos/replays/{quadra_id}/{evento_id}.mp4`; o backend não oferece upload público. Ele verifica o arquivo já colocado nessa pasta e fornece reprodução por link assinado, inclusive suporte a Range. O modo local não conecta o upload S3 do gateway automaticamente; o smoke de integração usa a pasta compartilhada deliberadamente.

## Testes

```powershell
mvn test
```

Por padrão os testes de API usam H2 no modo PostgreSQL, sem câmera ou AWS. A migration também é aplicada nos testes. Para validar contra PostgreSQL real no Windows, sem Docker:

```powershell
.\scripts\test-postgres.ps1 -DownloadBinaries
```

O runner baixa binários de teste pinados e verificados, cria um cluster descartável em `.tools`, roda a suíte contra ele e o encerra no final. Em execuções seguintes omita `-DownloadBinaries`. Consulte os parâmetros do script para selecionar testes ou Python. Não usa nem altera um serviço PostgreSQL existente. `PostgresMigrationTest` é opt-in e só roda com as variáveis definidas por esse runner.

Para o smoke entre o cliente Python, API Java, PostgreSQL e reprodução do MP4, instale também `tzdata` no interpretador Python no Windows e execute, a partir da raiz do repositório:

```powershell
.\backend_cloud\scripts\smoke-gateway.ps1 -PythonExecutable '.\gateway_captura\.venv\Scripts\python.exe'
```

Esse interpretador precisa ter as dependências de `gateway_captura/requirements.txt`, além de FFmpeg/ffprobe no PATH. O runner cria somente serviços locais descartáveis, gera vídeo sintético, usa segredos temporários e encerra seus processos no final. Ele não lê o `.env` do gateway nem usa câmera, serial ou AWS. Acrescente `-DownloadBinaries` se for a primeira execução e os binários PostgreSQL ainda não estiverem presentes.

Os testes cobrem cadastro/credenciais, negativos de autorização, origem de arquivos, reenvio concorrente, horário local, expiração pública, biblioteca entre usuários, JWT, retry de purga e entrega privada de mídia. Resultados e limites estão no [status](../docs/status_projeto.md).

## S3 privado

Sem perfil `local`, o backend exige `DATABASE_PASSWORD`, `ADMIN_TOKEN`, `JWT_SECRET` e `S3_BUCKET_NAME`. Segredos administrativos e de sessão devem ser diferentes, com pelo menos 32 caracteres. Gere valores aleatórios, por exemplo:

```powershell
[Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Execute novamente para cada segredo. Para subir a API pelo Compose, copie `.env.example` para `.env`, preencha os valores e execute `docker compose --profile api up --build`. Para executar pelo Maven, defina as variáveis no processo; o Spring Boot não importa `.env` automaticamente.

O SDK usa sua cadeia padrão de credenciais, incluindo credenciais temporárias com `AWS_SESSION_TOKEN`. A região padrão é `us-east-1`. `S3_ENDPOINT` e `S3_PATH_STYLE` permitem endpoint compatível quando executado diretamente; não foram validados contra um serviço externo nesta entrega.

O bucket deve ser privado, com bloqueio de acesso público. O Edge precisa de upload somente no prefixo da sua quadra. O backend precisa de `GetObject` (incluindo HEAD/reprodução), `DeleteObject` e `GetBucketVersioning` no bucket configurado. O upload deve enviar `Content-Type: video/mp4`. A key é estritamente `replays/{quadra_id}/{evento_id}.mp4`.

Esta versão **recusa purga em bucket com versionamento habilitado ou suspenso**. Ela verifica a configuração e mantém intenção de deleção pendente se não puder confirmar uma deleção física. Não criar lifecycle genérico que apague vídeos salvos. A proteção/backup de vídeos deve ser definida antes de produção; habilitar versionamento exige evoluir a rotina para tratar versões explicitamente.

URLs de reprodução têm TTL padrão de 300 segundos, nunca posterior à expiração pública quando o acesso é público. Para biblioteca, o prazo é renovado a cada consulta autorizada. Um link já emitido é uma capacidade temporária e pode funcionar até seu vencimento. O banco armazena a key, não uma URL pública ou assinada permanente.

## Expiração e operação

O job roda a cada 60 segundos em lotes de 100. Lances salvos viram `EXPIRADO_SALVO`. Lances sem vínculos são reivindicados como `PURGA_PENDENTE` em transação, apagados fora dela e só depois marcados `EXPIRADO_PURGADO`. Falhas usam retry persistido com espera de 60 segundos até uma hora. `PURGE_ENABLED=false` desativa a execução agendada.

O salvamento e a reivindicação de purga usam lock na mesma linha. Listagem e detalhe público verificam o prazo diretamente e não esperam o job. O histórico do lance é mantido após a deleção. Remoção de conta/vínculo ainda não tem endpoint; o job já recolhe expirados salvos sem vínculos caso o último vínculo seja removido por um fluxo futuro.

Há limite inicial de 30 chamadas de cadastro/login por minuto e IP direto, por instância. Em produção, terminar HTTPS em um proxy confiável e complementar limitação/monitoramento ali; a API não confia em `X-Forwarded-For` para esse limite. Recuperação de senha, verificação de e-mail, revogação individual de sessões, gestão administrativa por empresa, auditoria operacional e interface web permanecem no backlog. Nenhum pagamento foi implementado.

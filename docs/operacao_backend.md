# Operação do backend — piloto acompanhado

30/10/2026. Procedimento técnico local; AWS, SMTP real, câmera/botão alvo e
liberação do piloto continuam pendentes. Decisões em
[visão do backend](overview_backend_refinamento.md), contratos em
[OpenAPI](../backend/openapi.yaml), estado por história no
[backlog](sprints_backend.md).

## Configuração e ativação

Siga o README do backend para Java 21, PostgreSQL 17.11, chaves RSA e configuração
externa. V1–V12 são aplicadas por Flyway; não alterar migrations aplicadas.
Jogador usa email sem verificação inicial, sessão separada da administração e
biblioteca de até 100 lances enquanto a conta estiver ativa.

Configure SMTP com `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` e
`MAIL_FROM`. TLS e autenticação vêm habilitados; credenciais ficam somente no
ambiente/arquivo ignorado. Sem host/remetente a recuperação retorna 503 para
qualquer email. Falha de transporte não revela existência de conta: resposta
genérica 202, token não entregue invalidado, erro sem segredo no log. Não houve
envio para terceiros nesta entrega; o teste capturou SMTP em loopback.

O gateway envia saúde apenas com `HEALTH_URL` preenchida. Usa token do dispositivo
e dados locais da captura/fila a cada 60 segundos. O administrador consulta
`GET /api/v1/admin/dispositivos/{id}/saude`; UNKNOWN indica ausência de observação,
STALE indica contato/observação com 180 segundos ou mais. CURRENT não implica
READY. Erros de rede não interrompem aceite nem pipeline.

## Purga e reconciliação

`PURGE_ENABLED=false` é o padrão. Nenhuma rotina de exclusão ou publicação remota
foi ativada nesta entrega. Para homologar, usar bucket privado exclusivo, sem
versionamento, lifecycle concorrente ou backup de vídeo, com escrita imutável do
gateway. A política deve impedir sobrescrita/reintrodução de objetos e mudança
concorrente de versionamento; a verificação da API não substitui IAM.

Habilitar requer `PURGE_ENABLED=true` e `PURGE_AWS_PROFILE` dedicado, diferente
da credencial do gateway. Permissões necessárias: consultar versionamento,
HEAD/GET, listar somente prefixos provisionados e excluir somente os objetos
autorizados; nunca conceder credencial de deleção ao gateway. Recusar bucket
versionado ou suspenso é intencional; o piloto não apaga versões históricas.

As operações são explícitas e autorizadas por empresa:

1. `POST /api/v1/admin/empresas/{id}/purga/reservas`, corpo `{"limit":100}`:
   reserva expirados sem vínculos, coordenando com salvamento e encerramento.
2. `POST /api/v1/admin/empresas/{id}/purga/executar`, mesmo corpo: reivindica
   trabalhos com lease, chama storage fora da transação e confirma ausência antes
   de marcar purgado. Falha mantém retry durável. Queda após delete permite retomada.
3. `POST /api/v1/admin/empresas/{id}/reconciliacao`, corpo `{}`: inventaria apenas
   origens autorizadas; `GET` no mesmo caminho consulta as divergências.

Reconciliação registra ORPHAN_OBJECT, MISSING_OBJECT e PURGED_OBJECT_PRESENT.
É somente diagnóstico; um upload sem registro pode estar em fila offline por
tempo indefinido. Não remover por idade nem recriar eventos com outra identidade.
Para bloqueio emergencial, usar `PATCH /api/v1/admin/lances/{id}/bloqueio` com
`blocked` e razão POLICY/PRIVACY/OPERATIONAL. URLs emitidas antes do bloqueio são
bearer e podem continuar válidas por até cinco minutos; cópias já baixadas não
são recolhidas pelo backend.

## Backup e restauração isolada

Meta inicial: backup diário, RPO 24h e RTO 4h. O operador precisa estabelecer
armazenamento protegido, rotina diária e monitorar falhas; não há agendamento
externo criado por este procedimento. O dump inclui hashes de senha/sessão e
configuração restrita, inclusive possíveis credenciais RTSP. Guardar fora do
repositório, sob acesso restrito e proteção do volume/serviço de backup.

Instalar ferramentas cliente PostgreSQL 17.11 (`pg_dump`, `pg_restore`, `createdb`,
`psql`), disponíveis nos [binários EDB](https://www.enterprisedb.com/download-postgresql-binaries).
Com `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` e `DB_PASSWORD` no ambiente, em
`backend/`:

```powershell
.\tools\backup_restore.ps1 -Mode Backup -PgBin 'C:\ferramentas\pgsql\bin' -Archive 'D:\backups\clicklance-2026-10-30.dump'
.\tools\backup_restore.ps1 -Mode Restore -PgBin 'C:\ferramentas\pgsql\bin' -Archive 'D:\backups\clicklance-2026-10-30.dump' -DestinationDatabase 'clicklance_restore_20261030'
```

O script recusa sobrescrever arquivos ou banco existente. O destino precisa ser
novo, diferente da origem e conter `_restore_` no nome. Não usa DROP, --clean ou
senha em argumentos; restauração usa transação única e falha no primeiro erro.
Mantenha purga e SMTP desativados no destino. Não dar ao ambiente restaurado
permissão de apagar objetos de produção enquanto valida.

Comparar `tools/verify_restore.sql` na origem e no destino; conferir também API:
sessão válida, jogador sem privilégio administrativo, biblioteca do dono versus
outro jogador, origem, bloqueios e trabalho pendente. Inicializar a aplicação
deve validar Flyway sem reaplicar migrations. Antes de liberação real, conferir
amostra de vídeo no storage autorizado e executar retomada controlada do worker.
Dump de banco não recupera bytes de vídeos definitivamente apagados.

## Limites, métricas e ensaio

Os limites iniciais estão em D14. `GET /api/v1/admin/metricas` exige plataforma;
mostra contagens por operação/status e duração acumulada, sem identidade privada.
O backend usa contadores limitados em memória, adequados a uma instância;
reinício zera os contadores. Não confiar cabeçalhos de proxy por padrão. Para
mais instâncias, o controle precisa ser redefinido antes da expansão.

`backend/tools/load_probe.py` recebe JSON via stdin (`base_url`, `access_token`,
`path` de leitura de jogador, `requests`, `concurrency`). Gera status, p50/p95/p99
e duração; nunca imprime token/corpo. Não passar credenciais em argv nem versionar
entrada com token. Use apenas ambiente de ensaio. Essa sonda mede API de leitura;
não mede clique→corte→upload→registro→reprodução.

## Evidências de 30/10/2026

| Verificação | Resultado e limite |
|---|---|
| Maven Wrapper `-B -ntp -Pintegration -Ddebug=false verify` | 103 testes: 81 PostgreSQL/HTTP, 11 regras arquiteturais e 11 unitários; zero falhas/erros/ignorados. |
| Flyway | V1–V12 em banco dedicado novo; reinicialização valida sem reaplicar. V1–V8 preservadas. |
| OpenAPI/gateway | 15 verificações, 31 perfis de captura e 12 respostas HTTP reais (cadastro/auditoria/configuração/ACK/operação) aprovados; clientes originais usados contra API local. |
| Gateway | 73 casos: 72 aprovados na suíte e RTSP inicialmente ignorado; o RTSP foi repetido com MediaMTX local e aprovado. Sem câmera física. |
| Heartbeat | EventStore/DVR/SQLite locais originais enviaram estado sem câmera por HTTP real; sem alterar a fila. |
| Backup/restauração PostgreSQL | 1.002 lances sintéticos, 1 jogador, 1 vínculo, 1 job PENDING e 12 migrations; backup 1,169s, restore 0,607s; contagens iguais, zero origem incoerente/biblioteca quebrada. |
| API restaurada | Sessão preservada; dono acessa salvo expirado; outro jogador e tentativa de administração recusados; conteúdo reservado não é publicado. |
| Sonda de leitura | 60 GETs, 4 concorrentes, base com 1.000 lances vigentes; 60 respostas 200, 0,554s total; p50 30,063ms, p95 109,740ms, p99 111,194ms. Amostra pequena em loopback, sem mídia AWS. |

Logs e relatórios descartáveis em `backend/target/`: `remaining-verification.log`,
Surefire/Failsafe, `gateway-remaining-regression.log`, `gateway-rtsp-regression.log`,
`backup-result.log`, `restore-result.log`, `restore-source.txt`,
`restore-destination.txt`, `restored-http-result.txt`, `local-load-result.json`.

BE-029 permanece parcial até amostra real de mídia/retomada no ambiente alvo;
BE-030 depende de carga representativa, planos de consulta e medição ponta a ponta;
BE-031 depende de AWS, câmera/botão/aparelhos reais, informação aos participantes,
suporte definido e revisão humana de liberação. Não houve deploy nem envio real
de emails nesta execução.

# ClickLance — Gateway de captura

Gateway Windows para **uma camera e uma quadra por instancia**. A serial produz eventos duraveis; um worker gera o clipe e outro o envia ao S3 privado e registra no backend.

## O que foi implementado

- Identidade UUID do evento persistida antes de considerar o clique aceito.
- Fila SQLite com checkpoints `CAPTURED`, `CLIP_READY`, `UPLOADED`, `CONFIRMED`.
- Selecao pela janela original do clique, usando intervalos do manifesto CSV de segmentos finalizados do FFmpeg. O segmento que contem o clique e aguardado, em vez de ser descartado.
- Diretorios exclusivos por sessao, sem sobrescrever o buffer de uma execucao anterior.
- Recorte com reencoding para limitar o clipe ao intervalo escolhido; MP4 `yuv420p` com `faststart` para reproducao web.
- Upload e notificacao separados da geracao local, com retentativas progressivas e sem apagar clipes antes da confirmacao.
- Autenticacao `X-Device-Token`, `evento_id` e chave `replays/{quadraUUID}/{eventoUUID}.mp4` no contrato `/api/v1/replays`.
- Configuracao autenticada no inicio com ultima configuracao valida em cache para indisponibilidade de rede. Respostas 400/401/403/404 no bootstrap impedem inicializacao: cache nao ignora revogacao conhecida.
- Recuperacao do processo FFmpeg apos erro ou manifesto sem progresso; erro preservado no log de cada sessao.
- Reserva dos segmentos de eventos ainda sem clipe, limite de fila, reserva de disco e exclusao de apenas segmentos finalizados elegiveis.
- Cooldown com relogio monotono, leitura serial com timeout bloqueante e bloqueio contra duas instancias na mesma fila.
- Encerramento solicita finalizacao do FFmpeg e aguarda workers; trabalho pendente permanece no SQLite.

## Instalar e iniciar

Requer Windows, Python 3.10+, FFmpeg e ffprobe no PATH, camera DirectShow e Arduino com o firmware do projeto. A pinagem atual do firmware e do Arduino Mega; um Uno exige adaptacao.

```powershell
cd gateway_captura
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
ffmpeg -list_devices true -f dshow -i dummy
python main.py
```

Configure `DISPOSITIVO_ID`, `DEVICE_TOKEN`, `S3_BUCKET_NAME` e as credenciais IAM de **upload somente no prefixo da quadra**. O backend precisa estar acessivel no primeiro inicio para preencher o cache de configuracao. HTTP so e aceito em localhost; use HTTPS para outro host. `AWS_SESSION_TOKEN` e suportado para credenciais temporarias.

Se ja havia `.env` do prototipo, remova o antigo `API_URL=http://localhost:8080/api/replays`, configure `API_BASE_URL=http://localhost:8080/api/v1` e cadastre o dispositivo no backend. A quadra agora vem da autenticacao do dispositivo. Eventos existentes guardam a quadra, dispositivo e janela originais mesmo que a configuracao mude no futuro.

Os limites coincidem com o backend: duracao de 1 a 60 segundos, quantidade de 1 a 300 e produto de no maximo 600 segundos. A duracao e a quantidade sao consumidas do backend no inicio. Uma mudanca exige reiniciar o gateway. Para o piloto, segmentos de 10 segundos x 12 reduzem a espera de fechamento mantendo dois minutos antes do clique, comparados aos padroes antigos de 60 x 2. O clipe tambem inclui `CLIP_AFTER_SECONDS` (padrao 0,2 s) para abranger o instante marcado. O usuario precisa aguardar o aquecimento do buffer antes do primeiro clique aceito.

## Estados e recuperacao

`CAPTURED` conserva uma referencia temporal e reserva o buffer necessario. `CLIP_READY` conserva o clipe local pronto. `UPLOADED` retoma apenas a notificacao HTTP. `CONFIRMED` permite apagar o clipe local; se o processo cair antes da limpeza, a proxima inicializacao a completa.

Repetir o upload apos uma queda entre S3 e o checkpoint usa a mesma chave. Repetir a notificacao usa o mesmo UUID e requer idempotencia do backend. Uma falha nao substitui o evento por outro horario.

Erros HTTP 400, 401, 403, 404, 409 e 422 na entrega geram `BLOCKED_CLIP_READY` ou `BLOCKED_UPLOADED`: o clipe e o diagnostico permanecem locais, sem reenvio infinito. Corrija token/contrato/politica antes de recolocar o evento na fila. Um evento que ja ultrapassou a retencao publica e recusado pelo backend (422); reenvia-lo sem resolver essa politica nao o recupera automaticamente.

```powershell
python manage_events.py
python manage_events.py --requeue UUID_DO_EVENTO
```

Nao apague `state/`, `clips/` ou arquivos reservados para liberar espaco manualmente. Ha limite de eventos pendentes e reserva minima de disco; ao atingir limite, novos cliques nao sao aceitos. Os logs explicitam aceitacao ou recusa. O firmware atual ainda nao recebe confirmacao do gateway no LCD.

## Testes automatizados

```powershell
$env:CLICKLANCE_LOAD_ENV='0'
python -m unittest discover -s tests -p 'test_reliable_gateway.py' -v
```

Os testes nao abrem camera, serial, `.env` ou AWS. Cobrem selecao temporal, segmento ainda aberto, atraso de fila, reinicio/gaps, checkpoints SQLite, limites, falhas de rede, contrato de envio, cache, revogacao, reserva do buffer e bloqueio de instancia. Quando FFmpeg/ffprobe estao disponiveis, tambem geram video sintetico e verificam duracao e correspondencia de um frame do clipe. Sem os executaveis, somente esse teste e pulado.

`test_pipeline.py` permanece um diagnostico interativo **legado** de hardware. Ele replica comandos do prototipo e nao comprova a fidelidade temporal ou recuperacao desta implementacao. Use a suite automatizada acima para regressao; valide camera e instalacao em bancada antes de operar a quadra.

## Limites que exigem validacao no piloto

- O tempo do manifesto FFmpeg e ancorado na criacao do primeiro arquivo da sessao (birth time no Windows), persistida em `origin.json`. Isso remove deslocamentos de fila/reinicio, mas ainda exige medir/calibrar latencia do dispositivo, inicializacao e drift do relogio no hardware real. Nao foi demonstrada precisao absoluta de sincronizacao entre relogio e imagem.
- Janelas que atravessam uma interrupcao ou um segmento irrecuperavel ficam pendentes com erro e preservam dados para inspecao; nao sao preenchidas com cenas de outra sessao. Nao ha recuperacao de frames que nunca foram gravados.
- Manifests e arquivos ainda ativos nao sao tratados como clipes finalizados. Um desligamento abrupto pode danificar o ultimo segmento; segmentacao limita o impacto, nao garante ausencia de corrupcao.
- A reencodificacao melhora o controle das bordas, mas consome CPU. Rede, disco, qualidade optica e capacidade de dois encoders simultaneos precisam de medicao real. Nao ha SLA de cinco minutos validado.
- A fila duravel reduz perda por reinicio, mas depende do disco local. Nao substitui nobreak, backup, monitoramento remoto ou limites comerciais de armazenamento.
- Credenciais e configuracao sao carregadas na inicializacao; nao ha rotacao automatica ou atualizacao remota ao vivo. Revogacao durante operacao bloqueia a entrega quando o servidor responder, mas nao interrompe a gravacao local imediatamente.
- Este modulo nao provisiona AWS nem instala servico Windows. Ha um supervisor de inicio opcional em `start_gateway.ps1`, para uso manual ou cadastro posterior como tarefa do Windows pelo operador.

## Backup e restauracao local

O SQLite usa WAL. Copiar somente `events.sqlite3` durante a operacao pode omitir transacoes presentes em `events.sqlite3-wal`; copiar os tres arquivos separadamente tambem nao garante uma fotografia consistente. Para produzir um snapshot consistente com a API de backup do SQLite, sem sobrescrever um backup existente:

```powershell
python manage_events.py --backup state/backups/events-2026-10-27.sqlite3
```

O snapshot conserva checkpoints e metadados, **nao os arquivos de video**. Para um backup recuperavel do conjunto, pare o gateway e aguarde o encerramento dos workers; crie o snapshot, copie `clips/`, `segments/` (incluindo CSV, `origin.json` e metadados de sessoes) e `state/config.json` para um destino protegido, mantendo a mesma estrutura. Nao inclua `.env` em locais compartilhados; trate credenciais separadamente. A localizacao dos clipes e persistida como caminho absoluto: restaure no mesmo caminho ou planeje migracao explicita antes de reiniciar.

Para restaurar, mantenha o gateway parado, preserve uma copia do estado atual e substitua a fila por um snapshot validado, junto dos arquivos correspondentes. Nao combine um snapshot antigo com WAL/SHM remanescentes de outra versao da fila. Antes de voltar a aceitar cliques, confira `python manage_events.py`, disponibilidade dos clipes/segmentos e a identidade do dispositivo/quadra; execute a suite isolada e um evento de bancada. Agendamento, destino externo, protecao de backup e teste operacional de restauracao precisam ser definidos pelo operador.

## Calibragem do tempo de captura

A suite sintetica verifica recorte e preservacao do horario no software, mas nao mede atraso da camera real. Antes do piloto:

1. Sincronize o relogio do PC e apresente um relogio/contador visivel no campo da camera.
2. Registre cliques em instantes conhecidos, no inicio, meio e fim de segmentos, incluindo reinicio da captura e processamento com fila atrasada.
3. Compare o instante visivel na imagem com o timestamp do evento; meca erro e variacao em varios clipes, na resolucao e FPS que serao usados.
4. Repita apos um periodo longo de operacao para verificar drift. Documente camera, driver, configuracao, amostra e tolerancia aprovada.

`origin.json` ancora a linha temporal ao nascimento do primeiro arquivo da sessao; o atraso de inicializacao, buffering da camera/driver e ajustes abruptos do relogio podem alterar a relacao com a imagem. Nao ha compensacao automatica ou sincronizacao por hardware implementada. Se a tolerancia acordada nao for atingida, mantenha a operacao como bancada e corrija/calibre a origem temporal antes de prometer replay preciso ao cliente.

## Smoke opcional com o backend real

Requer backend **descartavel em localhost**, modo storage local, banco preparado e o diretorio de videos compartilhado dentro deste workspace. O teste cria empresa/quadra/dispositivo, gera apenas video sintetico, usa o metodo HTTP real do gateway duas vezes, confere idempotencia, grade, consulta individual, bytes de reproducao, Range e rejeicao de link adulterado. Ao terminar, desativa a empresa criada e remove o arquivo sintetico; registros de teste permanecem como historico inativo.

```powershell
$env:CLICKLANCE_LOAD_ENV='0'
$env:CLICKLANCE_TEST_BACKEND_URL='http://localhost:8080'
$env:CLICKLANCE_TEST_ADMIN_TOKEN='token-do-backend-descartavel'
$env:CLICKLANCE_TEST_LOCAL_STORAGE_DIR='C:/caminho/do/workspace/backend_cloud/data/videos'
python -m unittest discover -s tests -p 'test_backend_contract.py' -v
```

As tres variaveis precisam corresponder ao servidor local em execucao. Sem elas, o teste e explicitamente pulado. Nao aponte esse smoke para ambiente comercial.

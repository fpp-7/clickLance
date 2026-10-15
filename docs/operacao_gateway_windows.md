# Operação do gateway Windows — versão de desenvolvimento 0.3.0

15/10/2026. Escopo: uma câmera IP, um Mega e uma quadra por processo.
Veja [homologação e pendências](homologacao_2026-10-15.md) antes de instalar no piloto.

## Instalação e provisionamento

1. Instale Python 3.13 e FFmpeg/ffprobe. Nos testes foram usados FFmpeg Essentials
   9.0.1 e Python 3.13. O diretório dos dois executáveis deve estar no PATH.
2. Em `gateway_captura`, crie `.venv`, instale `requirements.txt` e copie
   `.env.example` para `.env`. O arquivo local não deve ser versionado.
3. Preencha `DEVICE_ID` igual ao `DEVICE` do firmware, porta COM correta,
   `DEVICE_TOKEN`, quadra, bucket privado e endpoint `/api/v1/replays`.
   Chaves AWS vazias usam a cadeia de credenciais do SDK; credenciais temporárias
   podem usar `AWS_SESSION_TOKEN`. Não publique o bucket para obter reprodução:
   a autorização e links temporários pertencem ao backend.
4. Informe `CAMERA_SOURCE=rtsp` e o stream confirmado da Intelbras VIP.
   O modelo ainda não foi identificado e nenhuma URL específica é presumida.
   Meça `CAMERA_LATENCY_SECONDS` com marcador visível e clique sincronizado.
   Vazio bloqueia aceitação; não preencher zero por conveniência na câmera real.
5. Defina diretórios separados para `SEGMENTS_DIR` e `OUTPUT_DIR`. Para instalação
   permanente, prefira caminhos absolutos fora do diretório de código atualizado.
6. Atualize o firmware CL1 conforme [guia do botão](../botao_local/README.md).
   Feche o monitor serial da IDE antes de iniciar o gateway.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -X utf8 main.py
```

`main.py` define o diretório de trabalho. Mutexes Windows impedem instância
duplicada da base, porta serial e fonte de câmera. O supervisor reinicia captura
com espera de 4–30 s após falha; uma sessão nova precisa formar novo buffer.
Uma rotina essencial encerrada provoca saída com erro para permitir reinício
pelo Agendador. Ctrl+C solicita encerramento; tarefas persistidas ficam no SQLite.

## Capacidade e estados

Padrões: 120 s antes, 0 s depois, segmentos de 60 s, margem de 100 ms nas bordas,
100 eventos pendentes, 2 GiB livres, buffer de 24 GiB e clipes de 20 GiB.
O bitrate de dimensionamento é 8 Mbit/s, também aplicado como teto VBV do encoder
com buffer de 2 s. A margem de armazenamento usa três vezes o tamanho estimado,
de forma conservadora; o limite de 100 eventos pode ser atingido depois das cotas.

Limpeza por retenção e pressão de disco preserva reservas e o buffer recente.
Se não há espaço recuperável, a captura é pausada e novos cliques são recusados.
Ao liberar capacidade, o supervisor pode reabrir captura; o aquecimento recomeça.
`MAX_PACKET_GAP_SECONDS=0.25` rejeita descontinuidades de timestamps dos pacotes
maiores que esse valor; o encoder mantém timestamps sem duplicar frames para
esconder lacunas. Confirmar FPS e essa tolerância na câmera real.

`ACEITO → AGUARDANDO_VIDEO → CLIPE_PRONTO → UPLOAD_CONFIRMADO → REGISTRO_CONFIRMADO`.
Uma falha preserva a etapa, erro classificado, tentativa e próxima execução.
Espera de rede: 5 s exponencial, teto de 15 min, jitter de até 20%, até dez
tentativas por etapa. Erro permanente ou esgotamento vai para `INTERVENCAO`.
Clipe só é removido após confirmação remota persistida; fontes são liberadas
quando o clipe validado, checksum e caminho estão persistidos.

`Evento aceito` no LCD significa commit local e reserva, não publicação cloud.
Falha posterior da captura pode deixar vídeo incompleto e evento em intervenção;
ele continua rastreável. Nenhum arquivo de outro horário é substituído no replay.

## Diagnóstico e recuperação

```powershell
.\.venv\Scripts\python.exe -X utf8 gatewayctl.py health
.\.venv\Scripts\python.exe -X utf8 gatewayctl.py list
```

`segments/health.json` é atualizado a cada 5 s e inclui data de geração, motivo de
indisponibilidade, sessão, último segmento válido, fila/idade, disco e última
confirmação cloud. Confira a data para não confundir relatório antigo com processo vivo.
`app.log` tem rotação de 5 MiB e três cópias, UTC, versão e identidade/etapa/tentativa.
Erros recentes do FFmpeg são sanitizados no relatório. Não há heartbeat remoto:
integração com um serviço desse tipo continua como dependência externa.

| Motivo/estado | Ação |
|---|---|
| `NOT_PROVISIONED` | Configurar bucket e token antes de aceitar cliques |
| `CAMERA_TIME_NOT_CALIBRATED` | Medir e configurar a latência da câmera |
| `WARMING_UP` | Aguardar cobertura contínua da janela |
| `CAMERA_UNAVAILABLE` | Conferir câmera, rede, erro recente e sessão |
| `INVALID_INPUT_CONFIGURATION` | Conferir tipo da fonte, URL, transporte e parâmetros |
| `CAPACITY` | Conferir espaço, cotas e pendências; preservar trabalho aceito |
| `HTTP_401` / `HTTP_403` | Corrigir credencial/dispositivo antes de recolocar a etapa |
| `REMOTE_CONTENT_CONFLICT` | Investigar divergência de checksum; não sobrescrever objeto |
| `VIDEO_COVERAGE_TIMEOUT` | Investigar lacuna/arquivo incompleto da sessão original |

Para retomar intervenção, pare o gateway, corrija a causa e execute:

```powershell
.\.venv\Scripts\python.exe -X utf8 gatewayctl.py retry EVENTO_ID
```

Depois, inicie novamente. O comando retorna à última etapa registrada e conserva
identidade e artefatos. Não existe descarte automático de evento em intervenção.

## Inicialização automática

Em PowerShell elevado, informe o diretório real de FFmpeg/ffprobe, acessível ao
usuário SYSTEM. A criação da tarefa não foi executada neste computador.

```powershell
.\windows\install-startup.ps1 -FfmpegBinDirectory 'C:\ffmpeg\bin'
Start-ScheduledTask -TaskName ClickLanceGateway
Get-ScheduledTaskInfo -TaskName ClickLanceGateway
```

A tarefa inicia no boot, sem login, com caminho do Python e diretório de trabalho
fixos. Reinicia até três vezes em falha. O comportamento após reboot ainda deve
ser validado no computador de instalação. Para parar, use
`Stop-ScheduledTask -TaskName ClickLanceGateway`; uma parada forçada é recuperada
pelas etapas persistidas. Remoção: `Unregister-ScheduledTask -TaskName ClickLanceGateway`.

## Backup, restauração, atualização e rollback

Pare a tarefa/processo e confirme que não há outra instância. Execute:

```powershell
.\.venv\Scripts\python.exe -X utf8 gatewayctl.py backup 'C:\ClickLanceBackups\2026-10-15'
```

O destino deve ser novo e ficar fora das duas pastas de dados. O comando copia
fontes e clipes e usa a API de backup SQLite para o catálogo. Preserve `.env`
separadamente. Restaure `segments` e `clips` aos mesmos caminhos originais:
referências de mídia no banco são absolutas. Com a instância parada, preserve
as pastas atuais antes de substituí-las; não misture bancos de backups distintos.
Execute `gatewayctl.py list`, inicie e acompanhe retomada/saúde.

Para atualizar: parar, fazer backup, preservar `.env`/diretórios de dados,
atualizar código e dependências, testar sinteticamente e reiniciar. O esquema
local atual é `user_version=1`, com migração aditiva de bases anteriores do
catálogo. Versões com esquema superior são recusadas.

Rollback exige código compatível com esquema 1 e CL1. A versão anterior à
persistência não é um destino seguro para continuar eventos confirmados.
Uma mudança incompatível exige procedimento próprio e backup correspondente;
nunca apagar pendências para viabilizar downgrade. Backup/restauração nos mesmos
caminhos foi verificado automaticamente, mas atualização/reboot no local ainda
exigem bancada.

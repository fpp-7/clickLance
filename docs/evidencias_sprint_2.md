# Sprint 2 — captura IP e catálogo (entrega parcial)

15/10/2026. Windows, Python 3.13, FFmpeg/ffprobe Essentials 9.0.1.
Câmera de produção informada: Intelbras VIP; modelo exato não identificado.

## Atualização GW-05 — seleção temporal

Resultado atual: **35 testes aprovados, sem falhas esperadas ou testes ignorados**.
As contagens e limitações da entrega inicial abaixo são históricas.

O worker exige uma reserva gerada no clique, seleciona por sessão/intervalo e
não consulta segmentos recentes. A reserva SQLite protege segmentos fechados
e o segmento que ainda fechará, durante fila atrasada e espera por cobertura.
Sobrevive à reabertura do catálogo. Lacuna ou ausência de arquivo impede upload
após o prazo configurado; ausência de contexto nunca usa o vídeo mais recente.

O receptor serial captura o contexto antes da fila e mede cooldown por
monotônico. Progresso `out_time_us` do FFmpeg ancora o conteúdo à recepção;
UTC é auditoria. RTSP exige `CAMERA_LATENCY_SECONDS` medido; sem configuração,
o clique é recusado no log. Validar essa compensação e sua variação na rede real
continua pendente. O protocolo serial ainda é o legado, sem ACK de aceitação.

O corte recodifica H.264, sem áudio, com faststart e margem de 100 ms nas bordas
disponíveis. Há lista temporária exclusiva, timeout e validação de decodificação
integral e duração (tolerância de 50 ms). A margem inclui o frame do clique.
Três posições do segmento são verificadas por pixels: marcadores vermelho,
verde e azul conferem tanto o clique como as bordas do conteúdo resultante.

Falha de geração conserva reserva e não faz upload; fontes são liberadas só
depois do clipe validado. Falha de upload/API conserva o clipe local; a remoção
ocorre somente após ambos confirmarem. Não há retentativa persistente ainda.

O diagnóstico gera `replay_corrigido_clique_t5.mp4`: t=2,9–5,1, duração 2,2 s,
usando a lista CSV real do FFmpeg. O clipe legado t=0–4 fica para comparação e
já não representa o algoritmo do worker.

**Limites atuais:** não houve conexão RTSP nem medição com câmera física.
Custo/precisão da janela de 120 s e calibração na câmera real estão pendentes.
As reservas são persistentes, mas a fila/eventos não têm recuperação automática;
reservas com falha permanecem protegidas para reconciliação na Sprint 3.
Não há promessa de aceitação durável nem confirmação nova no firmware.

## Registro histórico da entrega inicial GW-04

## Implementado

- Entrada RTSP configurável, transporte TCP/UDP e timeout de socket em microssegundos.
- Opção DirectShow preservada para testes; entrada sintética independe das duas.
- Recodificação H.264 sem áudio, GOP configurável em frames; FPS da fonte preservado.
- Diretório único por sessão e catálogo SQLite persistente.
- Lista CSV do muxer de segmentos com tempos relativos de início/fim de conteúdo.
- Promoção para CLOSED somente após fechamento no CSV e ffprobe com vídeo e duração coerente.
- Estados WRITING, CLOSED, INCOMPLETE, MISSING e REMOVED.
- Recuperação de sessões conhecidas ao iniciar captura; arquivo sem fechamento fica INCOMPLETE.
- Identificação de lacunas pelo conteúdo e retenção dos fechados sem apagar o segmento em escrita.
- URL autenticada omitida dos logs do gateway e sanitizada no diagnóstico.
- Encerramento via `q` no stdin do FFmpeg, com prazo e kill como fallback.

O tempo UTC de início da sessão é de auditoria, anterior à negociação da câmera.
Ele não é uma âncora validada entre a cena e o clique. O catálogo guarda os
tempos relativos do CSV; ainda não usa esse UTC para selecionar o replay.

## Verificação

```powershell
cd gateway_captura
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p "test_*.py" -v
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py --synthetic
```

23 testes executados: 19 aprovados e 4 falhas esperadas; nenhum ignorado.
A captura real com fonte lavfi gerou três segmentos de 2 s, todos catalogados
como CLOSED, inclusive o último após encerramento. Foi verificado também o
ciclo `start_recording`, com entrada sintética finita substituindo apenas a fonte.
O fim desse processo de teste gera o log esperado de encerramento inesperado,
pois o DVR de produção espera captura contínua.

Verificações adicionais: conexão SQLite reaberta, sessões distintas, arquivo
órfão incompleto, CSV parcial, arquivo removido externamente, lacuna t=2–4,
preservação de arquivo em escrita, configuração RTSP e sanitização de erro/timeout.
O diagnóstico sintético segue gerando e decodificando o clipe de 4 s.

## Limites e próximos passos

- Não houve conexão RTSP com servidor simulado nem com câmera física. Configuração
  e comando de entrada foram testados; isso não homologa a Intelbras VIP.
- O worker ainda usa os segmentos mais recentes. GW-05 deve migrá-lo para
  instante original, com âncora temporal, cobertura, espera limitada e reservas.
- O caminho legado de arquivos planos permanece no diagnóstico; a regressão
  de mistura desse caminho continua como falha esperada. Uma sessão de captura
  nova seleciona somente seus próprios registros fechados.
- As demais falhas esperadas continuam sendo perda do segmento do clique,
  deslocamento por fila e remoção após falha de upload.
- A limpeza ainda não tem reservas de evento; não há promessa de aceitação durável.
- A recuperação considera sessões existentes no SQLite. Diretórios sem registro
  e arquivos planos antigos não são importados por inferência de horário.
- Falta supervisão/reconexão automática e bloqueio de segunda instância (Sprint 5).

Referências usadas: [muxer segment do FFmpeg](https://ffmpeg.org/ffmpeg-formats.html#segment)
e [entrada RTSP](https://ffmpeg.org/ffmpeg-protocols.html#rtsp). O suporte local a
`-timeout` foi conferido em `ffmpeg -hide_banner -h demuxer=rtsp`.

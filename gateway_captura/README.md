# ClickLance — gateway de câmera IP

Gateway Windows 0.3.0 em desenvolvimento: uma câmera/quadra e um Arduino Mega
por instância. Captura RTSP, replay pela janela do clique, aceite local durável,
pipeline recuperável e diagnóstico operacional. A Intelbras VIP real ainda
precisa ser identificada e homologada.

[Resultado da entrega e pendências](../docs/homologacao_2026-10-15.md) ·
[Manual Windows](../docs/operacao_gateway_windows.md) ·
[Protocolo do botão](../docs/contratos_gateway_botao.md) ·
[Contrato cloud](../docs/contrato_cloud_gateway_v1.md)

## Executar

Python 3.13, FFmpeg e ffprobe no PATH. Em `gateway_captura`:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
```

Preencha `.env` antes do uso real: porta COM, `DEVICE_ID`, token, bucket privado,
API e URL RTSP confirmada da câmera. O identificador precisa corresponder ao
firmware. `CAMERA_LATENCY_SECONDS` é uma compensação medida, sem padrão para
RTSP; vazio bloqueia aceitação. Falta de token/bucket gera `NOT_PROVISIONED`.
Para a antiga webcam de teste, use `CAMERA_SOURCE=dshow` e `CAMERA_DEVICE_NAME`.

```powershell
.\.venv\Scripts\python.exe -X utf8 main.py
```

Atualize o [firmware CL1 do Mega](../botao_local/README.md) antes de usar o botão.
O modo de produção não aceita a antiga mensagem de texto. Feche o monitor serial.
Para encerrar no terminal, Ctrl+C. Autostart, saúde e recuperação estão no manual.

## Fluxo de produção

- O DVR cria diretório exclusivo e lista de fechamento por sessão. SQLite
  cataloga conteúdo e distingue arquivos em escrita, fechados, inválidos e ausentes.
- O progresso do FFmpeg ancora conteúdo ao monotônico de recepção. A compensação
  da câmera ainda precisa ser validada na rede real. Lacunas grandes de pacotes
  são rejeitadas e timestamps não são preenchidos com frames duplicados.
- O receptor CL1 identifica dispositivo/sessão/sequência. Evento e reserva são
  gravados no mesmo commit antes de `ACCEPTED`; reenvio retorna o mesmo resultado.
- `PersistentPipeline` consulta a fila SQLite, seleciona a janela original,
  recodifica e valida o clipe, envia ao S3 e registra a API autenticada.
- Fontes só são liberadas com clipe/checksum persistidos. Clipe só é removido
  após registro remoto persistido. Erros têm etapa, backoff e intervenção.
- Supervisão, mutexes, cotas e limpeza protegem pendências e recusam novos eventos
  quando não há capacidade. Rede indisponível não exige descartar trabalho local.

Padrão de replay: 120 s antes, zero depois e margem de 100 ms nas bordas
disponíveis. `SEGMENTS_TO_CONCAT` é legado e não controla a produção. O encoder
usa H.264 sem áudio, com teto VBV configurado por `CAPACITY_BITRATE`. Artefatos
MP4 usam faststart; o custo e a imagem na resolução da quadra estão pendentes.

O banco fica em `SEGMENTS_DIR/catalog.sqlite3` (esquema 1). Configure dados fora
do diretório de código para atualizações. As referências de mídia são absolutas;
restauração precisa preservar os caminhos originais.

## Testar sem câmera

```powershell
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p 'test_*.py' -v
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py --synthetic
.\.venv\Scripts\python.exe -X utf8 demo_recovery.py
```

O diagnóstico físico interativo, quando houver câmera configurada:

```powershell
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py
```

Cada diagnóstico usa diretório temporário exclusivo e imprime o caminho. A
demonstração recuperável usa vídeo real sintético, SQLite e cloud simulada;
exercita ACK perdido, falha de rede e reinício sem publicar em serviços externos.

Para incluir RTSP local, instale o executável oficial do MediaMTX e configure:

```powershell
$env:MEDIAMTX_PATH='C:\ferramentas\mediamtx.exe'
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p 'test_*.py' -v
```

Sem esse executável o teste de rede fica ignorado. Sem FFmpeg/ffprobe os testes
de mídia ficam ignorados. A execução completa registrada tem 67 testes aprovados,
sem falhas esperadas ou ignorados. Compilação do Mega também passou; validação
de câmera/botão físicos, AWS/backend reais e boot no PC alvo continuam pendentes.

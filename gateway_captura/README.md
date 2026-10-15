# 📹 ClickLance — Gateway de Captura (Edge)

> Módulo local (Edge PC) responsável pela gravação contínua, detecção de acionamentos do botão físico, recorte instantâneo de lances e sincronização com a nuvem (AWS S3 & Spring Boot API).

---

## 📌 Visão do Módulo

O **Gateway de Captura** atua como o nó de computação de borda (*Edge*) instalado no computador local de cada quadra esportiva. Ele atende diretamente aos requisitos **RF01, RF02, RF04, RF05, RF06, RNF01, RNF02 e RNF03** definidos no [Documento de Visão do Produto](../docs/documento_visao_produto_replay_quadras_v0_1.pdf):

- **Gravação Ininterrupta (RNF01):** Gravação da webcam/câmera da quadra em buffer local contínuo durante todo o horário de funcionamento.
- **Separação de Buffer e Clipe (RF04/RF05):** O buffer é particionado em segmentos curtos (ex: 60s), permitindo selecionar os últimos minutos para gerar o lance final sem travar a gravação.
- **Velocidade de Processamento (RNF02):** Concatenação ultra-rápida via FFmpeg demuxer sem re-encoding (apenas cópia de streams), gerando o vídeo final em poucos segundos.
- **Tolerância a Ruídos Elétricos:** Debounce de 5 segundos via software para evitar múltiplos cortes acidentais causados pelo botão físico.

---

## 🧵 Arquitetura Multithreading

Para garantir alta disponibilidade e evitar bloqueios na gravação durante operações de I/O pesadas (leitura serial ou upload para nuvem), a aplicação opera com 4 threads simultâneas:

```
                          ┌─────────────────────────────┐
                          │     Main Thread (Serial)    │
                          │   Escuta porta COM Arduino  │
                          └──────────────┬──────────────┘
                                         │ Evento de clique
                                         ▼
┌─────────────────────────┐       ┌──────────────┐       ┌────────────────────────┐
│   Thread 1: DVR Record  │       │  Queue (FIFO)│       │  Thread 2: DVR Cleanup │
│  ffmpeg -f segment (60s)│       └──────┬───────┘       │  Remove segmentos > 6h │
└────────────┬────────────┘              │               └────────────────────────┘
             │                           ▼
             │ Grava              ┌──────────────┐
             ▼ segmentos          │ Thread 3:    │ Concatena segmentos
       [ ./segments/ ] ──────────►│ VideoWorker  ├─────────────► [ ./clips/lance.mp4 ]
                                  └──────┬───────┘
                                         │
                         ┌───────────────┴───────────────┐
                         ▼                               ▼
                 [ AWS S3 Upload ]             [ Webhook POST Cloud API ]
```

1. **Main Thread (`src/serial_listener.py`):** Monitora a porta serial USB do Arduino em loop contínuo. Ao detectar `"Botao foi Pressionado!"`, valida o tempo desde o último clique (*debounce* de 5 segundos) e insere o evento em uma `Queue` thread-safe.
2. **Thread 1 — DVR Record (`src/dvr_manager.py`):** Mantém o processo do FFmpeg ativo capturando o dispositivo de vídeo via DirectShow e fatiando o fluxo em blocos MP4 de 60 segundos com nomenclatura baseada em timestamp.
3. **Thread 2 — DVR Cleanup (`src/dvr_manager.py`):** Rotina periódica que monitora a pasta de segmentos e exclui automaticamente arquivos com idade superior à configurada (`RETENTION_HOURS`), prevenindo o esgotamento do disco rígido.
4. **Thread 3 — Video Processor Worker (`src/video_processor.py`):** Consumidor da fila que extrai os últimos $N$ segmentos gravados, gera o arquivo de manifesto do FFmpeg `concat`, une os vídeos em codec copy (`-c copy`), realiza o upload para o bucket AWS S3 via `boto3` e dispara a notificação HTTP JSON para a API central em Spring Boot.

---

## 🛠️ Requisitos de Sistema

- **Sistema Operacional:** Windows 10/11 (utiliza `dshow` para captura de câmeras USB).
- **Python:** 3.10 ou superior.
- **FFmpeg:** Instalado e configurado no `PATH` do sistema operacional.
- **Hardware:**
  - Câmera USB / Webcam conectada à quadra.
  - Placa Arduino (Uno / Mega) com botão e display LCD (veja [`../botao_local/`](../botao_local/)).

---

## ⚙️ Variáveis de Ambiente (`.env`)

Copie o modelo [`./.env.example`](.env.example) para `.env`:

```powershell
cp .env.example .env
```

| Variável | Padrão | Descrição |
|---|---|---|
| `SERIAL_PORT` | `COM6` | Porta serial atribuída ao Arduino. |
| `BAUD_RATE` | `9600` | Velocidade da comunicação serial com o microcontrolador. |
| `CAMERA_DEVICE_NAME` | `USB Video Device` | Nome exato da câmera no Windows (DirectShow). |
| `SEGMENT_DURATION_SECONDS` | `60` | Duração de cada pedaço de vídeo gravado continuamente. |
| `SEGMENTS_DIR` | `segments` | Diretório local para armazenamento dos blocos temporários. |
| `RETENTION_HOURS` | `6` | Tempo máximo (em horas) de retenção dos segmentos temporários. |
| `SEGMENTS_TO_CONCAT` | `2` | Quantidade de segmentos anteriores ao clique para formar o clipe (ex: 2 × 60s = 2 min). |
| `OUTPUT_DIR` | `clips` | Diretório onde os lances processados são salvos antes do upload. |
| `AWS_ACCESS_KEY_ID` | — | Chave de acesso IAM para upload no S3. |
| `AWS_SECRET_ACCESS_KEY` | — | Segredo de acesso IAM para o S3. |
| `AWS_REGION` | `us-east-1` | Região do bucket AWS. |
| `S3_BUCKET_NAME` | — | Nome do bucket S3 onde os vídeos finais serão armazenados. |
| `API_URL` | `http://localhost:8080/api/replays` | Endpoint da API Cloud para registro do metadado do lance. |
| `QUADRA_ID` | `quadra_001` | Identificador único da quadra neste computador local. |

> 💡 **Como descobrir o nome exato da câmera no Windows:**
> ```powershell
> ffmpeg -list_devices true -f dshow -i dummy
> ```

---

## 🚀 Como Executar

### 1. Instalação das dependências
Crie um ambiente virtual (opcional) e instale os pacotes necessários:
```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

### 2. Execução da Aplicação Principal
```powershell
python main.py
```
O console exibirá os logs de inicialização do DVR, da limpeza de disco, do worker e da escuta serial. Para encerrar de forma limpa, pressione `Ctrl + C`.

---

## Fonte de vídeo de produção

O projeto utilizará câmeras IP. A entrada RTSP está implementada e é o padrão;
DirectShow continua disponível para webcam de teste. Equipamento informado:
Intelbras VIP, modelo exato e stream ainda pendentes de confirmação. Podemos
continuar sem webcam com vídeo sintético; homologação exige câmera IP real.
Veja o [planejamento atualizado](../docs/sprints_gateway_e_botao.md).

No `.env`, configure `CAMERA_SOURCE=rtsp`, `CAMERA_RTSP_URL` com o endereço
confirmado do stream, `CAMERA_RTSP_TRANSPORT=tcp` (ou `udp`) e
`CAMERA_TIMEOUT_SECONDS=10`. Não versionar credenciais. Para webcam de teste,
use `CAMERA_SOURCE=dshow` com `CAMERA_DEVICE_NAME`. O fluxo é recodificado
em H.264, sem áudio; FPS segue a entrada e `KEYFRAME_INTERVAL=30` define o GOP
em frames. O modelo de câmera não é inferido para compor URLs automaticamente.

Cada captura cria `segments/<sessão>/seg_*.mp4`, com lista CSV de fechamento e
catálogo `segments/catalog.sqlite3`. Só segmentos fechados com stream/duração
verificados ficam disponíveis. Os arquivos planos antigos não são importados
para a sessão nova. A seleção temporal e as reservas do replay ainda estão em
desenvolvimento; consulte [evidências da Sprint 2](../docs/evidencias_sprint_2.md).

## 🧪 Diagnóstico e testes automáticos

Execute os testes sem câmera, Arduino ou serviços cloud:

```powershell
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p "test_*.py" -v
```

Os testes de vídeo precisam de `ffmpeg` e `ffprobe` no PATH; sem eles ficam
explicitamente ignorados. Quatro regressões estão marcadas como falhas esperadas
até as correções de GW-04/GW-05/GW-07.

Para gerar vídeo com relógio, frames e marcadores sem câmera:

```powershell
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py --synthetic
```

Para diagnóstico físico interativo da câmera configurada no `.env`:

```powershell
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py
```

A captura dura 6 segundos e usa o comando compartilhado com o DVR, com segmentos
de 2 segundos. Cada execução mantém artefatos em diretório temporário exclusivo,
incluindo `report.json` e stderr do FFmpeg. O script exige captura bem-sucedida,
duração coerente e decodificação integral. A concatenação usa VideoProcessor;
nenhum vídeo é enviado à nuvem.

O modo sintético também gera um replay pelo seletor atual para demonstrar a
exclusão do segmento do clique. Captura válida não significa replay correto.
Este comando substitui o antigo modo combinado com Arduino; o diagnóstico manual
de leitura serial está em `tests/receptor_eventos.py` (ajuste a porta antes de
executar). Captura física, serial física e cloud são validações distintas.

Veja [resultados e limites da Sprint 1](../docs/evidencias_sprint_1.md).

---

## 📋 Status de Implementação e Backlog

Conforme registrado em [`../docs/status_projeto.md`](../docs/status_projeto.md):

### ✅ Concluído no Gateway
- [x] Estrutura modular (`config`, `logger`, `dvr_manager`, `serial_listener`, `video_processor`).
- [x] Gravação segmentada resiliente a corrupções de arquivo.
- [x] Concatenação direta sem perda de qualidade e baixo consumo de CPU.
- [x] Upload S3 via SDK oficial `boto3`.
- [x] Notificação via payload JSON para API Cloud.
- [x] Suite de testes de bancada offline.

### ⏳ Backlog Técnico do Gateway
- [ ] **Fila de Retentativas Offline:** Armazenamento local com SQLite ou fila em disco para reenvio automático caso haja instabilidade de internet na quadra.
- [ ] **Inicialização Automática com o Windows:** Criação de script `.bat` ou serviço de sistema para subir o `main.py` após reinicialização do PC da quadra.
- [ ] **Heartbeat e Telemetria Remota:** Envio periódico de status (CPU, espaço em disco, status da câmera) para monitoramento em tempo real da quadra.

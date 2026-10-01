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

## 🧪 Modo de Diagnóstico Offline (`test_pipeline.py`)

Para validar o hardware local sem precisar da nuvem (sem AWS ou API Spring Boot ativas), execute a suíte de testes do pipeline:

```powershell
python test_pipeline.py
```

O script executará automaticamente:
1. **Verificação de dependências:** Checagem do executável do FFmpeg.
2. **Diagnóstico da Câmera:** Teste de captura curta (3 segundos) para validar DirectShow.
3. **Simulação de DVR:** Gravação de múltiplos segmentos temporários para verificar a lógica de rotação.
4. **Simulação de Acionamento:** Concatenação dos segmentos e geração de um vídeo `lance_teste.mp4`.
5. **Teste de Limpeza:** Validação da rotina de remoção de segmentos expirados.

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
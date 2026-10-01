# ClickLance - Gateway de Captura (Edge)

Este módulo é o "Equipamento Local" (Edge PC) responsável por gravar os jogos de futsal/society e, ao receber um comando de um botão físico (Arduino via USB), recortar a jogada, fazer o upload para a nuvem e notificar o sistema central.

## 🚀 Arquitetura

O sistema é construído em Python e utiliza múltiplas threads para garantir que a gravação, a escuta do botão e o envio para a nuvem ocorram em paralelo sem bloqueios:

- **DVR Manager (Gravação Contínua)**: Grava a webcam continuamente dividindo o vídeo em segmentos curtos (ex: 1 minuto) para otimizar o uso de disco e processamento. Limpa segmentos antigos automaticamente.
- **Serial Listener (Escuta)**: Ouve a porta COM do Arduino. Possui um *debounce* de 5 segundos para evitar múltiplos cliques acidentais (bounce elétrico).
- **Video Processor (Worker)**: Pega os segmentos do momento do clique, concatena (corte do lance), envia o vídeo para a AWS S3 e notifica a API Spring Boot na nuvem.

## 🛠️ Requisitos

1. **Python 3.10+**
2. **FFmpeg**: Precisa estar instalado e acessível no `PATH` do Windows.
3. **Arduino**: Conectado via USB e configurado para enviar "Botao foi Pressionado!" na porta serial (baud 9600).

## ⚙️ Configuração

1. Copie o arquivo `.env.example` para `.env`:
   ```bash
   cp .env.example .env
   ```
2. Descubra o nome da sua Câmera no Windows:
   ```powershell
   ffmpeg -list_devices true -f dshow -i dummy
   ```
3. Edite o `.env` com a porta COM correta, o nome da Câmera, credenciais da AWS e a URL da API Spring Boot.

## 💻 Como Rodar

1. Instale as dependências:
   ```powershell
   pip install -r requirements.txt
   ```
2. Inicie a aplicação principal:
   ```powershell
   python main.py
   ```

## 🧪 Como Testar (Modo Offline)

Se você precisa testar a câmera, gravação e a concatenação dos vídeos sem enviar para a S3 ou API, use o script de teste local:

```powershell
python test_pipeline.py
```
O script testará se o FFmpeg está funcionando, validará a câmera e permitirá que você simule o fluxo do Arduino gerando um arquivo `lance_teste.mp4` para validação local.
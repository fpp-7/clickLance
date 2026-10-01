# Status do Projeto: Gateway de Captura (Edge)

Este documento registra o que já foi implementado no módulo local (Edge) do ClickLance e quais são os próximos passos de todo o ecossistema.

## ✅ O que já foi feito (Concluído)

1. **Estruturação do Projeto Edge**
   - Criação da pasta `gateway_captura` com organização limpa (arquitetura MVC-like).
   - Definição do arquivo `requirements.txt` com as dependências do Python.
   - Configuração dinâmica de variáveis de ambiente via `python-dotenv` (`.env`).

2. **Arquitetura Multithreading em Python**
   - **DVR Manager (`src/dvr_manager.py`)**: Implementado a gravação contínua segmentada. Substituímos a gravação de um arquivo único pesado pelo `ffmpeg -f segment`, que divide o vídeo em pedaços de 60 segundos (HLS like). Isso evita corrompimento de vídeo. Também adicionamos uma rotina de limpeza para liberar espaço no disco após X horas.
   - **Serial Listener (`src/serial_listener.py`)**: Leitor assíncrono do Arduino. Implementamos um sistema de **Debounce de 5 segundos** para evitar cortes duplicados causados por bounce elétrico do botão físico.
   - **Video Processor (`src/video_processor.py`)**: Worker que junta os últimos 2 minutos de vídeo de forma nativa e rápida (sem re-encoding) através do demuxer `concat` do FFmpeg.

3. **Integrações com Cloud**
   - Implementação da classe `S3Uploader` (via `boto3`) preparada para jogar o vídeo para a nuvem da AWS.
   - Implementação da notificação HTTP POST (via `requests`) preparada para alertar a API Cloud (Spring Boot) sobre o novo lance criado.

4. **Ferramenta de Testes**
   - Criação do `test_pipeline.py`, um script autônomo que permite diagnosticar o hardware local (FFmpeg e Webcam) e simular os cliques do botão e os cortes dos vídeos de forma visual, sem necessitar de internet, AWS ou da API ligada.

---

## 🚀 Próximos Passos (Backlog)

### 1. Infraestrutura Cloud (AWS)
- [ ] Criar uma conta/bucket na **AWS S3** dedicado aos lances do ClickLance.
- [ ] Configurar as permissões (Bucket Policy) para que os vídeos fiquem acessíveis de forma pública (ou via CloudFront) para os jogadores assistirem.
- [ ] Gerar as chaves de acesso (IAM `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY`) e configurar no `.env` do PC local.

### 2. Backend Cloud (Java / Spring Boot)
- [ ] Desenvolver a API (`/api/replays`) no Spring Boot para receber o POST com o JSON do gateway.
- [ ] Criar a modelagem do banco de dados (Tabelas: Quadra, Lance, Usuário).
- [ ] Criar o endpoint de listagem de lances para alimentar o aplicativo ou site dos clientes.

### 3. Melhorias no Gateway Edge (Avançado)
- [ ] **Fila de Retentativas Remota**: Se a internet da quadra cair no momento do upload, o Python precisa guardar esse lance numa pasta/tabela local e tentar fazer o upload novamente (Retry) quando a internet voltar.
- [ ] **Script de Auto-Start Windows**: Configurar um bat/serviço no Windows da quadra para garantir que o script `main.py` inicie sozinho caso o computador reinicie ou falte luz.
- [ ] Gerar log rotativo na nuvem (Datadog/CloudWatch) para monitorar remotamente se alguma quadra parou de gravar.

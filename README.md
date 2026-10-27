# ClickLance — Replay de jogadas para quadras

O jogador marca uma jogada pelo botão físico; o gateway recupera o intervalo de vídeo e registra o lance na plataforma. O repositório contém firmware Arduino, gateway Python/FFmpeg e backend Java/Spring Boot.

As quatro sprints do backend (US-01 a US-12) estão implementadas e passaram por testes e revisão. A validação comercial, o funcionamento em uma quadra real e a implantação AWS ainda estão pendentes.

## O que está disponível

- **Sprint 1:** empresas, quadras e dispositivos; cadastros protegidos por segredo administrativo; credencial por dispositivo; configuração de captura consultada pelo Edge.
- **Sprint 2:** ingestão autenticada, origem do arquivo validada e idempotência por identificador estável do evento, inclusive em reenvios simultâneos.
- **Sprint 3:** consulta de quadras e lances por data/horário local, paginação e reprodução temporária de objetos privados.
- **Sprint 4:** cadastro/login de jogadores, JWT, biblioteca pessoal e expiração com purga recuperável, coordenada com o salvamento.
- **Gateway:** eventos persistidos em SQLite; seleção temporal pelo clique; espera por segmentos finalizados; recorte e envio separados; retomada após falhas; supervisão do gravador; cache de configuração e proteção do buffer pendente.

O frontend web e os pagamentos não estão implementados. O MVP atual permite salvar lances gratuitamente; preços e prazo comercial de conservação precisam de decisão antes de produção.

## Estrutura

| Pasta | Responsabilidade |
|---|---|
| `botao_local/` | Firmware de botão/LCD; pinagem atual para Arduino Mega |
| `gateway_captura/` | Captura local, fila persistente, recorte e entrega |
| `backend_cloud/` | API, autenticação, PostgreSQL/Flyway, biblioteca e storage privado |
| `docs/` | Contrato vigente, status, planejamento e revisões |

## Começar

1. Leia o [guia do backend](backend_cloud/README.md) para subir a API e o banco local ou configurar S3 privado.
2. Cadastre empresa, quadra e dispositivo. Guarde o token de dispositivo exibido na criação.
3. Siga o [guia do gateway](gateway_captura/README.md), preencha seu próprio ambiente e sincronize a configuração.
4. Valide câmera, alinhamento temporal, qualidade e recuperação no equipamento real antes de operar com clientes.

Requisitos principais: Java 21, Maven 3.6.3+, PostgreSQL 15+; Python 3.10+, FFmpeg/ffprobe e Windows/DirectShow no gateway. O firmware atual usa pino 22 do Mega; Uno exige adaptação de pinagem.

## Validação

O backend tem testes HTTP, migrations, autorização, concorrência, expiração, storage privado e PostgreSQL real. O gateway tem testes isolados e vídeo sintético processado por FFmpeg, sem abrir câmera ou serial.

Consulte o [status e evidências](docs/status_projeto.md) e a [revisão de cada sprint](docs/revisao_sprints_2026-10-26.md). Testes de software não demonstram qualidade óptica, estabilidade da conexão da arena ou latência comercial em hardware real.

## Documentação

- [Contrato de API vigente](docs/contrato_api_mvp.md): comportamento implementado, exemplos, limites e respostas.
- [Status do projeto](docs/status_projeto.md): entregas verificadas e pendências.
- [Revisão das sprints](docs/revisao_sprints_2026-10-26.md): problemas encontrados e correções.
- [Review de consultoria inicial](docs/review_consultoria_2026-10-13.md): diagnóstico do estado anterior e riscos de produto.
- [Requisitos iniciais](docs/requisitos_e_regras_mvp.md), [histórias](docs/historias_de_usuario_e_endpoints.md) e [modelagem inicial](docs/modelagem_banco_de_dados.md): histórico do planejamento, com divergências superadas pelo contrato vigente e pelas migrations.
- [Visão v0.1 em PDF](docs/documento_visao_produto_replay_quadras_v0_1.pdf): concepção inicial; inclui compra e gestão de partidas que não fazem parte da entrega atual.

Os vídeos são privados. URLs temporárias são geradas após verificar a visibilidade pública ou o vínculo na biblioteca. Segredos ficam fora do Git; o administrador desta entrega é global de operação, com funções por empresa ainda no backlog.

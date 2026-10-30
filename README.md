# ClickLance

Replay de jogadas para quadras de futsal e society. O jogador aciona um botão físico; o gateway recupera a janela de vídeo e envia o clipe para a plataforma.

**Escopo confirmado do primeiro backend:** piloto gratuito com biblioteca pessoal. Pagamentos ficam para uma fase seguinte.

## Estado atual — 30/10/2026

| Módulo | Estado |
|---|---|
| Gateway | Captura IP/RTSP, replay temporal, aceite SQLite, pipeline recuperável, configuração remota, operação Windows e telemetria cloud opt-in implementados. |
| Botão | Protocolo CL1 implementado; firmware compilado para Mega. Validação física pendente. |
| Backend | BE-001–028 implementadas para revisão: administração, ingestão, contas de jogador, busca, mídia autorizada, biblioteca, bloqueio, purga e telemetria. BE-029/030 parcialmente verificadas; BE-031 pendente de homologação real. |
| Plataforma web | Landing, conta de jogador, descoberta, player, biblioteca e administração implementados; demonstração identificada e conexão real via BFF Next/cookies HttpOnly. Ensaio local com Spring/PostgreSQL aprovado; revisão visual e homologação de mídia/hardware pendentes. |
| Piloto | Hardware, AWS/backend reais e jornada completa ainda dependem de homologação. |

Verificação de 30/10: 103 testes Java aprovados, incluindo 81 PostgreSQL/HTTP;
15 verificações de contrato e 73 casos do gateway verificados. Backup/restauração
local preservou direitos e trabalhos pendentes. A sonda HTTP local não substitui
carga representativa ou medição clique→reprodução. Evidências e limites no
[guia operacional do backend](docs/operacao_backend.md); o
[relatório de 15/10](docs/homologacao_2026-10-15.md) permanece como registro histórico.

## Documentação para desenvolvimento

| Documento | Usar para |
|---|---|
| [Visão do backend](docs/overview_backend_refinamento.md) | Escopo, decisões confirmadas, parâmetros do piloto e pendências. Comece aqui. |
| [Catálogo de requisitos](docs/requisitos_e_regras_mvp.md) | RF, RN, RNF e critérios de aceite; propostas identificadas. |
| [Sprints e histórias do backend](docs/sprints_backend.md) | Fila BE-001–031, dependências, recortes e verificações para execução por agentes. |
| [Plataforma web](docs/plataforma_web.md) | Escopo, jornadas, telas, stack proposta e dependências do frontend. |
| [Guia do backend](backend/README.md) | Ambiente local, autenticação, APIs de jogador/administração e verificações. |
| [Operação do backend](docs/operacao_backend.md) | SMTP, telemetria, purga opt-in, backup/restauração, limites e pendências de homologação. |
| [Arquitetura do backend](docs/arquitetura_backend.md) | Pacotes `model`, `controller`, `service`, `repository` e auxiliares; responsabilidades e verificações. |
| [Guia da plataforma web](plataforma_web/README.md) | Execução Next.js, modo mock, integração e limites atuais. |
| [Contrato cloud v1](docs/contrato_cloud_gateway_v1.md) | Integração com o gateway existente. |
| [Modelo de dados](docs/modelagem_banco_de_dados.md) | Invariantes das migrations e coordenação de biblioteca/purga; sem DDL duplicado. |
| [Guia do gateway](gateway_captura/README.md) | Configuração, execução e diagnóstico local. |
| [Operação Windows](docs/operacao_gateway_windows.md) | Instalação, suporte, backup e recuperação do gateway. |
| [Contrato botão/gateway](docs/contratos_gateway_botao.md) | Protocolo CL1, aceite e comportamento temporal. |
| [Guia do botão](botao_local/README.md) | Firmware e hardware. |

## Estrutura

```text
botao_local/        Firmware do botão
gateway_captura/   Captura, processamento e envio locais
backend/           API Spring Boot; administração, jogador, biblioteca e ciclo de vida
plataforma_web/    Interface Next.js; jogador, player, biblioteca, administração e BFF
docs/              Referências atuais e evidências
```

O backend adota **Model–Controller–Service–Repository**, em um único módulo
Maven. As classes ficam diretamente nos pacotes de cada camada, conforme o
[guia de arquitetura](docs/arquitetura_backend.md).
## Visão original e evidências

- [Visão original v0.1](docs/documento_visao_produto_replay_quadras_v0_1.pdf): concepção inicial; não define sozinha o escopo vigente.
- [Homologação de 15/10](docs/homologacao_2026-10-15.md): evidência histórica; pendências atuais no backlog e no guia operacional.
- [Evidências da sprint 1](docs/evidencias_sprint_1.md) e [sprint 2](docs/evidencias_sprint_2.md): registros das verificações intermediárias do gateway.

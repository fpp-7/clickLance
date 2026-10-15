# ClickLance

Replay de jogadas para quadras de futsal e society. O jogador aciona um botão físico; o gateway recupera a janela de vídeo e envia o clipe para a plataforma.

**Escopo confirmado do primeiro backend:** piloto gratuito com biblioteca pessoal. Pagamentos ficam para uma fase seguinte.

## Estado atual — 15/10/2026

| Módulo | Estado |
|---|---|
| Gateway | Captura IP/RTSP, replay temporal, aceite SQLite, pipeline recuperável, configuração remota e operação Windows implementados. |
| Botão | Protocolo CL1 implementado; firmware compilado para Mega. Validação física pendente. |
| Backend | Fundação, autenticação administrativa e empresas implementadas em BE-001–004, entregues para revisão. Integração cloud e demais cadastros pendentes. |
| Frontend | Ainda não implementado. |
| Piloto | Hardware, AWS/backend reais e jornada completa ainda dependem de homologação. |

O [relatório de homologação](docs/homologacao_2026-10-15.md) registra 67 testes aprovados do gateway e as limitações. Isso não comprova backend ou piloto prontos.

## Documentação para desenvolvimento

| Documento | Usar para |
|---|---|
| [Visão do backend](docs/overview_backend_refinamento.md) | Escopo, decisões pendentes e primeira entrega. Comece aqui. |
| [Catálogo de requisitos](docs/requisitos_e_regras_mvp.md) | RF, RN, RNF e critérios de aceite; propostas identificadas. |
| [Sprints e histórias do backend](docs/sprints_backend.md) | Fila BE-001–031, dependências, recortes e verificações para execução por agentes. |
| [Plataforma web](docs/plataforma_web.md) | Escopo, jornadas, telas, stack proposta e dependências do frontend. |
| [Guia do backend](backend/README.md) | Ambiente local, sessões administrativas e provisionamento. |
| [Contrato cloud v1](docs/contrato_cloud_gateway_v1.md) | Integração com o gateway existente. |
| [Modelo de dados](docs/modelagem_banco_de_dados.md) | Invariantes antes das migrations; sem DDL duplicado. |
| [Guia do gateway](gateway_captura/README.md) | Configuração, execução e diagnóstico local. |
| [Operação Windows](docs/operacao_gateway_windows.md) | Instalação, suporte, backup e recuperação do gateway. |
| [Contrato botão/gateway](docs/contratos_gateway_botao.md) | Protocolo CL1, aceite e comportamento temporal. |
| [Guia do botão](botao_local/README.md) | Firmware e hardware. |

## Estrutura

```text
botao_local/        Firmware do botão
gateway_captura/   Captura, processamento e envio locais
backend/           API Spring Boot, autenticação e empresas; evolução cloud em andamento
frontend/          Reservado à plataforma web
docs/              Referências atuais e evidências
```



## Visão original e evidências

- [Visão original v0.1](docs/documento_visao_produto_replay_quadras_v0_1.pdf): concepção inicial; não define sozinha o escopo vigente.
- [Homologação atual](docs/homologacao_2026-10-15.md): entregas verificadas, limites e pendências para o piloto.
- [Evidências da sprint 1](docs/evidencias_sprint_1.md) e [sprint 2](docs/evidencias_sprint_2.md): registros das verificações intermediárias do gateway.

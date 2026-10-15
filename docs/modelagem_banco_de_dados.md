# Backend — diretrizes do modelo de dados

Atualizado em 15/10/2026. Proposta anterior à primeira migration; não há schema cloud executável nesta revisão.

Os comportamentos estão no [catálogo de requisitos](requisitos_e_regras_mvp.md) e as escolhas abertas na [visão do backend](overview_backend_refinamento.md). Este documento contém apenas os invariantes do modelo.

## Recorte inicial proposto

| Conceito | Responsabilidade e restrições |
|---|---|
| Empresa | Raiz operacional, status e política de retenção validada. |
| Quadra | Empresa, nome, fuso IANA efetivo e perfil de captura. |
| Dispositivo | Identificação CL1 de 1–32 caracteres, quadra e credencial própria armazenada como hash. |
| Administração | Identidade autenticada, papel e vínculo com a empresa; bootstrap de administrador global controlado. |
| Lance/evento | Identidade única estável, origem coerente, instante original, instante de recebimento, expiração e política aplicada. |
| Objeto | Bucket, chave e SHA-256; referência privada e autorizada. Um objeto por lance no modelo inicial. |
| Configuração | Versão publicada e parâmetros efetivos compatíveis com v1; separar perfil de quadra e instalação quando necessário. |

## Invariantes antes da primeira migration

- `evento_id` único; repetição não modifica conteúdo, origem ou prazo.
- `(bucket,key)` único no modelo de um objeto por lance.
- Dispositivo, quadra e empresa coerentes no banco e na aplicação. D10 define vínculo imutável ou histórico de alocação.
- `timestamp_evento` e recebimento como instantes; fuso civil definido para consulta por horário.
- Expiração e política aplicada determinadas por D04–D06, sem recalcular silenciosamente eventos existentes.
- URL temporária não é identidade persistida do arquivo.
- Estado de credencial, bloqueio de acesso e existência física do objeto têm significados distintos.

## Entregas seguintes

| Conceito | Invariante a acrescentar quando entrar no escopo |
|---|---|
| Usuário jogador | Conta global, distinta de autorização administrativa; sessão conforme D12. |
| Biblioteca | `UNIQUE(usuario_id,lance_id)`; dono do vínculo e conservação conforme D03. |
| Purga | Intenção durável, tentativas e confirmação remota; coordenada com novos vínculos. |
| Bloqueio/auditoria | Ator, motivo e instante; impedir novo acesso sem declarar arquivo já excluído. |

Quando implementadas, as migrations no backend passam a ser a fonte do schema; este documento permanece conceitual. A primeira migration deve garantir os invariantes acima, incluindo identidade de evento, autorização e tratamento de falhas.

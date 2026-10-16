# Backend — diretrizes do modelo de dados

Atualizado em 16/10/2026. V1 é fundação técnica; V2 identidade/sessão/vínculo administrativo, V3 empresa, V4 quadra/perfil, V5 dispositivo/credencial, V6 auditoria, V7 publicação de configuração e V8 replay/origem/política/objeto. As migrations em `backend/src/main/resources/db/migration/` são a fonte executável desses recortes. As decisões seguem BE-002 e seus schemas OpenAPI.

Os comportamentos estão no [catálogo de requisitos](requisitos_e_regras_mvp.md) e as escolhas abertas na [visão do backend](overview_backend_refinamento.md). Este documento contém apenas os invariantes do modelo.

## Recorte inicial proposto

| Conceito | Responsabilidade e restrições |
|---|---|
| Empresa | Raiz operacional, status e política de retenção validada. |
| Quadra | Empresa, nome, fuso IANA efetivo e perfil de captura. |
| Dispositivo | Identificação CL1 de 1–32 caracteres, quadra e credencial própria armazenada como hash. |
| Administração | Identidade autenticada, papel e vínculo com a empresa; bootstrap de administrador global controlado. |
| Auditoria administrativa | Identidades de ator/recurso/empresa, correlação, ação, resultado, código e instante. Snapshots sem FK para sobreviver à remoção do recurso; sem payload ou credenciais. Sucesso participa do commit da ação; recusas usam transação separada. |
| Lance/evento | Identidade única estável, origem coerente, instante original, instante de recebimento, expiração e política aplicada. |
| Objeto | Bucket, chave e SHA-256; referência privada e autorizada. Um objeto por lance no modelo inicial. |
| Configuração | Versão publicada e parâmetros efetivos compatíveis com v1; separar perfil de quadra e instalação quando necessário. |

## Invariantes das primeiras migrations de domínio

- `evento_id` único; repetição não modifica conteúdo, origem ou prazo.
- `(bucket,key)` único no modelo de um objeto por lance.
- Dispositivo, quadra e empresa coerentes no banco e na aplicação. D10 confirmada: vínculo imutável no piloto, sem transferir histórico.
- Em V5, a empresa do dispositivo é derivada da FK para quadra, cujo vínculo com empresa já é imutável em V4. ID CL1 e quadra do dispositivo também são imutáveis; hash SHA-256 é obrigatório e único. Consultas administrativas não leem o hash; somente o repository de autenticação o utiliza.
- `timestamp_evento` e recebimento como instantes; fuso civil definido para consulta por horário.
- Expiração e snapshot da política aplicada conforme D04–D06 confirmadas em BE-002, sem recalcular eventos existentes no reenvio.
- URL temporária não é identidade persistida do arquivo.
- Estado de credencial, bloqueio de acesso e existência física do objeto têm significados distintos.
- V7 mantém versões com autor/instante e ponteiro atual vinculado ao mesmo dispositivo. Publicação serializa no dispositivo e compara a base; auditoria participa da transação. Parâmetros podem conter credencial RTSP e exigem restrição de acesso ao banco/backups.
- V8 usa FKs compostas dispositivo/quadra e quadra/empresa, chave exata de objeto e unicidade global de evento e bucket/chave. Origem e política são imutáveis por trigger; prazo corresponde ao instante original mais dias de 24h. Chegada vencida é marcada duravelmente.
- Referência verificada inclui tamanho, ETag e VersionId quando disponível. `purged_at` representa exclusão confirmada futura; BE-009–012 não executam deleção. Reenvio não altera esse estado nem exige objeto novamente.

## Entregas seguintes

| Conceito | Invariante a acrescentar quando entrar no escopo |
|---|---|
| Usuário jogador | Conta global, distinta de autorização administrativa; sessão conforme D12. |
| Biblioteca | `UNIQUE(usuario_id,lance_id)`; dono do vínculo e conservação conforme D03. |
| Purga | Intenção durável, tentativas e confirmação remota; coordenada com novos vínculos. |
| Bloqueio/auditoria | Ator, motivo e instante; impedir novo acesso sem declarar arquivo já excluído. |

As migrations no backend são a fonte executável do schema; este documento permanece conceitual. Cada migration de domínio deve garantir os invariantes do seu recorte, incluindo identidade de evento, autorização e tratamento de falhas. V1 é somente fundação técnica e não antecipa entidades.

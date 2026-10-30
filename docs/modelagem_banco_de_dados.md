# Backend — diretrizes do modelo de dados

Atualizado em 30/10/2026. V1 é fundação técnica; V2 identidade/sessão/vínculo administrativo, V3 empresa, V4 quadra/perfil, V5 dispositivo/credencial, V6 auditoria, V7 publicação de configuração e V8 replay/origem/política/objeto. V9 acrescenta jogador/sessão/recuperação, V10 biblioteca/bloqueio/reserva, V11 trabalhos de purga/reconciliação e V12 saúde da instalação. As migrations em `backend/src/main/resources/db/migration/` são a fonte executável do schema; contratos e decisões estão na OpenAPI e na visão do backend.

Os comportamentos estão no [catálogo de requisitos](requisitos_e_regras_mvp.md) e as escolhas abertas na [visão do backend](overview_backend_refinamento.md). Este documento contém apenas os invariantes do modelo.

## Conceitos implementados para revisão

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
| Jogador | Identidade global, email normalizado e sessões próprias, sem privilégio administrativo pelo cadastro. |
| Biblioteca | Vínculo privado entre jogador e replay; até 100 por conta ativa, sem prazo fixo de conservação. |
| Ciclo de vida | Bloqueio e reserva de purga separados da identidade imutável e da existência física do objeto. |
| Trabalho de purga | Intenção, tentativas, lease e confirmação remota duráveis; exclusão não ocorre no request de biblioteca. |
| Reconciliação | Divergência por empresa/objeto/tipo, com primeira e última observação; sem exclusão automática de órfãos. |
| Saúde da instalação | Última observação aceita por dispositivo; contato, captura e fila têm significados distintos. |

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
- Referência verificada inclui tamanho, ETag e VersionId quando disponível. `purged_at` representa exclusão confirmada; BE-009–012 não executam deleção, acrescentada em BE-024 com ativação explícita. Reenvio não altera esse estado nem exige objeto novamente.

## Invariantes acrescentados em V9–V12

| Conceito | Invariante implementado |
|---|---|
| Usuário jogador | `app_user` mantém identidade global; elegibilidade de jogador não concede administração. Encerrada implica inativa. Sessão dura 60 minutos; token de recuperação dura 15 minutos e é protegido por hash/consumo único. |
| Biblioteca | Chave composta de `library_entry` impede duplicar usuário/evento. Cota de 100 é aplicada no serviço sob lock do usuário; salvar/remover/encerrar conta usa ordem usuário → replays. |
| Conservação | Novo vínculo exige acesso vigente; repetição existente não duplica nem renova prazo, inclusive após expiração. Bloqueio/reserva/purga impedem nova emissão de acesso. |
| Ciclo de vida | `replay_lifecycle` preserva autor, motivo, instante de bloqueio e reserva sem alterar os campos imutáveis de V8. Bloqueio não declara arquivo excluído. |
| Purga | Um job por evento; estados, tentativa, lease e conclusão coerentes. Reserva e biblioteca compartilham lock de replay; confirmação exige token de reivindicação válido e exclusão remota confirmada. |
| Reconciliação | Unicidade por empresa/bucket/chave/tipo; repetição atualiza a última observação, sem ampliar escopo nem apagar objeto. |
| Saúde | Uma linha por dispositivo; observação anterior ou repetida não substitui o estado nem renova contato. Ausência/obsolescência é calculada na consulta, separada do estado da captura. |

Regras que dependem de relógio, autorização, cota ou coordenação entre tabelas são
aplicadas pelos services e locks, além das constraints. O worker chama storage
fora das transações de decisão. A política inicial recusa purga em bucket
versionado/suspenso e usa credencial dedicada; detalhes no
[guia operacional](operacao_backend.md). Homologação AWS/hardware e validações
operacionais restantes continuam no [backlog](sprints_backend.md).

As migrations no backend são a fonte executável do schema; este documento permanece conceitual. Cada migration de domínio deve garantir os invariantes do seu recorte, incluindo identidade de evento, autorização e tratamento de falhas. V1 é somente fundação técnica e não antecipa entidades.

# ClickLance — Revisão independente das sprints implementadas

Revisão iniciada em 26/10/2026 e consolidada em 27/10/2026. Escopo: backend cloud, storage privado e integração do gateway disponíveis nesta entrega.

## Parecer

As quatro sprints documentadas foram submetidas a revisão estática conforme suas implementações ficaram disponíveis. Os problemas concretos identificados nessa revisão foram comunicados ao implementador e tiveram suas correções verificadas no código. No estado inspecionado, não foi encontrado bloqueador adicional para a validação local dos fluxos implementados.

Esse resultado se refere ao backend e à integração local. Não comprova prontidão comercial, qualidade óptica na quadra, funcionamento elétrico, permissões reais da AWS ou a meta operacional de disponibilidade dos vídeos. A homologação no local continua necessária.

## Como foi feita a revisão

- Leitura das histórias, regras do MVP e parecer de consultoria de 13/10/2026.
- Inspeção de controllers, DTOs, autorização, validação, SQL, transações, migrations, storage, persistência do gateway e seus testes.
- Revisão independente após cada sprint, incluindo autenticação, hierarquia ativa, identidade do evento, expiração, acesso privado e concorrência entre salvar e purgar.
- Execução independente de `git diff --check`: sem erro de whitespace; houve apenas aviso de normalização LF/CRLF em arquivo existente.
- Inspeção dos relatórios Surefire da execução final conduzida pelo agente principal: **29 testes, nenhuma falha, nenhum erro e nenhum teste ignorado**. Os relatórios incluem os dois testes de `PurgeConcurrencyTest` e quatro de `Sprint2ApiTest`, incluindo repetição com offsets equivalentes e nanossegundos. Foi verificada a propriedade `spring.datasource.url` dos testes de API apontando para PostgreSQL local descartável; não se inferiu validação PostgreSQL apenas a partir do modo de compatibilidade H2.

O revisor não executou a suíte Maven nem os testes Python. Os resultados dessas suítes pertencem às execuções dos agentes implementadores; seus arquivos e evidências foram inspecionados. Os comandos reproduzíveis estão nos READMEs e nos scripts da entrega.

O agente responsável pelo storage também confirmou o smoke **Python → Java → PostgreSQL** concluído com sucesso: um teste de integração em 2,282 segundos, com envio repetido pelo método real de notificação do gateway, consulta ao catálogo, reprodução do MP4 sintético, verificação do conteúdo, resposta Range `206` e recusa de assinatura adulterada. A execução utilizou `backend_cloud/scripts/smoke-gateway.ps1`; o revisor verificou a existência dos logs isolados em `.tools/postgres/runs/45a5417c958149a7a0f898ea9e87ef1c`, o registro de encerramento e a ausência de `postmaster.pid`. Não foram utilizados câmera, Arduino ou AWS nessa integração.

## Sprint 1 — Cadastros e configuração do Edge

**Arquivos principais:** `backend_cloud/.../admin`, `security`, `db/Rows.java` e migration `V1__init_schema.sql`.

Verificado:

- Cadastros e alterações administrativas exigem a credencial administrativa global; JWT de jogador não concede esse papel.
- Segredos de administração e sessão são obrigatórios e distintos; não há senha administrativa padrão embutida para produção.
- Token de dispositivo é aleatório, mostrado no cadastro e armazenado como hash BCrypt.
- Consulta de configuração valida token do próprio dispositivo, dispositivo ativo, quadra ativa e empresa ativa.
- IDs e campos têm validações de formato, existência e limites. Janela de captura é limitada a 600 segundos.
- A FK composta de origem impede associar um lance a uma quadra diferente daquela do dispositivo.

**Ajustes tratados durante a revisão:** diferença entre defaults da API e do schema foi apontada pelo implementador e corrigida; foi recomendada também a constraint do produto duração × quantidade no banco. Os testes de cadastro inspecionados incluem credencial ausente/incorreta, referência inexistente, token cruzado e desativação da hierarquia.

**Limite explícito:** existe um administrador global de operação. Esta implementação não entrega administradores de empresa com papéis e permissões limitadas a uma empresa. A separação de dados por empresa e a origem autenticada não devem ser apresentadas como esse módulo de RBAC.

## Sprint 2 — Ingestão autenticada e idempotência

**Arquivos principais:** `backend_cloud/.../replay`, migration e DTOs de origem.

Verificado:

- Backend resolve a quadra pelo dispositivo autenticado e exige a chave exata `replays/{quadra_id}/{evento_id}.mp4`.
- Identidade idempotente é `(dispositivo_id, evento_id)`, com unicidade no banco. A chave S3 também é única.
- Repetição com o mesmo instante e chave retorna o lance existente; reutilização divergente retorna conflito.
- Timestamp exige offset e é normalizado a microssegundos antes da persistência/comparação.
- A consulta de um evento já registrado ocorre antes de verificar novamente o objeto ou rejeitar um novo evento expirado. Assim, a repetição pode continuar sendo reconhecida após purga do arquivo.
- Lock por dispositivo e constraint impedem duas inserções em retentativas concorrentes.
- Verificação de existência, tipo e tamanho do objeto fica fora da transação de inserção.
- Há validação de desvio futuro e resposta específica para evento novo que já chegou fora da retenção.

**Decisão de contrato:** a proximidade de cinco segundos deixou de ser identidade de uma operação. O cooldown pertence à aceitação do clique no Edge; o backend usa `evento_id` estável. A mudança precisa permanecer documentada para clientes antigos não enviarem o contrato anterior.

**Limite explícito:** o POST não transforma upload e gravação no banco em uma transação única. A retomada do Edge cobre suas etapas persistidas; a reconciliação geral de objetos órfãos existentes fora dessa fila é uma evolução de operação.

## Sprint 3 — Catálogo e reprodução

**Arquivos principais:** `backend_cloud/.../catalog` e módulo `storage`.

Verificado:

- Listagem e detalhe público verificam disponibilidade e prazo, sem esperar a execução do job de expiração.
- Quadra e empresa inativas deixam de oferecer conteúdo publicamente.
- Consulta usa o fuso da quadra, intervalos com início incluído/fim excluído e suporta a passagem de meia-noite.
- Há limites de página/tamanho, ordenação estável e contagem de lances do dia local.
- Resposta pública não expõe chave do objeto e entrega link de reprodução temporário.
- Storage local valida assinatura e validade; testes inspecionados cobrem Range HTTP, assinatura adulterada e ausência do objeto. S3 usa presigned GET com prazo limitado e resposta sem cache público.

**Problemas encontrados e corrigidos:**

1. Acesso salvo passava prazo `null` ao storage, cujo contrato exigia prazo finito; isso causaria erro no detalhe e na biblioteca de vídeos expirados. A chamada passou a fornecer um limite finito recortado pelo TTL do storage.
2. Um item que expirasse entre a consulta e a geração da URL podia derrubar toda a página. A listagem passou a omitir especificamente o item cujo link já expirou.
3. Datas extremas aceitas por `LocalDate` podiam causar exceção ao calcular o dia seguinte. A busca passou a limitar o intervalo de anos.

**Limite explícito:** links temporários funcionam como capacidades compartilháveis até expirar. Não oferecem revogação individual imediata de um link já emitido. A duração curta e a política de cache fazem parte desse compromisso.

## Sprint 4 — Jogadores, biblioteca e purga

**Arquivos principais:** `backend_cloud/.../auth`, `library`, `purge` e `security`.

Verificado:

- E-mail é normalizado antes da persistência; unicidade é garantida no banco.
- Senha é protegida com BCrypt e a limitação considera bytes UTF-8, não apenas número de caracteres.
- Login usa erro uniforme; token adulterado ou expirado é rejeitado. Conta inativa é verificada a cada autenticação de requisição.
- Cadastro/login possuem limitação básica de 30 tentativas por minuto e IP direto, mantida em memória por processo pelo `AttemptLimiter`.
- Biblioteca usa a identidade do JWT, com vínculo único por jogador/lance e resposta idempotente para novo envio do mesmo salvamento.
- Salvar e reivindicar uma purga usam lock no mesmo registro de lance. Após `PURGA_PENDENTE`, um novo vínculo não é aceito.
- Deleção externa ocorre após confirmar a intenção no banco, sem manter transação aberta durante I/O do storage.
- Falha de deleção mantém estado recuperável e próxima tentativa durável; metadados históricos não são apagados.
- Vídeo expirado salvo fica acessível ao jogador vinculado; outro jogador não recebe acesso por conhecer o ID.
- Purga S3 falha de forma conservadora se houver versionamento ou se a resposta indicar marcador de exclusão; não declara bytes fisicamente removidos nesse caso.

**Problemas encontrados e corrigidos:**

1. Lances `EXPIRADO_SALVO` ainda com vínculos podiam ocupar repetidamente o início do lote e impedir a passagem para os demais. O seletor passou a considerar esse estado apenas quando não há vínculos.
2. Falhas permanentes de deleção entre os primeiros pendentes também podiam monopolizar o lote. Foram acrescentados contador e próximo horário de tentativa persistidos, com espera progressiva e revalidação sob lock.

Os testes inspecionados cobrem autenticação, privacidade de expirado salvo, preservação do arquivo salvo, exclusão de órfão registrado, manutenção de metadados e recuperação após erro de deleção. A execução final no PostgreSQL real também aprovou os dois testes específicos de concorrência e passagem de lotes: salvamento iniciado antes da expiração preserva o objeto diante de purga concorrente; lances conservados e falhas adiadas não bloqueiam a deleção de um novo órfão.

## Revisão da integração do gateway

Verificado no código Python:

- Evento aceito é persistido em SQLite com identidade, origem, janela e estado, antes de seguir para processamento.
- Seleção de vídeo usa intervalo original e segmentos finalizados catalogados. Uma janela que cruza interrupção não é silenciosamente concatenada como se fosse contínua.
- Cada sessão tem diretório próprio, evitando reiniciar a numeração sobre o buffer de sessão anterior.
- Enquanto o estado é `CAPTURED`, a rotina de limpeza protege os segmentos necessários; o recorte não precisa bloquear leitura serial durante toda a codificação.
- Geração do clipe e entrega são etapas separadas. Arquivo local é mantido em falha de upload/notificação e removido após confirmação.
- Estado `UPLOADED` retoma a notificação sem repetir upload desnecessariamente.
- Headers, IDs, chave de objeto, timestamp com offset e `ContentType` correspondem ao contrato do backend.
- Configuração é obtida com token e possui cache para falhas de transporte; recusa explícita de credencial não ativa fallback de cache.
- Fila tem limite, eventos bloqueados podem ser inspecionados/reencaminhados e há proteção contra dois processos usando a mesma fila.

**Problema encontrado e corrigido:** os limites de captura divergiam do backend. Configuração aceita na API, como um segundo × 120 segmentos, era rejeitada no bootstrap do gateway. Ambos passaram a usar duração de 1 a 60 segundos, quantidade de 1 a 300 e produto máximo de 600 segundos.

## Limitações e próximos pontos de homologação

1. O piloto permanece em uma câmera/quadra por instância do gateway; o protocolo físico não identifica múltiplos botões/quadras na mesma entrada.
2. O Arduino ainda não recebe confirmação do Edge. A mensagem física de botão pressionado não comprova persistência ou entrega do lance.
3. A ancoragem temporal do DVR depende do relógio e dos timestamps de arquivo no Windows. O teste sintético de recorte não substitui medir essa correspondência na câmera real, inclusive após reinícios e correção de relógio.
4. O manifesto CSV de uma sessão longa cresce enquanto a captura continua. Rotação/compactação desse índice é relevante para operação contínua prolongada.
5. Os testes de S3 revisados usam substitutos do SDK. Credenciais, bucket privado, permissões mínimas, versionamento e reprodução na AWS precisam de homologação própria.
6. A limitação básica de tentativas por processo/IP já foi entregue. Proteção distribuída e configuração confiável de proxy ainda exigem evolução; recuperação de senha, verificação de e-mail, revogação de sessões, rotação de credenciais, observabilidade remota e políticas de conta/retirada não devem ser tratados como já entregues.
7. As sprints implementam os endpoints para o frontend. Interface web de produção, cobrança e regras comerciais não são inferidas como prontas por existir uma API funcional.
8. A política de conservação permanente e suas cotas/custos continua exigindo decisão de produto; a biblioteca técnica não é comprovação de sustentabilidade desse compromisso.

O avanço é substancial em relação ao protótipo original: as garantias de identidade, retomada e autorização passaram a ter implementação e testes direcionados. A aprovação para uso em quadra deve depender de um ensaio real que una clique, relógio da gravação, conteúdo correto, reprodução, queda de internet e reinício, seguido de medição de qualidade e latência.

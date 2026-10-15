# Backend — sprints e histórias de implementação

Atualizado em 15/10/2026. BE-001–004 entregues para revisão; BE-005 em diante aguardam as dependências correspondentes. Piloto gratuito com biblioteca, pagamentos depois.

Este é o único backlog do backend. As histórias referenciam o [catálogo de requisitos](requisitos_e_regras_mvp.md), as [decisões D01–D14](overview_backend_refinamento.md#3-o-que-falta-e-quando-precisa-estar-resolvido), as [diretrizes de dados](modelagem_banco_de_dados.md) e o [contrato cloud v1](contrato_cloud_gateway_v1.md). Não copiar payloads ou DDL para este arquivo. Os IDs `US-01–12` do catálogo são referências anteriores; os IDs executáveis deste plano são `BE-001–031`.

## 1. Como executar com agentes

Uma história por tarefa. Escolher a primeira elegível na ordem abaixo; concluir dependências antes de iniciar a implementação. As sprints são agrupamentos por resultado, sem compromisso de calendário ou velocidade por modelo.

| Estado | Critério |
|---|---|
| Pronta | Dependências concluídas, decisões necessárias registradas e contrato do recorte definido. |
| Aguardando dependência | Uma história anterior ainda não foi concluída. |
| Bloqueada por decisão | Falta uma resposta Dxx ou contrato que altere o comportamento a implementar. |
| Em execução | Um agente é responsável pelo recorte. |
| Em revisão | Implementação e verificações disponíveis para revisão. |
| Concluída | Aceites e verificações passaram; resultado revisado e integrado. |

**Fila atual:** BE-001/002 continuam como base entregue para revisão; BE-003/004 foram executadas sequencialmente por solicitação explícita do responsável, sobre essa base presente no workspace, e estão em revisão com as evidências nas fichas abaixo. BE-005 é o próximo recorte de implementação após revisão/integração das dependências. As demais histórias aguardam implementações e decisões ainda abertas quando indicadas. Não criar endpoints fictícios, bypass de autorização ou políticas silenciosas para declarar uma história concluída.

Cada ficha abaixo define **objetivo**, **dependências/decisões**, **escopo**, **aceite**, **verificação** e **referências**. Os caminhos são relativos à raiz do repositório. A estrutura interna de pacotes e os comandos serão fixados em BE-001; fichas seguintes referem-se a módulos lógicos, sem impor arquivos ainda inexistentes.

Para distribuir o trabalho, usar o seguinte pedido, preenchendo o ID:

```text
Execute somente BE-XXX de docs/sprints_backend.md.
Leia backend/AGENTS.md, a ficha e as referências que ela indica.
Confirme dependências concluídas e decisões necessárias registradas.
Implemente o recorte e seus aceites sem iniciar histórias seguintes.
Entregue arquivos alterados, comandos/resultados de verificação,
limitações e estado proposto da história para revisão.
```

Se uma ficha exigir várias mudanças independentes ou deixar uma escolha de produto aberta, dividi-la ou refiná-la antes de atribuir sua implementação. Testes devem verificar comportamento relevante; arquivos modificados e quantidade de testes não são critério de conclusão.

**Distribuição recomendada:** BE-002 é trabalho de refinamento com o responsável pelo produto/arquitetura. Agentes executores recebem histórias prontas, com esse contexto já resolvido. BE-003,011,018,023,024 exigem revisão cuidadosa de autorização, transação e concorrência. Não escolher um modelo apenas pelo tamanho da alteração; usar o recorte e o risco para definir a revisão necessária. Este plano não dispara agentes nem cria chats automaticamente.

## 2. Visão das sprints

| Sprint | Histórias | Resultado demonstrável | Condição de saída |
|---|---|---|---|
| 1 — Fundação e operação administrativa | BE-001–007 | Aplicação local, administração protegida e origem física cadastrada. | Outro tenant não acessa cadastros ou credenciais; aplicação e migrations reproduzíveis. |
| 2 — Integração com gateway | BE-008–012 | Configuração v1 e registro durável de replay. | Cliente atual aceita configuração/ACK; concorrência e reenvio convergem. |
| 3 — Conta, descoberta e reprodução | BE-013–017 | Jogador encontra e reproduz apenas conteúdo autorizado. | Fuso, expiração, sessão e emissão de acesso demonstrados conforme decisões. |
| 4 — Biblioteca pessoal | BE-018–021 | Salvar, listar, remover e encerrar conta. | Direitos por usuário, conservação e elegibilidade de purga coerentes. |
| 5 — Limpeza e acompanhamento | BE-022–028 | Bloqueio, purga retomável, reconciliação e saúde remota. | Falhas não quebram bibliotecas; instalação e trabalhos pendentes rastreáveis. |
| 6 — Validação operacional do piloto | BE-029–031 | Restauração, carga e homologação real. | Critérios do piloto satisfeitos com evidências e pendências explícitas. |

Sprints posteriores permanecem condicionadas às decisões indicadas. A autorização nasce com os cadastros, a expiração com as consultas e a coordenação de purga com o salvamento, mesmo que o worker seja implementado depois.

## 3. Sprint 1 — Fundação e operação administrativa

### BE-001 — Criar aplicação e ambiente local

- **Objetivo:** como desenvolvedor, iniciar e verificar o backend a partir de um checkout limpo.
- **Depende de:** nenhuma história; não depende de D02/D03. **Estado inicial:** pronta.
- **Estado atual:** em revisão (15/10/2026). Build e integração com PostgreSQL real verificados; execução via Docker Compose não exercitada nesta máquina. Evidências e comandos no README do backend.
- **Escopo:** somente `backend/`: Spring Boot/Maven Wrapper, configuração externa, PostgreSQL local, Flyway e README de execução. Registrar versões fixadas, JDK, pacote raiz e módulos de build antes de expandir o código. Disponibilizar verificação mínima de saúde, sem expor configuração sensível.
- **Aceite:** build e aplicação usam comandos reproduzíveis no Windows; conexão/configuração inválida gera erro acionável; credenciais ficam em variáveis/arquivo local ignorado, com exemplo sem segredos. Incluir uma migration técnica mínima, sem antecipar entidades/políticas de outras histórias. Nenhuma rota de negócio é aberta por conveniência.
- **Verificação:** build do wrapper, inicialização com PostgreSQL real local e execução do Flyway em banco vazio e reinicialização sem reaplicar migration. Documentar pré-requisitos de Java/Docker ou serviço PostgreSQL; indisponibilidade de runtime é limitação explícita, não validação aprovada.
- **Fora do escopo:** cadastros, JWT de jogadores, ingestão, storage, CI externa e deploy.
- **Referências:** RNF-MVP-11; diretrizes de dados; stack prevista na visão do backend.

### BE-002 — Fechar decisões do primeiro recorte e contratos administrativos

- **Objetivo:** como responsável pela implementação, ter comportamento definido para schema, autorização e ingestão antes de codificá-los.
- **Depende de:** nenhuma história. **Estado inicial:** pronta para refinamento; entrega documental.
- **Estado atual:** em revisão (15/10/2026). Produto confirmou D04–D06, D08, D10 e os recortes administrativo de D12 e temporal de D13 na sessão. Escolhas técnicas, papéis, bootstrap, autorização, sessão e verificação do objeto registrados na visão; schemas/erros em `backend/openapi.yaml`. D02/D03 e demais políticas futuras não foram presumidas.
- **Evidência local:** `backend/.venv/Scripts/python.exe -X utf8 backend/tools/validate_contract.py` (a partir da raiz) aprovou 10 verificações, sem ignorados. Estrutura OpenAPI/referências/exemplos, limites da configuração, payload e ACK do cliente real v1 e classificação de erros verificados com HTTP substituído, sem modificar o cliente. Comandos de instalação no README do backend. Isso não valida autorização implementada, integração AWS ou endpoints reais; nenhuma migration ou funcionalidade BE-003 foi criada.
- **Escopo:** atualizar as decisões existentes na visão, sem criar outro registro concorrente. Tratar D04–D06, D08, D10 e D13 para a primeira integração; definir política de autenticação administrativa e seus limites de sessão, relacionada a D12. Separar decisões técnicas justificadas de respostas de produto ainda ausentes. D02/D03 não precisam ser fechadas para iniciar BE-001.
- **Aceite:** limite/default/unidade da retenção, chegada tardia, política aplicada ao evento, origem/alocação, inativação inicial e tolerância de relógio estão determinados ou explicitamente bloqueados. Definir papéis plataforma/empresa, bootstrap e validação da sessão administrativa, inclusive expiração/inativação. Definir schemas e erros dos cadastros/configuração em OpenAPI versionada em `backend/`, sem divergir do contrato v1. Decidir estratégia de verificação do objeto e suas limitações antes de BE-010.
- **Verificação:** conferir coerência entre decisões, catálogo, OpenAPI e cliente v1. Exemplos precisam validar contra o schema quando houver ferramental. Não marcar uma decisão aprovada apenas por constar como recomendação.
- **Conclusão:** respostas ainda pendentes podem ser entregues como refinamento parcial, com dependentes bloqueadas; não declarar o pacote concluído enquanto faltar decisão necessária ao primeiro recorte. O registro de um bloqueio não o resolve.
- **Fora do escopo:** implementar regras ainda sem decisão, checkout ou fechar toda a biblioteca antecipadamente.
- **Referências:** RF-MVP-01–08,15,24; RN-MVP-01,03,06,16,24–30,38; D04–D06,D08,D10,D12,D13.

### BE-003 — Autenticar administração e autorizar por empresa

- **Objetivo:** como operador, administrar somente os recursos permitidos à minha identidade.
- **Depende de:** BE-001 e recorte de autenticação/papéis/sessão de BE-002 resolvido.
- **Estado atual:** em revisão (15/10/2026). JWT RS256 de 60 minutos com sessão durável, hash de jti, revogação, consulta de usuário/vínculos a cada requisição, BCrypt, bootstrap explícito e provisionamento operacional de administrador de empresa implementados em `backend/src/main/java/br/com/clicklance/identity`; migrations V2/V3 sem alterar V1. Nenhum cadastro público ou senha padrão.
- **Evidências:** 7 testes `AdministrativeIdentityIT` aprovados em PostgreSQL/HTTP reais: login/logout, expiração exata, inativação, token adulterado/algoritmo/issuer/audience/sessão inválidos, limite de senha e bootstrap concorrente/idempotente. Teste unitário cobre falha de conexão e de abertura da transação com 503 e sem acesso. Teste de fundação confirma ausência de chaves sem criação de privilégio. JAR executado no perfil bootstrap recusou segredo ausente e aprovou criação/repetição com senha ausente dos logs capturados. Gerador RSA criou o par e recusou sobrescrita.
- **Escopo:** módulos de identidade/autorização, migrations de papel/vínculo, bootstrap controlado de administrador global e contrato administrativo definido em BE-002. Reutilizar a identidade para jogadores depois, sem cadastro público de administradores.
- **Aceite:** visitante não administra; administrador de empresa A não obtém poderes em B; cadastro comum não eleva papel; credenciais/sessões inválidas, expiradas ou de identidade inativa seguem a política definida. Bootstrap repetido não cria administradores duplicados nem usa senha padrão de produção.
- **Verificação:** casos positivos e negativos por papel/empresa, expiração e inativação; aplicação iniciada sem segredo de bootstrap não ganha acesso privilegiado.
- **Fora do escopo:** recuperação de jogador e interfaces públicas.
- **Referências:** RF-MVP-15; RN-MVP-24; RNF-MVP-02,03.

### BE-004 — Cadastrar e consultar empresas autorizadas

- **Objetivo:** como administrador da plataforma, provisionar uma empresa com política válida.
- **Depende de:** BE-003; D04 e contrato de empresa em BE-002.
- **Estado atual:** em revisão (15/10/2026). Criação/consulta/listagem de empresa, retenção validada/default, status explícito, ID estável e isolamento por vínculo entregues. V3 cria empresa e fecha a FK dos vínculos introduzidos em V2. Não há cadastro de quadras/dispositivos ou alterações BE-005 nesta entrega.
- **Evidências:** 6 testes `CompanyIT` aprovados com PostgreSQL/HTTP reais: plataforma cria, tenant não cria nem vê outra empresa, paginação/contagem filtradas, retirada de vínculo/papel tem efeito imediato, entrada inválida não deixa linha parcial, constraints de retenção/FK/unicidade/papel recusam escrita inválida. Provisionamento operacional exige sessão de plataforma e preserva senha/vínculo no reenvio.
- **Comando conjunto:** em `backend/`, com banco de teste dedicado configurado, `mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify`: build aprovado, 16 testes de integração e 1 unitário aprovados, zero ignorados. `backend/.venv/Scripts/python.exe -X utf8 backend/tools/validate_contract.py` (raiz): 10 verificações de contrato aprovadas. PostgreSQL 17.11 temporário real em loopback; migrações V1→V2→V3 e reinicialização sem reaplicação verificadas. Logs locais em `backend/target/be0034-final.log`; relatórios Surefire/Failsafe. Docker Compose, TLS/deploy de produção, frontend e AWS não homologados.
- **Escopo:** entidade/migration de empresa, criação e consulta administrativa conforme OpenAPI; validar retenção e status. Não implementar vitrine pública.
- **Aceite:** empresa recebe identidade estável; retenção inválida não é persistida; só administrador global cria; consultas respeitam escopo autorizado e não retornam segredos. Falhas de validação não deixam registro parcial.
- **Verificação:** criação, consulta por papel e limites de política em PostgreSQL; erro padronizado e transação sem escrita parcial.
- **Fora do escopo:** edição de retenção retroativa, faturamento e exclusão de empresa.
- **Referências:** RF-MVP-01,15; RN-MVP-01,24; RNF-MVP-03.

### BE-005 — Cadastrar quadras e perfil inicial de captura

- **Objetivo:** como administrador da empresa, cadastrar a origem dos lances e o fuso da quadra.
- **Depende de:** BE-004; perfil/validações de BE-002.
- **Escopo:** quadra vinculada à empresa, fuso IANA efetivo, perfil de captura, criação e consulta administrativa. Persistir campos necessários à configuração v1, sem gerar nova interpretação de janela por quantidade de segmentos.
- **Aceite:** empresa A não cadastra quadra em B; empresa inexistente não gera órfão; fuso inválido e parâmetros fora do domínio são recusados; perfil distingue janela antes/depois de segmentação. Dados sensíveis da câmera não aparecem em consultas genéricas.
- **Verificação:** isolamento cruzado, constraints de relacionamento e valores aceitos/rejeitados pelo validador do gateway.
- **Fora do escopo:** publicação/edição versionada do perfil e partidas formais.
- **Referências:** RF-MVP-02; RN-MVP-02,10,11,24,27; RNF-MVP-03.

### BE-006 — Provisionar dispositivo e credencial

- **Objetivo:** como administrador autorizado, vincular um botão à quadra e obter uma credencial própria.
- **Depende de:** BE-005; D10 e política de emissão de BE-002.
- **Escopo:** cadastro/consulta de dispositivo, geração de token seguro, hash no banco e autenticação do dispositivo reutilizável por configuração/ingestão.
- **Aceite:** ID globalmente único e compatível com CL1; token exibido somente na emissão; credencial A não autentica B; vínculo com quadra/empresa coerente conforme D10. Consulta não devolve token ou hash; ausência/erro de credencial não autoriza operação.
- **Verificação:** unicidade e vínculo no PostgreSQL, autorização entre empresas, hash versus segredo e autenticação cruzada. Provisionamento informa como configurar ID da quadra e bucket no gateway: v1 não altera esses campos remotamente.
- **Fora do escopo:** realocação, rotação posterior, mudança de firmware e cadastro multicâmera.
- **Referências:** RF-MVP-03; RN-MVP-03–08,24,29; RNF-MVP-01,03.

### BE-007 — Auditar ações administrativas e padronizar erros

- **Objetivo:** como operador, identificar ator, recurso e resultado de uma ação sem expor credenciais.
- **Depende de:** BE-003–006.
- **Escopo:** registro de auditoria e correlação, formato de erro e consulta administrativa mínima por escopo. Aplicar aos cadastros entregues; histórias seguintes reutilizam o mecanismo.
- **Aceite:** criação de empresa/quadra/dispositivo é rastreável; recusas relevantes têm correlação; token, senha e URL de câmera com credencial não entram em logs/erros. Falha na auditoria de ação crítica não é ocultada como sucesso auditado. Fixar em OpenAPI o formato de erro, sem mudar códigos v1.
- **Verificação:** ação permitida/negada e captura de saída sem segredos; consulta de auditoria de B negada a A.
- **Fora do escopo:** plataforma externa de logs, painéis e telemetria do gateway.
- **Referências:** RF-MVP-19; RN-MVP-24,36; RNF-MVP-07.

## 4. Sprint 2 — Integração com gateway

### BE-008 — Publicar e entregar configuração v1

- **Objetivo:** como gateway, obter configuração válida para meu dispositivo autenticado.
- **Depende de:** BE-005–007; D08 para a resposta de origem inativa.
- **Escopo:** publicação administrativa versionada e `GET /api/v1/dispositivos/{id}/config`; validar as chaves/faixas a partir de `gateway_captura/src/remote_config.py`.
- **Aceite:** raiz contém exatamente `version`, `active`, `settings`; dispositivo não consulta outro; versão publicada tem autor/instante; versão inválida não substitui a válida; segredos só são entregues ao contexto autorizado. Não incluir campos raiz extras. Documentar que omitir chave não a remove do cache e que quadra/bucket exigem provisionamento local no cliente atual.
- **Verificação:** resposta consumida pelo validador/aplicador real do gateway com substitutos locais; revisão do caso inativo sem reescrever configuração dos eventos pendentes.
- **Fora do escopo:** editar dados de eventos já aceitos, armazenar segredos em logs ou mudar o cliente para aceitar contrato divergente.
- **Referências:** RF-MVP-04,24; RN-MVP-11,25,26,38; RNF-MVP-01,11.

### BE-009 — Persistir identidade, origem e política do replay

- **Objetivo:** como sistema, manter um registro único do evento e sua referência de objeto.
- **Depende de:** BE-006; D04–D06,D10 e recorte temporal D13 de BE-002.
- **Escopo:** migration e persistência de evento/lance, origem, instantes, bucket/chave/checksum, expiração e política aplicada. Criar restrições de unicidade de evento/objeto e coerência da origem, e índices necessários ao recorte.
- **Aceite:** banco rejeita duplicação de evento/objeto e origem contraditória; timestamps preservam instante original; política/expiração não mudam no reenvio; chegada já expirada segue D05 sem publicar silenciosamente.
- **Verificação:** inserts conflitantes e simultâneos no PostgreSQL real, round-trip temporal e invariantes da migration. Não usar apenas H2 para comprovar constraints/concorrência de PostgreSQL.
- **Fora do escopo:** endpoint de ingestão, biblioteca ou deleção S3.
- **Referências:** RF-MVP-06–08; RN-MVP-08,09,13–16,20,27–30; RNF-MVP-04,05.

### BE-010 — Validar referência privada do objeto

- **Objetivo:** como sistema, registrar somente objetos autorizados e verificáveis para a origem autenticada.
- **Depende de:** BE-006,009; estratégia de verificação de objeto definida em BE-002.
- **Escopo:** porta de storage e adaptador configurável para consultar o objeto autorizado; validar bucket permitido, chave estável vinculada ao dispositivo/evento, existência e integridade conforme a estratégia escolhida. Desenvolver com substituto local e isolar o adaptador externo.
- **Aceite:** origem A não consulta nem associa objeto B; não seguir URL arbitrária do payload; mismatch é conflito/entrada inválida conforme contrato; dependência transitória gera erro retentável. Não chamar metadata coincidente de prova integral dos bytes ou de validade do vídeo. Definir ausência definitiva versus falha transitória sem usar 404 terminal por conveniência.
- **Verificação:** origem cruzada, checksum divergente, objeto ausente, timeout e permissão negada; verificar chamadas ao storage e ausência de deleção/upload por este recorte.
- **Fora do escopo:** reprodução, purga, transcodificação cloud e provisionamento de conta AWS.
- **Referências:** RF-MVP-05,06,08; RN-MVP-12,20,29,30; RNF-MVP-01,03.

### BE-011 — Registrar replay com confirmação idempotente

- **Objetivo:** como gateway, registrar o mesmo evento quantas vezes for necessário sem criar duplicatas.
- **Depende de:** BE-007–010; D05,D06,D08,D13 aplicáveis resolvidas.
- **Escopo:** `POST /api/v1/replays` e OpenAPI correspondente, respeitando o contrato v1. Autenticar, validar chave/corpo, localizar evento existente antes de exigir objeto novo e confirmar após commit.
- **Aceite:** primeiro registro confirma 201; repetição idêntica confirma 200 com mesma identidade/checksum; mesmo ID com conteúdo imutável divergente retorna 409. `Idempotency-Key` coincide com `evento_id`; quadra declarada coincide com origem autenticada. Corrida entre duas requisições converge pela constraint, sem erro 500 espúrio. Repetição de registro já purgado não recria arquivo/prazo nem falha apenas pela ausência legítima do objeto. Aplicar autorização/inativação definida antes de devolver confirmação.
- **Verificação:** concorrência real no PostgreSQL, divergência de campos, payload inválido, evento próximo com ID distinto, commit com resposta perdida, reenvio após purga simulada e falha transitória do storage. Sucesso nunca antecede commit durável.
- **Fora do escopo:** cooldown cloud de cinco segundos, player, biblioteca e cleanup.
- **Referências:** RF-MVP-05–08; RN-MVP-05,06,08,09,13–16,26–30,36; RNF-MVP-05,11.

### BE-012 — Demonstrar integração com o cliente do gateway

- **Objetivo:** como responsável pela entrega, comprovar configuração e ingestão pelo cliente existente.
- **Depende de:** BE-008,011.
- **Escopo:** cenário local reproduzível com backend real e PostgreSQL, usando `CloudGateway.register` e `RemoteConfig` do gateway. Storage pode ser substituído explicitamente; não exigir câmera/Arduino para este teste. Registrar comandos e resultado no README do backend.
- **Aceite:** cliente aceita config/ACK, repete após resposta perdida com um registro e trata conflito/erro retentável conforme v1. Caminho de produção do gateway não é alterado para contornar incompatibilidade do backend.
- **Verificação:** executar o cenário em processo real e inspecionar quantidade/identidade persistida. Distinguir contrato local aprovado de integração AWS/hardware pendente.
- **Fora do escopo:** piloto aprovado, medidas de imagem e alteração funcional do Edge.
- **Referências:** RF-MVP-04–08; RNF-MVP-05,11; contrato cloud v1.

## 5. Sprint 3 — Conta, descoberta e reprodução

As fichas desta e das próximas sprints precisam dos contratos web em OpenAPI antes da implementação de cada recorte. Acrescentar o contrato na própria história, mantendo as decisões na visão; não iniciar por endpoints inferidos pelo agente.

### BE-013 — Cadastrar e autenticar jogador

- **Objetivo:** como jogador, usar uma conta global sem receber permissões administrativas.
- **Depende de:** BE-003,007; D12 resolvida para login, email, validade e revogação.
- **Escopo:** cadastro/login público, normalização/unicidade de email, hash de senha e mecanismo de sessão definido; reutilizar identidade administrativa sem duplicar a segurança.
- **Aceite:** conta funciona entre arenas; email equivalente não cria duplicata; cadastro não define papel privilegiado; credenciais/sessões inválidas ou conta inativa são recusadas; respostas não contêm senha/hash. Login/logout seguem a política definida, inclusive token previamente emitido.
- **Verificação:** duplicação concorrente de email, sessão expirada/inativa, tentativa de elevação de papel e encerramento de sessão.
- **Fora do escopo:** login social, cobrança, player e biblioteca.
- **Referências:** RF-MVP-11; RN-MVP-24; RNF-MVP-02,09.

### BE-014 — Recuperar conta e redefinir senha

- **Objetivo:** como jogador, recuperar acesso sem reutilizar um segredo vencido ou já consumido.
- **Depende de:** BE-013; D12 e contrato do canal de recuperação definidos.
- **Escopo:** solicitação de recuperação, token protegido em repouso, validade/uso único e redefinição; integrar canal por adaptador, usando capturador local em desenvolvimento.
- **Aceite:** token expirado, reutilizado ou de outra conta não redefine senha; duas requisições concorrentes não consomem o mesmo token com sucesso duas vezes; sessão anterior segue regra de revogação aprovada. Resposta não expõe existência de conta ou token de recuperação em logs.
- **Verificação:** cenários de validade, concorrência, indisponibilidade do canal e invalidação aplicável da sessão.
- **Fora do escopo:** envio real a terceiros durante testes sem autorização explícita.
- **Referências:** RF-MVP-16; RNF-MVP-02,09.

### BE-015 — Descobrir arena, quadras e disponibilidade

- **Objetivo:** como jogador, encontrar a quadra e os horários que têm lances acessíveis.
- **Depende de:** BE-005,011,013; D02,D13 e contrato de navegação definidos.
- **Escopo:** identificação navegável da arena, catálogo de quadras e datas/slots disponíveis. Contrato público ou autenticado conforme D02, sem expor configuração operacional.
- **Aceite:** não exige conhecer UUID previamente; recursos inativos e contagens seguem política de acesso; “hoje” usa fuso da quadra; contagem e lista aplicam o mesmo prazo e bloqueios.
- **Verificação:** arena sem conteúdo, tenant incorreto, inativação, virada de dia e cron de expiração parado.
- **Fora do escopo:** domínios personalizados, busca geográfica e gestão de partidas.
- **Referências:** RF-MVP-02,23; RN-MVP-02,10,17,26,27,31,37; RNF-MVP-03,04.

### BE-016 — Consultar lances por data e faixa horária

- **Objetivo:** como jogador, localizar lances no horário local em que joguei.
- **Depende de:** BE-011,015; D02,D13.
- **Escopo:** listagem paginada por quadra/data/intervalo, OpenAPI única e ordenação estável. Sem horas, retornar dia local paginado; com apenas um limite, rejeitar entrada inválida.
- **Aceite:** intervalos `[início,fim)` sem duplicar bordas; slot final termina na meia-noite seguinte; eventos UTC aparecem no dia/fuso corretos; paginação não duplica itens com mesmo timestamp; expirado/bloqueado não entra mesmo se o cron estiver parado.
- **Verificação:** bordas adjacentes, virada de dia, offset, filtro inválido, falta de permissão e paginação com timestamps iguais.
- **Fora do escopo:** abrir/encerrar partida e retornar URL permanente de mídia.
- **Referências:** RF-MVP-09; RN-MVP-09,10,17,27,31,37; RNF-MVP-04.

### BE-017 — Consultar detalhe e emitir acesso temporário ao vídeo

- **Objetivo:** como usuário autorizado, reproduzir um lance sem tornar o objeto público.
- **Depende de:** BE-010,013,016; D02,D07 e contrato de emissão de acesso definidos.
- **Escopo:** detalhe do lance e autorização de reprodução; download só se D07 incluir. Criar política de acesso reutilizável pela biblioteca, com direito privado acrescentado por BE-018/019.
- **Aceite:** detalhe e mídia respeitam expiração/bloqueio e contexto autorizado; identificador conhecido não concede acesso; link público não dura além do prazo público restante; endereço assinado não é persistido como identidade do objeto. Definir comportamento de recurso inexistente/inacessível e limite de reutilização de link já emitido.
- **Verificação:** acesso permitido/negado, relógio controlado no limite da expiração, objeto privado e TTL. O caso expirado salvo por A versus B será obrigatório também em BE-019.
- **Fora do escopo:** concessão de biblioteca ainda inexistente, transcodificação, watermark ou revogação impossível de downloads concluídos.
- **Referências:** RF-MVP-10,17; RN-MVP-17,19,23,31; RNF-MVP-03.

## 6. Sprint 4 — Biblioteca pessoal

### BE-018 — Salvar lance com vínculo único e conservação definida

- **Objetivo:** como jogador, adicionar um lance autorizado à minha biblioteca.
- **Depende de:** BE-013,017; D02,D03 e semântica da coordenação com purga definidas.
- **Escopo:** migration do vínculo, salvamento, cota/prazo aprovados e ponto único de coordenação da elegibilidade para purga, reutilizado por BE-023.
- **Aceite:** novo vínculo só antes da expiração e com autorização; repetição de vínculo existente retorna sucesso sem nova cota/linha, inclusive após expiração. Constraint garante unicidade; teste concorrente prova que reservar purga e salvar não confirmam estados incompatíveis. Não adiar essa garantia até implementar o worker.
- **Verificação:** dois salvamentos simultâneos, limite de cota, fronteira de expiração, outro usuário e disputa com intenção de purga persistida.
- **Fora do escopo:** “para sempre” sem política, compra e worker de deleção.
- **Referências:** RF-MVP-12; RN-MVP-19,21–23,31,32,34; RNF-MVP-10.

### BE-019 — Listar biblioteca e autorizar acesso privado

- **Objetivo:** como jogador, rever meus lances salvos após saírem da vitrine.
- **Depende de:** BE-017,018; D03.
- **Escopo:** consulta paginada por dono, ordenação estável e integração do vínculo válido com a autorização de mídia/detalhe.
- **Aceite:** biblioteca A não é listada por B; expirado salvo por A permite novo acesso para A, mas não para B; conservação vencida ou bloqueio impede acesso conforme a política; listagem não entrega link permanente.
- **Verificação:** matriz visitante/A/B no detalhe e mídia, expiração pública com vínculo válido e paginação de vínculos com mesmo instante.
- **Fora do escopo:** tornar privado um arquivo público apenas ocultando a linha da grade.
- **Referências:** RF-MVP-13,17; RN-MVP-19,23,31,34; RNF-MVP-03,04.

### BE-020 — Remover lance da própria biblioteca

- **Objetivo:** como jogador, desfazer um vínculo sem afetar a biblioteca de outra pessoa.
- **Depende de:** BE-018,019; D03 e política de remoção definidas.
- **Escopo:** remoção idempotente do próprio vínculo e reavaliação durável da conservação/elegibilidade de purga; ainda sem chamada de deleção remota.
- **Aceite:** usuário não remove vínculo de outro; segundo usuário preserva acesso; último vínculo de expirado sinaliza trabalho futuro de purga; repetição não gera efeito adicional; corrida com salvamento segue a coordenação de BE-018.
- **Verificação:** remoção repetida, um/dois donos, último vínculo de expirado e concorrência.
- **Fora do escopo:** apagar objeto diretamente no request.
- **Referências:** RF-MVP-21; RN-MVP-32–34; RNF-MVP-10.

### BE-021 — Solicitar e executar encerramento da conta

- **Objetivo:** como jogador, encerrar a conta conforme a política aprovada.
- **Depende de:** BE-014,020; D03,D11,D12 e regras de dados/auditoria aprovadas.
- **Escopo:** solicitação autenticada, estado de execução retomável, revogação de acesso e tratamento de vínculos pelo mecanismo existente; dados retidos/anonimizados conforme D11.
- **Aceite:** não apaga vídeo de outro usuário; falha intermediária permite retomada; sessões da conta encerrada não continuam autorizadas; metadados preservados seguem política e não são apresentados como exclusão total de todos os dados.
- **Verificação:** interrupção durante execução, conta com vínculos compartilhados, repetição de solicitação e acesso após encerramento.
- **Fora do escopo:** decidir prazos ou obrigações jurídicas pelo agente.
- **Referências:** RF-MVP-21; RN-MVP-26,33–35; RNF-MVP-02,12.

## 7. Sprint 5 — Limpeza e acompanhamento

### BE-022 — Bloquear ou retirar lance de todas as superfícies

- **Objetivo:** como operador autorizado, suspender a distribuição de um conteúdo com rastreabilidade.
- **Depende de:** BE-007,017,019; D11 e contrato de bloqueio definidos.
- **Escopo:** bloqueio separado de existência física do objeto; autor/motivo/instante; aplicação na descoberta, contagem, lista, detalhe e nova emissão de mídia, inclusive bibliotecas.
- **Aceite:** apenas papel autorizado bloqueia; todos os vínculos respeitam suspensão; ação não declara arquivo purgado sem exclusão. Explicitar limite de links emitidos e downloads prévios.
- **Verificação:** todas as superfícies com visitante/A/B, inclusive contagens, e isolamento entre empresas.
- **Fora do escopo:** prometer revogação de cópia já baixada ou apagar sem política D09/D11.
- **Referências:** RF-MVP-20; RN-MVP-31,35; RNF-MVP-12.

### BE-023 — Reservar trabalho durável de purga

- **Objetivo:** como sistema, separar elegibilidade de exclusão do efeito remoto.
- **Depende de:** BE-018,020,022; D03,D09,D11.
- **Escopo:** estado/intenção de purga, seleção em lotes e reivindicação de trabalho por worker; reutilizar a coordenação introduzida em BE-018 e a sinalização de BE-020.
- **Aceite:** lance expirado com direito de conservação não é elegível; expirado sem direito tem trabalho durável; dois workers não reclamam trabalho de forma incompatível; após reserva de purga não surge vínculo novo confirmado. Chamadas S3 não ficam dentro do lock/transação de decisão.
- **Verificação:** salvar versus reservar, dois workers, remoção do último vínculo e reinício entre seleção/claim/commit.
- **Fora do escopo:** delete remoto ou status final de purgado antes da confirmação.
- **Referências:** RF-MVP-14; RN-MVP-18,19,32–34; RNF-MVP-10.

### BE-024 — Executar e confirmar exclusão remota com retomada

- **Objetivo:** como sistema, concluir o trabalho de purga sem perder o controle após falhas.
- **Depende de:** BE-010,023; D09, política de versões e destino da auditoria definidos.
- **Escopo:** worker, retentativas/limites, confirmação e tratamento de objeto já ausente segundo D09; credencial de deleção separada do Edge.
- **Aceite:** só marca purgado após confirmar a exclusão prevista na política; falha remota permanece pendente; queda após delete e antes do update converge na retomada; versão/lifecycle/backups não são ignorados ao declarar exclusão física ou economia.
- **Verificação:** timeout, ausência já reconciliada, permissão negada, crash entre efeitos e dois workers. Validar adaptador real em homologação antes de ligar limpeza automática sobre dados reais.
- **Fora do escopo:** exclusão de prefixos arbitrários e credencial de delete no gateway.
- **Referências:** RF-MVP-14; RN-MVP-18,20,33,36; RNF-MVP-10.

### BE-025 — Reconciliar objetos e registros divergentes

- **Objetivo:** como operador, encontrar uploads sem registro e registros sem objeto sem apagar trabalho legítimo atrasado.
- **Depende de:** BE-011,024; D05,D08,D09 e janela segura de reconciliação definida.
- **Escopo:** inventário/detecção, registro durável de divergência e ação conservadora definida; proteção de objetos de eventos offline ou em intervenção. Se o contrato atual não comprovar elegibilidade, reportar para intervenção em vez de excluir automaticamente.
- **Aceite:** upload sem POST ainda em atraso não é considerado lixo apenas pela ausência de linha; registro sem objeto é rastreado sem repetir ACK como se fosse novo upload; limpeza de divergência exige evidência autorizada e pode ser repetida sem ampliar escopo.
- **Verificação:** evento com POST tardio, credencial revogada e fila pendente, objeto de outra instalação e divergência reprocessada.
- **Fora do escopo:** apagar órfãos por simples comparação de idade/lista, sem política e evidência suficientes.
- **Referências:** RF-MVP-22; RN-MVP-28–30,33,36; RNF-MVP-07,10.

### BE-026 — Rotacionar credenciais e inativar origem com controle

- **Objetivo:** como administrador, conter uma origem sem corromper histórico ou perder visibilidade da fila pendente.
- **Depende de:** BE-006–008,011; D08,D10.
- **Escopo:** alteração operacional de empresa/quadra/dispositivo e rotação/revogação conforme política, com auditoria e configuração efetiva coerente; preservar identidade e origem histórica dos eventos.
- **Aceite:** somente autorizado altera; segredo novo é exibido uma vez; credencial antiga segue prazo definido; inativação altera config/ingestão conforme D08; efeitos em reenvios pendentes são explícitos. Não inventar drenagem de evento revogado nem mudar identidade para forçar novo registro.
- **Verificação:** rotação em meio a retry, inativação de pai, usuário de outra empresa e cache inativo do gateway após reinício.
- **Fora do escopo:** realocar dados históricos e descartar fila local.
- **Referências:** RF-MVP-24; RN-MVP-03,06,25,26,38; RNF-MVP-01,03.

### BE-027 — Definir e receber saúde da instalação

- **Objetivo:** como operador, distinguir ausência de contato de uma captura saudável.
- **Depende de:** BE-006,007; recorte de D14 e contrato de telemetria definido antes do código.
- **Escopo:** extensão explícita e versionada de heartbeat: identidade, versão, captura, fila, último contato, autenticação, limite e consulta administrativa. Mudança de contrato é adicionada à referência existente, sem alterar ingestão/config v1 silenciosamente.
- **Aceite:** origem A não publica saúde como B; telemetria atrasada não vira estado atual sem regra; token/URL de câmera não entram no payload; último contato isolado não prova câmera produzindo vídeo.
- **Verificação:** duplicação, atraso, falta de contato, captura indisponível e consulta cross-tenant.
- **Fora do escopo:** afirmar que o gateway atual já envia heartbeat cloud ou alterar o Edge nesta história.
- **Referências:** RF-MVP-18,19; RN-MVP-36; RNF-MVP-07,09.

### BE-028 — Integrar telemetria no gateway

- **Objetivo:** como operador, receber saúde real sem interferir no aceite/captura e na retomada de eventos.
- **Depende de:** BE-027.
- **Escopo:** única história deste plano que altera funcionalmente `gateway_captura/`: cliente do contrato de saúde aprovado, envio controlado e documentação local. Revisar instruções próprias do gateway antes de executar.
- **Aceite:** backend recebe estado proveniente das verificações reais locais; falha do heartbeat não bloqueia cliques válidos nem interrompe pipeline; dados sensíveis não são enviados; atualização não muda identidades/etapas de evento.
- **Verificação:** integração com API real local, ausência de rede e regressões do gateway relacionadas ao runtime/pipeline/configuração. Medir impacto suficiente para detectar bloqueio do caminho crítico.
- **Fora do escopo:** firmware, câmera nova e mudanças de protocolo CL1.
- **Referências:** RF-MVP-18; RN-MVP-36; RNF-MVP-07,11.

## 8. Sprint 6 — Validação operacional do piloto

### BE-029 — Demonstrar backup e restauração

- **Objetivo:** como operador, recuperar os dados protegidos dentro dos objetivos definidos.
- **Depende de:** BE-024–026; D03,D09,D11,D14 e RPO/RTO definidos.
- **Escopo:** procedimento executável de backup/restauração do backend, referências de objetos e trabalhos pendentes; diferenciar banco recuperado de vídeo efetivamente recuperável.
- **Aceite:** restauração isolada reconstrói origem, direitos e trabalhos sem exposição indevida ou deleção por duplicidade; perda de dados/tempo de restauração medidos contra D14; segredo não vai para repositório/relatório.
- **Verificação:** restaurar uma cópia real de ambiente controlado e conferir permissões, amostra de reprodução e retomada dos jobs.
- **Fora do escopo:** declarar backup validado apenas por criar arquivo ou prometer recuperar vídeo definitivamente removido.
- **Referências:** RNF-MVP-08,10,12; RF-MVP-19.

### BE-030 — Verificar limites, carga e métricas de entrega

- **Objetivo:** como responsável pelo piloto, conhecer os limites antes de operar sem acompanhamento.
- **Depende de:** BE-012,017,019,024,027,028; D14 e limites por operação definidos.
- **Escopo:** limites de login/recuperação, consultas, emissão de mídia, ingestão e heartbeat; métricas de evento, fila e falhas; carga representativa e relatório com condições/amostra/percentis.
- **Aceite:** limites não apagam eventos nem trocam idempotência; 429 é retentável para o gateway; tenant não consulta métricas privadas de outro; clique→registro/reprodução distingue online de recuperação offline. Meta de cinco minutos é referência a verificar, não resultado presumido.
- **Verificação:** cenário de carga documentado, análise dos índices/consultas de grade e biblioteca e falhas/recovery sob limite. Se o recorte ficar grande, separar instrumentação e ensaio em subtarefas antes da atribuição.
- **Fora do escopo:** benchmark só de corte local como evidência de latência completa.
- **Referências:** RNF-MVP-04,06,07,09; RF-MVP-18,19.

### BE-031 — Homologar fluxo completo e revisar liberação do piloto

- **Objetivo:** como responsável pelo produto, liberar o piloto com critérios demonstrados e limites conhecidos.
- **Depende de:** BE-001–030 concluídas ou recortes explicitamente retirados do piloto com atualização do catálogo/plano; D11,D14 resolvidas e políticas das entregas anteriores registradas.
- **Escopo:** AWS/backend reais, dispositivo/câmera alvo, busca/player nos aparelhos do público, falhas de rede/reinício e operação; reutilizar o [relatório de homologação](homologacao_2026-10-15.md) como referência das pendências físicas, registrando nova evidência datada sem reescrever resultados anteriores como se fossem atuais.
- **Aceite:** clique aceito produz vídeo correto, encontrado no horário local e reproduzido no contexto autorizado; reenvio não duplica; biblioteca/retirada/expiração/limpeza seguem as políticas; suporte, recuperação e limites do piloto definidos. Falta de hardware/AWS/política mantém homologação pendente.
- **Verificação:** executar matriz completa com dados/participantes autorizados, guardar condições e evidências; revisão final humana de liberação conforme contexto de implantação. Não confundir testes simulados com homologação real.
- **Fora do escopo:** pagamentos ou expansão comercial implícita.
- **Referências:** RF-MVP-01–24; RNF-MVP-01–12; D11,D14.

## 9. Cobertura de RF e roteiro de revisão

BE-031 verifica o conjunto e não substitui a implementação/aceite individual das histórias abaixo.

| RF | Histórias responsáveis |
|---|---|
| RF-MVP-01 | BE-004 |
| RF-MVP-02 | BE-005,015 |
| RF-MVP-03 | BE-006 |
| RF-MVP-04 | BE-008,012 |
| RF-MVP-05 | BE-010,011,012 |
| RF-MVP-06 | BE-009–011 |
| RF-MVP-07 | BE-009,011,012 |
| RF-MVP-08 | BE-009–011 |
| RF-MVP-09 | BE-016 |
| RF-MVP-10 | BE-017 |
| RF-MVP-11 | BE-013 |
| RF-MVP-12 | BE-018 |
| RF-MVP-13 | BE-019 |
| RF-MVP-14 | BE-023,024 |
| RF-MVP-15 | BE-003 |
| RF-MVP-16 | BE-013,014 |
| RF-MVP-17 | BE-017,019 |
| RF-MVP-18 | BE-027,028 |
| RF-MVP-19 | BE-007,022,027,029,030 |
| RF-MVP-20 | BE-022 |
| RF-MVP-21 | BE-020,021 |
| RF-MVP-22 | BE-025 |
| RF-MVP-23 | BE-015 |
| RF-MVP-24 | BE-008,026 |

Antes de aceitar uma história, revisar: escopo cumprido; decisões respeitadas; autorização negativa; contrato único; migration compatível; falhas relevantes; comandos/resultados reproduzíveis; limitações reais. Revisão de autorização, concorrência e exclusão merece atenção especial independentemente do modelo executor.

O agente executor propõe o estado **em revisão**. Registrar conclusão só após revisão/integração, sem marcar dependências ou decisões como resolvidas por suposição. Atualizações de status devem ficar nesta fila/história, sem criar um segundo relatório de progresso permanente.

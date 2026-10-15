# Trabalho no backend ClickLance

## Referências e escopo

- O backlog executável está em `../docs/sprints_backend.md`.
- Regras e aceites estão em `../docs/requisitos_e_regras_mvp.md`.
- Decisões D01–D14 e seu estado estão em `../docs/overview_backend_refinamento.md`.
- Integração existente está em `../docs/contrato_cloud_gateway_v1.md`.
- Invariantes do schema estão em `../docs/modelagem_banco_de_dados.md`.
- O primeiro backend é um piloto gratuito com biblioteca. Pagamentos ficam fora.

## Ao receber uma história

1. Leia a ficha BE-XXX e suas referências; confira o estado das dependências e decisões.
2. Inspecione o código existente antes de criar componentes; não presuma que o plano já foi implementado.
3. Execute somente o recorte atribuído. Se faltar decisão de produto, indique qual Dxx e prossiga apenas com trabalho independente dela. Não invente política para passar no teste.
4. Siga a estrutura e os comandos fixados em BE-001. Enquanto ela não existir, não presuma versões ou comandos disponíveis.
5. Verifique os aceites relevantes, com casos negativos/falhas quando exigidos. Teste de banco concorrente ou constraint de PostgreSQL deve exercitar PostgreSQL real.
6. Entregue resumo do resultado, arquivos alterados, comandos/resultados, limitações e dependências ainda abertas. Proponha a história para revisão; não declare homologação real com simuladores.

Escolhas técnicas rotineiras dentro do recorte podem ser resolvidas com justificativa e registro, respeitando a autorização do usuário. Uma decisão Dxx pendente bloqueia somente o comportamento que dela depende; não exige pedir confirmação para cada edição ou teste independente.

## Limites de implementação

- A arquitetura obrigatória é **Model–Controller–Service–Repository**, em um único módulo Maven Spring Boot, com pacotes por camada diretamente em `br.com.clicklance`: `model`, `controller`, `service` e `repository`. Classes de todas as funcionalidades ficam nesses pacotes; não recriar a organização anterior por `identity`, `company` ou `court`. Consulte `../docs/arquitetura_backend.md` antes de implementar ou refatorar.
- Use `config` para beans/configuração Spring, `security` para JWT/filtros/principal/senhas, `exception` para erros e seu tratamento HTTP, `validation` para validações compartilhadas e `cli` para comandos operacionais. Models representam os dados e não dependem de controllers, services, repositories, configuração ou infraestrutura Spring.
- Controllers adaptam HTTP e chamam services; não acessam repositories. Services concentram regras, autorização e limites de transação. Repositories concentram SQL/JDBC e mapeamento de persistência; não chamam services ou controllers. Use injeção por construtor e sufixos `Controller`, `Service` e `Repository` para essas classes.
- Não introduzir módulos `domain/application/infrastructure`, ports ou interactors de Clean Architecture sem uma nova decisão explícita. Essa orientação substitui a proposta anterior de Clean Architecture. Preserve contratos e migrations nas refatorações; mantenha as verificações arquiteturais aprovadas.

- Alterações funcionais ficam em `backend/` e nas referências estritamente necessárias da história. Não alterar firmware/gateway para fazer a API parecer compatível. BE-028 é o recorte explícito de extensão funcional do gateway e deve ser executado a partir das instruções aplicáveis àquela pasta.
- Use a stack prevista (Java/Spring Boot, PostgreSQL e Flyway). BE-001 fixa versões, Maven Wrapper, pacote raiz e procedimento local; tarefas seguintes reutilizam essa base.
- Prefira módulos no mesmo backend. Não introduzir microserviços, broker, provedor de identidade ou outra plataforma sem necessidade da história e decisão técnica registrada.
- Não manter SQL/JSON concorrentes em documentos: migrations e OpenAPI do backend serão as fontes executáveis; contrato v1 continua obrigatório para o gateway.
- Nunca versionar senhas, tokens, credenciais AWS, URLs de câmera autenticadas ou configuração local real.
- Identidade autenticada e autorização por empresa são verificações distintas. UUID conhecido não concede acesso.
- `evento_id` é a identidade da ingestão; proximidade de cinco segundos não é idempotência cloud.
- Confirmação v1 deve refletir commit durável. Repetição não altera origem/prazo nem recria objeto purgado.
- O envelope da configuração v1 tem exatamente `version`, `active`, `settings`. Não acrescentar campos raiz.
- Storage privado; não persistir URL temporária como identidade do objeto. Não introduzir deleção automática fora das histórias de purga e suas políticas.
- Não editar migrations já aplicadas em ambiente compartilhado; acrescentar migration. Não apagar banco/volumes/dados reais para contornar erro de schema.

## Verificação e revisão

- Execute build e verificações previstas na ficha, usando os comandos documentados no backend. Não invente resultado se JDK, PostgreSQL, Docker, AWS ou hardware estiverem indisponíveis.
- Para autorização, teste identidade sem permissão e origem de outra empresa; para ingestão, repetição/conflito; para purga, interrupção e concorrência quando fizerem parte da ficha.
- Substituto local comprova o comportamento exercitado por ele; não comprova AWS, câmera ou operação real.
- Não criar testes que apenas reproduzam a implementação. Priorize o resultado e os invariantes exigidos no aceite.
- Não ampliar a tarefa para a próxima história, publicar serviços, fazer deploy ou enviar mensagens a terceiros por iniciativa própria.

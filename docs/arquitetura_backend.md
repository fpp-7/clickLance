# Arquitetura do backend

Decisão do responsável pelo projeto em 15/10/2026:
**Model–Controller–Service–Repository**, com os pacotes de cada camada na raiz
`br.com.clicklance` e as classes diretamente dentro deles. O backend continua
como um único módulo Maven Spring Boot. A organização por camada passa a ser
o padrão para todo o código existente e para as próximas histórias.

```text
Requisição HTTP → Controller → Service → Repository → PostgreSQL
Comando operacional ─────────→ Service
Filtro de autenticação ─────→ Service
```

## Responsabilidades

| Camada | Responsabilidade | Exemplos |
|---|---|---|
| Model | Representação dos dados e valores compartilhados, sem dependência de Spring ou das camadas de execução. | `Company`, `Court`, `CaptureProfile`, `AdminUser` |
| Controller | Rotas, entrada/saída HTTP, status e cabeçalhos; delegação ao service. | `CompanyController`, `CourtController`, `AdminController` |
| Service | Regras, autorização, validações e coordenação da operação/transação. | `CompanyService`, `CourtService`, `AdminSessionService`, `AdminAccountService` |
| Repository | Consultas, escrita, locks de banco e mapeamento dos registros. | `CompanyRepository`, `CourtRepository`, `AdminUserRepository`, `AdminSessionRepository`, `AdminMembershipRepository` |

Services podem chamar outros services quando reutilizam uma regra, como a
verificação de acesso à empresa antes de consultar suas quadras. Repositories
recebem identificadores e parâmetros da consulta; o service decide qual escopo
está autorizado. Toda consulta por empresa deve aplicar esse escopo também no SQL.

Models são usados conforme necessário pelas outras camadas. Controllers não
acessam repositories ou JDBC. Services não dependem de HTTP ou
JDBC. Repositories não dependem de services ou da camada web. As dependências
são injetadas pelo construtor. Classes dessas camadas usam os sufixos
`Controller`, `Service` e `Repository`.

## Estrutura

```text
backend/src/main/java/br/com/clicklance/
  ClickLanceApplication.java
  model/
    AdminUser.java
    CaptureProfile.java
    Company.java
    Court.java
  controller/
    AdminController.java
    CompanyController.java
    CourtController.java
  service/
    AdminAccountService.java
    AdminSessionService.java
    CompanyService.java
    CourtService.java
  repository/
    AdminMembershipRepository.java
    AdminSessionRepository.java
    AdminUserRepository.java
    CompanyRepository.java
    CourtRepository.java
  config/
    AdminSecurity.java
    IdentityConfiguration.java
  security/
    AdminBearerFilter.java
    AdminJwt.java
    AdminPasswords.java
    AdminPrincipal.java
  exception/
    ApiAdvice.java
    ApiErrors.java
    ApiException.java
  validation/
    Input.java
  cli/
    BootstrapAdmin.java
    ProvisionCompanyAdmin.java
```

Não criar pacotes de funcionalidade acima das camadas: por exemplo,
`CompanyController` fica em `controller`, `CompanyService` em `service`,
`CompanyRepository` em `repository` e `Company` em `model`.

Modelos e validações podem ser compartilhados entre as camadas. Os contratos
HTTP continuam definidos em `backend/openapi.yaml`. Os mapas e records de resposta
existentes são preservados; registros internos com hash de senha ou sessão nunca
devem ser retornados por controllers. `AdminUser` é público para permitir o
uso entre repository e service, mas continua sendo um registro interno.
Componentes de segurança, configuração, erros, validação compartilhada e execução
operacional ficam nos pacotes auxiliares indicados acima.

## Transações e persistência

O service delimita a transação com `@Transactional`; todos os repositories
chamados participam dela. A resposta só sai após o commit. Listagens com contagem
usam `REPEATABLE_READ` para manter a mesma visão entre itens e total.

O SQL permanece parametrizado e centralizado nos repositories, usando
`JdbcTemplate`. Locks necessários ao provisionamento ficam no repository e
duram a transação iniciada pelo service. Constraints e migrations Flyway
continuam garantindo os invariantes do banco. A refatoração não altera V1–V4.

## Aplicação às histórias

BE-001–005 mantêm seus comportamentos e aceites. A adequação técnica separa
persistência de regras em identidade, empresas e quadras. BE-006 em diante
seguem o mesmo padrão desde o início. Não há novos endpoints ou decisões de
produto decorrentes dessa refatoração.

## Verificação

`BackendArchitectureTest` usa ArchUnit para verificar as dependências das
camadas, a nomenclatura e os pacotes de controllers, services e repositories.
Também verifica que models não dependem das camadas de execução ou de Spring.
Os testes PostgreSQL/HTTP continuam verificando
autorização, expiração, concorrência, constraints e compatibilidade da API.

Em `backend/`, com JDK 21 e banco de teste dedicado configurados:

```powershell
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --capture-profiles target/be005-capture-profiles.json
```

Reorganização por camada validada em 15/10/2026 e disponível para revisão:
`clean verify` com perfil `integration` aprovou 34 testes (22 PostgreSQL/HTTP,
11 regras de arquitetura e 1 unitário), sem falhas ou ignorados. As 10 verificações
de contrato e a comparação de 31 perfis HTTP com o validador real do gateway
também passaram. Flyway validou V1–V4 sem reaplicação.

O JAR foi inspecionado: contém 4 models, 3 controllers, 4 services e 5 repositories,
além dos auxiliares; não contém classes nos pacotes antigos `api`, `identity`,
`company` e `court`. Log local em `backend/target/layers-verification.log` e
relatórios Surefire/Failsafe. Essa verificação local não representa homologação
de câmera, AWS ou deploy.

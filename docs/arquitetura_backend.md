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
| Model | Representação dos dados e valores compartilhados, sem dependência de Spring ou das camadas de execução. | `Company`, `Court`, `CaptureProfile`, `AdminUser`, `Device` |
| Controller | Rotas, entrada/saída HTTP, status e cabeçalhos; delegação ao service. | `CompanyController`, `CourtController`, `AdminController`, `DeviceController` |
| Service | Regras, autorização, validações e coordenação da operação/transação. | `CompanyService`, `CourtService`, `AdminSessionService`, `AdminAccountService`, `DeviceService`, `DeviceAuthenticationService` |
| Repository | Consultas, escrita, locks de banco e mapeamento dos registros. | `CompanyRepository`, `CourtRepository`, `AdminUserRepository`, `AdminSessionRepository`, `AdminMembershipRepository`, `DeviceRepository` |

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
    Device.java
    DeviceCredentials.java
    DeviceIssued.java
    AuditAction.java
    AuditEntry.java
    ConfigPublication.java
    GatewayConfig.java
    ReplayInput.java
    VerifiedObject.java
    Replay.java
    ReplayAck.java
    ReplayRegistration.java
  controller/
    AdminController.java
    CompanyController.java
    CourtController.java
    DeviceController.java
    AuditController.java
    ConfigurationController.java
    ReplayController.java
  service/
    AdminAccountService.java
    AdminSessionService.java
    CompanyService.java
    CourtService.java
    DeviceService.java
    DeviceAuthenticationService.java
    AuditService.java
    AuditQueryService.java
    ConfigurationService.java
    ReplayService.java
    ObjectStorageService.java
  repository/
    AdminMembershipRepository.java
    AdminSessionRepository.java
    AdminUserRepository.java
    CompanyRepository.java
    CourtRepository.java
    DeviceRepository.java
    AuditRepository.java
    ConfigurationRepository.java
    ReplayRepository.java
  config/
    AdminSecurity.java
    IdentityConfiguration.java
    StorageConfiguration.java
  security/
    AdminBearerFilter.java
    AdminJwt.java
    AdminPasswords.java
    AdminPrincipal.java
    DevicePrincipal.java
    DeviceTokens.java
    CorrelationContext.java
    CorrelationFilter.java
  exception/
    ApiAdvice.java
    ApiErrors.java
    ApiException.java
    ApiErrorResponder.java
  validation/
    Input.java
    ConfigSettings.java
    ReplayPayload.java
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
`DeviceCredentials` também é interno; consultas retornam somente `Device`.
`DeviceIssued` inclui o token apenas na emissão inicial após commit. Sua
representação textual e a de `DeviceCredentials` omitem o segredo/hash.
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

BE-006 acrescenta provisionamento nas mesmas camadas, com migration V5, geração
criptográfica em `security/DeviceTokens` e serviço transacional de autenticação.
Esse serviço retorna `DevicePrincipal` sem segredo/hash, com origem resolvida no
banco e estado atual dos três níveis. As rotas de configuração/ingestão serão
implementadas em BE-008/011; não há endpoint fictício para testar autenticação.

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

BE-006 validada em 16/10/2026: 43 testes Java aprovados (30 de integração,
11 regras arquiteturais e 2 unitários), 11 verificações de contrato e 3 respostas
HTTP de dispositivos conferidas contra a OpenAPI, além dos 31 perfis de captura
comparados com o gateway. V5 validada sem reaplicação. O JAR passa a conter
7 models, 4 controllers, 6 services e 6 repositories. Log local em
`backend/target/be006-verification.log`; ajuste dos temporários de rede do JDK
Windows e comandos reproduzíveis no README do backend.

BE-007 acrescenta auditoria nas mesmas camadas. `AuditService.success` exige
transação do chamador; a gravação participa do commit da ação e o log de sucesso
é emitido por callback após commit. `ApiErrorResponder` adapta erros de HTTP
e filtros para `AuditService.failure`, cuja transação separada preserva recusas
após rollback. `CorrelationFilter` gera o UUID do servidor e limpa o contexto ao
terminar. `AuditQueryService` aplica o escopo de empresa ou o papel de plataforma
antes de consultar registros, sem dependência circular com os serviços de escrita.

Verificação BE-007 em 16/10/2026: build limpo e 54 testes Java aprovados
(38 PostgreSQL/HTTP, 11 regras arquiteturais e 5 unitários), sem ignorados.
Os testes de auditoria provocam falhas reais de insert e verificam rollback,
ausência de sucesso prematuro nos logs, isolamento A/B e respostas sem credenciais.
Também passaram 12 verificações de contrato, 2 respostas HTTP de auditoria,
3 respostas de dispositivos e os 31 perfis de captura. V6 aplicada e validada
sem reaplicação. O JAR contém 9 models, 5 controllers, 8 services e 7 repositories
(classes principais), sem classes de erro geradas pelo build incremental do IDE.
Log em `backend/target/be007-verification.log`; limites de infraestrutura reais
continuam registrados no README.

## Integração v1 (BE-008–012)

ConfigurationController e ReplayController adaptam HTTP; ConfigurationService e
ReplayService autorizam, validam e coordenam transações. ConfigurationRepository
serializa publicação e mantém histórico/ponteiro; ReplayRepository concentra SQL,
INSERT ON CONFLICT e mapeamento temporal. ObjectStorageService encapsula leitura
S3 com SDK configurado em StorageConfiguration, sem SQL, upload ou delete.
Não foram criados pacotes por funcionalidade, ports ou módulos adicionais.

V7/V8 preservam V1–V6. Build limpo de 16/10/2026 aprovou 65 testes Java, incluindo
48 PostgreSQL/HTTP, 11 regras de arquitetura e 6 unitários. Cliente gateway
original consumiu API real local; storage foi substituído explicitamente.
Comandos e limitações no README do backend.

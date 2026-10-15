# ClickLance Backend

Aplicação Spring Boot em um único módulo Maven, pacote raiz `br.com.clicklance`,
com arquitetura **Model–Controller–Service–Repository**, organizada por camada. Inclui a fundação,
sessões administrativas e cadastro/consulta de empresas e quadras (BE-001–005).
Dispositivos e integração cloud continuam nas histórias seguintes.

## Base técnica fixada

| Componente | Versão / escolha |
|---|---|
| JDK | Java 21 LTS; validação local com Temurin 21.0.12.1 |
| Spring Boot | 4.1.1 |
| Maven | 3.9.16 pelo Wrapper 3.3.4, distribuição verificada por SHA-256 |
| PostgreSQL | 17.11; imagem local `postgres:17.11-bookworm` |
| Flyway, driver JDBC e plugins | Versões gerenciadas pelo parent Spring Boot fixado no `pom.xml` |
| Build | Um único módulo; JAR executável `target/backend-0.1.0-SNAPSHOT.jar` |

Java 21 aproveita o JDK LTS já disponível. A base usa versões estáveis, JDBC e
Flyway sem antecipar ORM ou infraestrutura das próximas histórias. O wrapper
`only-script` dispensa Maven instalado; o primeiro build precisa de internet.
Fontes: [requisitos Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html),
[Maven Wrapper](https://maven.apache.org/tools/wrapper/index.html) e
[inicialização Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html).

## Estrutura

Os pacotes `model`, `controller`, `service` e `repository` ficam diretamente
em `br.com.clicklance`, com as classes de todas as funcionalidades em cada camada.
Models representam os dados; controllers adaptam HTTP; services aplicam regras,
autorização e transações; repositories executam SQL e mapeiam resultados.
O guia está em [arquitetura do backend](../docs/arquitetura_backend.md).
`BackendArchitectureTest` verifica os pacotes das camadas, a independência dos
models e as dependências permitidas entre controllers, services e repositories.

```text
src/main/java/br/com/clicklance/
  ClickLanceApplication.java     ponto de entrada Spring Boot
  model/                        Company, Court, CaptureProfile, AdminUser
  controller/                   controllers HTTP de sessões, empresas e quadras
  service/                      regras, autorização e transações
  repository/                   SQL/JDBC e mapeamento de persistência
  config/                       configuração Spring e segurança HTTP
  security/                     JWT, filtro, principal e validação de senhas
  exception/                    erros padronizados e tratamento HTTP
  validation/                   validações compartilhadas de entrada
  cli/                          bootstrap e provisionamento operacional
src/main/resources/             configuração e db/migration (fonte executável do schema)
src/test/java/br/com/clicklance/ testes unitários e integração PostgreSQL/HTTP *IT
.mvn/wrapper/                   versão e checksum da distribuição Maven
compose.yaml                    PostgreSQL local com volume persistente
```

## Executar no Windows (PowerShell)

Pré-requisitos: JDK 21 e Docker Desktop com Docker Compose e containers Linux,
ou PostgreSQL 17.11 como serviço local. Rode os comandos desta pasta.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
.\mvnw.cmd --version
Copy-Item .env.example .env
```

Edite `.env` e substitua `DB_PASSWORD` por uma senha local exclusiva. O arquivo
é ignorado pelo Git e lido tanto pelo Compose quanto pelo Spring. Use propriedades
simples `CHAVE=valor`, sem aspas, interpolação ou comentários no fim da linha.
Variáveis do processo têm precedência na aplicação; mantenha os mesmos valores
para o Compose. Não reutilize credenciais reais no exemplo.

```powershell
docker compose up -d --wait
.\mvnw.cmd -B -ntp verify
.\mvnw.cmd spring-boot:run
```

Em outro terminal:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

A resposta contém apenas `status: UP`; o indicador verifica também a conexão com
o banco. Só saúde é exposta pelo Actuator, sem componentes, configurações ou
detalhes. A aplicação escuta em `127.0.0.1` por padrão. `SERVER_PORT` permite
alterar a porta; `SERVER_ADDRESS` só deve ser alterado conforme a implantação.

Para executar o pacote, depois de `verify`:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/backend-0.1.0-SNAPSHOT.jar
```

Encerre a aplicação com Ctrl+C e o banco com `docker compose stop`. O volume
preserva dados. Não remova volumes para resolver erro de migration. Alterar
`POSTGRES_PASSWORD` no Compose não troca a senha de uma instalação já existente.

### PostgreSQL como serviço

Crie um usuário e banco locais dedicados e informe `DB_HOST`, `DB_PORT`,
`DB_NAME`, `DB_USER` e `DB_PASSWORD` no `.env` ou no ambiente. O usuário precisa
de permissão para migrations no schema. A aplicação não cria o banco nem seu
usuário; Flyway cria sua tabela de histórico e executa a migration técnica V1.
Reiniciar valida o histórico sem reaplicar V1. Não há H2 nem fallback em memória.

## Verificação com banco real

`verify` compila, verifica e empacota sem exigir banco; **não executa os testes
`*IT`**. Para exercitar a aplicação e Flyway, use um banco dedicado:

```powershell
# Para os valores de usuário do exemplo; ajuste se o usuário local for outro.
docker compose exec postgres createdb -U clicklance clicklance_test
$env:DB_NAME = 'clicklance_test'
.\mvnw.cmd -B -ntp -Pintegration verify
# Segunda inicialização: mesmo banco, mesma instalação e apenas uma V1.
.\mvnw.cmd -B -ntp -Pintegration verify
Remove-Item Env:DB_NAME
```

Se `DB_NAME` já estava definido, restaure seu valor anterior ao terminar. Se o
banco de teste já existe, não repita `createdb`. Nunca aponte os testes para um
banco compartilhado ou de produção. O perfil falha quando PostgreSQL está
indisponível; não ignora testes nem substitui o banco por um simulador.

Os testes verificam aplicação HTTP real, saúde mínima, ausência de rotas de
negócio/configuração e persistência da migration sem reaplicação. A V1 contém
somente o marco técnico `backend_installation`, sem políticas ou entidades futuras.

### Diagnóstico de inicialização

| Sintoma | Ação |
|---|---|
| `JAVA_HOME` inválido ou compilador incompatível | Apontar para a raiz do JDK 21, não para `bin`. |
| Senha ausente / autenticação inválida | Criar `.env` nesta pasta ou definir `DB_PASSWORD` no processo; ausência também pode aparecer como falha de autenticação JDBC. |
| `Connection refused` / timeout JDBC | Conferir host/porta, serviço ou `docker compose ps`; iniciar o banco. |
| `password authentication failed` | Conferir usuário/senha e a credencial efetiva do volume existente. |
| Banco inexistente | Criar o banco dedicado antes de iniciar e conferir `DB_NAME`. |
| Flyway checksum/validation error | Investigar a divergência; não editar migration aplicada ou apagar dados. |
| Porta HTTP ocupada | Definir `SERVER_PORT` livre. |

Erros de banco impedem o startup via Flyway; uma aplicação sem conexão válida
não é apresentada como pronta. Não inclua senhas em URLs JDBC, comandos ou logs.

## Estado e evidências

**BE-001 proposta para revisão em 15/10/2026.**

| Verificação executada | Resultado |
|---|---|
| `.\mvnw.cmd -B -ntp verify` | Build e empacotamento aprovados com JDK 21. |
| Startup/Flyway em PostgreSQL 17.11 vazio | V1 aplicada, marco técnico e histórico persistidos. |
| `.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify` | 2 testes aprovados, zero ignorados; HTTP real e PostgreSQL real. |
| Repetição do mesmo comando no mesmo banco | 2 testes aprovados; V1 não reaplicada, um registro técnico e um histórico. |
| JAR com `.env` em diretório temporário e `--spring.main.web-application-type=none --debug=false` | Startup aprovado e histórico validado; senha ausente da saída capturada. |
| JAR com senha incorreta / ausente | Exit 1; erro JDBC de autenticação (`28P01`). |
| JAR com porta de banco indisponível | Exit 1; erro de conexão (`08001`) com orientação de host/porta. |
| Regras Git | `.env` e `target/` ignorados; exemplo e fontes versionáveis. |

O PostgreSQL real foi executado temporariamente em loopback, com autenticação
SCRAM e senha gerada, a partir dos binários Windows 17.11 empacotados por
[Zonky](https://github.com/zonkyio/embedded-postgres-binaries). Não houve
substituição por H2/simulador. O cluster foi criado fora do repositório. Logs de
verificação ficam em `target/be001-*.log` e relatórios em `target/failsafe-reports/`,
todos locais e ignorados. O teste do JAR não iniciou servidor HTTP; HTTP foi
exercitado pelos testes de integração.

Docker não está instalado nesta máquina: **Compose não foi executado**. A
existência da tag da imagem foi conferida no registry, mas não substitui subir o
container. AWS, deploy e homologação de produção não fazem parte de BE-001.
As demais histórias de implementação não fazem parte desta fundação. A conclusão da história depende
da revisão e integração, conforme o guia do agente.

## Contratos do primeiro recorte (BE-002)

[`openapi.yaml`](openapi.yaml) é a fonte dos schemas, rotas e erros planejados
para administração e integração v1. Operações BE-003–005 estão implementadas;
as demais continuam planejadas, sem endpoints disponíveis. Decisões de produto, autorização, bootstrap, sessão e estratégia de
verificação de objetos estão na [visão do backend](../docs/overview_backend_refinamento.md).
BE-002 não alterou o gateway nem acrescentou migrations de domínio.

Para validar o contrato, é necessário Python 3.13 disponível no terminal, além
do checkout com `gateway_captura/`. Ferramentas Python são só de desenvolvimento;
a aplicação Java não depende delas. A instalação usa as dependências fixadas do
cliente para verificar seu código real, sem câmera/Arduino ou conexão cloud:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r tools/requirements-contracts.txt
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
```

Se Python não estiver no PATH, use o caminho completo do executável no primeiro
comando. `backend/.venv/` e logs gerados são ignorados. O validador confere a
estrutura OpenAPI 3.1, referências, exemplos, parâmetros da configuração e o
payload/ACK do cliente real com transporte HTTP substituído explicitamente.
Não testa autorização implementada, banco, AWS ou endpoints do backend.

Validações semânticas adicionais estão declaradas no contrato: vínculo da origem,
IANA, host RTSP, inteiro lexical, limite de senha em bytes, comparação do
`Idempotency-Key`, chave S3 e tolerância temporal. Exemplos sintaticamente válidos
não demonstram esses comportamentos. O progresso e a revisão de BE-002 ficam
somente na [ficha do backlog](../docs/sprints_backend.md), sem outro status paralelo.

## Administração e empresas (BE-003/004)

Rotas disponíveis: login em `POST /api/v1/admin/sessoes`, identidade em
`GET /api/v1/admin/sessoes/atual`, logout em `DELETE` no mesmo caminho e
criação/listagem/consulta de empresas nos caminhos da OpenAPI. JWT usa RS256,
validade absoluta de 60 minutos e não tem refresh. Cada requisição consulta a
sessão, o usuário e os vínculos atuais; logout, inativação ou retirada do papel
não dependem de o token expirar. Uma indisponibilidade do banco recusa acesso
com 503. Não há autenticação Basic, cookie ou cadastro público de administradores.

V2 cria identidade global, sessão (hash de `jti`) e vínculos administrativos.
V3 cria empresa e acrescenta a FK do vínculo à empresa. Ambas são migrations
novas; V1 permanece intacta. Nenhum vínculo órfão é permitido após V3.
Senhas usam BCrypt custo 12, sem truncamento, com 12–72 bytes UTF-8. Não há
senhas padrão; chave privada e segredos operacionais ficam fora do Git.

### Chaves e primeiro administrador

Depois de configurar PostgreSQL e `.env`, nesta pasta:

```powershell
& "$env:JAVA_HOME\bin\java.exe" tools/GenerateAdminKeys.java .local-secrets
.\mvnw.cmd -B -ntp verify
```

O gerador cria RSA 3072, PKCS8/X509 PEM e recusa sobrescrever arquivos. Restrinja
o acesso à pasta ao operador. No `.env` local, habilite as duas linhas
`ADMIN_JWT_PRIVATE_KEY_PATH` e `ADMIN_JWT_PUBLIC_KEY_PATH` do exemplo. Caminhos
relativos partem da pasta de execução do backend. Sem ambas as chaves, login
responde 503 e nenhum administrador é gerado. Um par parcialmente configurado,
inválido ou incompatível impede startup com orientação para corrigir os PEMs.

O bootstrap exige perfil explícito e segredos no ambiente, sem endpoint HTTP.
O comando não inicia servidor web:

```powershell
$env:BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Email do administrador da plataforma'
$adminSecret = Read-Host 'Senha administrativa (12–72 bytes UTF-8)' -AsSecureString
$env:BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $adminSecret).Password
try {
    & "$env:JAVA_HOME\bin\java.exe" -jar target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=bootstrap-admin --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Bootstrap falhou; confira a mensagem sem reenviar segredos.' }
} finally {
    Remove-Item Env:BOOTSTRAP_ADMIN_EMAIL,Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable adminSecret -ErrorAction SilentlyContinue
}
```

Repetir para o mesmo administrador ativo preserva senha e identidade, inclusive
com duas execuções concorrentes. Email de outra identidade não é promovido e
administrador inativo não é reativado por bootstrap. Sem o perfil, essas variáveis
não criam privilégios. O bootstrap pressupõe acesso operacional ao servidor;
não é um mecanismo remoto de recuperação de senha.

### Login e criação de empresa

Inicie a aplicação normalmente em outro terminal, a partir de `backend/`, com
`mvnw.cmd spring-boot:run` ou com o JAR. No terminal do operador:

```powershell
$adminEmail = Read-Host 'Email administrativo'
$adminSecret = Read-Host 'Senha administrativa' -AsSecureString
$loginBody = @{ email=$adminEmail; password=[System.Net.NetworkCredential]::new('', $adminSecret).Password } | ConvertTo-Json
$session = Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/sessoes -Method Post -ContentType application/json -Body $loginBody
Remove-Variable loginBody,adminSecret
$headers = @{ Authorization="Bearer $($session.access_token)" }
$empresaBody = @{ nome='Minha Arena'; active=$true; retencao_dias=2 } | ConvertTo-Json
$empresa = Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/empresas -Method Post -Headers $headers -ContentType application/json -Body $empresaBody
```

Somente `PLATFORM_ADMIN` cria empresas. Retenção omitida usa o default definido
em D04; campo presente inválido é recusado sem escrita parcial. Nome é normalizado
com strip; IDs opacos são gerados no servidor. Empresa inativa continua consultável
pelo administrador autorizado para operação; isso não habilita ingestão.

Listagem usa `page=0&size=20` por padrão, até 100 itens, ordem por ID e contagem
no mesmo escopo. `COMPANY_ADMIN` só vê empresas com vínculo ativo. Consulta por
ID de outra empresa responde 403 antes de revelar sua existência; plataforma
recebe 404 para recurso inexistente. Respostas não incluem senhas, hashes ou
configuração de câmera. JSON/campos extras, booleanos e inteiros são validados
sem coerção silenciosa. Demais rotas permanecem indisponíveis.

### Provisionar administrador de empresa

Exige empresa criada e uma sessão válida da plataforma (a variável `$session`
do exemplo). É uma operação explícita de servidor, sem cadastro público:

```powershell
$env:PROVISION_ADMIN_TOKEN = $session.access_token
$env:PROVISION_EMPRESA_ID = $empresa.empresa_id
$env:PROVISION_ADMIN_EMAIL = Read-Host 'Email do administrador da empresa'
$companySecret = Read-Host 'Senha (usada somente se a conta for nova)' -AsSecureString
$env:PROVISION_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $companySecret).Password
try {
    & "$env:JAVA_HOME\bin\java.exe" -jar target/backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=provision-company-admin --spring.main.web-application-type=none
    if ($LASTEXITCODE -ne 0) { throw 'Provisionamento falhou.' }
} finally {
    Remove-Item Env:PROVISION_ADMIN_TOKEN,Env:PROVISION_EMPRESA_ID,Env:PROVISION_ADMIN_EMAIL,Env:PROVISION_ADMIN_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable companySecret -ErrorAction SilentlyContinue
}
```

A sessão é validada também nesse processo. Token de administrador de empresa
não provisiona outros administradores. Repetição não troca senha, não reativa
usuário/vínculo e não concede papel de plataforma. Para encerrar a sessão:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/admin/sessoes/atual -Method Delete -Headers $headers
Remove-Variable session,headers
```

### Verificação e limites

O comando `mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify` usa PostgreSQL
real e HTTP real, além do teste unitário de falha de acesso ao banco. Os testes
criam IDs próprios e removem somente suas fixtures; continuam exigindo banco
dedicado. `verify` sem o perfil executa os testes unitários, mas não os `*IT`.
Os relatórios estão em `target/surefire-reports/` e `target/failsafe-reports/`.

O [backlog](../docs/sprints_backend.md) registra resultados e estado de revisão.
Não há frontend, contas de jogadores, cadastro de dispositivos, auditoria
BE-007 ou integração AWS neste recorte. TLS de produção, Docker Compose e
implantação real não foram homologados. Os códigos 429 planejados na OpenAPI
não significam que um limitador distribuído já foi entregue. Referência técnica:
[validação JWT no Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Quadras e perfil inicial (BE-005)

Criação e listagem usam `/api/v1/admin/empresas/{empresa_id}/quadras`; consulta
individual acrescenta `/{quadra_id}`. Requerem Bearer de plataforma ou vínculo
administrativo ativo com a empresa do caminho. Use os schemas `QuadraCreate`,
`Quadra` e `CaptureProfile` da OpenAPI como fonte dos campos e exemplos.

Fuso é obrigatório e deve pertencer aos IDs disponíveis na base de fusos do JDK;
não aceita offset fixo nem usa implicitamente o fuso do servidor. A janela antes
e depois aceita números fracionários dentro dos limites do gateway, enquanto
segmentação e keyframes exigem inteiros JSON. Não se deduz janela pela quantidade
de segmentos. Perfil incompleto, campos extras ou URL de câmera no cadastro
geram 422, sem registro parcial ou reprodução do valor sensível no erro.

Listagem usa `page`/`size`, ordenação por ID e contagem da empresa autorizada.
Empresa de outro tenant gera 403; quadra fora da empresa autorizada do caminho
gera 404. Operações administrativas em empresa inativa não habilitam captura.
V4 acrescenta a FK da quadra e constraints do perfil, além da proteção contra
transferência de empresa conforme D10. Consultas retornam apenas os campos do
cadastro e o perfil; publicação de configuração de câmera pertence a BE-008.

Com PostgreSQL de teste configurado, execute nesta pasta:

```powershell
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --capture-profiles target/be005-capture-profiles.json
```

O último comando depende das ferramentas Python já descritas e do arquivo gerado
por `CourtIT`. Compara perfis efetivamente aceitos/rejeitados por HTTP ao validador
real do gateway, incluindo bordas, frações e inteiros lexicais. Não altera o
gateway nem exige câmera. Os resultados e limites ficam na ficha BE-005 do backlog.

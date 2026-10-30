# ClickLance Backend

Aplicação Spring Boot em um único módulo Maven, pacote raiz `br.com.clicklance`,
com arquitetura **Model–Controller–Service–Repository**, organizada por camada. Inclui a fundação,
sessões administrativas, cadastros, configuração remota e ingestão idempotente,
além de contas de jogador, descoberta, acesso a vídeo, biblioteca, ciclo de vida
e telemetria (BE-001–028), disponíveis para revisão. Restauração e capacidade
operacional têm ferramentas locais; homologação AWS e física permanece pendente.

Referências: [arquitetura e pacotes](../docs/arquitetura_backend.md),
[guia do agente](AGENTS.md), [backlog e estado de revisão](../docs/sprints_backend.md)
e [contrato OpenAPI](openapi.yaml).

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
  model/                        empresas, quadras, dispositivos e registros internos
  controller/                   controllers HTTP de sessões, empresas, quadras e dispositivos
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
usuário; Flyway cria sua tabela de histórico e executa as migrations pendentes
de V1 a V12. Reiniciar valida o histórico sem reaplicar migrations já executadas.
Não há H2 nem fallback em memória.

## Verificação com banco real

`verify` compila, verifica e empacota sem exigir banco; **não executa os testes
`*IT`**. Para exercitar a aplicação e Flyway, use um banco dedicado:

```powershell
# Para os valores de usuário do exemplo; ajuste se o usuário local for outro.
docker compose exec postgres createdb -U clicklance clicklance_test
$env:DB_NAME = 'clicklance_test'
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify
Remove-Item Env:DB_NAME
```

Se `DB_NAME` já estava definido, restaure seu valor anterior ao terminar. Se o
banco de teste já existe, não repita `createdb`. Nunca aponte os testes para um
banco compartilhado ou de produção. O perfil falha quando PostgreSQL está
indisponível; não ignora testes nem substitui o banco por um simulador.

Os testes de integração verificam saúde mínima, proteção de rotas, sessões,
autorização por empresa, cadastros de empresas/quadras/dispositivos, paginação,
constraints e concorrência de provisionamento. Flyway é validado sem reaplicação.
V1 contém o marco técnico `backend_installation`; V2–V8 acrescentam administração,
origem, configuração e ingestão. V9–V12 acrescentam identidade de jogador,
biblioteca, coordenação de purga/reconciliação e saúde da instalação.

Para conferir somente a arquitetura, sem banco, execute nesta pasta:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=BackendArchitectureTest' test
```

Esse comando verifica 11 regras de dependência, nomenclatura, localização dos
pacotes e independência dos models. Não substitui a suíte PostgreSQL/HTTP.

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

## Estado e evidências atuais

BE-001–028 estão implementadas para revisão, sem homologação AWS/hardware.
As decisões de jogador foram confirmadas em 30/10/2026; as escolhas operacionais
delegadas estão na [visão do backend](../docs/overview_backend_refinamento.md).
BE-029 tem backup/restauração local exercitados parcialmente; BE-030 tem limites,
métricas e carga HTTP local, com carga representativa ainda pendente. BE-031 continua
dependendo de ambiente e dispositivos reais e revisão humana da liberação.

Evidências locais de 30/10/2026:

| Verificação | Resultado e limite |
|---|---|
| Maven `verify` com perfil `integration` | BUILD SUCCESS; 103 testes aprovados: 22 unitários/arquiteturais e 81 PostgreSQL/HTTP, sem falhas, erros ou ignorados. V1–V12 validadas sem reaplicação; banco PostgreSQL novo também exercitado. |
| Contratos Python | 15 verificações e 3 respostas HTTP reais BE-026 aprovadas. |
| Regressão do gateway | 73 testes executados, 72 aprovados e 1 RTSP inicialmente ignorado por falta do MediaMTX; repetido isoladamente com `MEDIAMTX_PATH`, passou. Os 73 casos foram verificados. |
| Backup/restore PostgreSQL local | Snapshot com 1.002 replays, 1 usuário, 1 vínculo, 1 job PENDING e 12 migrations; backup em 1,169s e restore em 0,607s. Origem/destino iguais em `verify_restore.sql`, sem origem inválida ou biblioteca quebrada. API restaurada aprovada: sessão preservada, salvo expirado acessível ao dono e negado a outro jogador, reserva oculta e sem elevação administrativa. Mídia real permanece pendente. |
| Sonda HTTP local | 60 GETs da grade com 1.000 replays vigentes e 4 concorrentes: 60 respostas 200 em 0,554s; p50 30,063ms, p95 109,740ms, p99 111,194ms. Não mede clique→reprodução nem carga representativa de produção. |

Relatórios Java ficam em `target/surefire-reports/` e `target/failsafe-reports/`;
artefatos locais de build são descartáveis. Nenhum desses resultados homologa
AWS, hardware ou operação com participantes reais. Procedimentos adicionais e
limites operacionais ficam em [operação do backend](../docs/operacao_backend.md).

### Histórico da reorganização por camada

| Verificação da reorganização em 15/10/2026 | Resultado |
|---|---|
| `mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' clean verify` | Build e JAR aprovados; 34 testes sem falhas ou ignorados. |
| Arquitetura e teste unitário | 11 regras ArchUnit e 1 teste de falha de autenticação por indisponibilidade do banco aprovados. |
| Integração PostgreSQL/HTTP | 22 testes aprovados; V1–V4 validadas sem reaplicação. |
| `tools/validate_contract.py` | 10 verificações de contrato aprovadas. |
| Comparação com o validador do gateway | 31 perfis aceitos/rejeitados por HTTP comparados com sucesso. |
| Inspeção do JAR | 4 models, 3 controllers, 4 services e 5 repositories nos pacotes por camada; sem classes nos pacotes antigos. |

Log local: `target/layers-verification.log`; relatórios em
`target/surefire-reports/` e `target/failsafe-reports/`. Esses artefatos são
ignorados pelo Git e podem ser removidos por `clean`; os comandos acima permitem
gerar novos relatórios. O [guia de arquitetura](../docs/arquitetura_backend.md)
registra a estrutura validada. Essas evidências não homologam AWS, câmera ou deploy.

### Histórico da fundação (BE-001)

As verificações abaixo foram executadas na entrega inicial de BE-001, antes da
implementação dos demais recortes. Suas contagens não representam a suíte atual.

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
para administração, integração v1, jogador, biblioteca e operações. A versão
1.0.0 inclui os recortes BE-003–028 implementados para revisão. Decisões de
produto, autorização, bootstrap, sessão e estratégia de
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
sem coerção silenciosa. As rotas de quadras e dispositivos estão descritas nas
seções BE-005/006; configuração remota e ingestão são descritas em BE-008–012 abaixo.

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
Não há área administrativa web, contas de jogadores ou integração AWS
neste recorte. TLS de produção, Docker Compose e
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

## Dispositivos e credenciais (BE-006)

As rotas exigem uma sessão administrativa da plataforma ou vínculo ativo com a
empresa da quadra:

| Operação | Caminho |
|---|---|
| Criar e emitir credencial | `POST /api/v1/admin/empresas/{empresa_id}/quadras/{quadra_id}/dispositivos` |
| Listar na quadra | `GET` no mesmo caminho, com `page`/`size` |
| Consultar por ID global | `GET /api/v1/admin/dispositivos/{dispositivo_id}` |

O corpo de criação tem exatamente os campos de `DispositivoCreate` na OpenAPI.
`dispositivo_id` é informado pelo operador e aceita de 1 a 32 caracteres
`[A-Za-z0-9_-]`, sem normalização. Ele é único globalmente e deve coincidir com
`DEVICE_ID` do gateway e `DEVICE` do botão. O firmware atual usa `mega01`.

V5 cria a FK para quadra, da qual deriva a empresa; o banco impede renomear o ID
ou transferir a quadra. Trocar a origem exige outro dispositivo. O token usa
32 bytes de `SecureRandom`, base64url sem padding (43 caracteres), e somente seu
hash SHA-256 é persistido. O retorno 201 inclui `dispositivo` e `device_token`,
com `Cache-Control: no-store`, depois do commit. Consultas e listagens não
retornam token ou hash. Repetição do cadastro retorna 409 e preserva a credencial;
se a resposta de emissão for perdida, é necessária intervenção operacional.
Rotação e recuperação de credencial não foram antecipadas de BE-026.

### Preparar a lista de buckets e cadastrar

No `.env` do backend, preencha `DEVICE_ALLOWED_BUCKETS` com nomes de buckets
privados já autorizados pela plataforma, separados por vírgulas, e reinicie a
aplicação. O exemplo deixa a lista vazia: nessa condição, emissão retorna 503.
Um bucket fora da lista retorna 422. A lista governa novos cadastros; não revoga
tokens existentes, não cria bucket e não comprova acesso/IAM ou existência na AWS.

No terminal que já contém `$headers` e `$empresa` dos exemplos administrativos:

```powershell
$quadraId = Read-Host 'ID da quadra ja cadastrada'
$deviceId = Read-Host 'ID CL1 do dispositivo'
$deviceBucket = Read-Host 'Bucket privado autorizado na configuracao do backend'
$deviceBody = @{ dispositivo_id=$deviceId; active=$true; bucket=$deviceBucket } | ConvertTo-Json
$deviceIssue = Invoke-RestMethod "http://127.0.0.1:8080/api/v1/admin/empresas/$($empresa.empresa_id)/quadras/$quadraId/dispositivos" -Method Post -Headers $headers -ContentType application/json -Body $deviceBody
```

A atribuição guarda a resposta sem imprimi-la. Transfira a credencial para a
configuração local protegida da instalação; não imprima ou versione o objeto
`$deviceIssue`. Remova-o do terminal após guardar a credencial com segurança.

### Provisionar o gateway localmente

No `.env` local e ignorado de `gateway_captura/`, configure estes campos:

| Campo | Valor de origem |
|---|---|
| `DEVICE_ID` | `dispositivo.dispositivo_id` da emissão; igual ao `DEVICE` do botão. |
| `DEVICE_TOKEN` | `device_token` da emissão, tratado como segredo. |
| `QUADRA_ID` | `dispositivo.quadra_id`, sem escolher outra quadra no payload. |
| `S3_BUCKET_NAME` | `dispositivo.bucket`. Credenciais IAM são provisionadas separadamente. |
| `CONFIG_URL` | URL de `/api/v1/dispositivos/{dispositivo_id}/config`, entregue em BE-008. |

Esses IDs e bucket não são alterados remotamente pelo contrato v1. Eventos já
aceitos no gateway preservam sua configuração original; não reutilize uma fila
de outra instalação ao trocar a origem. A ingestão `/api/v1/replays` foi entregue em BE-011; exige storage autorizado e verificável, além do cadastro.

### Autenticação e consultas

`DeviceAuthenticationService.authenticate(id, token)` é reutilizável por
BE-008/011. Resolve empresa, quadra e bucket pelo banco; compara hashes em tempo
constante; credencial ausente/inválida/cruzada retorna 401. Credencial correta
com dispositivo, quadra ou empresa inativo retorna 403 `ORIGIN_INACTIVE`.
O estado é consultado novamente em cada chamada. Não há rota pública de teste
nem uso de `X-Device-Token` como sessão administrativa.

Cadastros e consultas administrativas continuam permitidos em origem inativa,
sem habilitar autenticação operacional. Para consulta pelo ID global, plataforma
recebe 404 se ele não existe; administrador de empresa recebe o mesmo 403 para
ID ausente ou fora do escopo, sem confirmar existência. Listagem por quadra
inclui inativos e mantém paginação/contagem no mesmo escopo e snapshot.

### Verificação BE-006

Em `backend/`, com PostgreSQL dedicado configurado:

```powershell
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --capture-profiles target/be005-capture-profiles.json
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --device-responses target/be006-device-responses.json
```

Verificação em 16/10/2026: 43 testes Java aprovados (30 PostgreSQL/HTTP,
11 regras de arquitetura e 2 unitários), 11 verificações de contrato, 31 perfis
comparados com o gateway e 3 respostas HTTP de dispositivos validadas contra a
OpenAPI. As fixtures exportadas contêm somente respostas sem segredo.
Logs em `target/be006-verification.log` e relatórios Surefire/Failsafe.

A primeira execução nesta máquina falhou no JDK ao abrir sockets locais
(`Unable to establish loopback connection`, `UnixDomainSockets.connect`).
A suíte completa passou com temporários de rede isolados no diretório de build:

```powershell
$javaSocketDir = Join-Path (Get-Location).Path 'target/temporary-java-domain-sockets'
New-Item -ItemType Directory -Path $javaSocketDir -Force | Out-Null
$previousJavaOptions = [Environment]::GetEnvironmentVariable('JAVA_TOOL_OPTIONS', 'Process')
try {
    $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' -Djdk.net.unixdomain.tmpdir="' + $javaSocketDir + '"').Trim()
    .\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' verify
} finally {
    [Environment]::SetEnvironmentVariable('JAVA_TOOL_OPTIONS', $previousJavaOptions, 'Process')
}
```

Esse ajuste foi usado apenas no processo de verificação Windows; não muda
autenticação nem substitui HTTP/PostgreSQL reais. AWS, hardware e jornada
completa ainda dependem das histórias de integração e homologação.

## Auditoria e correlação (BE-007)

V6 acrescenta `administrative_audit`. Criação de empresa/quadra/dispositivo,
login/logout, bootstrap e provisionamento de administrador registram sucesso na
mesma transação da escrita. Falha na auditoria desfaz a ação; nenhum token ou
cadastro é entregue como sucesso. O log `administrative_audit` só é emitido após
commit. Comandos operacionais usam ator `SYSTEM`; HTTP autenticado usa `ADMIN`.

Erros administrativos são registrados em transação separada, depois do rollback
da operação original. Sem identidade autenticada, o ator é `ANONYMOUS`. Falhas
de gravação dessas recusas retornam 503 e emitem somente o marcador
`administrative_audit_unavailable`, a ação e a correlação, sem detalhes internos.

As respostas da API têm `X-Correlation-ID`, um UUID novo gerado pelo servidor;
o header enviado pelo cliente não é reutilizado. O corpo de erro tem o mesmo
`correlation_id`, `code`, mensagem genérica e, quando aplicável, `violations`
somente com campos/códigos. JSON inválido, 401/403, 404/405/415, validação e falha
de banco seguem esse envelope. Erro inesperado recebe 500 `INTERNAL_ERROR`,
sem mensagem da exception ou stack trace. Os códigos existentes do contrato
cloud v1 foram preservados; correlação não substitui `evento_id`.

| Consulta administrativa | Autorização |
|---|---|
| `GET /api/v1/admin/empresas/{empresa_id}/auditoria?page=0&size=20` | Plataforma ou vínculo ativo com essa empresa. |
| `GET /api/v1/admin/auditoria/{correlation_id}` | Somente plataforma; lista vazia se a correlação não existe. |

A listagem por empresa ordena por instante e ID decrescentes, com contagem e
itens no mesmo snapshot. Consultar auditoria com sucesso não cria novos registros.
O registro contém IDs de ator/recurso/empresa, ação, resultado, código, instante
e correlação; não contém dados do cadastro, email, IP, payload, headers, tokens,
hashes, senha ou URL de câmera. IDs de recusas só identificam empresa encontrada
no banco, sem copiar o caminho bruto. Snapshots permanecem após remoção do recurso;
não há API de edição/exclusão de auditoria ou política de purga automática neste recorte.

Após configurar o banco de teste e, quando necessário, o ajuste do JDK Windows
descrito em BE-006, execute em `backend/`:

```powershell
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' clean verify
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --audit-responses target/be007-audit-responses.json
```

O build limpo também evita reaproveitar classes temporárias geradas pelo IDE.
Os testes exercitam rollback real por falha de auditoria, isolamento A/B,
paginação, correlação de sucesso/recusa, falha no registro de recusa e ausência
de credenciais na saída. Fixtures exportadas não incluem segredos. Relatórios
em `target/surefire-reports/`, `target/failsafe-reports/` e log
`target/be007-verification.log`. Os limites de AWS/hardware permanecem os das
histórias de integração e homologação.

Resultado em 16/10/2026: 54 testes Java aprovados (38 PostgreSQL/HTTP,
11 regras de arquitetura e 5 unitários), sem falhas ou ignorados. Os 8 `AuditIT`
incluem falhas provocadas no PostgreSQL e captura de saída sem senha, token,
hash ou URL RTSP com credenciais. Passaram também 12 verificações de contrato,
2 respostas HTTP de auditoria, 3 respostas de dispositivos e 31 perfis de captura
contra o validador real do gateway. V6 aplicada e validada sem reaplicação;
V1–V5 preservadas.

## Configuração e ingestão do gateway (BE-008–012)

V7 acrescenta histórico de publicação e ponteiro da versão atual; V8 acrescenta
replay, origem composta, snapshot de retenção e referência verificada do objeto.
V1–V6 permanecem intactas. A organização continua Model–Controller–Service–Repository.

Com sessão de plataforma ou vínculo ativo na empresa, publicar por
`POST /api/v1/admin/dispositivos/{id}/configuracoes`. Enviar `base_version: null`
na primeira publicação e a versão atual nas seguintes, além de `settings` com
pelo menos uma chave da allowlist. O servidor mescla perfil da quadra, valores
anteriores e alteração solicitada; assume RTSP na primeira publicação se a fonte
for omitida. Para câmera IP, informar `CAMERA_RTSP_URL` válida com host.
`CAMERA_RTSP_TRANSPORT` aceita tcp/udp e `dshow` continua disponível para testes.
Não inserir credenciais reais em exemplos versionados ou histórico de terminal.

Faixas e tipos seguem `remote_config.py`; inteiros lexicais recusam `1.0`.
Chaves desconhecidas e `null` são recusadas sem valores sensíveis nos erros.
Omitir chave mantém o valor anterior no backend/cache v1. Não há remoção por
omissão. Publicação retorna 201 com versão opaca, autor e instante; duas
alterações da mesma base produzem 201/409. Versão inválida e falha na auditoria
não substituem a configuração válida. Histórico no PostgreSQL pode conter
credenciais RTSP e exige acesso operacional restrito, inclusive em backups.

Provisionar `CONFIG_URL` localmente para
`GET /api/v1/dispositivos/{id}/config`. Token em `X-Device-Token` autentica somente
o próprio dispositivo; a resposta contém exatamente `version`, `active` e
`settings`, com `Cache-Control: no-store`. Sem publicação: 503 retentável. Token
inválido: 401; dispositivo ou pai inativo: 403. O cliente persiste a inativação
por 401/403. Essas respostas não usam desafio Bearer administrativo.
`DEVICE_ID`, `QUADRA_ID`, `S3_BUCKET_NAME` e credenciais AWS continuam locais.
Publicações não reescrevem snapshots de eventos já aceitos pelo gateway.

### Storage e confirmação

SDK S3 2.56.0, região em `AWS_REGION`, credenciais pela cadeia padrão AWS
(perfil/IAM/ambiente); a API não recebe essas credenciais. `STORAGE_ENDPOINT`
opcional é exclusivo do operador para endpoint S3 controlado. Nunca vem do
payload do replay. O SDK usa timeout de conexão/leitura e limite de chamada.
Conceder somente leitura das instalações autorizadas, incluindo
`GetObjectVersion` e KMS quando aplicáveis. Não há upload, delete ou listagem.

Antes de chamar storage, conferir origem, bucket provisionado e chave exata
`replays/{dispositivo_id}/{evento_id}.mp4`. HEAD verifica tamanho positivo e
metadata `sha256`; GET calcula SHA-256 dos bytes. GET fixa VersionId ou condiciona
a leitura por `If-Match` do ETag observado; ETag não é checksum.
Referência: [API oficial S3 GetObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html).
Divergência de hash retorna 409; objeto vazio, 422. Ausência, timeout, permissão
negada, 429/5xx remoto ou alteração na leitura retornam 503 com `Retry-After`.
Conferência de bytes não prova qualidade/decodificação do vídeo. IAM/política do
bucket precisa impedir sobrescrita futura; esse provisionamento não foi realizado.

`POST /api/v1/replays` usa payload v1, `X-Device-Token` e
`Idempotency-Key=evento_id`. Timestamp exige offset e precisão representável em
microssegundos, como o cliente atual; precisão excedente é recusada sem truncar
a identidade. Até 300 segundos no futuro, inclusive, é aceito. Retenção é copiada
da empresa no primeiro registro: 1–30 dias, cada dia com 24h desde o instante
original. Chegada vencida é confirmada com `expired_on_arrival=true`, sem
rejuvenescimento ou publicação para jogadores.

ACK contém somente `evento_id` e `sha256`: 201 para inserção, 200 para repetição
idêntica, 409 para conteúdo divergente. Commit termina antes da resposta HTTP;
duas requisições simultâneas convergem pela constraint PostgreSQL. Reenvio
autentica a origem e compara o registro antes de consultar storage, inclusive
após purga. Não recria objeto, prazo ou política. Não há worker de deleção neste recorte.

### Verificação reproduzível

Usar PostgreSQL dedicado e instalar `tools/requirements-contracts.txt` na `.venv`
antes dos testes: BE-012 executa clientes originais Python em subprocesso. As
fixtures sintéticas chegam por stdin, sem credenciais em argumentos ou relatórios.
O teste inicia HTTP S3 em loopback e chama o SDK real contra esse substituto.

```powershell
.\mvnw.cmd -B -ntp -Pintegration '-Ddebug=false' clean verify
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --capture-profiles target/be005-capture-profiles.json
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --device-responses target/be006-device-responses.json
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --audit-responses target/be007-audit-responses.json
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --config-responses target/be008-config-responses.json
.\.venv\Scripts\python.exe -X utf8 tools/validate_contract.py --replay-responses target/be011-replay-responses.json
```

`GatewayIntegrationIT` verifica configuração, tenant, inativação, rollback de
publicação, falha adiada no commit, snapshots, chegada vencida, limite futuro,
concorrência, integridade e conflitos. BE-012 chama `RemoteConfig.refresh/apply`
e `CloudGateway.register` originais contra a API real, perde a resposta após 201,
repete com 200 e verifica uma única linha. Confirma conflito terminal, ausência
retentável e cache inativo. Não requer câmera, botão ou AWS; código funcional do
gateway permanece intacto. Relatórios em `target/surefire-reports/`,
`target/failsafe-reports/`, `target/be008-012-verification.log` e
`target/be012-gateway-result.txt`, descartáveis após build limpo.

Esta verificação de 16/10/2026 não homologou AWS/hardware e antecedeu jogador,
biblioteca, purga e rotação. A evolução de 30/10/2026 está descrita abaixo;
os resultados históricos seguintes permanecem os da entrega BE-008–012.

Resultado BE-008–012 em 16/10/2026: 65 testes Java aprovados, sendo 48 de
PostgreSQL/HTTP, 11 regras arquiteturais e 6 unitários, sem falhas ou ignorados.
V7/V8 aplicadas e validadas sem reaplicação; V1–V6 preservadas.

Também aprovadas 12 verificações de contrato, 31 perfis HTTP contra o gateway,
3 respostas de dispositivos, 2 de auditoria, 2 de configuração e 2 ACKs reais.

## Jogador, busca e biblioteca (BE-013–021)

As rotas abaixo são relativas a `/api/v1/jogadores`. Cadastro, login, recuperação
e redefinição são públicos; as demais exigem JWT de jogador no header Bearer.
O contrato completo de corpos, paginação e erros está na OpenAPI.

| Método e caminho | Resultado |
|---|---|
| `POST /api/v1/jogadores` (raiz, sem barra final) | Cadastra `{email,password}`; 201 com identidade global, sem permissões administrativas. |
| `POST /sessoes` | 201 com JWT e validade de 3.600 segundos. |
| `GET /sessoes/atual`, `DELETE /sessoes/atual` | Consulta identidade ou revoga a sessão. |
| `POST /recuperacao`, `POST /redefinicao` | Solicita email ou consome `{token,password}`. |
| `GET /arenas`, `GET /arenas/{empresa_id}/quadras` | Descobre arenas e quadras ativas. |
| `GET /quadras/{quadra_id}/disponibilidade` | Datas e slots com lances acessíveis; `data` omitida usa hoje na quadra. |
| `GET /quadras/{quadra_id}/lances` | Busca por data e par `hora_inicio`/`hora_fim`; sem horas retorna o dia. |
| `GET /lances/{evento_id}` | Detalhe autorizado sem endereço permanente do objeto. |
| `POST /lances/{evento_id}/acesso` | `{download:boolean}` emite URL temporária para reprodução ou download. |
| `GET /biblioteca`, `PUT /biblioteca/{evento_id}`, `DELETE /biblioteca/{evento_id}` | Lista, salva ou remove somente os próprios vínculos. |
| `DELETE /conta` | Encerra a conta e remove seus vínculos atomicamente. |

Email é obrigatório, recebe trim/lowercase e **não exige verificação inicial**.
Senhas têm 12–72 bytes UTF-8, com BCrypt. Email já existente, inclusive de
administrador, não cria nova identidade. JWT reutiliza as chaves RSA configuradas,
com audiência e tabela de sessões exclusivas de jogador: 60 minutos, sem refresh.
Logout revoga a sessão; redefinição revoga todas. Contas administrativas são
protegidas contra cadastro, recuperação e encerramento por esse fluxo.

Datas e faixas usam o fuso IANA da quadra, com intervalos `[início,fim)`; fim 24
é a meia-noite seguinte. Ordenação usa instante e identidade para estabilizar a
paginação. Expiração e bloqueio são avaliados nas consultas, sem depender do cron.
Vitrine de origem inativa fica indisponível; direito privado de biblioteca é
preservado, sujeito a bloqueio do próprio lance.

Biblioteca conserva até **100 lances por conta ativa, sem prazo fixo**. Novo
salvamento só ocorre enquanto disponível; repetição de vínculo existente não
duplica nem consome nova cota, inclusive após expiração pública. Locks coordenam
salvamento, encerramento e reserva de purga. Encerramento revoga acesso, remove
somente os vínculos do solicitante e anonimiza email/senha; preserva UUID e data
do fechamento. Falha reverte a transação e permite repetir. Depois do sucesso,
o JWT anterior recebe 401.

URL assinada é uma credencial bearer: pode ser reutilizada ou compartilhada até
expirar. Sua validade máxima é 300 segundos, também limitada pelo prazo público
restante quando o jogador não salvou o lance. O backend confere a validade real
produzida pelo SDK e fixa a versão do objeto quando existente. Bloqueio ou logout
impedem novas emissões; não recolhem downloads nem invalidam todo link já emitido.

### SMTP para recuperação

Configure `MAIL_HOST` e `MAIL_FROM`; sem eles, recuperação retorna 503 para
qualquer email. `MAIL_PORT` tem padrão 587; `MAIL_USERNAME` e `MAIL_PASSWORD`
ficam no ambiente/secret local. `MAIL_AUTH` e `MAIL_TLS` são true por padrão;
timeouts de conexão/leitura/escrita são de 5 segundos. Para um capturador SMTP
em loopback, use a porta local e ajuste autenticação/TLS ao capturador. Nenhum
email real foi enviado pelos testes; o adaptador foi exercitado em SMTP local.

O email leva um token aleatório de uso único, com prazo de 15 minutos; só seu hash
fica no banco. A API responde 202 genérico para conta existente, ausente, inativa
ou administrativa. Falha de transporte mantém o mesmo 202, descarta o token não
entregue e registra somente código/correlação; o jogador pode repetir. Limites
reduzem abuso, mas tempo de resposta não garante ocultar a existência da conta.
Tokens e URLs não devem ser incluídos em relatórios ou logs.

## Bloqueio, purga e operação (BE-022–028)

| Método e caminho administrativo | Função |
|---|---|
| `PATCH /api/v1/admin/lances/{evento_id}/bloqueio` | Define `{blocked,reason}`; motivos `POLICY`, `PRIVACY` ou `OPERATIONAL`. |
| `POST /api/v1/admin/empresas/{empresa_id}/purga/reservas` | Reserva até `limit` trabalhos duráveis para expirados sem vínculos. |
| `POST /api/v1/admin/empresas/{empresa_id}/purga/executar` | Executa até `limit` trabalhos, com confirmação remota e retomada. |
| `POST` / `GET /api/v1/admin/empresas/{empresa_id}/reconciliacao` | Registra divergências ou lista até 100 ocorrências no tenant. |
| `PATCH /api/v1/admin/empresas/{empresa_id}/status` | Altera `{active}` da empresa; exclusivo de plataforma. |
| `PATCH /api/v1/admin/empresas/{empresa_id}/quadras/{quadra_id}/status` | Altera `{active}` da quadra autorizada. |
| `PATCH /api/v1/admin/dispositivos/{dispositivo_id}/status` | Altera `{active}` do dispositivo autorizado. |
| `POST /api/v1/admin/dispositivos/{dispositivo_id}/credenciais/rotacao` | Corpo `{}`; novo segredo só na resposta `no-store`, token anterior imediatamente revogado. |

Plataforma ou administrador vinculado à empresa executa bloqueio e operações
da empresa. Escritas e auditoria confirmam juntas; falhas preservam recusa com
correlação sem payload ou segredos. Bloqueio suspende descoberta, contagens,
detalhe, biblioteca e nova emissão de acesso, sem declarar objeto apagado.

`PURGE_ENABLED=false` é o padrão. Execução remota exige habilitação explícita e
`PURGE_AWS_PROFILE` próprio, com credenciais de deleção separadas das do gateway
e da leitura. `PURGE_STORAGE_ENDPOINT` é opcional para ambiente controlado. A
política delegada do piloto é bucket privado sem versionamento, backups de vídeo
ou lifecycle concorrente. O adaptador verifica o estado de versionamento e recusa
bucket versionado/suspenso ou referência versionada; exclusão usa chave exata e
ETag observado, confirmando ausência antes de marcar purgado. Ambiente AWS e IAM
reais precisam ser homologados antes de ativar o worker.

Reserva, claim e finalização usam transações curtas; rede não fica dentro do lock
de decisão. Falha remota mantém trabalho retomável. Chamadas operacionais por
lotes disparam o worker; não há agendador externo provisionado. Reconciliação
reporta objeto sem registro, registro sem objeto e objeto ainda presente após
purga. **Não exclui órfãos:** upload sem POST pode continuar numa fila offline.

Inativação de empresa/quadra/dispositivo recusa configuração e ingestão, inclusive
reenvios. Operações autenticadas já em andamento terminam antes do commit da
alteração. Cache inativo do gateway permanece no disco e é respeitado offline;
reativação com credencial válida é aplicada no próximo refresh bem-sucedido.
Rotação exige instalar o novo segredo no gateway e tratar a fila local; repetir
rotação emite outra credencial, sem recuperar o segredo de resposta perdida.

### Saúde e limites

`POST /api/v1/dispositivos/{dispositivo_id}/saude` usa `X-Device-Token` e payload
versionado separado da configuração/ingestão v1. Recebe versão, estado de captura,
progresso e fila, sem câmera/segredos. O gateway envia por thread própria, com
`HEALTH_URL` configurada e `HEALTH_INTERVAL_SECONDS=60` por padrão. Falha do envio
não bloqueia aceite/captura. Amostras antigas não substituem as mais recentes.

`GET /api/v1/admin/dispositivos/{dispositivo_id}/saude` exige autorização da origem.
Retorna `UNKNOWN` sem amostra; após 180 segundos sem contato/observação recente,
`STALE`. Contato recente isolado não atesta captura saudável. O Actuator continua
mostrando apenas saúde mínima do backend; SMTP não configurado não o torna DOWN.

Os limites por minuto são 5 cadastros, 10 logins, 3 recuperações/redefinições,
120 consultas, 30 emissões de mídia, 120 ingestões e 2 heartbeats por credencial.
A rede também é limitada; para ingestão/heartbeat são 1.200/minuto. Rejeições
retornam 429 com `Retry-After`, preservando identidade/idempotência dos eventos.
`REQUEST_LIMITS_ENABLED=true` é o padrão. Contadores e limites estão em memória,
adequados ao piloto de uma instância; reiniciam com o processo.
`GET /api/v1/admin/metricas` é exclusivo de plataforma e não expõe dados pessoais.

## Backup, restauração e capacidade (BE-029–031)

`tools/backup_restore.ps1` usa os binários PostgreSQL indicados em `-PgBin` e as
variáveis `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` do processo.
O script não carrega `.env` automaticamente nem recebe senha em argumento.

```powershell
.\tools\backup_restore.ps1 -Mode Backup -PgBin 'C:\caminho\postgres\bin' -Archive 'C:\backups\clicklance.dump'
.\tools\backup_restore.ps1 -Mode Restore -PgBin 'C:\caminho\postgres\bin' -Archive 'C:\backups\clicklance.dump' -DestinationDatabase 'clicklance_restore_validacao'
```

O backup recusa sobrescrever arquivo existente; restore exige banco novo isolado
com nome `*_restore_*` e não executa DROP/clean sobre dados existentes. Compare
origem e destino com `tools/verify_restore.sql`, valide direitos e trabalhos e
mantenha worker desabilitado e SMTP sem credenciais no destino. O arquivo inclui
hashes e configuração restrita; proteja acesso e conservação. Backup do banco
não recupera vídeo definitivamente excluído. Metas delegadas são backup diário,
RPO de 24h e restauração em até 4h; criar arquivo não comprova essas metas.

`tools/load_probe.py` recebe JSON por stdin com `base_url`, `access_token`, `path`
de consulta de jogador, `requests` e `concurrency`. Tokens não entram em argv nem
no relatório, que mostra status e percentis HTTP. A sonda não mede clique→player
nem substitui carga representativa de 1 arena/2 quadras/100 jogadores e até
1.000 eventos/dia. Metas online de p95 até 300s e p99 até 600s ainda precisam ser
medidas com captura, AWS, backend e aparelhos reais. BE-031 permanece pendente,
incluindo informação aos participantes, suporte e revisão humana da liberação.

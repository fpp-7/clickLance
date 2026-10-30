# ClickLance — Plataforma web

Atualizado em 30/10/2026. Frontend Next.js para o piloto gratuito: conta do jogador,
busca de quadras, replays, biblioteca e operação administrativa. A interface usa
fundo claro, verde de quadra, Barlow Condensed e Manrope; a imagem da entrada é
ilustrativa. Pagamentos ficam fora deste recorte.

## Executar

Ambiente usado nesta entrega: **Node.js 24.22.1**, npm e Next.js 16.4.3.
As versões resolvidas estão no [lockfile](package-lock.json). Em PowerShell:

```powershell
cd plataforma_web
node --version
npm ci
# Somente se .env.local ainda não existir:
Copy-Item .env.example .env.local
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000). Preserve configurações locais
existentes. O modo padrão é demonstrativo; não exige Spring Boot, câmera ou AWS.
Instalação, fontes Google e vídeo público de exemplo podem depender de internet.

Para produção local: `npm run build` e `npm start`. Em uma implantação real,
servir sob HTTPS: os cookies de sessão usam `Secure` em produção.

## Telas disponíveis

| Rota                                    | Comportamento                                                                     |
| --------------------------------------- | --------------------------------------------------------------------------------- |
| `/`                                     | Apresentação do produto, imagem ilustrativa e entrada para encontrar quadras.     |
| `/entrar`, `/cadastro`                  | Login por email/senha e cadastro de jogador sem verificação inicial de email.     |
| `/recuperar-acesso`, `/redefinir-senha` | Solicitação genérica de recuperação e redefinição pelo código recebido por email. |
| `/arenas`                               | Lista paginada e busca nas arenas carregadas, com acesso autenticado.             |
| `/arenas/[slug]`                        | Quadras da arena; o segmento identifica a empresa conforme a API.                 |
| `/quadras/[id]`                         | Data, faixas horárias, fuso da quadra e lances paginados.                         |
| `/lances/[id]`                          | Detalhe, player com acesso autorizado, download e salvar/remover da biblioteca.   |
| `/biblioteca`                           | Biblioteca do jogador autenticado, com disponibilidade e remoção confirmada.      |
| `/conta`                                | Identidade atual, logout e encerramento da conta com confirmação.                 |
| `/admin/entrar`, `/admin`               | Login administrativo e empresas autorizadas; criação exclusiva de plataforma.     |
| `/admin/empresas/[id]`                  | Quadras, dispositivos, credenciais, configuração, saúde, auditoria e manutenção.  |

O player usa vídeo MP4 autorizado, controles nativos e nova emissão de acesso
quando necessário. Download solicita sua própria autorização. URLs temporárias
não viram identidade do lance nem ficam em armazenamento persistente.

A administração permite cadastro e estado de empresa/quadra/dispositivo, emissão
ou rotação de credencial exibida uma vez, publicação de configuração com versão
base, consulta de saúde e auditoria paginada. Bloqueio e manutenção exigem
confirmação. A interface não habilita a rotina de purga no servidor; a política
continua sendo a do [guia operacional](../docs/operacao_backend.md).

## Configuração

Use `.env.local`, ignorado pelo Git. Reinicie o servidor após mudar o modo e gere
um novo build para alterar valores públicos em produção.

| Variável               | Padrão                  | Uso                                                                                          |
| ---------------------- | ----------------------- | -------------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_API_MODE` | `mock`                  | Somente `http` seleciona a conexão real; os demais valores usam a demonstração identificada. |
| `BACKEND_URL`          | `http://127.0.0.1:8080` | Origem do Spring Boot, lida somente pelo servidor Next. Não adicionar `/api/v1`.             |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | Origem pública dos metadados.                                                                |

`NEXT_PUBLIC_API_URL` não é usado pela nova camada das telas. O navegador chama
`/api/backend/...` na mesma origem; o Next encaminha somente caminhos e métodos
permitidos para `/api/v1/...` do backend. Falha da conexão real nunca ativa o
modo demonstrativo automaticamente.

Exemplo de conexão local real:

```properties
NEXT_PUBLIC_API_MODE=http
BACKEND_URL=http://127.0.0.1:8080
NEXT_PUBLIC_SITE_URL=http://localhost:3000
```

Inicie o backend conforme seu [README](../backend/README.md), com PostgreSQL,
chaves e dados provisionados. Cadastro de jogador não concede administração.
SMTP precisa estar configurado no backend para envio real de recuperação.

## Sessão e integração

As telas usam [lib/platform/api.ts](src/lib/platform/api.ts), seus
[tipos](src/lib/platform/types.ts), [sessão](src/lib/platform/session.tsx) e o
[proxy Next](src/app/api/backend/%5B...path%5D/route.ts). A
[OpenAPI](../backend/openapi.yaml) permanece a fonte de rotas e schemas.

No login real, o BFF guarda o token em cookie `HttpOnly`, `SameSite=Lax`, separado
por papel (`player` e `admin`), por até 60 minutos. A resposta JSON não devolve
o token para as telas. O BFF acrescenta Bearer na chamada ao Spring; as telas não
persistem JWT em `localStorage`. Logout, conta encerrada e respostas 401 limpam
a sessão aplicável. O cache de consultas é separado por identidade e limpo nas
trocas de sessão.

Mutações exigem JSON e origem igual à do site. O proxy limita os caminhos,
parâmetros de consulta e tamanho do corpo, não segue redirecionamentos do
upstream e devolve erros tratados com correlação quando disponível. O backend
continua responsável pela autorização, validade da sessão e isolamento entre
empresas; esconder um botão não substitui esses controles.

As URLs de mídia aceitas são HTTPS; HTTP de loopback é permitido apenas em
desenvolvimento. O jogador não recebe credenciais AWS, URL da câmera ou token do
dispositivo. O operador administrativo recebe a nova credencial do dispositivo
somente na emissão/rotação, mantida na memória da tela até ser dispensada.

## Demonstração

O rótulo de demonstração permanece visível. Use **Explorar demonstração** na
entrada do jogador ou **Explorar administração demonstrativa** no login admin.

- Dados e sessões são sintéticos e vivem apenas na memória da aba; recarregar
  reinicia o cenário. Nenhuma conta ou gravação real é criada.
- É possível cadastrar uma conta demonstrativa, entrar e manter biblioteca
  isolada por identidade, respeitando a cota de 100 lances.
- As faixas usam o fuso da quadra. Vídeos públicos de exemplo são identificados
  como demonstração; não são lances capturados na arena.
- A recuperação mostra resposta genérica sem enviar email. A demonstração não
  inventa um código de recuperação válido; redefinição real depende de SMTP.
- Cadastro/status/configuração administrativos têm respostas demonstrativas.
  Purga e reconciliação remotas permanecem indisponíveis na demonstração.

## Estrutura

```text
src/app/
  (institucional)/        entrada do produto
  (jogador)/              autenticação, arenas, quadras, lances, biblioteca e conta
  admin/                  login e operação administrativa
  api/backend/[...path]/  BFF de mesma origem e cookies de sessão
  globals.css             tokens e estilos compartilhados
  provedores.tsx          cache de consultas e sessão
src/components/
  auth/                   conta e formulários de autenticação
  player/                 descoberta, filtros, player e biblioteca
  admin/                  cadastros, dispositivos, saúde e manutenção
  layout/ e ui/           navegação e componentes compartilhados
src/lib/platform/         contratos, cliente, sessão, erros e demonstração
public/                   assets da interface
```

## Verificação e limites

| Comando                | Ação                                                                     |
| ---------------------- | ------------------------------------------------------------------------ |
| `npm run lint`         | Análise de código.                                                       |
| `npm run typecheck`    | Verificação TypeScript.                                                  |
| `npm test`             | Testes de proxy, API, sessão, demonstração, administração e componentes. |
| `npm run build`        | Build de produção.                                                       |
| `npm run verificar`    | Lint, tipos, testes e build, nessa ordem.                                |
| `npm run format:check` | Conferência de formatação.                                               |

Ensaio real local de 30/10: **18 verificações Next → Spring Boot → PostgreSQL
17.11 aprovadas**, cobrindo cadastro, cookie HttpOnly sem token em JSON,
isolamento jogador/admin, arena/quadra/disponibilidade/filtro/detalhe,
salvar/listar/remover, recusa CSRF, SMTP ausente, logout, sessão revogada e
encerramento de conta. Dados eram sintéticos em banco temporário; não houve
chamada de mídia AWS nem envio de email. Relatório local descartável em
`backend/target/web-proxy-integration-result.json`.

Verificação final: `npm run lint`, `npm run typecheck`, `npm test` (48 testes) e
`npm run build` aprovados. `npm audit` não reportou vulnerabilidades após atualizar
o lockfile. A [direção visual](../docs/frontend_design.md) registra os tokens,
a imagem ilustrativa e o prompt usado. O CSS foi implementado para celular e desktop, mas **não houve
validação visual no navegador nem aprovação por screenshots** nesta execução.
O acesso de automação ao navegador foi interrompido antes dessa revisão.

PWA instalável, service worker, cache offline e reprodução offline não estão
implementados. SMTP real, vídeo/storage AWS, câmera/botão e aparelhos do público
continuam sem homologação. Nenhum deploy foi realizado. Próximos trabalhos:
revisão visual/acessibilidade em aparelhos reais e fechamento dos ensaios
BE-029–031 conforme o [backlog](../docs/sprints_backend.md).

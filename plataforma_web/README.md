# ClickLance — Plataforma Web

Interface web para encontrar arenas e quadras. O projeto usa Next.js e uma
camada de dados com adaptadores mock e HTTP.

**Estado atual:** landing, busca de arenas e detalhe com quadras implementados.
O modo padrão usa dados fictícios. Player, login, biblioteca e instalação PWA
ainda não têm implementação de interface neste módulo.

O piloto é gratuito, com biblioteca pessoal; pagamentos ficam para outra fase.
As políticas reais seguem as [decisões do backend](../docs/overview_backend_refinamento.md).

## Executar localmente

Use **Node.js 24.15.0 ou superior na linha 24** e npm para atender aos requisitos
das dependências do lockfile, incluindo jsdom e Vitest. O projeto ainda não fixa
o runtime por `engines` ou `.nvmrc`.

Na raiz do repositório, em PowerShell:

```powershell
cd plataforma_web
node --version
npm --version
npm ci
Copy-Item .env.example .env.local
npm run dev
```

Crie `.env.local` somente se ele ainda não existir, preservando sua configuração.
Abra [http://localhost:3000](http://localhost:3000).

O modo mock dispensa backend, gateway e AWS para as consultas demonstrativas.
Instalação de dependências, fontes Google e mídias externas podem precisar de
internet; mock não significa funcionamento offline completo.

Para executar o build de produção local:

```powershell
npm run build
npm start
```

## O que está disponível

| Camada | Disponível | Limite atual |
|---|---|---|
| Landing `/` | Apresentação do produto e acesso à busca. | Textos sobre assistir, baixar e compartilhar descrevem a jornada pretendida. |
| Busca `/arenas` | Filtro por nome/localidade, carregamento, vazio, erro e nova tentativa. | Dados fictícios no modo padrão. |
| Arena `/arenas/[slug]` | Nome, localização e quadras com estados demonstrativos. | Cartões de quadra ainda não abrem a jornada de lances. |
| API mock | Arenas, quadras, faixas horárias, lances, SMS, favoritos e solicitação comercial simulados. | Métodos da API não equivalem a telas implementadas. |
| API HTTP | Transporte JSON, Bearer e tradução de erros. | Rotas propostas; integração com o backend atual pendente. |
| Player/HLS | Dependência `hls.js` declarada. | Player ainda não implementado. |
| PWA | Metadados básicos de aplicação e viewport. | Sem manifesto ou service worker implementado. |

Rotas como `/q/[quadraId]`, `/quadra/[id]`, `/lance/[id]`, `/entrar` e
`/meus-lances` ainda não existem no App Router. Também não há área administrativa
web nem formulário comercial implementado.

## Stack

| Componente | Uso |
|---|---|
| Next.js 16.3.8 | App Router, layouts e metadados. |
| React / React DOM 19.2.8 | Interface e componentes. |
| TypeScript | Tipos do domínio e da API. |
| Tailwind CSS 4 | Estilos e tokens do tema em `globals.css`. |
| TanStack Query | Consultas, cache e retentativas. |
| Lucide React | Ícones. |
| Vitest, Testing Library e jsdom | Testes de lógica e componentes. |
| ESLint e Prettier | Análise e formatação. |

Versões declaradas em [package.json](package.json) e resolvidas em
[package-lock.json](package-lock.json). Use `npm ci` para instalar pelo lockfile.

## Configuração

Use `.env.local`, ignorado pelo Git. Variáveis `NEXT_PUBLIC_` podem ser
publicadas no navegador: não coloque segredos nelas. Reinicie o servidor após
alterações; para produção, gere novo build.

| Variável | Exemplo / padrão | Finalidade |
|---|---|---|
| `NEXT_PUBLIC_API_MODE` | `mock` | Somente o valor exato `http` seleciona HTTP; ausente ou diferente usa mock. |
| `NEXT_PUBLIC_API_URL` | Exemplo: `http://localhost:8080/api`; fallback: `/api`. | Base dos caminhos do adaptador HTTP. |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | Base válida para metadados. |
| `NEXT_PUBLIC_MOCK_LATENCIA` | `450` | Latência média simulada, em ms; `0` elimina a espera. |
| `NEXT_PUBLIC_MOCK_FALHAS` | `0` | Fração de falhas simuladas entre 0 e 1; aceita pelo código, ainda ausente do exemplo. |

A seleção fica em [src/lib/api/index.ts](src/lib/api/index.ts).
Falha da API HTTP não ativa mocks automaticamente.

## Camada de dados e integração real

As telas usam a interface [ClickLanceApi](src/lib/api/cliente.ts) através dos
hooks em `src/lib/consultas/`. Os [tipos](src/lib/api/tipos.ts) e
[erros](src/lib/api/erros.ts) ficam junto dos adaptadores:

- [Mock](src/lib/api/mock/index.ts): desenvolvimento demonstrativo e testes.
- [HTTP](src/lib/api/http/index.ts): requisições ao servidor configurado.

O adaptador HTTP espera caminhos para arenas, quadras, partidas, lances, SMS,
favoritos e solicitações comerciais. São propostas do frontend, **não contratos
já implementados no backend**. A fonte dos contratos é a
[OpenAPI do backend](../backend/openapi.yaml).

O backend atual oferece sessões administrativas, empresas e quadras em
`/api/v1/admin/...`; este cliente ainda não implementa esse fluxo. Mudar apenas
o modo e a URL base não torna os projetos compatíveis. É preciso alinhar
caminhos, schemas, paginação, autenticação e erros no adaptador.
Não alterar o backend para atender silenciosamente a rotas provisórias do mock.

A organização Java por `model`, `controller`, `service` e `repository` está no
[guia de arquitetura](../docs/arquitetura_backend.md). Essa separação interna
preserva os caminhos e payloads HTTP; o cliente continua seguindo a OpenAPI.

A integração do jogador depende das histórias BE-013–021 e das decisões
aplicáveis. O tipo `Partida` representa faixas horárias no protótipo; não cria
uma entidade Partida no backend. SMS e retenção do mock não definem políticas reais.

O adaptador HTTP atual guarda a sessão em `localStorage`, com os mesmos helpers
do mock. Essa escolha precisa ser revista com a política de sessão antes de usar
contas reais; não representa a autenticação administrativa de BE-003. Ao alternar
modos, remova apenas as chaves `clicklance:` da origem da aplicação para não
reaproveitar uma sessão demonstrativa.

## Comportamento do mock

- Arenas e quadras fictícias ficam em [mock/dados.ts](src/lib/api/mock/dados.ts).
  O rótulo “Gravando” é demonstrativo, sem telemetria de câmera real.
- Faixas de uma hora são chamadas de partidas, incluindo a hora atual
  `ao_vivo`. O histórico padrão é de nove dias.
- Lances usam geração por semente e mídias públicas em `storage.googleapis.com`;
  não são gravações da quadra.
- Na consulta de lances ao vivo, a simulação considera um lance após 15 segundos,
  os seguintes a cada 30 segundos e oito segundos no estado `processando`.
  Ainda não há tela ou polling de lances para apresentar esse fluxo.
- A retenção demonstrativa é de sete dias. A política real segue D04–D06.
- Após solicitar um código para um telefone demonstrativo com DDD, o código
  aceito é `123456`, válido por cinco minutos. Nenhum SMS é enviado.
- Sessão, favoritos, códigos e início da simulação usam chaves `clicklance:` no
  `localStorage`. Favoritos não são isolados por usuário como uma biblioteca real.
- Solicitação comercial valida campos e gera protocolo fictício; não envia
  email nem registra contato em serviço externo.

Esses recursos podem ser exercitados pelos métodos da API e testes mesmo sem
tela correspondente. Não comprovam autorização, persistência cloud ou hardware.

## Estrutura

```text
plataforma_web/
  src/
    app/
      (institucional)/    landing e layout institucional
      (jogador)/arenas/   busca e detalhe por slug
      layout.tsx         fontes, metadados e estrutura base
      provedores.tsx     provider do TanStack Query
      globals.css        tokens de tema e estilos globais
    components/
      arenas/            busca e cartões de arena/quadra
      layout/            cabeçalho e rodapé
      ui/                botões, campos e estados de interface
    hooks/               hooks compartilhados
    lib/
      api/               interface, tipos, erros e adaptadores
      consultas/         hooks e chaves de cache
      utils/             formatação e utilitários
    test/                setup dos testes
  package.json
  package-lock.json
  .env.example
```

O domínio usa nomes em português; a estrutura técnica usa `components`, `hooks`
e `lib`. Cores compartilhadas vêm dos tokens de `globals.css`. Os formatadores
atuais usam o fuso do ambiente; a integração real deverá respeitar o fuso da quadra.

## Comandos de desenvolvimento

| Comando | Ação |
|---|---|
| `npm run dev` | Servidor de desenvolvimento. |
| `npm run build` | Build de produção. |
| `npm start` | Executar o build já gerado. |
| `npm run lint` / `npm run lint:fix` | Analisar código / aplicar correções disponíveis. |
| `npm run typecheck` | Executar `tsc --noEmit`. |
| `npm test` / `npm run test:watch` | Executar testes uma vez / acompanhar alterações. |
| `npm run format` / `npm run format:check` | Formatar arquivos / conferir formatação. |
| `npm run verificar` | Lint, typecheck, testes e build, nessa ordem; não inclui `format:check`. |

Há testes de API mock, formatação e cartão de arena. Eles não comprovam integração
com Spring Boot, reprodução de vídeo ou instalação PWA. A existência dos scripts
não significa que as verificações passaram; registre os resultados ao executá-las.

## Próximos recortes

1. Alinhar o adaptador HTTP aos contratos disponíveis no backend.
2. Definir a sessão real do jogador e identificar visualmente o modo demonstrativo.
3. Implementar quadra/data/horário e lances conforme as APIs forem entregues.
4. Implementar reprodução autorizada e biblioteca segundo as políticas aprovadas.
5. Avaliar PWA com regras explícitas de cache e acesso offline.

O [documento da plataforma](../docs/plataforma_web.md) descreve o planejamento
funcional; este README registra a estrutura e os comandos observados neste módulo.
Consulte o [backlog](../docs/sprints_backend.md) e o
[guia do backend](../backend/README.md).

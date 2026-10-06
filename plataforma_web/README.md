# 📱 Click Lance — Plataforma Web

> Site e PWA do jogador (QR code da quadra, lista de lances, player, compartilhamento) e landing comercial para donos de quadra. Construído em Next.js com camada de API trocável entre mock e backend real.

---

## 🧰 Stack

| Peça | Escolha | Por quê |
|---|---|---|
| Framework | **Next.js 16** (App Router) + React 19 + TypeScript | Rotas de servidor para link compartilhado com preview, PWA e SEO da landing no mesmo projeto |
| Estilo | **Tailwind CSS 4** | Tema escuro em tokens CSS, mobile-first sem CSS solto |
| Dados | **TanStack Query** | Cache, polling da partida ao vivo e retry pensado para sinal fraco |
| Vídeo | `<video>` nativo + **hls.js** | MP4 direto; HLS só quando o backend entregar stream |
| Ícones | lucide-react | Leve e consistente |
| Qualidade | ESLint, Prettier, Vitest + Testing Library | Mesmos comandos no CI e na máquina |

Requisitos: **Node 24+** e **npm**.

---

## 🚀 Como rodar

```powershell
cd plataforma_web
npm install
cp .env.example .env
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000). Por padrão a plataforma sobe em **modo mock**: não precisa de backend, S3 nem gateway ligado.

### Scripts

| Comando | O que faz |
|---|---|
| `npm run dev` | Servidor de desenvolvimento com hot reload |
| `npm run build` / `npm start` | Build de produção e servidor |
| `npm run lint` | ESLint (regras do Next + TypeScript) |
| `npm run typecheck` | `tsc --noEmit` |
| `npm test` | Testes unitários e de componente (Vitest) |
| `npm run format` | Prettier em tudo |
| `npm run verificar` | Lint, typecheck, testes e build em sequência. Rode antes de abrir PR |

---

## 📁 Estrutura de pastas

```
plataforma_web/
├── public/                  # Ícones, manifest e arquivos estáticos
└── src/
    ├── app/                 # Rotas (App Router)
    │   ├── (institucional)/ # Landing e páginas comerciais, com rodapé
    │   ├── (jogador)/       # Fluxo do jogador: busca, quadra, lances, player
    │   ├── layout.tsx       # HTML base, fontes, metadados e provedores
    │   └── globals.css      # Tokens do tema escuro (Tailwind @theme)
    ├── components/
    │   ├── ui/              # Primitivos: Botao, CampoBusca, Esqueleto, EstadoVazio, EstadoErro, Marca
    │   ├── layout/          # Cabecalho, Rodape
    │   └── arenas/          # Componentes do domínio (CartaoArena, BuscaArenas...)
    ├── hooks/               # Hooks genéricos (useValorAtrasado...)
    ├── lib/
    │   ├── api/             # Camada de API (veja abaixo)
    │   ├── consultas/       # Hooks do TanStack Query por recurso + chaves de cache
    │   └── utils/           # Formatação de data/hora/telefone, cn()
    └── test/                # Setup do Vitest
```

Convenções que seguimos:

- **Domínio em português** (Arena, Quadra, Partida, Lance, `CartaoArena`, `useArenas`); estrutura técnica em inglês (`components`, `lib`, `hooks`).
- **Mobile-first.** Layout do jogador tem largura máxima pequena e toques de pelo menos 44 px. Teste no celular, de preferência com rede lenta no DevTools.
- **Tema por tokens.** Cores vêm de `globals.css` (`bg-surface`, `text-muted`, `bg-primary`...). Não use hex solto em componente.
- **Estados sempre tratados:** carregando (skeleton), vazio, erro e offline têm componente próprio.

---

## 🔌 Camada de API

Todas as telas falam com uma única interface, `ClickLanceApi` (`src/lib/api/cliente.ts`), através dos hooks em `src/lib/consultas/`. Existem duas implementações:

| Implementação | Arquivo | Quando usar |
|---|---|---|
| **mock** | `src/lib/api/mock/` | Desenvolvimento local, demos e testes. Padrão. |
| **http** | `src/lib/api/http/` | Contra a API real (Spring Boot). |

A escolha é feita em `src/lib/api/index.ts` lendo o `.env`:

```env
NEXT_PUBLIC_API_MODE=http
NEXT_PUBLIC_API_URL=https://api.clicklance.com.br/api
```

### Como trocar o mock pela API real

1. Suba a API e aponte `NEXT_PUBLIC_API_URL` para ela.
2. Troque `NEXT_PUBLIC_API_MODE` para `http`.
3. Confira as rotas em `src/lib/api/http/index.ts`. Elas são a nossa proposta de contrato; se o backend usar outro caminho ou formato, ajuste **só ali**. Nenhuma tela precisa mudar.

Rotas que o cliente HTTP espera hoje:

| Método | Rota | Retorno |
|---|---|---|
| GET | `/arenas?busca=` | `Arena[]` |
| GET | `/arenas/slug/{slug}` · `/arenas/{id}` | `Arena` |
| GET | `/arenas/{id}/quadras` | `Quadra[]` |
| GET | `/quadras/{id}` | `Quadra` |
| GET | `/quadras/{id}/contexto` | `{ arena, quadra, partidaAtual }` (deep link do QR) |
| GET | `/quadras/{id}/partidas?dias=` | `Partida[]` |
| GET | `/partidas/{id}` | `Partida` |
| GET | `/partidas/{id}/lances?desde=` | `Lance[]` |
| GET | `/lances/{id}` | `Lance` |
| POST | `/auth/sms` · `/auth/sms/confirmar` · `/auth/sair` | Login por código SMS |
| GET | `/auth/sessao` | `Sessao` |
| GET/PUT/DELETE | `/favoritos` · `/favoritos/{lanceId}` | Favoritos do usuário |
| POST | `/comercial/demonstracoes` | Pedido de demonstração da landing |

Os tipos trafegados estão em `src/lib/api/tipos.ts`. Datas são sempre ISO 8601. Erros viram `ErroApi` com um `codigo` (`nao_encontrado`, `rede`, `expirado`...) que as telas usam para decidir o que mostrar.

### O que o mock faz

- **Arenas e quadras fictícias** em `src/lib/api/mock/dados.ts`. Nomes e cidades são inventados.
- **Partidas** são as faixas de uma hora cheia da quadra (ex.: 20h – 21h). A hora atual sempre existe como partida **ao vivo**; é nela que o QR code cai.
- **Lances** são gerados por semente a partir do id da partida, então são sempre os mesmos para a mesma URL. Vídeos e thumbnails são amostras públicas do Google até termos clipes reais.
- **Simulação ao vivo:** ao abrir uma partida em andamento, um lance novo aparece 15 s depois e mais um a cada 30 s, primeiro como "processando" e depois "disponível".
- **Lance expirado:** lances com mais de 7 dias vêm com status `expirado`.
- **Login por SMS:** qualquer celular com DDD; o código é sempre **123456**.
- **Favoritos e sessão** ficam no `localStorage`.
- `NEXT_PUBLIC_MOCK_LATENCIA` controla a latência simulada e `NEXT_PUBLIC_MOCK_FALHAS` (0 a 1) faz uma fração das chamadas falhar, para testar as telas de erro.

---

## 🗺️ Rotas

| Rota | Tela |
|---|---|
| `/` | Landing comercial |
| `/arenas` | Busca de arena/quadra |
| `/arenas/[slug]` | Quadras da arena |

As próximas etapas adicionam `/q/[quadraId]` (deep link do QR), `/quadra/[id]` (partidas e lances), `/lance/[id]` (player e compartilhamento), `/entrar` e `/meus-lances`.

# Direção visual e implementação do frontend

30/10/2026. Skill aplicada: `frontend-design`; imagem de abertura criada com a
ferramenta integrada da skill `imagegen`. O código da aplicação está em
`plataforma_web/`. Contratos funcionais seguem a OpenAPI do backend.

## Conceito e tokens

Arquivo esportivo para quem acabou de jogar society ou futsal. O elemento de
maior expressão é o conjunto de título condensado e fotografia de uma jogada.
Busca, horário e biblioteca organizam a experiência; estados de captura não são
inventados pela interface.

| Papel | Cor |
|---|---|
| Fundo claro frio | `#F3F6F5` |
| Superfície | `#FFFFFF` |
| Texto principal | `#142B2D` |
| Verde de quadra / ação | `#087D66` |
| Detalhe de marca | `#E1B750` |
| Texto secundário | `#657575` |

Barlow Condensed 500–700 dá a escala esportiva aos títulos e à marca.
Manrope atende texto, campos e navegação. Os textos são alinhados à esquerda,
com rótulos em caixa de frase, hierarquia por tamanho e linhas curtas.

```text
Marca             Quadra / Biblioteca / Como funciona          Conta

O jogo acaba.                 Fotografia da jogada
O lance fica.                 Acesso à busca de replays
Texto / Encontrar quadra      Indicação de imagem ilustrativa

Encontrar arena               Busca por nome                  Buscar

Botão na quadra               Data e horário                  Biblioteca
```

Na revisão do plano, a fotografia ficou concentrada na abertura. A galeria usa
desenho de campo como capa neutra porque o backend não fornece thumbnails; isso
evita apresentar fotografias ilustrativas como se fossem frames reais de cada
lance. Bordas delimitam filtros, tabelas e formulários. As páginas do jogador
têm largura máxima e margens laterais; os filtros passam a uma coluna no celular.
Movimento fica restrito a feedback e carregamento, respeitando movimento reduzido.

## Sessão e integração

O navegador usa `/api/backend/...`. O servidor Next encaminha apenas os caminhos
permitidos para o Spring Boot configurado em `BACKEND_URL`, acrescentando Bearer
a partir de cookie HttpOnly específico de jogador ou administrador. Login não
devolve o token ao JavaScript. Mutações exigem origem correspondente e JSON.
URLs temporárias de vídeo não são persistidas. A troca/expiração de identidade
limpa os caches; o player é desmontado ao trocar de usuário.

O modo demonstrativo é explícito, mantém estado em memória e separa bibliotecas
por conta. O modo HTTP não recorre à demonstração quando o servidor falha. O
protótipo anterior em `src/lib/api` e seus componentes permanecem no repositório;
as novas rotas usam `src/lib/platform`.

## Imagem e prompt

Arquivo final: [society-hero-v1.png](../plataforma_web/public/images/society-hero-v1.png).
Imagem ilustrativa gerada, sem vínculo com partidas reais; o Next Image fornece
as versões otimizadas. A geração usou a ferramenta integrada, sem CLI/API própria.

Prompt usado:

> Use case: photorealistic-natural. Asset type: editorial photographic hero asset for ClickLance, a Brazilian community football replay website. Create a beautiful cinematic but natural photo of a grassroots five-a-side society soccer game in Brazil, close low sideline viewpoint, main focus on the legs of an adult player in unbranded dark teal shorts and cream socks controlling a white football on textured green artificial turf, another adult player further behind in golden yellow shirt, white goal and chain-link enclosure softly out of focus, late afternoon sunlight. Natural human proportions, authentic neighborhood sports atmosphere, confident action and premium sports magazine photography, subtle 35mm grain, tactile turf, no professional stadium. Vertical 4:5 framing, ball and main player in lower-middle, strong diagonal white sideline, usable upper background, realistic unretouched colors with emerald green, deep petrol shadows and warm golden highlights. Full bleed photo only, no UI, text, logos, watermarks, badges, borders or collage. This is an illustrative brand image, not an actual match recording.

## Verificação e limites

O frontend foi exercitado por testes de componentes e API, e por 18 verificações
do BFF contra Spring Boot/PostgreSQL locais. A auditoria de dependências passou
após atualizar Next/ESLint Next para 16.4.3 e as dependências transitivas do lockfile.
O README registra os comandos e resultados finais de lint, tipos, testes e build.

A revisão visual automatizada foi interrompida pelo Computer Use porque a URL
do navegador Windows não pôde ser identificada com segurança. A responsividade
e a acessibilidade foram implementadas e revistas por código; a inspeção visual
e a jornada em aparelhos reais permanecem pendentes. Não houve deploy, envio de
emails, acesso a mídia AWS ou ativação de purga em storage real.

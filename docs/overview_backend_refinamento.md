# Backend — visão e decisões

Atualizado em 16/10/2026. Fundação e decisões entregues; BE-003–012 implementam administração, cadastros, auditoria, configuração e ingestão v1, propostos para revisão no backlog. Integração local usa PostgreSQL e clientes reais; AWS e hardware continuam sem homologação.

## 1. Por onde começar

**Já há definição suficiente para iniciar a fundação e a integração com o gateway.** Acesso aos vídeos e conservação da biblioteca precisam de decisão antes das funcionalidades correspondentes. Não é necessário resolver todo o produto futuro para criar o primeiro fluxo técnico.

| Referência ativa | Responsabilidade |
|---|---|
| Este documento | Escopo, decisões e ordem de implementação. |
| [Catálogo de requisitos](requisitos_e_regras_mvp.md) | RN, RF, RNF e critérios de aceite; IDs preservados. |
| [Sprints e histórias](sprints_backend.md) | Backlog executável BE-001–031; dependências, escopo e verificações por tarefa. |
| [Contrato cloud v1](contrato_cloud_gateway_v1.md) | Payloads e comportamento exigido pelo gateway existente. |
| [Modelo de dados](modelagem_banco_de_dados.md) | Invariantes que a primeira migration precisa garantir. |
| [Arquitetura do backend](arquitetura_backend.md) | Organização Java por camada, responsabilidades e verificações arquiteturais. |

As migrations e a OpenAPI versionadas no backend são as referências executáveis de dados e contratos. A OpenAPI também descreve operações futuras; o [backlog](sprints_backend.md) e o [README](../backend/README.md) identificam o recorte implementado. Não manter cópias de DDL ou JSON em vários documentos.

## 2. Escopo do primeiro backend

**Arquitetura confirmada em 15/10/2026:** Model–Controller–Service–Repository, em um único módulo Spring Boot organizado por camada. Os pacotes `model`, `controller`, `service` e `repository` ficam diretamente em `br.com.clicklance`, com pacotes auxiliares para configuração, segurança, erros, validação e comandos operacionais. Responsabilidades e verificações em [arquitetura do backend](arquitetura_backend.md); as decisões de produto, contratos e aceites permanecem válidos.

**Confirmado pelo produto:** piloto gratuito com biblioteca pessoal; pagamentos em fase seguinte.

Fluxo: botão → gateway gera o clipe e envia objeto privado → backend autentica e registra o evento → jogador encontra o lance por quadra/data/horário → acessa e salva conforme as políticas aprovadas.

- Empresas, quadras e dispositivos com administração autorizada por empresa.
- Configuração e ingestão compatíveis com o gateway v1.
- Busca por faixas horárias no fuso da quadra; slot não representa uma partida formal.
- Conta global e biblioteca pessoal, sem cobrança no piloto.
- Expiração, controle de acesso e limpeza com retomada após falhas.

Pagamentos, gestão formal de partidas, multicâmera e personalização de domínio não entram na primeira entrega. A capacidade física proposta é uma câmera/quadra por instância de gateway, ainda a confirmar.

## 3. O que falta e quando precisa estar resolvido

As escolhas abaixo continuam distinguindo confirmado, proposto e pendente. As recomendações técnicas são pontos de partida para discussão, sem aprovação implícita.

| ID | Decisão | Estado / proposta | Precisa fechar |
|---|---|---|---|
| D01 | Escopo comercial inicial. | **Confirmado: piloto gratuito com biblioteca; pagamentos depois.** Duração, público e limite do piloto ainda a definir. | Confirmado; detalhar limites antes do piloto. |
| D02 | Quem pode ver os lances enquanto vigentes? | **Pendente:** visitante, conta autenticada ou participante com código. | Antes de grade/player e salvamento. |
| D03 | Por quanto tempo e com qual cota conservar salvos? | **Pendente:** prazo configurável, conta ativa sem prazo fixo ou plano futuro. Evitar promessa de eternidade sem aprovação. | Antes de conservação e purga da biblioteca. |
| D04 | Limites de retenção pública. | **Confirmado:** padrão 2; de 1 a 30 dias inteiros de 24h. | Definido em BE-002; implementar no recorte correspondente. |
| D05 | Evento chega quando prazo já acabou. | **Confirmado:** registrar/confirmar sem publicar; preservar clique e identidade. | Definido em BE-002; purga depende de D09 e das histórias próprias. |
| D06 | Política de retenção muda entre clique e registro. | **Confirmado:** snapshot na primeira ingestão durável, sem recálculo no reenvio. | Definido em BE-002. |
| D07 | Download e compartilhamento. | **Pendente:** streaming, download autenticado ou ambos; efeito de compartilhar URL temporária. | Antes de download/compartilhamento. |
| D08 | Inativação e rotação com fila pendente. | **Confirmado:** inativa recusa configuração/ingestão, incluindo reenvios; fila para intervenção. Rotação revoga token anterior imediatamente. | Sem drenagem automática/sobreposição; implementação de rotação em BE-026. |
| D09 | Proteção e exclusão no storage. | **Pendente:** versionamento, versões antigas, backups, lifecycle e prazo de reconciliação de órfãos. | Antes de purga real/reconciliação automática. |
| D10 | Escopo físico e identidade de instalação. | **Confirmado:** uma câmera/quadra por instância; vínculo do dispositivo imutável. | Troca de quadra exige novo dispositivo. |
| D11 | Privacidade, retirada e conservação de metadados. | **Pendente:** responsáveis, informação aos participantes, menores, atendimento, prazos e bloqueio emergencial. | Antes de gravar/publicar participantes reais. |
| D12 | Sessões de usuários e recuperação. | **Admin confirmado:** JWT de 60 minutos, sem refresh, revogação imediata e vínculo verificado a cada requisição. **Jogadores/recuperação/email pendentes.** | Recorte administrativo definido em BE-002; não libera contas de jogadores. |
| D13 | Relógio e tolerância temporal. | **Confirmado para ingestão:** offset obrigatório, até 300 s futuros e sem limite de atraso além da política de chegada vencida. Fuso IANA por quadra. | Definido em BE-002; filtros civis seguem histórias de consulta. |
| D14 | Metas e operação do piloto. | **Pendente:** número de arenas/contas, volume, latência, disponibilidade, limite offline, suporte, RPO/RTO e critério de encerramento. | Antes da homologação operacional do piloto. |

### Fechamento BE-002 — 15/10/2026

O responsável pelo produto confirmou expressamente nesta sessão os três conjuntos
de respostas abaixo. Este fechamento substitui as propostas anteriores de
D04–D06, D08, D10 e dos recortes administrativo de D12 e temporal de D13.
As escolhas técnicas identificadas como tal foram feitas dentro do escopo BE-002;
não equivalem à homologação da implementação. A fonte dos schemas e erros é
[`backend/openapi.yaml`](../backend/openapi.yaml); não há endpoints implementados
por esta entrega documental.

| Decisão | Resultado confirmado pelo produto | Limites |
|---|---|---|
| D04 | Retenção padrão de 2 dias; inteiro de 1 a 30 dias; dia = duração de 24h. | Sem alteração retroativa implícita. |
| D05 | Evento novo já vencido é registrado e confirmado sem publicação, preservando o clique original. | Validar origem, objeto e demais regras antes do commit; elegibilidade para purga não executa deleção nesta história. |
| D06 | Usar a política vigente na primeira ingestão durável, persistindo o snapshot e a expiração. | Reenvio não recalcula prazo; falha/rollback não estabelece política. Corridas convergem para o primeiro commit. |
| D08 | Empresa, quadra ou dispositivo inativo recusa configuração e ingestão; fila local permanece para intervenção. Rotação revoga o token antigo imediatamente. | Sem drenagem automática nem sobreposição de credenciais; vale também antes de responder a reenvio já registrado. Rotação/inativação são implementadas em BE-026. |
| D10 | Uma câmera/quadra por instância; dispositivo permanece vinculado à mesma quadra. | Troca de quadra exige novo dispositivo; não realocar empresa da quadra nem histórico. |
| D12 — administração | JWT com validade absoluta de 60 minutos, sem refresh; logout revoga imediatamente; usuário, sessão, papel e vínculo verificados em cada requisição. | Políticas de jogadores, recuperação e verificação de email permanecem pendentes. |
| D13 — ingestão | Offset obrigatório; aceitar evento até 300 segundos à frente do relógio UTC do servidor, inclusive a borda. | Mais de 300 s retorna 422; não há limite de atraso independente da retenção. Repetição idêntica usa o registro existente e não refaz validação temporal de evento novo. |

Expiração é `timestamp_evento + retencao_dias_aplicada × 24h`. O evento já está
vencido quando `agora >= expira_em`, inclusive na borda. Instantes equivalentes
com offsets diferentes são o mesmo timestamp para comparação idempotente.
Datas civis de consulta usam fuso IANA da quadra, sem alterar esses instantes.

#### Autorização e sessão administrativa — escolhas técnicas

- `PLATFORM_ADMIN` pode administrar todas as empresas; somente esse papel cria
  empresas. `COMPANY_ADMIN` cria/consulta quadras e dispositivos e publica
  configuração apenas nas empresas com vínculo ativo. Não recebe poderes de
  plataforma pelo cadastro comum. A consulta de empresas é filtrada por vínculo.
- Todas as rotas administrativas, exceto login, exigem Bearer. Ausência, token
  inválido/expirado, sessão revogada ou usuário inativo retornam 401. Papel
  insuficiente ou vínculo ausente/inativo retorna 403, antes de revelar o recurso
  de outra empresa. Identificador conhecido não concede autorização.
- JWT usa assinatura RS256, algoritmo explicitamente permitido, `iss`, `aud`,
  `sub`, `jti`, `iat` e `exp` obrigatórios. `exp = iat + 3600`; sem tolerância
  adicional após expiração. Claims de papel não substituem a consulta ao vínculo.
  Sessão persistida por hash de `jti`, usuário, criação, expiração e revogação.
  Banco de sessões indisponível falha fechado (503), sem aceitar só a assinatura.
- Tokens Bearer são enviados apenas por header, nunca URL/cookie de autenticação.
  Respostas de login/credencial/configuração usam `Cache-Control: no-store`.
  Não há autenticação HTTP Basic, refresh ou cadastro público de administradores.
- Senhas administrativas têm 12–72 bytes UTF-8 e BCrypt com custo inicial 12;
  validar o limite em bytes antes do hash, sem truncamento. Email administrativo
  recebe trim e lowercase com locale neutro e unicidade; política de jogador não
  é fechada por essa escolha. Login inválido usa resposta 401 genérica.
- Bootstrap é uma execução operacional explícita de BE-003, fora da API e sem
  habilitação por padrão: email/senha vêm do ambiente ou secret local ignorado.
  Sem os dois valores, não cria administrador. Repetir para o mesmo administrador
  global não duplica nem troca senha; se o email já pertence a outra identidade,
  falha sem elevar papel. Chave privada e senhas nunca são versionadas ou logadas.
  Provisionar `COMPANY_ADMIN` exige o operador de plataforma, com vínculo explícito;
  não se cria rota pública nem senha padrão para facilitar testes. O mecanismo
  operacional de provisionamento faz parte de BE-003, com os mesmos controles.
- Token de dispositivo usa 32 bytes aleatórios criptográficos, representação
  base64url sem padding, hash SHA-256 persistido e comparação em tempo constante.
  Só a emissão inicial/rotação devolve o segredo; consultas não expõem hash/token.
  Isso não substitui BCrypt das senhas humanas. TLS é requisito fora do loopback.

#### Configuração e erros — escolhas técnicas

BE-007: correlação UUID gerada pelo servidor, compartilhada entre resposta,
auditoria e logs; header recebido não é confiado. Sucessos administrativos
participam da mesma transação da escrita, com log após commit. Recusas HTTP são
registradas separadamente; indisponibilidade dessa gravação retorna 503, sem
detalhes internos. Consulta por empresa exige autorização da empresa; investigação
por correlação é exclusiva de plataforma, sem listagem global por conveniência.
Snapshots não contêm payload, headers, emails, IPs, credenciais ou URLs; não há
purga automática de auditoria. Erros inesperados usam `INTERNAL_ERROR` (500),
preservando os códigos de configuração/ingestão v1.

Implementação BE-006 em 16/10/2026: a lista `DEVICE_ALLOWED_BUCKETS` é controlada
pela configuração do servidor da plataforma, separada por vírgulas. Lista vazia
impede nova emissão com 503; bucket fora dela é recusado com 422. Essa lista
controla novos cadastros; não é um mecanismo de revogação de credenciais já
emitidas, nem valida existência/permissão AWS (BE-010). Cadastros administrativos
continuam permitidos em origem inativa, mas a autenticação do dispositivo recusa
qualquer um dos três níveis inativos com 403 após validar a credencial.

A consulta por ID global do dispositivo filtra o vínculo administrativo no SQL.
Para administrador de empresa, recurso ausente ou de outra empresa recebe o
mesmo 403, sem revelar existência; plataforma recebe 404 para ID ausente.
Provisionamento repetido/conflitante nunca recupera nem substitui o token.
O serviço de autenticação fica pronto para BE-008/011, sem expor essas rotas em BE-006.

- Cadastro gera IDs opacos estáveis de empresa/quadra no servidor; `dispositivo_id`
  é informado e segue CL1. Cadastro não aceita papel/vínculo vindos de campos extras.
  Fuso é obrigatório na quadra e validado por `ZoneId`, sem assumir fuso do servidor.
- Listagens administrativas usam página zero-based de 20 itens, máximo 100, com
  ordenação estável por ID. Não há listagem administrativa global por conveniência.
- Perfil da quadra exige janela antes/depois, segmentação e keyframes separados.
  Instalação/câmera e parâmetros operacionais são publicados por dispositivo.
  Publicação é versionada no servidor, com autor/instante e comparação de
  `base_version` para evitar perda concorrente. Primeira publicação usa null;
  subsequentes exigem a versão atual. Antes de publicar, validar perfil efetivo
  mesclado; configuração RTSP ativa precisa de URL válida com host.
- Gateway recebe exatamente `version`, `active`, `settings`, sem autor/instante
  na raiz. A resposta 200 é apenas para origem ativa; origem inativa recebe 403
  (credencial inválida/revogada recebe 401). O cliente existente persiste inativação
  para ambos. Sem configuração publicada, retornar 503 retentável, sem inventar
  configuração nem devolver 404 terminal. O caso ativo é entregue com `active=true`.
- Omitir uma chave mantém o valor anterior no cache v1. Não publicar null para
  removê-la; quadra, dispositivo e bucket são provisionados localmente no gateway.
  URLs RTSP só aparecem na publicação/consulta restrita e no endpoint do dispositivo;
  consultas genéricas e erros nunca as devolvem. Reativação remota não descarta fila.
- JSON malformado/header incompatível: 400; domínio inválido: 422; credencial: 401;
  autorização: 403; recurso autorizado inexistente: 404; unicidade/conteúdo/versão:
  409; limite de requisições: 429; dependência indisponível: 503. 429/503 incluem
  `Retry-After`. Erro usa código estável, mensagem sem valores sensíveis e UUID de
  correlação gerado pelo servidor; violações citam só campos e códigos, nunca valores.
  Não redirecionar requisições autenticadas. Nada neste recorte libera grade/player.

#### Implementação BE-008–012 — escolhas técnicas

V7 mantém histórico e ponteiro atual de configuração; lock no dispositivo serializa
publicações da mesma base. Perfil da quadra, valores anteriores e patch são
mesclados antes da validação. Fonte omitida na primeira publicação assume RTSP;
URL válida é obrigatória. Inteiros seguem a semântica lexical do gateway.

V8 usa FKs compostas de origem e trigger de imutabilidade. Timestamp de ingestão
exige offset e precisão representável em microssegundos (cliente v1), sem
truncamento silencioso, com anos UTC de 1 a 9998. Snapshot de retenção e chegada
vencida ficam duráveis; `purged_at` só simula o estado futuro nos testes.

Replays usam READ COMMITTED e INSERT ON CONFLICT DO NOTHING; o perdedor consulta
a linha confirmada e compara campos imutáveis. A transação termina antes do ACK.
A auditoria administrativa permanece atômica na publicação. O adaptador S3 é
ObjectStorageService, na camada service, com cliente configurado em config; não
introduz ports ou módulos de outra arquitetura. Testes usam SDK real/HTTP S3
substituído; homologação AWS segue pendente.

#### Verificação de objeto antes de BE-010 — escolha técnica

O backend confere bucket provisionado e chave exata
`replays/{dispositivo_id}/{evento_id}.mp4` para a origem autenticada antes de
consultar storage. Nunca segue URL recebida no payload. O adaptador é somente
leitura nesta história de integração; não envia nem exclui objetos.

Para evento novo, usar HEAD para tamanho positivo e metadata SHA-256 e depois
GET em streaming, calculando SHA-256 dos bytes e comparando ao payload. Fixar
VersionId quando houver; sem versão, condicionar GET ao ETag observado por
`If-Match`, tratando alteração como 503. ETag é um identificador de concorrência,
não checksum. Persistir a referência efetivamente verificada (incluindo versão
quando disponível). A política/IAM do bucket precisa impedir sobrescrita do
objeto verificado; sem imutabilidade, a conferência não garante bytes futuros.

Hash/metadata divergente retorna 409; objeto vazio retorna 422; ausência, timeout,
429/5xx remotos, alteração concorrente ou falha de permissão do adaptador retornam
503 retentável, com correlação para intervenção operacional. Nunca converter
ausência de objeto/erro IAM em 404 terminal para o gateway. Referência de outra
origem retorna 403 sem chamada ao storage.

No reenvio idêntico de evento já registrado, autenticar/autorizar e comparar os
campos imutáveis antes de qualquer acesso ao objeto. Confirmar registro existente
mesmo se já purgado, sem recriar objeto, prazo ou política. Divergência retorna
409. O ACK de evento novo só sai após commit durável, nunca após apenas HEAD/GET.

Essa estratégia comprova igualdade dos bytes lidos, não decodificação do vídeo,
qualidade na quadra ou titularidade das imagens. Exige permissões GET (e KMS se
aplicável), transfere o clipe inteiro e tem custo/latência a medir em D14. Não
exige mudar o uploader v1 para enviar checksum nativo S3. Versionamento, purga e
lifecycle continuam em D09. Referências técnicas:
[integridade S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity.html),
[GetObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetObject.html) e
[OpenAPI 3.1](https://spec.openapis.org/oas/v3.1.0).

#### Dependências depois de BE-002

- BE-003: recorte administrativo de D12 definido; aguarda revisão/integração de
  BE-001 e BE-002, sem implementar autenticação nesta entrega documental.
- BE-004/009: D04–D06 definidos; BE-006: D10 definido; BE-008/011/026: D08 definido;
  BE-009/011: tolerância de D13 definida; BE-010: estratégia de objeto definida.
  As dependências de implementação de cada história continuam válidas.
- D02, D03, D07, D09, D11, D14 e o recorte jogador de D12 continuam abertos e
  bloqueiam somente as histórias que dependem deles. Não foram aprovados por
  associação ao piloto ou aos limites administrativos.

D02 e D03 continuam abertas. O primeiro recorte usa o fechamento BE-002 abaixo;
as políticas de jogadores e conservação não foram presumidas.

## 4. Primeira entrega proposta

O recorte abaixo está dividido em histórias nas [sprints 1 e 2](sprints_backend.md#3-sprint-1--fundação-e-operação-administrativa). A ordem das tarefas e seus aceites são mantidos no backlog, sem duplicar contratos aqui.

**Objetivo:** o gateway atual consulta configuração, registra um lance válido e recebe confirmação durável, sem duplicidade e sem acesso cruzado entre empresas.

1. Criar projeto na stack já prevista: Java/Spring Boot, PostgreSQL e Flyway. Verificar versões ao implementar.
2. Preparar ambiente local reproduzível, configuração externa e migration do recorte necessário.
3. Provisionar administração de forma controlada e cadastrar empresa, quadra e dispositivo.
4. Entregar configuração e ingestão v1 com autenticação, validação de origem/objeto e unicidade de evento.
5. Verificar reenvio concorrente, conflito de conteúdo, perda de resposta após commit e indisponibilidade temporária do storage.

D04–D06 e D10 estão definidos no fechamento BE-002. A consulta ao storage pode ser desenvolvida com substituto local; a integração real com AWS é uma etapa de homologação separada.

**Aceite da entrega:** cliente existente aceita a configuração e o ACK; evento repetido cria uma linha; conteúdo divergente gera conflito; origem não autorizada é recusada; sucesso só é devolvido depois do commit. Essa entrega ainda não constitui o piloto completo.

## 5. Próximas entregas

| Entrega | Dependências principais | Resultado |
|---|---|---|
| Encontrar e assistir | D02, D07, D13 | Navegação temporal e reprodução autorizada, com expiração aplicada em toda emissão de acesso. |
| Conta e biblioteca | D02, D03, D12 | Salvar/listar/remover com vínculo único e conservação definida. |
| Limpeza e operação | D03, D08, D09 | Purga coordenada com salvamento, trabalho durável, reconciliação e suporte. |
| Piloto real | D11, D14 e homologação técnica | Hardware/cloud/player reais e critérios de operação demonstrados. |

Autorizações administrativas nascem com os cadastros. Expiração lógica nasce com as consultas. A exclusão automática só entra quando estiver recuperável e coerente com os vínculos salvos.

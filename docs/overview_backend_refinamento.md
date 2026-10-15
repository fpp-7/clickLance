# Backend — visão e decisões

Atualizado em 15/10/2026. Estado: refinamento; implementação cloud ainda não iniciada.

## 1. Por onde começar

**Já há definição suficiente para iniciar a fundação e a integração com o gateway.** Acesso aos vídeos e conservação da biblioteca precisam de decisão antes das funcionalidades correspondentes. Não é necessário resolver todo o produto futuro para criar o primeiro fluxo técnico.

| Referência ativa | Responsabilidade |
|---|---|
| Este documento | Escopo, decisões e ordem de implementação. |
| [Catálogo de requisitos](requisitos_e_regras_mvp.md) | RN, RF, RNF e critérios de aceite; IDs preservados. |
| [Sprints e histórias](sprints_backend.md) | Backlog executável BE-001–031; dependências, escopo e verificações por tarefa. |
| [Contrato cloud v1](contrato_cloud_gateway_v1.md) | Payloads e comportamento exigido pelo gateway existente. |
| [Modelo de dados](modelagem_banco_de_dados.md) | Invariantes que a primeira migration precisa garantir. |

Quando houver implementação, migrations e OpenAPI versionados no backend serão as referências executáveis de dados e API. Não manter cópias de DDL ou JSON em vários documentos.

## 2. Escopo do primeiro backend

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
| D04 | Limites de retenção pública. | Existente: default de dois dias nas histórias. **Pendente:** faixa permitida; propor dias como duração de 24h. | Antes da migration de política e cálculo de expiração. |
| D05 | Evento chega quando prazo já acabou. | **Proposta:** registrar/confirmar sem publicar; purgar conforme política. | Antes do aceite final da ingestão tardia. |
| D06 | Política de retenção muda entre clique e registro. | **Proposta:** snapshot da política na primeira ingestão; não recalcular lances existentes. | Antes do cálculo persistido de expiração. |
| D07 | Download e compartilhamento. | **Pendente:** streaming, download autenticado ou ambos; efeito de compartilhar URL temporária. | Antes de download/compartilhamento. |
| D08 | Inativação e rotação com fila pendente. | **Proposta:** bloquear novos eventos de origem inativa; conservar fila para intervenção. Decidir se haverá drenagem autorizada e sobreposição de chaves. | Antes de entregar inativação/rotação. |
| D09 | Proteção e exclusão no storage. | **Pendente:** versionamento, versões antigas, backups, lifecycle e prazo de reconciliação de órfãos. | Antes de purga real/reconciliação automática. |
| D10 | Escopo físico e identidade de instalação. | **Proposta:** uma câmera/quadra por instância; cadastro de dispositivo sem realocação no piloto. | Antes de fechar origem/alocação no schema. |
| D11 | Privacidade, retirada e conservação de metadados. | **Pendente:** responsáveis, informação aos participantes, menores, atendimento, prazos e bloqueio emergencial. | Antes de gravar/publicar participantes reais. |
| D12 | Sessões de usuários e recuperação. | **Pendente:** validade, revogação/logout, verificação de email e recuperação antes de contas reais. | Antes de autenticação de contas reais. |
| D13 | Relógio e tolerância temporal. | **Proposta:** offset obrigatório e fuso por quadra; **pendente:** desvio futuro máximo e tratamento de relógio incorreto. | Antes de validação temporal e consultas locais. |
| D14 | Metas e operação do piloto. | **Pendente:** número de arenas/contas, volume, latência, disponibilidade, limite offline, suporte, RPO/RTO e critério de encerramento. | Antes da homologação operacional do piloto. |

D02 e D03 são as principais escolhas de produto ainda abertas. D04–D06 e D10 precisam de uma decisão registrada ao implementar política/origem/expiração; são refinamentos delimitados, não motivo para suspender a criação do projeto.

## 4. Primeira entrega proposta

O recorte abaixo está dividido em histórias nas [sprints 1 e 2](sprints_backend.md#3-sprint-1--fundação-e-operação-administrativa). A ordem das tarefas e seus aceites são mantidos no backlog, sem duplicar contratos aqui.

**Objetivo:** o gateway atual consulta configuração, registra um lance válido e recebe confirmação durável, sem duplicidade e sem acesso cruzado entre empresas.

1. Criar projeto na stack já prevista: Java/Spring Boot, PostgreSQL e Flyway. Verificar versões ao implementar.
2. Preparar ambiente local reproduzível, configuração externa e migration do recorte necessário.
3. Provisionar administração de forma controlada e cadastrar empresa, quadra e dispositivo.
4. Entregar configuração e ingestão v1 com autenticação, validação de origem/objeto e unicidade de evento.
5. Verificar reenvio concorrente, conflito de conteúdo, perda de resposta após commit e indisponibilidade temporária do storage.

Antes de persistir a política de expiração, registrar D04–D06. Antes de fechar o vínculo de origem, registrar D10. A consulta ao storage pode ser desenvolvida com substituto local; a integração real com AWS é uma etapa de homologação separada.

**Aceite da entrega:** cliente existente aceita a configuração e o ACK; evento repetido cria uma linha; conteúdo divergente gera conflito; origem não autorizada é recusada; sucesso só é devolvido depois do commit. Essa entrega ainda não constitui o piloto completo.

## 5. Próximas entregas

| Entrega | Dependências principais | Resultado |
|---|---|---|
| Encontrar e assistir | D02, D07, D13 | Navegação temporal e reprodução autorizada, com expiração aplicada em toda emissão de acesso. |
| Conta e biblioteca | D02, D03, D12 | Salvar/listar/remover com vínculo único e conservação definida. |
| Limpeza e operação | D03, D08, D09 | Purga coordenada com salvamento, trabalho durável, reconciliação e suporte. |
| Piloto real | D11, D14 e homologação técnica | Hardware/cloud/player reais e critérios de operação demonstrados. |

Autorizações administrativas nascem com os cadastros. Expiração lógica nasce com as consultas. A exclusão automática só entra quando estiver recuperável e coerente com os vínculos salvos.

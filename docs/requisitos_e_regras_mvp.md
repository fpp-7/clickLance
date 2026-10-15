# Backend — catálogo de requisitos

Atualizado em 15/10/2026. Fonte única do inventário de RN/RF/RNF do backend; ainda em refinamento.

**Confirmado:** piloto gratuito com biblioteca; pagamentos depois. **Compatibilidade:** ingestão e configuração seguem o [contrato cloud v1](contrato_cloud_gateway_v1.md). **Proposto:** os demais ajustes abaixo aguardam fechamento conforme a entrega correspondente. Não há backend implementado ou homologado nesta revisão.

A [visão do backend](overview_backend_refinamento.md) reúne escopo, decisões pendentes e ordem de trabalho. Este catálogo contém os requisitos e seus critérios, sem repetir payloads de API, DDL ou sprints. O inventário tem **24 RF, 38 RN e 12 RNF**, incluindo os complementos propostos.

## 1. RF existentes, refinados

RF-MVP-01–14 preservam os IDs originais. US-01–12 identificam as histórias de origem, agora consolidadas nas linhas abaixo; não há um segundo contrato de histórias ou endpoints. O refinamento e os aceites abaixo são propostas; a compatibilidade de ingestão/configuração v1 é exigida pelo cliente existente.

| ID / história | Função já mapeada | Refinamento | Aceite essencial |
|---|---|---|---|
| RF-MVP-01 / US-01 | Cadastrar empresa. | Exigir admin da plataforma; retenção validada; fuso padrão e status explícitos. “2 a 7” hoje é exemplo, não limite aprovado. | Visitante e admin de outra empresa não criam empresas; valores fora da política são recusados. |
| RF-MVP-02 / US-02,06 | Cadastrar quadra. | Gerir/listar/editar/inativar com escopo da empresa; definir fuso e catálogo público. | Empresa A não altera quadra B; catálogo omite recursos sem visibilidade. |
| RF-MVP-03 / US-03 | Cadastrar dispositivo e emitir token. | ID compatível com CL1 (1–32 caracteres permitidos); vínculo de origem controlado; rotação/revogação. | Token aparece somente na emissão; hash no banco; credencial de um dispositivo não autentica outro. |
| RF-MVP-04 / US-04 | Sincronizar configuração. | Entregar envelope v1 exato e parâmetros aceitos no cliente; versões publicadas rastreáveis. | Gateway atual aceita resposta válida; chave desconhecida não é publicada; eventos antigos preservam contexto. |
| RF-MVP-05 / US-05 | Receber lance autenticado. | Receber contrato v1; validar identidade, campos, origem e referência privada. | Payload real do cliente é aceito e confirmado somente após persistência; origem incorreta é recusada. |
| RF-MVP-06 / US-05 | Resolver empresa/quadra. | Derivar do dispositivo autenticado e confrontar `quadra_id` informado; manter origem histórica. | Campo adulterado não muda destino; IDs de outra empresa não são aceitos. |
| RF-MVP-07 / US-05 | Não duplicar lances. | Substituir proximidade temporal por `evento_id`, constraint e comparação de conteúdo imutável. | Reenvios concorrentes geram um registro; conteúdo divergente retorna 409; eventos distintos próximos continuam distintos. |
| RF-MVP-08 / US-05 | Registrar e calcular expiração. | Guardar evento, bucket, chave, checksum, instante recebido e política aplicada; tratar chegada tardia. | Repetição mantém expiração original; mudança de política não a altera silenciosamente. |
| RF-MVP-09 / US-07 | Buscar por quadra/data/horário. | Paginação; fuso; filtros determinísticos e limites; disponibilidade calculada pelo tempo. | Borda de slot aparece uma vez; data local é correta; cron parado não expõe expirados. |
| RF-MVP-10 / US-08 | Consultar lance por ID. | Mesma autorização da grade/biblioteca; metadados não incluem URL permanente do arquivo. | Expirado salvo por A não fica disponível para B pelo ID; inexistente/inacessível não revela dados privados. |
| RF-MVP-11 / US-09 | Cadastro e autenticação. | Conta global, normalização documentada de email, conta inativa, recuperação e encerramento de sessão. | Email equivalente não cria conta duplicada; senha nunca retorna; conta inativa não obtém acesso válido. |
| RF-MVP-12 / US-10 | Salvar na biblioteca. | Autorização, prazo, cota pendente, unicidade e coordenação com purga; repetição com sucesso estável. | Novo vínculo só antes da expiração; repetição de vínculo existente não duplica, mesmo após expiração; disputa com purga não produz biblioteca quebrada. |
| RF-MVP-13 / US-11 | Consultar biblioteca. | Paginação, autorização por dono e reprodução separada; conservação conforme política a decidir. | A não lista biblioteca de B; expirado salvo continua acessível somente aos autorizados, salvo bloqueio/remoção. |
| RF-MVP-14 / US-12 | Expirar e purgar. | Separar indisponibilidade lógica, intenção durável, deleção remota e confirmação; retries e último vínculo removido. | Falha após deleção é retomada; duas instâncias não perdem trabalho; falha S3 não vira “purgado”. |

## 2. RN existentes, refinadas

| ID | Regra atual | Refinamento proposto / situação |
|---|---|---|
| RN-MVP-01 | Empresa define dias de retenção. | Manter; definir default, mínimo/máximo e se dia significa 24h. Guardar política aplicada ao lance. |
| RN-MVP-02 | Quadra pertence a uma empresa. | Manter; não transferir histórico ao alterar cadastros. |
| RN-MVP-03 | Dispositivo pertence a uma quadra. | Manter; propor alocação imutável no piloto. Mudança de instalação exige identidade nova ou alocação histórica. |
| RN-MVP-04 | Identificador único de dispositivo. | Manter globalmente único e compatível com CL1: `[A-Za-z0-9_-]`, 1–32 caracteres. |
| RN-MVP-05 | Token próprio no header. | Manter `X-Device-Token`; validar token e dispositivo em conjunto. |
| RN-MVP-06 | Somente dispositivo ativo envia. | Ampliar para situação da empresa/quadra; separar credencial inválida (401) de bloqueio operacional (403), sujeito à política de inativação. |
| RN-MVP-07 | Armazenar apenas hash do token. | Manter; emissão/rotação revela segredo uma vez; logs e respostas de consulta não o contêm. |
| RN-MVP-08 | Backend resolve quadra pelo dispositivo. | Manter; `quadra_id` do contrato v1 é uma afirmação a conferir, nunca autorização. |
| RN-MVP-09 | Registrar instante original do clique. | Manter com offset obrigatório; separar `timestamp_evento` e `recebido_em`. Não trocar horário por chegada à API. |
| RN-MVP-10 | Agrupar por data e slot de uma hora. | Manter como navegação, sem entidade Partida; conversão pelo fuso da quadra e intervalo semiaberto. |
| RN-MVP-11 | Configuração de captura da quadra. | Manter perfil por quadra, mas separar janela antes/depois da segmentação; câmera/latência e limites físicos podem ter configuração da instalação. Publicar versão efetiva compatível com v1. |
| RN-MVP-12 | Edge gera clipe e faz upload. | Manter responsabilidade. Retirar exigência de “sem re-encoding”: gateway atual pode recodificar para cumprir a janela. |
| RN-MVP-13 | Cada acionamento válido gera um lance. | Precisar: cada evento de vídeo válido registrado gera no máximo um lance; aceite local não garante registro cloud imediato nem recuperação de vídeo inexistente. |
| RN-MVP-14 | Mesmo dispositivo em cinco segundos = mesmo lance. | Substituir por identidade estável v1. Cooldown de cinco segundos permanece no aceite local e não é deduplicação cloud. |
| RN-MVP-15 | Todo lance recebido nasce disponível. | Condicionar a objeto validado, origem ativa, política de acesso e prazo vigente. Envio já expirado precisa da decisão D05. |
| RN-MVP-16 | Expiração = clique + dias da empresa. | Preservar como base existente; propor duração em múltiplos de 24h e snapshot na primeira ingestão. Alterações retroativas exigem operação explícita. |
| RN-MVP-17 | Expirado deixa a vitrine. | Aplicar a grade, detalhe, player e download; não depender de atualização de status pelo cron. |
| RN-MVP-18 | Expirado sem usuário é deletado do S3. | Manter objetivo; bloquear novos vínculos e registrar intenção antes de apagar; confirmar resultado remoto e considerar configuração de versões. |
| RN-MVP-19 | Expirado salvo permanece acessível. | Manter biblioteca no piloto; conservação e cota pendentes. Regras de retirada/bloqueio podem suspender o acesso. |
| RN-MVP-20 | Guardar chave para exclusão segura. | Ampliar para bucket, chave, checksum e identidade/versão quando aplicável; impedir compartilhamento acidental do mesmo objeto entre lances. |
| RN-MVP-21 | Só salvar disponível e não expirado. | Revalidar autorização e prazo dentro da operação coordenada. Já estar salvo permite reconhecer repetição, sem conceder novo direito. |
| RN-MVP-22 | Um vínculo por usuário/lance. | Manter constraint; propor 201 na criação e 200 no reenvio, sem 409 para repetição idêntica. |
| RN-MVP-23 | Biblioteca preserva acesso após vitrine. | Manter o princípio; retirar promessa absoluta de eternidade até fechar prazo, cotas, encerramento de conta e retirada de conteúdo. |

## 3. Complementos propostos

### 3.1. Novos requisitos funcionais propostos

| ID | Função | Resultado verificável | Prioridade sugerida |
|---|---|---|---|
| RF-MVP-15 | Gerir papéis e vínculos administrativos por empresa. | Admin A não recebe nem concede poderes em B; primeiro admin nasce por provisionamento controlado. | Primeira entrega. |
| RF-MVP-16 | Recuperar conta e encerrar sessões. | Recuperação usa token com prazo/uso único; logout/inativação seguem política de revogação definida. | Antes de contas reais. |
| RF-MVP-17 | Autorizar reprodução e, se aprovado, download. | Endereço temporário só é emitido para acesso permitido e dentro do prazo; download é decisão explícita. | Primeira consulta de vídeo. |
| RF-MVP-18 | Receber e consultar saúde da instalação. | Último contato, versão, captura e fila permitem identificar indisponibilidade. | Antes de operação desassistida; exige extensão do cliente. |
| RF-MVP-19 | Registrar auditoria e consultar falhas operacionais. | Operação identifica ator, recurso, instante, resultado e evento sem expor segredos. | Primeira entrega. |
| RF-MVP-20 | Bloquear/retirar conteúdo em todas as superfícies. | Vídeo bloqueado não recebe novos acessos pela grade, detalhe ou biblioteca; ação e motivo rastreáveis. | Antes de imagens reais. |
| RF-MVP-21 | Remover vínculo e solicitar encerramento de conta. | Remover um usuário não remove o vídeo de outro; último vínculo de expirado provoca reavaliação da conservação. | Antes de contas reais. |
| RF-MVP-22 | Reconciliar storage e registros. | Objeto sem registro, registro sem objeto e falha de purga são detectados e tratados sem apagar trabalho legítimo pendente. | Antes de limpeza automática em produção. |
| RF-MVP-23 | Descobrir arena/quadras e datas/slots com lances. | Jogador encontra a quadra sem conhecer UUID interno; contagens usam mesma autorização e expiração da listagem. | Jornada do piloto. |
| RF-MVP-24 | Gerir ciclo operacional de dispositivos e configurações. | Rotação, inativação e publicação de versão são autorizadas, auditadas e coerentes com eventos pendentes. | Primeira entrega. |

### 3.2. Novas regras propostas

| ID | Regra de negócio proposta | RF relacionados |
|---|---|---|
| RN-MVP-24 | Toda ação administrativa exige papel e vínculo válido com a empresa; cadastro público não cria administrador. | 01–03,15,19,24 |
| RN-MVP-25 | Credencial de dispositivo pode ser rotacionada/revogada; efeito sobre fila pendente e eventual sobreposição de chaves deve ser explícito. | 03–05,24 |
| RN-MVP-26 | Inativação de empresa/quadra/dispositivo bloqueia novas operações definidas na matriz; não implica automaticamente apagar histórico ou bibliotecas. | 01–06,10,13,24 |
| RN-MVP-27 | Instantes usam offset obrigatório; filtros civis usam fuso IANA da quadra. Desvio futuro tem limite a definir; atraso offline não é erro de relógio por si só. | 05,08–10,23 |
| RN-MVP-28 | Envio tardio preserva clique e identidade; disponibilidade e confirmação seguem política D05, sem rejuvenescimento silencioso do conteúdo. | 05,07,08,14 |
| RN-MVP-29 | Um dispositivo só referencia objetos no bucket autorizado e em `replays/{dispositivo_id}/{evento_id}.mp4`; `quadra_id` deve coincidir com origem cadastrada. | 05,06,08,14 |
| RN-MVP-30 | `evento_id` é único e seu conteúdo imutável. Repetição idêntica retorna confirmação original; divergência não sobrescreve registro nem objeto. Um objeto pertence a um único lance. | 05,07,08,22 |
| RN-MVP-31 | Visibilidade, prazo, bloqueio e direito do usuário são verificados em toda emissão de acesso; URL temporária não é armazenada como identidade do vídeo. | 09,10,13,17,20 |
| RN-MVP-32 | Salvar e declarar um lance elegível para purga são decisões coordenadas. Após intenção de purga confirmada, não se cria novo vínculo. | 12,14,21 |
| RN-MVP-33 | “Purgado” só ocorre após confirmação da exclusão prevista na política de storage; falhas mantêm trabalho retomável. Remoção do último vínculo reavalia expirados salvos. | 14,21,22 |
| RN-MVP-34 | Biblioteca obedece prazo/condição e cota aprovados; salvar conteúdo não concede propriedade exclusiva das imagens. | 12,13,21 |
| RN-MVP-35 | Bloqueio/retirada vale para todos os usuários que salvaram o mesmo lance; conservar metadados e remover arquivo seguem políticas próprias. | 10,13,17,20,21 |
| RN-MVP-36 | Todo evento registrado é rastreável pela mesma identidade entre gateway, API e trabalhos de limpeza; falha não pode ser apresentada como sucesso. | 05,07,14,18,19,22 |
| RN-MVP-37 | Slots são `[início,fim)` no fuso da quadra. Sem horas, retornar dia local paginado; com apenas uma hora ou faixa inválida, rejeitar. Último slot termina à meia-noite seguinte. | 09,23 |
| RN-MVP-38 | Configuração publicada tem versão, autor e momento; mudanças não alteram eventos já aceitos. Remover uma chave do payload não significa apagá-la do cache v1. | 04,19,24 |

RF relacionados nesta tabela usam o prefixo `RF-MVP-` omitido apenas para legibilidade. Esses novos itens completam o levantamento; ainda precisam de aprovação do escopo e detalhamento em histórias.

## 4. Requisitos não funcionais

| ID | Situação / refinamento | Evidência futura necessária |
|---|---|---|
| RNF-MVP-01 | Existente: autenticação do dispositivo com hash. Ampliar para proteção do transporte, não exposição de segredo e rotação. | Credenciais incorretas/cruzadas/revogadas não autorizam ingestão; logs sem segredo. |
| RNF-MVP-02 | Existente: senha BCrypt e JWT. Completar duração, recuperação, revogação e conta inativa. | Sessão segue a política escolhida; senha/token de recuperação protegidos. |
| RNF-MVP-03 | Existente: isolamento multiempresa. Aplicar autorização no serviço e coerência no banco; identidade global é distinta. | Testes negativos entre empresas em todos os recursos administrativos e de ingestão. |
| RNF-MVP-04 | Existente: índices. Revisar índices para evento único, grade, biblioteca e trabalho pendente. | Planos/tempo de consulta com volume representativo, sem prometer desempenho só pela presença do índice. |
| RNF-MVP-05 | Existente: idempotência resiliente. Exigir unicidade, confirmação durável e comportamento sob concorrência. | Commit com resposta perdida, reenvios concorrentes e divergência. |
| RNF-MVP-06 | Proposto: objetivo mensurável de disponibilidade do replay. | Medir clique→reprodução com amostra e percentis; referência original de cinco minutos ainda não homologada. Separar operação online de backlog offline. |
| RNF-MVP-07 | Proposto: observabilidade por evento e instalação, sem dados sensíveis nos logs. | Operador identifica fila antiga, falha de registro e purga pendente; responsável de suporte definido. |
| RNF-MVP-08 | Proposto: backup e restauração conforme política de conservação. | Restauração real de banco/referências; esclarecer se vídeo removido é recuperável e definir RPO/RTO. |
| RNF-MVP-09 | Proposto: limites de abuso e capacidade. | Limites de login, consultas, emissão de acesso e ingestão; 429 retentável sem perder a identidade do evento. |
| RNF-MVP-10 | Proposto: processamento de limpeza recuperável e consistente. | Falhas em cada etapa e múltiplos workers convergem, sem apagar arquivo de vínculo confirmado. |
| RNF-MVP-11 | Proposto: contratos versionados e verificáveis. | Backend aceita payloads do gateway existente; formato de config/ACK validado em integração. |
| RNF-MVP-12 | Proposto: políticas de privacidade e conservação operáveis. | Fluxo de informação aos participantes, acesso, retirada, conta e retenção definido antes de imagens reais; responsabilidades e critérios aprovados pelo produto. |

Esta revisão não define fundamento jurídico nem atesta conformidade. O requisito aqui é fechar as decisões de produto e viabilizar sua execução; avaliação jurídica específica não foi realizada.

## 5. Cenários críticos de integração

- Reenvio depois de commit com resposta perdida, inclusive após expiração ou purga legítima: reconhecer o registro original sem recriar prazo ou exigir que o objeto já purgado exista.
- Mesmo evento com conteúdo alterado: conflito, sem sobrescrever o registro. Eventos distintos não são fundidos por proximidade temporal.
- Conferir origem, bucket e chave autorizados antes de consultar objetos de um evento novo. Não retornar confirmação de sucesso antes do commit.
- Falha transitória de dependência: resposta retentável conforme o contrato v1; não transformar indisponibilidade em erro terminal de conteúdo.
- Configuração: envelope com exatamente `version`, `active` e `settings`; não publicar campos raiz adicionais ou parâmetros desconhecidos pelo cliente.
- Checksum declarado na metadata não comprova sozinho integridade dos bytes ou validade do vídeo. A confirmação deve refletir a verificação efetivamente implementada.
- Salvar concorrendo com purga, queda após exclusão e dois workers: preservar vínculos confirmados e retomar o trabalho sem declarar exclusão antes de confirmação remota.
- Link de reprodução temporário: limitar validade ao contexto autorizado. Revogação de acesso não recolhe downloads já concluídos nem invalida automaticamente todo link previamente emitido.

## 6. Fora do piloto

Pagamentos, pedidos, preços/pacotes, prévia comercial e concessão por compra ficam para uma fase seguinte por decisão confirmada. Partidas formais, multicâmera, domínios próprios e agendamento cloud de funcionamento não estão aprovados para a primeira entrega.

A [visão original v0.1](documento_visao_produto_replay_quadras_v0_1.pdf) registra a concepção do produto. Os requisitos de captura/corte pertencem ao gateway; associação a partida foi simplificada para navegação por faixas horárias; compra e liberação por pagamento foram adiadas. O escopo do backend é o catálogo deste documento e as decisões registradas na visão atual.

# ClickLance — Parecer de consultoria sobre produto, documentação e arquitetura

Data: 13 de outubro de 2026. Escopo: conteúdo disponível neste repositório.

## 1. Parecer executivo

O ClickLance tem uma proposta de uso clara: permitir que o jogador marque uma jogada sem interromper a partida para usar o celular. A combinação de botão físico, gravação local e consulta posterior é coerente com esse problema. A separação entre buffer e clipe, a conta única do jogador e a organização por empresa e quadra são boas bases.

O estágio evidenciado pelo repositório é **protótipo de captura local com especificação de backend**, ainda sem evidência suficiente de prontidão para operação comercial. O README atribui conclusão a garantias que o código e os testes disponíveis não demonstram. A documentação está organizada, mas várias decisões parecem fechadas antes de os fluxos de falha, acesso, custos e operação terem sido resolvidos.

Minha recomendação é priorizar a fidelidade do replay, a recuperação de falhas e a definição do produto comercial antes de ampliar a plataforma. O principal risco atual é entregar um arquivo que não contenha a jogada marcada. Em seguida vêm perda de processamento em falhas de rede, exposição indevida de vídeos e compromissos de retenção sem sustentabilidade definida.

Este parecer distingue três tipos de evidência:

- **Confirmado no código:** comportamento observado na implementação, em alguns casos reproduzido isoladamente.
- **Contradição documental:** documentos ou exemplos estabelecem contratos incompatíveis.
- **Lacuna ou risco de desenho:** cenário ainda não especificado; não significa que exista uma vulnerabilidade implantada.

Não há backend, frontend, infraestrutura AWS ou migrations executáveis nesta árvore para auditar. Configurações e sistemas eventualmente existentes fora do repositório não foram avaliados. Não foram acessadas credenciais do `.env`, gravadas imagens da câmera nem feitas chamadas à AWS ou à API do produto.

## 2. Material lido e verificações

Foram lidos integralmente:

- [README principal](C:/Users/Felipe/Documents/Projetos/clickLance/README.md).
- [Visão do produto v0.1, cinco páginas](C:/Users/Felipe/Documents/Projetos/clickLance/docs/documento_visao_produto_replay_quadras_v0_1.pdf), com extração textual e inspeção visual de todas as páginas.
- [Requisitos e regras do MVP](C:/Users/Felipe/Documents/Projetos/clickLance/docs/requisitos_e_regras_mvp.md).
- [Modelagem do banco](C:/Users/Felipe/Documents/Projetos/clickLance/docs/modelagem_banco_de_dados.md).
- [Histórias e contratos de API](C:/Users/Felipe/Documents/Projetos/clickLance/docs/historias_de_usuario_e_endpoints.md).
- [Status do projeto](C:/Users/Felipe/Documents/Projetos/clickLance/docs/status_projeto.md).
- [README do gateway](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/README.md).

Também foram revisados o firmware Arduino, a entrada principal do gateway, configuração, logging, DVR, leitura serial, processamento, os dois scripts de diagnóstico, dependências, modelo de ambiente e regras de exclusão do Git.

Seis verificações isoladas executaram métodos reais do DVR e do processador, com arquivos artificiais e substitutos para câmera, SDK e HTTP:

| Cenário | Resultado confirmado |
|---|---|
| Clique durante o terceiro segmento | A seleção inclui os dois anteriores e exclui o segmento atual. |
| Novos segmentos enquanto o evento espera | A seleção muda, embora o timestamp do evento continue o mesmo. |
| Reinício com arquivos de sessão anterior | Após reabrir `seg_0000`, a seleção pode usar segmentos da sessão anterior. |
| Falha simulada de upload | O clipe local gerado é excluído. |
| Falha simulada de notificação | O clipe local gerado também é excluído. |
| Notificação HTTP atual | O payload contém `quadra_id`; não contém `dispositivo_id` nem o header `X-Device-Token`. |

Essas verificações confirmam seleção de arquivos e fluxo de controle. Não validam decodificação, qualidade óptica, câmera, funcionamento elétrico, latência real, DDL no PostgreSQL ou permissões reais da nuvem. O diagnóstico interativo do projeto não foi executado porque exigiria usar o hardware e não cobre os principais cenários acima.

## 3. Achados prioritários

Prioridades: **P0** precisa de decisão ou correção antes de disponibilizar o produto a clientes; **P1** deve estar resolvido antes de um piloto com compromisso de serviço; **P2** deve ser corrigido para evitar ambiguidade, retrabalho ou problemas de escala. As prioridades expressam impacto de negócio, não um escore de vulnerabilidade.

### A01 — P0 — O replay pode não conter a jogada marcada

**Confirmado.** [DVR, seleção de segmentos](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/dvr_manager.py:143) remove o arquivo mais recente com `all_segments[:-1]`. [Processador](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/video_processor.py:82) usa essa seleção imediatamente.

Exemplo: gravação iniciada às 20:00, segmentos nominais de 60 segundos, gol às 20:02:25 e clique às 20:02:30. O arquivo de 20:02–20:03 ainda está aberto. O clipe selecionado cobre aproximadamente 20:00–20:02 e perde o gol. O atraso pode se aproximar da duração de um segmento, além das diferenças de corte nos keyframes.

**Impacto:** falha na promessa principal do produto, com perda de confiança e pedidos de suporte ou reembolso.

**Recomendação:** definir o clipe pelo intervalo do evento, por exemplo `[clique - segundos_antes, clique + segundos_depois]`. Catalogar início, fim e conclusão de cada segmento. Esperar o fechamento dos segmentos necessários e então recortar a janela, com tolerância de corte declarada. Reduzir segmentos ajuda a latência, mas não substitui esse contrato. Cortes exatos podem exigir reencoding nas bordas; cópia de streams precisa respeitar os limites possíveis. A documentação oficial do [segmentador FFmpeg](https://ffmpeg.org/ffmpeg-formats.html#segment) descreve a relação entre keyframes e os cortes.

**Aceite:** um vídeo sintético com relógio visível deve mostrar o instante marcado em cliques no início, meio e fim de segmentos, inclusive sob carga.

### A02 — P0 — A fila desloca o conteúdo do replay

**Confirmado.** O timestamp é usado no nome do arquivo e na notificação, mas não é passado à seleção de vídeo. Um evento das 20:02:30 processado às 20:05 pode receber vídeo próximo de 20:05, com metadados de 20:02:30.

**Impacto:** lances trocados, dificuldade de localizar conteúdo e possível associação do vídeo a outro grupo de jogadores.

**Recomendação:** selecionar por intervalo temporal original e reservar os segmentos até concluir o recorte. Separar processamento local e envio de rede, mantendo estados duráveis. Não basta aumentar o número de workers: sem seleção temporal e proteção dos arquivos, a falha permanece.

### A03 — P0 — Falhas de envio descartam o clipe e não geram retomada automática

**Confirmado.** [Limpeza no `finally`](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/video_processor.py:103) exclui o clipe mesmo após erro de upload ou de API. A fila é somente em memória e o evento é marcado como concluído no worker. O buffer bruto pode ainda permitir recuperação manual por algum tempo, mas não existe mecanismo automático que a execute.

**Impacto:** se o upload falhar, o replay pode nunca chegar à plataforma; se o upload funcionar e a API falhar, fica um objeto sem registro. Um reinício perde eventos ainda não processados.

**Recomendação:** persistir evento e progresso localmente, com estados como `CAPTURADO`, `CLIPE_PRONTO`, `ENVIADO` e `CONFIRMADO`. Repetir upload e registro com identificador estável, espera progressiva e limites de disco. Remover o clipe somente após confirmação durável. Uma fila em SQLite pode atender o piloto; não há necessidade demonstrada de um sistema distribuído complexo.

**Aceite:** interromper internet e processo em cada etapa e confirmar, ao retomar, um único registro com o vídeo correto.

### A04 — P0 — Visibilidade pública, biblioteca privada e URL direta são incompatíveis como estão descritas

**Contradição e risco de desenho.** A RN-MVP-19 promete acesso privado após a expiração, enquanto os exemplos retornam URL direta de S3 e o status recomenda tornar vídeos públicos. A US-08 só determina `404` para lance purgado ou inexistente: falta proibir o acesso público a um lance expirado que foi salvo por outro usuário.

**Impacto:** esconder a linha da grade não revoga acesso a um objeto público. Um link conhecido pode continuar funcionando. Se o bucket for privado, os links atuais exigem outro mecanismo de entrega.

**Recomendação:** manter os objetos privados; autorizar a reprodução no backend; emitir URL temporária apenas após validar visibilidade ou direito do usuário. A consulta individual deve aplicar as mesmas regras da grade e da biblioteca. Não armazenar uma URL assinada como endereço permanente do vídeo. A [AWS documenta URLs e cookies assinados](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/PrivateContent.html) e recomenda impedir acesso direto à origem para evitar contornar as restrições.

**Aceite:** usuário sem vínculo não reproduz um lance expirado, nem pela consulta individual nem por acesso direto ao S3; usuário autorizado recebe um novo link temporário. O prazo dos links deve ser coerente com a expiração pública.

### A05 — P0 — Administração e isolamento entre empresas não estão especificados

**Lacuna.** A [US-01](C:/Users/Felipe/Documents/Projetos/clickLance/docs/historias_de_usuario_e_endpoints.md:44) define autenticação como “Aberta no MVP / Admin”. Quadras e dispositivos não têm contrato claro de autorização. O modelo contém jogadores, mas não papéis ou vínculos dos administradores às empresas.

**Impacto:** se implementado literalmente, um visitante pode cadastrar estruturas e receber credenciais. Apenas aceitar `empresa_id` ou `quadra_id` não comprova que o usuário pode administrar aquela empresa. UUID e índices não garantem isolamento.

**Recomendação:** definir administrador global, administrador de empresa e técnico, com poderes explícitos. Proteger os cadastros desde o início; vincular a empresa autorizada à identidade e validar todos os recursos relacionados. Especificar o efeito de empresa ou quadra inativa, além do dispositivo inativo. A conta global do jogador é adequada e deve ser preservada; não precisa ser artificialmente limitada a uma empresa.

**Aceite:** um administrador da empresa A não cadastra, configura, consulta dados administrativos ou emite tokens da empresa B, mesmo conhecendo seus IDs.

### A06 — P0 — O produto comercial e o MVP têm promessas diferentes

**Contradição.** PDF e README descrevem comercialização, prévia e compra. O MVP permite assistir ao vídeo completo e salvá-lo gratuitamente. A seção final da modelagem propõe adicionar pagamento por um simples vínculo na biblioteca.

**Impacto:** o MVP pode validar interesse em conteúdo gratuito, mas não disposição de pagar. Se o jogador já recebe o arquivo completo, uma cobrança futura pelo mesmo acesso precisa de uma proposta diferente. Reembolso, concessão gratuita e compra não podem compartilhar um vínculo sem origem e estado do direito de acesso.

**Recomendação:** registrar uma decisão comercial: quem paga, pelo quê e qual hipótese o piloto mede. Possibilidades a comparar com clientes incluem assinatura da arena, cobrança por lance ou assinatura do jogador. Um piloto gratuito é válido se tiver duração, limites e objetivo declarados. Se houver venda por clipe, definir prévia separada do original, confirmação de pagamento, expiração durante checkout, reembolso, tentativas repetidas e direito de acesso. Não prometer ausência de alterações futuras no modelo.

### A07 — P1 — Gravação e publicação de pessoas ainda são um ponto aberto

**Lacuna já reconhecida na visão v0.1 e não resolvida nos documentos seguintes.** Gravação contínua, publicação por horário e biblioteca duradoura envolvem pessoas que podem ser identificáveis, inclusive terceiros e menores.

**Impacto:** pedidos de retirada, desacordo sobre divulgação e dificuldade de cumprir políticas de retenção ou solicitações dos titulares.

**Recomendação:** antes de gravar e publicar usuários reais, definir finalidade, base legal adequada, responsabilidades da arena e plataforma, informação aos participantes, atendimento a solicitações, tratamento de menores, remoção e retenção. Salvar um vídeo na biblioteca não prova autoria nem exclusividade sobre as imagens de todos os participantes. A [LGPD, especialmente arts. 6, 14 e 18](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709compilado.htm), é a referência para essa avaliação. A escolha da base e dos termos requer análise jurídica do caso concreto; este review identifica a lacuna de produto e implementação.

### A08 — P1 — “Guardar para sempre” cria um compromisso sem limites

**Lacuna e risco de negócio.** RN-MVP-19, RN-MVP-23 e US-10 preservam vídeos indefinidamente. Qualquer usuário pode salvar qualquer lance público. Não há cota, regra de encerramento de conta ou tratamento de inatividade.

**Impacto:** basta uma conta salvar sistematicamente os vídeos para neutralizar a economia da purga. O estoque cresce com o tempo e continua gerando obrigação de serviço mesmo sem receita correspondente.

**Recomendação:** definir prazo ou condição de conservação, limites por plano, quem paga, exclusão de vínculo e conta, continuidade de acesso e recuperação. Deduplicar o objeto compartilhado entre usuários, como o modelo já permite, é bom; isso reduz cópias, mas não resolve retenção ilimitada. Evitar prometer “para sempre” antes de estabelecer a política e seu financiamento.

### A09 — P1 — A notificação pós-upload não é uma transação atômica

**Erro conceitual documental.** [Decisão 3 do MVP](C:/Users/Felipe/Documents/Projetos/clickLance/docs/requisitos_e_regras_mvp.md:11) chama o fluxo de “único e atômico”. Upload S3 e commit no banco são operações distintas, ainda que haja só um POST.

**Impacto:** falhas intermediárias produzem objeto órfão ou confirmação incerta. A purga de lances registrados não encontra objetos que nunca viraram registros.

**Recomendação:** descrever consistência eventual com retomada idempotente. Reservar uma identidade/chave de upload associada ao evento, validar o objeto no registro e reconciliar uploads sem confirmação após um prazo seguro. Preservar a intenção no Edge. Não exigir uma transação distribuída para resolver esse problema.

### A10 — P1 — Janela de cinco segundos não substitui idempotência

**Falha de desenho.** RN-MVP-14 usa proximidade entre horários como identidade. O DDL só cria um índice comum em `(dispositivo_id, timestamp_evento)`, sem unicidade do evento.

**Impacto:** dois eventos legítimos próximos podem ser fundidos. Requisições simultâneas podem consultar ausência e inserir duas linhas. Exemplos em cadeia, em 0s, 4s e 8s, também dependem de como o algoritmo escolhe a referência. O debounce é uma regra de aceitação física; idempotência é a repetição da mesma operação.

**Recomendação:** criar `evento_id` estável no recebimento do clique, persistido e reutilizado em todas as tentativas. Aplicar unicidade no banco, por exemplo `(dispositivo_id, evento_id)`. Para o mesmo ID e conteúdo, retornar o mesmo resultado; para conteúdo divergente, retornar conflito. Definir separadamente o intervalo mínimo entre cliques.

### A11 — P1 — O gateway atual não implementa o contrato do backend planejado

**Confirmado.** [Notificação atual](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/video_processor.py:181) envia `quadra_id` sem token. A [configuração](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/config.py:30) aponta para `/api/replays`. A especificação exige `/api/v1/replays`, `dispositivo_id` e `X-Device-Token`. Não há consulta à configuração remota.

**Impacto:** um backend construído conforme o documento não aceita o cliente atual. Configurações no banco não alteram o DVR enquanto não existir sincronização.

**Recomendação:** versionar um contrato único e registrar o gateway como ainda pendente de integração. Incluir configuração remota com versão e cópia local válida para inicialização sem internet. Definir quando alterações se aplicam e como manter compatibilidade de segmentos durante mudanças.

### A12 — P1 — Reinícios podem misturar sessões e sobrescrever o buffer

**Confirmado na seleção; risco operacional no ciclo completo.** O DVR usa [nomes sequenciais reiniciados em zero e `-y`](C:/Users/Felipe/Documents/Projetos/clickLance/gateway_captura/src/dvr_manager.py:46). Não há identificação de sessão. Ordenar por modificação permite que arquivos antigos restantes sejam tratados como recentes após uma nova sessão.

**Impacto:** replay de outra sessão, perda de segmentos necessários a eventos pendentes e confusão na reconstrução temporal.

**Recomendação:** nomes únicos por sessão/câmera, catálogo de intervalos, reconciliação no início e reserva dos arquivos usados por eventos. Não resolver apagando todo o buffer ao iniciar, pois ele pode conter trabalho ainda não enviado.

### A13 — P1 — “Gravação ininterrupta” não tem mecanismo de recuperação correspondente

**Confirmado.** Quando FFmpeg encerra inesperadamente, o DVR registra erro e termina sua rotina. A leitura serial pode continuar aceitando cliques. O `stderr` do gravador é descartado, dificultando diagnóstico. O encerramento usa `terminate`, sem confirmação de finalização do arquivo ativo; o worker daemon não é drenado ou persistido antes da saída.

**Impacto:** sistema aparentemente ativo sem gravação útil, cliques sem vídeo e perda de trabalho ao encerrar.

**Recomendação:** supervisor com verificação de progresso real da gravação, reinício controlado, captura de erros, estado de prontidão e encerramento que preserve eventos. O monitoramento deve verificar frames/segmentos recentes, não apenas processo existente. Inicialização automática, recuperação e alerta são requisitos do piloto, não melhorias avançadas.

Há ainda uma possibilidade de corrida na inicialização: a rotina de limpeza usa um evento que só é ativado dentro da thread de gravação. Se a limpeza executar antes disso, pode terminar imediatamente. Definir prontidão antes de disparar rotinas dependentes evita essa ambiguidade.

### A14 — P1 — Uma fila ilimitada e um worker sequencial não oferecem controle de capacidade

**Confirmado quanto à estrutura; carga real não medida.** `Queue()` não tem limite. Um worker concatena, envia e notifica cada evento em sequência. Não há controle de idade da fila, reserva de segmentos ou política de saturação.

**Impacto:** sob internet lenta ou muitos cliques, cresce o atraso; arquivos necessários podem ser limpos antes do processamento. Um bloqueio de etapa pode impedir todos os próximos eventos.

**Recomendação:** filas duráveis por etapa, métricas de idade e tamanho, limites de ocupação de disco, política de degradação e prazo de recuperação. Só aumentar paralelismo depois de tornar temporários exclusivos e controlar CPU, rede e disco. O arquivo fixo `concat_list.txt` é seguro apenas sob o uso sequencial atual; vira conflito se múltiplos workers compartilham a pasta.

### A15 — P1 — Timestamp e fuso não têm uma semântica única

**Contradição.** O gateway envia data local sem offset; requisitos também usam exemplos sem offset; histórias usam `Z`, que indica UTC. A grade exibe `22:14` para um instante `22:14Z`, sem definir fuso da quadra. No horário de São Paulo, esse exemplo corresponde a 19:14 locais.

**Impacto:** o jogador procura no dia ou horário errado; viradas de dia, relógio incorreto e envios offline alteram consultas e expiração.

**Recomendação:** enviar instantes com offset explícito, armazenar instantes em `TIMESTAMPTZ`, ter fuso IANA por local e converter filtros locais para UTC. Sincronizar o relógio do Edge e validar desvios. Usar relógio monotônico para cooldown. Definir intervalos `[inicio, fim)`, sem duplicar o evento exatamente às 23:00 em dois slots.

Também decidir o que acontece com um replay enviado após dias sem internet: pela RN-MVP-16 ele pode chegar já expirado. Preservar horário original e definir explicitamente se haverá janela de disponibilidade após sincronização ou descarte. Não criar silenciosamente `DISPONIVEL` para conteúdo já sem prazo público.

### A16 — P1 — A chave S3 informada precisa de autorização e validação

**Lacuna, sem backend implementado para demonstrar exploração.** O contrato aceita `video_url` e extrai `s3_key`. Não exige bucket confiável, prefixo exclusivo, identidade do evento, existência do objeto ou vínculo com o dispositivo.

**Impacto:** se implementado sem validação, uma credencial de um local pode referenciar objeto de outro. A rotina de purga poderia apagar conteúdo compartilhado ou pertencente a terceiros dentro do bucket. Um upload concluído tampouco demonstra que o vídeo é válido e reproduzível.

**Recomendação:** preferir referência estruturada com bucket/chave autorizados pelo servidor, associada ao evento. Validar origem, prefixo, existência, tamanho e tipo, com limites de processamento. Restringir credenciais do Edge ao upload no seu prefixo; reservar deleção à rotina autorizada. Não permitir múltiplos lances apontando ao mesmo objeto sem modelar essa relação.

### A17 — P1 — Salvar e purgar precisam de coordenação

**Lacuna de concorrência.** US-10 verifica disponibilidade e depois salva; US-12 verifica ausência de vínculos e depois apaga o objeto. Sem coordenação, uma operação iniciada antes da expiração pode concluir após a decisão de purga.

**Impacto:** biblioteca com vídeo ausente. Outro caso: o S3 apaga o arquivo e o processo cai antes de atualizar o banco, que continua indicando disponibilidade. Mais de uma instância da aplicação pode executar a mesma purga.

**Recomendação:** coordenar a mudança de elegibilidade e o salvamento por transação/lock no banco; registrar intenção de deleção durável; executar deleção repetível e confirmar o estado. Não manter uma transação de banco aberta durante uma longa chamada S3. Processar lotes com reivindicação de trabalho e retentar falhas. Definir a remoção do último vínculo de um lance já `EXPIRADO_SALVO`: o job atual só busca `DISPONIVEL` e não o recolheria.

### A18 — P1 — Deletar no S3 não garante a economia prometida em todas as configurações

**Premissa não especificada.** US-12 considera `deleteObject(bucket, key)` deleção física. Em bucket com versionamento habilitado, a requisição simples cria um marcador de exclusão e preserva versões anteriores; isso está documentado na [API DeleteObject da AWS](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html).

**Impacto:** o sistema marca `EXPIRADO_PURGADO`, mas os bytes podem continuar armazenados. Uma regra de lifecycle indiscriminada pode, no sentido oposto, remover vídeos salvos.

**Recomendação:** definir versionamento, versões não atuais, lifecycle, backups e política de recuperação em conjunto com a regra de biblioteca. Tratar também uploads multipart incompletos e uploads órfãos. Não interpretar bytes marcados no banco como economia efetiva sem conferir o inventário de storage.

### A19 — P1 — O banco não impede inconsistências entre dispositivo e quadra

**Confirmado no DDL proposto.** `lances.quadra_id` e `lances.dispositivo_id` têm FKs independentes. Uma linha pode apontar para quadra A e dispositivo da quadra B. O índice de idempotência não é único. Não há identidade própria do evento ou unicidade da referência ao objeto.

**Impacto:** dados de origem contraditórios, duplicações e dificuldade de manter isolamento. Reassociar um dispositivo depois também pode distorcer consultas históricas se o relacionamento atual for usado para inferir a origem passada.

**Recomendação:** garantir coerência de origem na aplicação e no banco, por exemplo com referência composta apropriada ou cadastro imutável/histórico de alocação. Acrescentar identidade do evento. Definir identidade do arquivo, restrições de estados e expiração, e limites operacionais superiores de captura e retenção. Valores somente maiores que zero ainda permitem configurações impraticáveis.

**Observação:** o DDL foi revisado estaticamente, não executado. A existência de tipos e constraints não comprova a implementação dos fluxos de autorização e concorrência.

### A20 — P1 — A expansão para várias quadras não está resolvida no Edge

**Diferença entre intenção e implementação.** O MVP afirma que um computador pode atender vários botões/quadras. O gateway atual tem uma câmera, porta serial, pasta e `QUADRA_ID`; o firmware envia uma frase sem identificação do botão. Não há modelo de câmera/gateway para rotear cada clique ao vídeo correto.

**Impacto:** backend aceita muitos dispositivos, mas o equipamento local não consegue distinguir suas origens com o protocolo atual.

**Recomendação:** fixar o piloto em uma câmera/quadra por instância ou definir mensagens estruturadas com identidade física, sequência e associação a câmera. Se um gateway compartilhado for necessário, modelar gateway, câmera e botão separadamente. Avaliar o impacto de uma única falha parar todas as quadras do local.

### A21 — P1 — O feedback do botão confirma pressão, não captura útil

**Confirmado no firmware.** O LCD informa “apertado/solto”; não recebe confirmação do Edge. O Python ignora cliques dentro de cinco segundos, mas o jogador recebe o mesmo feedback físico. Um clique no início da gravação também pode ser descartado por falta de segmentos completos.

**Impacto:** o usuário acredita ter salvo um lance que nunca foi aceito ou que não tem vídeo. Cinco segundos é também uma decisão de produto, pois impede marcações distintas próximas.

**Recomendação:** distinguir “clique aceito”, “aguarde”, “sem gravação” e “envio pendente”. Confirmar persistência local do evento ao jogador, sem exigir internet para esse primeiro retorno. Definir comportamento no aquecimento do buffer e tornar o cooldown informado e configurável.

### A22 — P1 — Hardware e qualidade de imagem ainda precisam de critérios de aprovação

**Erro concreto e lacuna.** README recomenda Mega/Uno, mas o firmware usa pino 22, incompatível com o pinout do Uno Rev3 sem adaptação. Ver [pinout oficial do Uno](https://content.arduino.cc/assets/Pinout-UNOrev3_latest.pdf). Também falta especificação fechada de câmera, lentes, montagem, iluminação, FPS, resolução, bitrate, formato de cor e áudio. A captura atual usa DirectShow com câmera local; suporte a câmeras IP/RTSP não está implementado.

**Impacto:** instalação que segue o guia pode não funcionar. Um vídeo tecnicamente gerado pode ter baixa utilidade por bola pequena, borrão, pontos cegos ou pouca luz. Sem bitrate controlado, as estimativas de disco e rede são incertas.

**Recomendação:** documentar placa suportada ou parametrizar pinagem; publicar esquema de ligação, alimentação, proteção e lista de materiais. Aprovar o vídeo em uma quadra real com jogadas rápidas e iluminação normal do local. Definir formato reproduzível em navegadores e aparelhos do público, além de inicialização rápida do MP4. Áudio deve ser uma escolha explícita de experiência e privacidade.

### A23 — P2 — Autenticação de jogadores e operação de credenciais estão incompletas

**Lacuna.** Cadastro/login estão descritos, mas não há recuperação de senha, verificação de e-mail, limitação de tentativas, política de sessão, revogação, exclusão de conta ou comportamento de conta inativa. Token por dispositivo é uma boa decisão, porém faltam rotação, revogação, provisionamento e prevenção de registro de segredos.

**Impacto:** contas irrecuperáveis, suporte manual e dificuldade de conter comprometimento. `UNIQUE(email)` sozinho não documenta normalização e equivalência de endereços.

**Recomendação:** definir esses fluxos e o tratamento de contas globais. Especificar HTTPS obrigatório em produção, normalização de e-mail e proteção dos tokens no PC. Se credenciais temporárias AWS forem usadas, o cliente precisa suportar também o token de sessão; não tratá-las como já implementadas.

### A24 — P1 — Os testes existentes não demonstram as garantias declaradas

**Confirmado por leitura.** O diagnóstico replica comandos, em vez de validar todo o fluxo de produção. Não testa autenticação, idempotência, rede, expiração, multi-tenancy ou recuperação. O guia anuncia teste de limpeza, mas o script não implementa essa etapa. O teste de câmera aceita presença de `frame=` mesmo com código de erro; a gravação considera arquivos presentes como evidência de sucesso, inclusive possíveis arquivos antigos.

**Impacto:** teste “verde” enquanto o comportamento principal continua incorreto. Concatenar arquivos não comprova que o gol marcado está no clipe nem que o player web o reproduz.

**Recomendação:** manter o diagnóstico para bancada, corrigir suas afirmações e separar testes de contrato, seleção temporal e falhas. Para o piloto, registrar duração, relógio e reprodução do conteúdo final, além do retorno dos processos. Não é necessário testar cada linha; é necessário testar os compromissos que o produto faz.

### A25 — P1 — Operação, suporte e recuperação não têm requisitos suficientes

**Lacuna documental.** Não há critérios de saúde da quadra, limite de disco, procedimento de instalação, atualização/rollback, backup/restauração, alerta com responsável ou acordo de suporte. Há logging local rotativo, mas não observabilidade da entrega de cada evento.

**Impacto:** a primeira expansão multiplica visitas e diagnósticos manuais. Reinício automático sem observação pode apenas repetir uma falha. Preservar metadados não recupera um objeto definitivamente apagado.

**Recomendação:** definir métricas mínimas: progresso da câmera, último segmento válido, idade da fila, espaço livre, erros de upload, eventos confirmados e latência até reprodução. Identificar versão do software e `evento_id` nos logs, sem tokens. Especificar quem recebe alerta e como agir. Fazer restauração de banco e de vídeo conforme a política de proteção; escolher explicitamente o que poderá ser recuperado.

### A26 — P2 — Navegação por hora simplifica o MVP, mas não identifica uma partida

**Boa simplificação com limite não declarado.** Um jogo pode ocorrer das 20:30 às 21:30, cruzar meia-noite ou atrasar. Um clique próximo da troca de grupos pode produzir um clipe que inclui ambas as partidas. Agrupar pelo horário do clique não elimina esse problema.

**Impacto:** o usuário procura em mais de um slot ou recebe conteúdo de outro grupo. A descrição “minha partida” sugere uma correspondência que o modelo não mantém.

**Recomendação:** chamar o conceito de faixa de gravação e manter busca por intervalo flexível, com horários localizados e thumbnails. Não incluir gestão completa de partidas no piloto sem evidência de necessidade. Se evoluir para sessões, preservar dados de origem e intervalos para permitir associação posterior.

## 4. Erros e ambiguidades específicas da documentação

| Local | Problema | Ajuste recomendado |
|---|---|---|
| README, matriz RF04 | Recuperação do trecho do evento está “concluída”, mas a seleção pode perder o lance. | Marcar implementação parcial até passar no aceite temporal. |
| README, RNF01 | “Sem interrupções” consta como concluído, sem supervisor ou validação prolongada. | Separar gravação implementada de continuidade validada. |
| README, RNF02 | Disponibilidade em menos de cinco minutos consta concluída com base no corte local. | Medir clique até reprodução, incluindo fila, rede e API. |
| README vs MVP | Partidas e compra continuam como fluxo principal, mas MVP tem slots e salvamento gratuito. | Declarar visão futura, piloto e escopo vigente. |
| MVP, decisão 3 | Um POST pós-upload é chamado de atômico. | Documentar as falhas parciais e a reconciliação. |
| MVP, decisão 5 | API key com hash é chamada de Zero-Trust. | Descrever as medidas concretas, sem atribuir garantia arquitetural não demonstrada. |
| RF-MVP-07 vs RN-MVP-14 | RF permite rejeitar duplicado; RN determina sucesso com o registro existente. | Fixar uma resposta idempotente no contrato. |
| Requisitos vs histórias, ingestão | Resposta usa `lance_id` em um documento e `id` no outro; campos também diferem. | Uma especificação de API versionada, com exemplos derivados dela. |
| Requisitos vs histórias, listagem | Um contrato retorna array; o outro retorna objeto com `lances`. | Escolher o envelope e definir paginação. |
| Requisitos vs histórias, horários | Datas sem offset vs UTC; `horario_formatado` sem fuso definido. | Um formato obrigatório e exemplos coerentes com o fuso da quadra. |
| US-06 | Critério exige quantidade de lances de hoje; response não contém esse campo. | Acrescentar campo e fuso de “hoje”, ou remover o requisito. |
| US-07 | Omitir horas retorna dia completo “ou” agrupamento. | Escolher comportamento determinístico. |
| US-10 | Salvar novamente retorna conflito “ou” sucesso. | Escolher sucesso idempotente para repetição, se essa for a decisão do produto. |
| US-08 vs RN-MVP-17/19 | Falta proibir consulta pública de expirado salvo. | Definir autorização e expiração para a rota individual. |
| Banco, dicionário vs DDL | `gen_random_uuid()` vs `uuid_generate_v4()` e extensão `uuid-ossp`. | Ambos podem ser escolhas válidas; padronizar a descrição e a necessidade de extensão. |
| Banco, tabela de índices vs DDL | `idx_usuario_lances_lookup` no texto vs `idx_usuario_lances_lance` no script. | Uniformizar nome para rastreabilidade. |
| Banco, desempenho | Índice comum é descrito como consulta em `O(1)` e busca “instantânea”. | Explicar benefício sem garantia absoluta; medir com volume e plano de execução. |
| Banco, grade | `BETWEEN` inclui os dois extremos. | Usar `>= inicio AND < fim` para slots adjacentes. |
| Banco, diagrama de estados | Entrada aponta a `DISPONIVEL`, mas estado composto usa alias `DISP`. | Usar identificador único; revisar renderização após ajuste. |
| Banco, multi-tenancy | Diz que todas as entidades pertencem a uma empresa, mas usuário é global e biblioteca cruza empresas. | Distinguir dados operacionais por empresa de identidade global e direitos do jogador. |
| Banco, pagamentos futuros | Garante monetização sem refatorar tabelas e basta inserir vínculo. | Tratar como hipótese de extensão; modelar origem e revogação do direito, tentativas e reembolsos. |
| Gateway README, gravação | Afirma nomes por timestamp; código usa sequência numérica. | Ajustar a descrição e resolver identidade de sessão. |
| Gateway README/status | Segmentação é apresentada como proteção contra corrupção. | Dizer que limita o impacto; arquivo em escrita e encerramento abrupto continuam exigindo tratamento. |
| Gateway README, diagnóstico | Anuncia captura de três segundos e etapa de limpeza; teste usa dois segundos e não testa limpeza. | Fazer guia e teste concordarem. |
| README, hardware | Indica Mega/Uno, com firmware fixado no pino 22. | Publicar suporte real e pinagem por placa. |
| Status, implementação | Cita classe `S3Uploader`; implementação usa cliente S3 dentro de `VideoProcessor`. | Descrever componente existente. |
| Matriz de rastreabilidade | Referências alternam `RN-xx` e `RN-MVP-xx`; numeração da visão e do MVP são distintas. | Criar namespaces e mapa entre requisitos originais e atuais, incluindo os adiados. |

Além disso, falta especificar paginação, limites de filtro, validações de parâmetros, erros padronizados, comportamento de recursos inativos, rotação de token e edição das configurações cadastradas. Esses pontos devem virar decisões explícitas, não alternativas deixadas ao implementador.

O PDF apresenta tabela de entidades dividida entre as páginas 2 e 3 e uma célula de compra com texto ultrapassando o limite direito da tabela na página 1. Não são os riscos principais do projeto, mas reduzem a qualidade do documento de referência. A versão 0.1 deve continuar identificada como histórico de levantamento, sem assumir prioridade sobre decisões mais recentes.

Existe ainda um arquivo `gateway_captura/test_segments/seg_0000.mp4` rastreado no Git, apesar da regra de exclusão de segmentos de teste. A regra não remove arquivos já rastreados. Revisar a necessidade desse arquivo e eventual conteúdo antes de distribuí-lo; não presumir que vídeos capturados ficam fora do histórico apenas por existir `.gitignore`.

## 5. Boas ideias que vale preservar

| Ideia | Por que funciona | Condição para preservar o benefício |
|---|---|---|
| Botão durante o jogo | Reduz interação com celular no momento de maior atenção. | Posicionamento acessível, retorno de aceitação e resistência adequada. |
| Buffer local separado do clipe | Permite recuperar o passado sem enviar toda a partida. | Referência temporal correta e proteção de arquivos pendentes. |
| Processamento no Edge | Reduz necessidade de transmitir gravação contínua para a nuvem. | Dimensionar hardware e manter recuperação local. |
| Concatenação com cópia de streams | Evita uma segunda codificação completa quando os arquivos são compatíveis. | Garantir qualidade da captura e aceitar/definir precisão nas bordas. A captura atual já codifica com `libx264`. |
| Empresa → quadra → dispositivo | Reflete bem a operação física e facilita identificação da origem. | Autorizar os relacionamentos e assegurar coerência no banco. |
| Resolver quadra pelo dispositivo autenticado | Reduz confiança em um campo arbitrário enviado pelo cliente. | Validar token e identidade em conjunto; implementar isso também no Edge. |
| Token próprio com hash no backend | Evita armazenar o segredo em texto puro no banco. | Provisionamento seguro, rotação, revogação e escopo mínimo. |
| Conta única entre arenas | Mantém a biblioteca do jogador consistente em vários locais. | Separar identidade global de permissões administrativas por empresa. |
| Vínculo N:N entre usuário e lance | Vários jogadores podem salvar o mesmo objeto sem duplicar o arquivo. | Definir quem pode acessar e quando o último vínculo permite remoção. |
| Retenção curta para conteúdo não salvo | Pode limitar crescimento desnecessário do storage. | Cobrir órfãos, versões e abuso do salvamento permanente. |
| PostgreSQL com migrations e constraints | Boa base para cadastro, vínculos e evolução controlada. | Executar migrations reais e incluir integridade das regras críticas. |
| Configuração por quadra | Permite experimentar janelas sem alterar código para cada instalação. | Validar limites, sincronizar e manter fallback offline. |
| Slots no MVP | Reduz dependência de operador e integração com reservas. | Assumir que são filtros de horário, não identidade de partida. |
| Histórias, critérios e rastreabilidade | Facilita transformar decisões em trabalho verificável. | Resolver divergências e incluir fluxos de exceção e operação. |
| Diagnóstico local e logs rotativos | Ajuda instalação e investigação de falhas de bancada. | Tornar diagnósticos confiáveis e acrescentar saúde/alertas remotos. |
| Gateway externo para pagamento | Evita projetar armazenamento direto de cartão no produto. | Não equiparar tokenização isolada a comprovação de conformidade; definir integração e responsabilidades. |

As escolhas de Python/FFmpeg no Edge e Spring Boot/PostgreSQL no backend são coerentes com o desenho apresentado. Não encontrei razão no escopo atual para trocar tecnologias ou introduzir microserviços. A prioridade é fechar contratos, comportamento temporal e operação.

## 6. Viabilidade e riscos de crescimento

Não há pesquisa de clientes, preços, custos ou métricas de uso no material. Portanto, não é possível concluir viabilidade financeira ou demanda. As hipóteses abaixo são simulações para dimensionamento, não medições do projeto nem cotação da AWS.

Supondo vídeo de **4 Mb/s**, clipe de **120 segundos**, **100 cliques por dia por quadra** e seis horas de buffer:

- Cada clipe ocupa aproximadamente 60 MB, antes de pequenas diferenças de container/áudio.
- A quadra gera aproximadamente 6 GB de clipes por dia; não são necessariamente seis gigabytes novos de cenas diferentes, pois cliques próximos repetem trechos.
- Se todos forem conservados, são aproximadamente 180 GB adicionais por mês por quadra.
- Se 20% forem salvos permanentemente, entram aproximadamente 36 GB permanentes por mês, além do estoque público temporário.
- O buffer contínuo de seis horas ocupa aproximadamente 10,8 GB por câmera, separado dos clipes e da fila de envio.
- Em upload efetivo de 5 Mb/s, apenas enviar um clipe de 60 MB leva aproximadamente 96 segundos, sem contar fila, tentativas, API ou competição com outros usos da internet.

O cooldown de cinco segundos admite até 12 eventos por minuto. No exemplo de 96 segundos de upload por evento, um único worker enviaria cerca de 0,625 evento por minuto. Isso não é previsão de uso, mas mostra que o intervalo entre cliques não é controle suficiente de capacidade.

O custo por quadra deve incluir armazenamento temporário e permanente, tráfego de reprodução/download, requisições, backend/banco, hardware, instalação, energia, manutenção, suporte e pagamento, quando aplicável. Evitar concentrar a análise apenas no S3: custo de atendimento e visitas técnicas pode ser decisivo.

Riscos a validar antes de crescer:

| Momento | Problema provável | Evidência a obter |
|---|---|---|
| Primeiro teste na quadra | A câmera não mostra bem a bola/jogada ou o botão atrapalha o fluxo. | Vídeos de jogadas rápidas e observação de uso real. |
| Primeira oscilação de internet | Replay fica sem publicação ou fila acumula. | Teste de interrupção e recuperação por etapa. |
| Primeiro reinício/queda de energia | Mistura de sessões ou eventos perdidos. | Reinício com buffer e fila pendentes. |
| Primeiro prazo de retenção | Link ainda público ou vídeo salvo desaparece. | Matriz de acesso e teste de salvar/purgar concorrente. |
| Primeiro usuário que salva tudo | Purga deixa de controlar custo. | Limites, métricas de estoque e política de conservação. |
| Segunda empresa | Permissões e credenciais cruzam locais. | Testes negativos entre empresas e prefixos S3. |
| Mais quadras no mesmo PC | Falha compartilhada, origem ambígua e gargalos. | Roteamento físico e teste de carga por câmera/quadra. |
| Introdução de cobrança | Gratuidade anterior, checkout e reembolso entram em conflito. | Política comercial e ciclo de direitos de acesso. |
| Pedido de retirada | Vídeo aparece em várias bibliotecas ou cópias. | Processo de atendimento, remoção e comunicação. |

Para avaliar a proposta comercial, medir a sequência: jogada marcada → replay correto → jogador encontra → assiste/compartilha → arena ou jogador aceita pagar. Se o piloto for gratuito, documentar que a última etapa ainda não foi validada. Um vídeo encontrado e baixado não prova, por si só, que o serviço tem preço sustentável.

## 7. Ordem recomendada de trabalho

| Etapa | Entrega concreta | Critério para avançar |
|---|---|---|
| 1. Fechar decisões | Escopo do piloto, pagador/receita, privacidade, retenção e matriz de acesso; contrato único da API. | Documentos sem respostas alternativas e sem promessas incompatíveis. |
| 2. Corrigir o núcleo | Seleção temporal, identidade de evento, isolamento de sessões e confirmação local. | Replay contém o instante correto em todas as posições do segmento. |
| 3. Garantir recuperação | Persistência, tentativas, confirmação, controle de disco e supervisão da captura. | Reinícios e falhas de rede não perdem eventos aceitos. |
| 4. Entregar fluxo mínimo de ponta a ponta | Uma quadra, ingestão autenticada, objeto privado, busca e player. | Clique até vídeo correto reproduzível, com origem e acesso válidos. |
| 5. Completar retenção e biblioteca | Expiração em todas as consultas, salvamento coordenado, purga repetível e limites. | Usuários autorizados mantêm acesso; demais o perdem; inventário confirma limpeza. |
| 6. Rodar piloto acompanhado | Hardware aprovado, instalação recuperável, alertas e métricas de uso/custo. | Qualidade, confiabilidade e demanda medidas no ambiente real. |
| 7. Expandir e monetizar | Direitos de acesso, cobrança escolhida e operação para múltiplos locais. | Unidade econômica e suporte sustentáveis sob hipóteses verificadas. |

As quatro sprints atuais são úteis como agrupamento, mas priorizam cadastros e só trazem limpeza no final. A autorização administrativa deve nascer com os cadastros; acesso privado deve nascer com a ingestão; a política de retenção deve existir antes de publicação real. A primeira entrega de valor deveria atravessar todo o fluxo de uma quadra, em vez de apenas concluir entidades isoladas.

Não sugiro transformar todos os riscos em funcionalidades complexas antes do piloto. Sugiro fechar os requisitos indispensáveis e limitar o escopo físico para tornar o piloto confiável e mensurável.

## 8. Verificações obrigatórias antes do piloto

| Verificação | Resultado esperado |
|---|---|
| Cliques perto de bordas e após atraso humano | Instante e janela contratados presentes no vídeo. |
| Evento esperando na fila | Mesmo conteúdo selecionado pelo horário original. |
| Dois cliques legítimos próximos | Comportamento de cooldown declarado; eventos aceitos não fundidos por horário. |
| Mesmo evento reenviado e recebido simultaneamente | Um registro, uma identidade de objeto e resposta consistente. |
| Reinício em cada etapa | Trabalho retomado sem misturar sessões e sem depender de ação manual. |
| Internet indisponível por período representativo do local | Eventos preservados dentro do limite anunciado; envio retomado. |
| Câmera desconectada e FFmpeg encerrado | Estado de falha visível, recuperação controlada e alerta acionável. |
| Disco perto do limite | Proteção do sistema e dos eventos aceitos; degradação definida. |
| Salvar perto da expiração durante purga | Nenhum vínculo confirmado aponta para objeto apagado. |
| Expirado salvo acessado por outro usuário | Reprodução negada, inclusive pela consulta direta. |
| Credencial de empresa A com objeto/recurso B | Operação recusada e sem efeito colateral em B. |
| Dia local, UTC, meia-noite e limite de slot | Consulta correta, sem duplicação em slots adjacentes. |
| Playback em dispositivos do público | Qualidade suficiente e reprodução compatível, com início aceitável. |
| Carga e internet reais | Meta de latência validada com amostra, condições e percentis definidos. |
| Restauração e atualização do gateway | Recuperação verificada e possibilidade de voltar à versão anterior. |

A referência de cinco minutos precisa dizer em quais condições vale e como será medida. Recomendo registrar, no mínimo, percentual de eventos aceitos que produzem vídeo correto, percentual reproduzível, distribuição da latência, tamanho/idade da fila e custo por quadra. Os valores de aprovação devem ser acordados para o piloto a partir do teste real; este parecer não inventa um SLA já validado.

## 9. Manutenção da documentação

Estabelecer uma fonte vigente para cada assunto: visão e hipótese comercial; regras de negócio; contrato de API; schema/migrations; manual de instalação/operação; status de entrega. Nos documentos antigos, registrar explicitamente o que foi substituído e por qual decisão.

Para cada requisito, diferenciar **planejado**, **implementado**, **verificado isoladamente** e **validado em operação**, com evidência. Evitar “completo”, “instantâneo”, “sem interrupções”, “resiliente”, “atômico” e “para sempre” sem critérios mensuráveis. Um checklist marcado não substitui o teste do resultado entregue ao jogador.

Decisões que precisam ficar registradas antes do desenvolvimento do backend:

1. Pagador, produto vendido e objetivo do piloto gratuito, se houver.
2. Quem pode ver, salvar, compartilhar e pedir retirada de cada vídeo.
3. Prazo público, prazo da biblioteca, cotas e tratamento de conta encerrada.
4. Janela temporal do clipe e precisão aceita nas bordas.
5. Identidade do evento e respostas para repetição/conflito.
6. Fuso, relógio, slots, virada de dia e envio tardio.
7. Papéis administrativos e comportamento de empresa/quadra/dispositivo inativo.
8. Referência e autorização do objeto, credenciais e deleção.
9. Limite do piloto: câmeras, botões e quadras por gateway.
10. Falhas recuperáveis, limite offline, monitoramento e responsáveis por suporte.

**Conclusão do parecer:** a ideia merece um piloto, mas o piloto precisa demonstrar que cada clique aceito entrega a jogada correta, com recuperação de falhas e política de acesso sustentável. A base conceitual é boa; a principal correção de rumo é transformar garantias documentais em comportamento comprovado e alinhar o MVP ao negócio que se pretende validar.

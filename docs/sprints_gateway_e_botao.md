# ClickLance — Sprints de evolução do gateway e do botão

Data: 15 de outubro de 2026. Status: Sprint 1 verificada automaticamente, com bancada pendente; Sprint 2 iniciada.

### Andamento da Sprint 2

- Entrada RTSP configurável implementada (TCP/UDP e timeout), sem URL ou credenciais nos logs do gateway. Equipamento informado: Intelbras VIP, modelo exato ainda pendente.
- GW-04 iniciado: diretórios únicos por sessão, catálogo SQLite, lista CSV de fechamento do FFmpeg, validação de stream/duração, identificação de lacunas e reconciliação na inicialização. Arquivos em escrita e órfãos ficam separados dos fechados.
- GW-05 implementado com fonte temporal controlada: janela reservada antes da fila, seleção por sessão/intervalo, espera limitada, proteção contra limpeza, corte recodificado com temporários exclusivos e validação de duração/decodificação. Cooldown e âncora de recepção usam monotônico.
- Evidência atual: 35 testes aprovados, sem falhas esperadas ou testes ignorados. Inclui conteúdo por pixels nas bordas e no clique, fila atrasada, reservas persistidas, timeout e falhas de geração/upload/API. [Detalhes e limites](evidencias_sprint_2.md).
- Pendentes: stream RTSP simulado/real, medição da latência da câmera e validação da âncora contra a cena, custo da janela completa no equipamento alvo. Sprint 2 ainda não homologada. Recuperação de eventos e confirmação serial pertencem à Sprint 3.

### Andamento da Sprint 1

- GW-01/GW-02: [contratos iniciais registrados](contratos_gateway_botao.md), sujeitos à validação em bancada; protocolo ainda não implementado.
- GW-03: testes persistidos, serial simulada, substitutos de storage/API e vídeo sintético com relógio/frame/marcadores implementados. Diagnóstico compartilha captura/concatenação com produção e valida duração e decodificação. [Evidências e comandos](evidencias_sprint_1.md).
- Validação local: 10 testes executados, 6 aprovados e 4 falhas esperadas que reproduzem defeitos conhecidos; diagnóstico sintético aprovado com FFmpeg real. Não houve validação física. GW-01/GW-02 continuam sujeitos à revisão e medição em bancada; Sprint 1 ainda não está homologada.

## Objetivo e limites

Entregar um conjunto gateway + botão capaz de registrar um acionamento, recuperar a jogada correta, preservar o trabalho durante falhas e informar ao jogador se o evento foi aceito.

O plano parte da implementação atual e dos achados A01–A03, A09–A16 e A20–A25 do [parecer de consultoria](review_consultoria_2026-10-13.md). Complementa as sprints do backend em [histórias e endpoints](historias_de_usuario_e_endpoints.md).

Escopo atualizado em 15/10/2026: Windows, câmera IP como fonte de produção, Arduino Mega com LCD I2C e um botão/uma câmera/uma quadra por instância do gateway. A webcam foi utilizada somente para testes; sua ausência não bloqueia desenvolvimento e testes sintéticos. Modelo e protocolo da câmera IP ainda precisam ser confirmados; RTSP é a hipótese inicial, não suporte já entregue. O pino 22 atual corresponde ao Mega; suporte ao Uno exige perfil de pinagem próprio e validação física. Múltiplas quadras por processo, frontend, pagamentos e implementação do backend ficam fora deste ciclo.

### Ajuste para câmeras IP

- Sprint 1: confirmar protocolo, codec, resolução, FPS, GOP, bitrate, autenticação e latência da câmera alvo; continuar regressões com fonte sintética enquanto não houver hardware.
- Sprint 2 (GW-04/GW-05): adicionar entrada de rede configurável, mantendo DirectShow como opção de teste. Validar com stream local simulado e registrar separadamente a pendência de câmera real. Não registrar URL com credenciais em logs ou relatórios.
- Contrato temporal: medir atraso entre a cena e sua chegada ao gateway. Timestamp de recepção não comprova instante da cena; definir sincronização e tolerância antes de homologar a correspondência entre clique e conteúdo. Reconexão abre nova sessão e marca lacunas.
- Sprint 5 (GW-10): exercitar interrupção da rede local, stream travado, credencial inválida e reconexão. Sem progresso ou cobertura, recusar novos eventos com motivo explícito.
- Sprint 6: homologar câmera IP real na rede da quadra, incluindo latência, keyframes, perda de conexão e recuperação. Webcam não é requisito de aceite do piloto.

Premissas de planejamento, a confirmar na Sprint 1:

- Janela inicial de replay: 120 segundos antes do clique e zero depois, com parâmetros independentes para experimentação.
- O instante do clique deve estar presente no clipe. Precisão das bordas e duração real serão medidas; concatenação com cópia de streams não implica corte exato.
- O gateway só aceita eventos quando consegue preservar a janela necessária. Aquecimento, câmera indisponível, descontinuidade e falta de capacidade geram resposta explícita.
- Internet indisponível permite aceitação local enquanto houver vídeo e capacidade de armazenamento.
- “Evento aceito” significa registro durável local e reserva do vídeo necessário. “Publicado” significa upload e registro confirmados. São momentos distintos.
- Persistência em SQLite é a solução inicial proposta. A garantia cobre reinício do processo/Windows com disco íntegro, dentro dos limites de capacidade definidos; não cobre perda física do equipamento.

As sprints têm duração sugerida de uma a duas semanas para uma pessoa dedicada. A faixa serve para dimensionar o backlog; calendário depende de disponibilidade de hardware, capacidade de implementação e integração cloud. Reservar validação em bancada em cada sprint relevante.

## Sequência e resultado de cada sprint

| Sprint | Prioridade | Resultado demonstrável | Dependência |
|---|---|---|---|
| 1 — Contratos e base de diagnóstico | P0 | Comportamentos definidos e falhas atuais reproduzíveis sem câmera | Código atual |
| 2 — Replay pelo instante do evento | P0 | Clipe contém a jogada mesmo com atraso na fila | Sprint 1 |
| 3 — Evento durável e botão com confirmação | P0 | LCD confirma somente após aceitação local durável | Sprints 1 e 2 |
| 4 — Envio recuperável e integração | P0 | Upload e notificação retomam após falhas, com a mesma identidade | Sprint 3; contrato cloud alinhado |
| 5 — Recuperação e operação no Windows | P1 | Captura, disco e inicialização têm supervisão e estados claros | Sprints 2–4 |
| 6 — Homologação do piloto | P1 | Evidências de conteúdo, recuperação, capacidade e uso real | Sprints 1–5; ambiente de homologação |

O planejamento não depende de o backend completo existir para iniciar. Simuladores permitem desenvolver os contratos; a aprovação da integração e do piloto exige serviços reais de homologação.

## Sprint 1 — Contratos e base de diagnóstico

**Objetivo:** eliminar ambiguidades que afetam a captura e criar meios confiáveis de verificar as próximas entregas.

### GW-01 — Definir contrato temporal e política de aceitação

Como responsável pela instalação, quero configurar a janela e saber quando o gateway está pronto para aceitar um clique.

Entregas:

- Definir `segundos_antes`, `segundos_depois`, duração de segmento, FPS, intervalo de keyframes e tolerância das bordas.
- Separar debounce elétrico do firmware, cooldown de produto e deduplicação de mensagens.
- Definir prontidão por disponibilidade real de vídeo, tratamento de lacunas, relógio UTC com offset e relógio monotônico para intervalos.
- Registrar parâmetros iniciais e limites de disco, fila, retentativas e operação offline. Dimensioná-los com bitrate e hardware disponíveis.

Aceite: uma tabela de cenários define aceitar/rejeitar para aquecimento, clique válido, cooldown, câmera parada, vídeo incompleto e disco insuficiente. Não há aceitação silenciosa de janela parcial.

### GW-02 — Especificar protocolo serial bidirecional

Como gateway e firmware, precisamos identificar mensagens e confirmar o mesmo evento sem duplicá-lo.

Entregas:

- Protocolo versionado, com enquadramento por linha, tamanho máximo, identificação do dispositivo, sessão e sequência.
- Mensagens de apresentação/sincronização, acionamento, aceitação, rejeição e estado do gateway.
- Regras para timeout, repetição, mensagem inválida, reconexão e reinicialização do Arduino.
- Relação entre identidade serial `(dispositivo, sessão, sequência)` e `evento_id` estável no gateway.
- Definir a origem do horário do clique e como preservar esse instante em mensagens retransmitidas. Horário de chegada de uma repetição não pode substituir o horário original.

Aceite: exemplos válidos e inválidos, transições e limites estão documentados. A perda de uma confirmação tem resposta definida. Reinício do Arduino antes da confirmação não pode produzir falsa mensagem de sucesso; o limite de recuperação sem memória persistente no botão fica explícito.

### GW-03 — Criar diagnóstico e testes do comportamento atual

Entregas:

- Vídeo sintético com relógio/marcadores, entrada serial simulada e substitutos de storage/API.
- Reproduzir perda do segmento atual, deslocamento por fila, mistura de sessões e remoção após erro de envio.
- Separar diagnóstico físico interativo de testes automáticos; corrigir falsos positivos por arquivos antigos ou presença de `frame=` apesar de falha.
- Usar componentes de produção nos testes, evitando uma segunda implementação do pipeline.

Aceite: cenários conhecidos têm resultado esperado registrado; os testes de regressão que evidenciam defeitos atuais ficam identificados até as correções correspondentes. Diagnóstico retorna falha quando captura/FFmpeg falham e usa diretório exclusivo por execução.

**Demonstração:** reproduzir uma jogada ausente no fluxo atual e mostrar os contratos que orientarão a correção.

## Sprint 2 — Replay pelo instante do evento

**Objetivo:** recuperar o conteúdo correspondente ao clique, independentemente do momento em que o worker o processa.

### GW-04 — Catalogar segmentos e isolar sessões

Entregas:

- Arquivos com identificação única de sessão/câmera, sem sobrescrita de gravações anteriores.
- Catálogo de início, fim, duração real, sessão, caminho e conclusão de cada segmento.
- Marcar como utilizável somente segmento fechado e verificável; distinguir arquivo em escrita de arquivo concluído.
- Identificar lacunas e reconciliar catálogo/arquivos na inicialização.

Aceite: reiniciar com arquivos existentes não mistura sessões nem sobrescreve segmentos. A seleção usa intervalos de conteúdo, sem depender apenas de nome ou data de modificação. Encerramento abrupto deixa o segmento afetado identificado como incompleto.

### GW-05 — Selecionar e gerar o replay pela janela original

Entregas:

- Selecionar segmentos que cobrem `[instante_clique - segundos_antes, instante_clique + segundos_depois]`.
- Aguardar fechamento dos segmentos necessários com prazo limitado; verificar cobertura e continuidade.
- Reservar segmentos necessários para impedir sua remoção pela limpeza.
- Temporários exclusivos por evento, timeout de FFmpeg e validação de duração, conteúdo e reprodução do clipe.
- Escolher cópia de streams ou recodificação conforme a tolerância definida, medindo o custo no equipamento alvo.

Aceite: cliques no início, meio e fim de segmentos incluem o marcador correto. Processar o evento minutos depois gera o mesmo intervalo de conteúdo. Atraso na fila e limpeza concorrente não alteram a seleção. Ausência de cobertura resulta em erro identificado, sem publicação de vídeo de outro horário.

**Demonstração:** três cliques em posições diferentes de um vídeo com relógio; atrasar um deles e conferir os clipes resultantes.

## Sprint 3 — Evento durável e botão com confirmação

**Objetivo:** conectar a experiência do botão à capacidade real do gateway de guardar o lance.

### GW-06 — Persistir eventos antes de confirmar aceitação

Entregas:

- SQLite para evento, identidade serial, instante original, janela, reservas de segmentos, estado e erros.
- Restrição de unicidade da identidade serial; repetição retorna o mesmo `evento_id` e resultado.
- Gravar evento e reservas em transação antes de enviar confirmação serial.
- Recuperar trabalho pendente e reservas após reinício, coordenando a limpeza com a persistência.
- Revalidar prontidão/capacidade no momento da aceitação; estado exibido anteriormente não basta.

Aceite: encerrar o processo antes e depois do commit e da confirmação não gera duplicidade. Depois de confirmação recebida pelo botão, reiniciar o gateway preserva o evento e suas fontes. Uma confirmação perdida pode ser solicitada novamente com a mesma identidade.

### BT-01 — Implementar firmware sem bloqueios no fluxo normal

Entregas:

- Debounce por estabilidade do sinal e temporização com `millis()`, sem `delay()` bloqueando leitura serial e estados.
- Um acionamento por pressão, exigindo soltura para nova pressão; cooldown separado e informado.
- Protocolo da Sprint 1 com buffers limitados, validação de comprimento, timeout e tratamento de mensagens parciais.
- Perfis explícitos de placa/pinagem e atualização do LCD somente quando o estado mudar.
- Temporização compatível com retorno de `millis()` ao início da contagem.

Aceite: bounce, botão mantido pressionado e mensagens malformadas não produzem eventos extras nem travam o firmware. Compilação para o Mega e teste físico de leitura, LCD e serial são registrados. Uno só é anunciado como suportado após compilação e validação próprias.

### BT-02 — Exibir aceitação e indisponibilidade reais

Entregas:

- Estados no LCD 16×2: conectando, preparando buffer, pronto, aguardando confirmação, evento aceito, aguarde, indisponível e sem confirmação.
- “Evento aceito” somente após confirmação de persistência do gateway; não significa publicação cloud concluída.
- Repetir acionamento pendente com a mesma identidade, dentro dos limites definidos.
- Sem conexão ou confirmação, exibir condição correspondente; não informar sucesso.

Aceite: desconectar USB, perder a resposta serial, parar a câmera e clicar durante aquecimento produzem estados coerentes. Com internet desligada e capacidade local disponível, o clique pode ser aceito. Reset do Arduino com mensagem pendente não reapresenta um evento antigo como novo de forma silenciosa.

**Demonstração:** clicar, perder a confirmação, retransmitir, obter um único evento e reiniciar o gateway com o trabalho preservado.

## Sprint 4 — Envio recuperável e integração cloud

**Objetivo:** separar geração local, upload e registro remoto, permitindo retomar cada etapa.

### GW-07 — Implementar pipeline persistente por etapas

Entregas:

- Estados duráveis, por exemplo: `ACEITO`, `AGUARDANDO_VIDEO`, `CLIPE_PRONTO`, `UPLOAD_CONFIRMADO`, `REGISTRO_CONFIRMADO` e falha que exige intervenção.
- Histórico de tentativas, próxima execução e erro classificado; recuperação de trabalhos interrompidos.
- Retentativas com espera progressiva, variação aleatória e timeouts. Falha de credencial/validação não entra em repetição rápida infinita.
- Remover o clipe somente após confirmação durável do registro remoto; liberar segmentos após geração validada e persistida do clipe.
- Limitar trabalhos simultâneos e impedir que lentidão de rede pare a captura ou a escuta serial.

Aceite: falhas na geração, upload e notificação conservam os artefatos necessários. Reiniciar em cada transição retoma da etapa correta. Upload concluído com API indisponível mantém o clipe e a identidade do objeto para retomada.

### GW-08 — Alinhar contrato e identidade com o backend

Entregas:

- Contrato versionado com `evento_id`, `dispositivo_id`, timestamp com offset e referência de objeto autorizada.
- Header `X-Device-Token` e rota `/api/v1/replays`, substituindo a integração atual sem autenticação.
- Chave de objeto estável por evento; identificar/reconciliar respostas de upload incertas, sem sobrescrever conteúdo diferente silenciosamente.
- Mesma identidade e conteúdo retornam o registro existente; conteúdo divergente gera conflito explícito.
- Credenciais e tokens fora de logs; suporte ao token de sessão caso a instalação use credenciais temporárias AWS.
- Objetos privados. A autorização de reprodução e a emissão de links temporários pertencem ao backend.

Aceite: timeout após commit remoto e reenvio resultam em um único lance. Token inválido e conflito ficam visíveis e preservam o trabalho local. O cenário passa no simulador e em homologação real.

Dependência externa: o backend precisa aceitar identidade estável com unicidade e validar a referência do objeto. O contrato atual baseado apenas em proximidade de cinco segundos precisa ser revisado. Até isso existir, marcar integração real como pendente, mesmo com testes simulados aprovados.

### GW-09 — Consumir configuração com fallback local

Entregas:

- Consultar configuração autenticada do dispositivo e validar limites antes de aplicar.
- Persistir última configuração válida e sua versão para inicialização offline.
- Mudanças de gravação abrem nova sessão de captura; eventos pendentes preservam configuração e fontes originais.
- Rotina de atualização com timeout e tratamento de revogação/inativação conforme contrato.

Aceite: API indisponível permite reinício com configuração válida em cache. Primeira inicialização sem configuração válida apresenta erro acionável. Resposta inválida não substitui configuração funcional; mudança de parâmetros não corrompe lances pendentes.

**Demonstração:** aceitar cliques sem internet, reiniciar e restabelecer rede; cada evento aparece uma vez no backend com seu vídeo original.

## Sprint 5 — Recuperação e operação no Windows

**Objetivo:** tornar falhas detectáveis e a instalação recuperável sem apagar trabalho aceito.

### GW-10 — Supervisionar captura e encerramento

Entregas:

- Monitorar progresso real de frames/segmentos, capturar stderr e detectar FFmpeg travado ou encerrado.
- Reinício controlado com espera e nova sessão; prontidão somente após cobertura suficiente.
- Estado de saúde comum ao receptor serial, worker e LCD.
- Encerramento com finalização do FFmpeg quando possível e persistência das tarefas; prazo limite explícito.

Aceite: matar FFmpeg, desconectar câmera, reconectar serial e encerrar o gateway não geram sucesso falso. Eventos aceitos continuam rastreáveis; uma lacuna irrecuperável é registrada e sinalizada. Rotinas de limpeza não terminam por corrida de inicialização.

### GW-11 — Controlar disco, fila e retenção local

Entregas:

- Cotas para buffer, clipes e pendências; margem mínima de espaço livre e limite de eventos.
- Limpeza coordenada com reservas e estados; não remover fontes/artefatos ainda necessários.
- Recusar novos eventos antes de comprometer trabalho aceito e informar o motivo ao botão.
- Recuperação ao liberar espaço e procedimento de intervenção para pendências antigas, sem descarte silencioso.

Aceite: simulação de disco cheio e saturação de fila preserva pendências e bloqueia aceitação com motivo claro. Retenção não apaga arquivos reservados. Operação normal retorna após recuperação de capacidade.

### GW-12 — Entregar instalação e diagnóstico operacional

Entregas:

- Inicialização automática no Windows com diretório de trabalho e ambiente definidos, evitando duas instâncias na mesma câmera/serial/base.
- Logs rotativos com `evento_id`, versão, estado, tentativa e duração, sem segredos.
- Relatório local de saúde: câmera, último segmento válido, fila/idade, espaço livre e última confirmação cloud.
- Manual de instalação, provisionamento, atualização, rollback, backup/restauração e recuperação de falhas.
- Se houver serviço de heartbeat, integrar a ele; caso contrário, documentar essa dependência e manter diagnóstico local disponível.

Aceite: reiniciar Windows inicia a aplicação sem login manual quando configurada para isso. Tentativa de segunda instância é recusada. Atualização/rollback preserva banco e pendências; migrations de estado local têm compatibilidade definida. Responsável pelo piloto consegue identificar a causa de uma falha usando o manual.

**Demonstração:** reiniciar o PC com fila pendente e provocar falhas de câmera/disco, verificando estados e retomada.

## Sprint 6 — Homologação e preparação do piloto

**Objetivo:** demonstrar a promessa completa do conjunto nas condições do local.

### QA-01 — Executar matriz de aceitação

| Cenário | Evidência esperada |
|---|---|
| Clique no início/meio/fim de segmento | Marcador do clique e janela contratada presentes no vídeo |
| Processamento atrasado | Mesmo conteúdo temporal, sem substituição por gravação recente |
| Mensagem repetida e confirmação perdida | Um evento local e um registro remoto |
| Bounce, pressão longa e cooldown | Quantidade correta de eventos e feedback coerente |
| Reset do Arduino e reconexão serial | Sessões distintas, sem falsa confirmação ou duplicidade silenciosa |
| Reinício em cada etapa persistente | Retomada dos eventos aceitos dentro da política de recuperação |
| Internet indisponível e API lenta | Fila preservada dentro dos limites e envio retomado |
| Câmera parada e lacuna no vídeo | Falha identificada, sem clipe de horário incorreto |
| Disco/filas perto do limite | Proteção das pendências e recusa explícita de novos eventos |
| Mudança de configuração | Eventos antigos preservam parâmetros e fontes |
| Ajuste de relógio e virada de dia | Identidade preservada e horário do conteúdo coerente |
| Atualização e restauração | Pendências e configuração recuperáveis conforme manual |

### QA-02 — Validar hardware, reprodução e capacidade

Entregas:

- Teste prolongado de uma jornada de funcionamento representativa da quadra; registrar duração e carga.
- Verificar imagem em jogadas rápidas, iluminação, enquadramento, FPS, resolução e bitrate.
- Validar reprodução em navegador e celulares do público, incluindo início do MP4 e duração.
- Medir CPU, memória, crescimento do disco, idade da fila e recuperação após período offline representativo.
- Medir clique → aceitação local, clique → clipe pronto e clique → reprodução cloud, com tamanho de amostra e percentis.

Aceite: todos os cenários obrigatórios têm evidência e resultado. A referência de até cinco minutos é avaliada de ponta a ponta nas condições declaradas; não é considerada cumprida apenas pelo tempo de concatenação. Pendências e limites conhecidos constam no relatório.

### QA-03 — Atualizar documentação e liberar versão candidata

Entregas:

- Atualizar READMEs, `.env.example`, protocolo, instruções do botão e status do projeto.
- Guia de ligação, placa/pinos suportados, versões de firmware/gateway e dependências.
- Relatório de homologação com condições, métricas, falhas restantes e procedimento de suporte.
- Registrar prontidão como planejado, implementado, verificado automaticamente, verificado em bancada ou validado no piloto.

Aceite: outra pessoa consegue instalar, identificar dispositivo/câmera, executar diagnóstico e recuperar uma falha seguindo a documentação. Não há defeito conhecido que perca silenciosamente um evento confirmado ou publique vídeo do horário errado. Antes de usar imagens de participantes reais, as políticas de acesso, retenção e gravação do piloto devem estar definidas no trabalho de produto.

**Demonstração:** jornada completa com botão real, falha de rede, reinício, retomada e reprodução do mesmo lance em homologação.

## Critério comum de conclusão

- História implementada com critérios de aceite verificados e evidências registradas.
- Testes de regressão relevantes aprovados; hardware indicado como pendente quando não foi exercitado fisicamente.
- Mensagens de falha e retomada verificadas, além do caminho de sucesso.
- Configuração e documentação correspondem ao comportamento entregue.
- Dependência externa não atendida fica identificada como pendência; simulador não comprova integração real.
- Cada sprint termina com demonstração do resultado descrito e atualização do backlog.

## Ponto de partida

Começar por GW-01, GW-02 e GW-03. A primeira correção funcional de vídeo é GW-04/GW-05. A confirmação de sucesso no LCD depende de GW-06 e não deve ser antecipada sem persistência local. Expansão para várias quadras e aumento de paralelismo entram após os critérios deste ciclo e as medições de capacidade.

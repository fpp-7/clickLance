# Contratos do gateway e botão — versão 1

Data: 15/10/2026. Especificação inicial para GW-01 e GW-02. Estes contratos
orientam a implementação; o firmware e o gateway atuais ainda usam a mensagem
legada e não oferecem aceitação durável.

## Tempo e capacidade

Fonte de produção: câmera IP (alinhamento de 15/10/2026). Modelo e protocolo
pendentes de confirmação; RTSP é hipótese inicial. DirectShow permanece no
código como entrada de teste. Os testes sintéticos não exigem webcam.

Para a câmera IP, medir atraso de transporte/decodificação e relacionar o tempo
do conteúdo ao clique. A âncora de recepção do gateway, sozinha, não garante
essa correspondência. A tolerância das bordas abaixo não autoriza ignorar
latência desconhecida. Validar FPS/GOP/bitrate com o stream real; decidir entre
cópia e recodificação com base nos cortes e na capacidade medida. Reconexão
abre sessão nova, sem unir silenciosamente vídeo separado por lacunas.

| Parâmetro | Valor inicial | Regra |
|---|---|---|
| Janela anterior / posterior | 120 s / 0 s | Independentes; janela fechada inclui o clique |
| Segmento | 60 s | Aguardar fechamento por até 65 s após o fim da janela |
| FPS / GOP | 30 / 30 frames | Alvo de um keyframe por segundo; validar na câmera |
| Tolerância das bordas | 1 s de conteúdo extra por borda | Não permite excluir o clique ou reduzir a janela; recodificar se necessário |
| Debounce elétrico | 50 ms estáveis | Firmware, um evento por pressão e soltura |
| Cooldown de produto | 5 s | Medido por relógio monotônico desde a última aceitação |
| Fila pendente | 100 eventos | Rejeitar antes de exceder; não descartar aceitos |
| Espaço livre mínimo | 2 GiB | Além da reserva estimada do novo evento |
| Cota buffer / clipes pendentes | 24 GiB / 20 GiB | Reservas impedem limpeza; saturação bloqueia novas aceitações |
| Bitrate de dimensionamento | 8 Mbit/s | Hipótese de capacidade, não limite aplicado ao encoder atual |
| Retenção do buffer | 6 h | Aproximadamente 21,6 GB a 8 Mbit/s |
| Tentativas de rede | Até 10 por ciclo | Espera 5 s × 2^(tentativa−1), teto 15 min, jitter de até 20% |

Depois do ciclo de tentativas, manter evento e artefatos em intervenção,
sem descarte. Credencial inválida ou conflito interrompem o ciclo imediatamente.
100 clipes de 122 s a 8 Mbit/s consomem aproximadamente 12,2 GB; prever
temporários e medir bitrate real antes de homologar os limites. Operação offline
é limitada pela primeira cota atingida, não por promessa de horas garantidas.

Registrar UTC em ISO 8601 com offset (`2026-10-15T15:00:00+00:00`) para
auditoria e âncora de sessão. Intervalos, cooldown e correspondência do vídeo
usam relógio monotônico. Ajuste do relógio civil não desloca eventos existentes.
Reinício abre nova sessão; não ligar intervalos monotônicos de processos distintos.

| Cenário no momento do clique | Decisão |
|---|---|
| Aquecimento sem 120 s contínuos | Rejeitar `WARMING_UP` |
| Cobertura contínua, captura ativa e capacidade disponível | Aceitar somente após commit do evento e reservas |
| Menos de 5 s desde aceitação anterior | Rejeitar `COOLDOWN` |
| Câmera parada ou progresso sem atualização por 5 s | Rejeitar `CAMERA_UNAVAILABLE` |
| Lacuna ou janela parcial | Rejeitar `VIDEO_GAP` |
| Disco abaixo da margem, cota ou fila excedida | Rejeitar `CAPACITY` |
| Internet ausente, vídeo e capacidade disponíveis | Aceitar localmente |
| Mesma identidade serial retransmitida | Retornar resultado original, sem novo cooldown/evento |

A reserva inclui o segmento aberto que contém o clique e sua finalização.
Falha posterior de captura deve ficar registrada como falha do evento aceito.
Prontidão anunciada é informativa; revalidar tudo na transação de aceitação.

## Serial v1

9600 baud, 8N1. ASCII, campos separados por `|`, uma mensagem por LF;
aceitar CRLF removendo somente CR final. Máximo de 192 bytes incluindo LF.
IDs: 1–32 caracteres `[A-Za-z0-9_-]`; campos não podem conter separador.
Inteiros decimais sem sinal. Buffers fixos no firmware. Linha excessiva ou
parcial por mais de 1 s é descartada até o próximo LF, sem ação nem sucesso.

| Direção | Forma / exemplo |
|---|---|
| Botão → gateway | `CL1|HELLO|mega01` |
| Gateway → botão | `CL1|WELCOME|mega01|sessao_gateway_01` |
| Botão → gateway | `CL1|SYNC|mega01|sessao_gateway_01|1500` |
| Gateway → botão | `CL1|SYNCED|mega01|sessao_gateway_01|1500` |
| Botão → gateway | `CL1|TRIGGER|mega01|sessao_gateway_01|1|3500` |
| Gateway → botão | `CL1|ACCEPTED|mega01|sessao_gateway_01|1|evento_uuid` |
| Gateway → botão | `CL1|REJECTED|mega01|sessao_gateway_01|1|WARMING_UP` |
| Gateway → botão | `CL1|STATE|mega01|sessao_gateway_01|READY` |

O gateway emite sessão aleatória nova a cada handshake, inclusive após reset
do Arduino. HELLO só ocorre sem sessão; retransmissão de HELLO não é um clique.
SYNC informa `millis()` atual do Arduino. Gateway ancora esse contador ao seu
monotônico de recepção e UTC; SYNCED ecoa o contador. Rejeitar sincronização
com ida e volta acima de 250 ms; repetir antes de permitir acionamentos.
Precisão serial deve ser medida em bancada; orçamento inicial de 250 ms.

TRIGGER contém sequência (1–4294967295) e millis original da pressão estável.
Gateway calcula instante pela diferença modular de 32 bits contra SYNC;
retransmissões preservam todos os campos. Não usar sua chegada como novo clique.
Renovar sincronização sem evento pendente a cada hora; exigir nova sessão antes
de esgotar sequência. Aceitar idade de até 15 s; rejeitar contador futuro ou
ambíguo (`INVALID_TIME`). Lookup da identidade ocorre antes da validação de idade.

Identidade única: `(dispositivo, sessão, sequência)`. Mapear para evento UUID
persistido; conteúdo divergente na mesma identidade gera `IDENTITY_CONFLICT`.
Persistir também rejeições para resposta estável. Versão, quantidade de campos,
identificador ou número inválidos não geram evento. Exemplo inválido:
`CL1|TRIGGER|mega01|sessao_gateway_01|-1|3500`.

Timeout de resposta: 2 s; até 3 retransmissões idênticas (4 envios totais).
Sem resposta: LCD `Sem confirmacao`, sem declarar sucesso. Gateway envia STATE
a cada segundo; ausência por 3 s indica desconexão. Estados: `CONNECTING`,
`WARMING_UP`, `READY`, `UNAVAILABLE`. Rejeições adicionais: `NOT_SYNCED`,
`INVALID_TIME`, `IDENTITY_CONFLICT`. REJECTED encerra a tentativa; nova pressão
recebe nova sequência. ACCEPTED só sai depois de commit e reservas duráveis.

Ao reconectar sem reset, primeiro consultar repetindo o TRIGGER pendente com
sessão antiga; gateway só retorna resultado já persistido, nunca aceita um novo
evento de sessão inativa. Sem resultado, rejeitar `NOT_SYNCED` e refazer handshake.
Reset do botão perde o pendente em RAM: mostrar `Conectando`, nunca sucesso.
Sem EEPROM não há recuperação garantida dessa identidade no botão; o evento
eventualmente aceito continua no gateway. Reset do gateway invalida sincronização;
lookup de identidade persistida continua permitido antes do novo handshake.

## Evidências e limites da Sprint 1

GW-01/GW-02: especificação registrada; valores precisam de medição no hardware.
GW-03: diagnóstico e regressões verificados automaticamente; veja
[evidências](evidencias_sprint_1.md). Validação física permanece pendente.
Implementar contratos temporais na Sprint 2 e aceitação/protocolo na Sprint 3.

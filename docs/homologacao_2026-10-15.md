# Entrega e homologação — 15/10/2026

Versão de desenvolvimento: gateway 0.3.0, protocolo CL1, esquema SQLite 1.
**Implementação e verificação automática entregues; piloto ainda não liberado.**
Os critérios que exigem equipamentos, cloud real e jornada no local permanecem
pendentes. Simulação não foi marcada como homologação física.

## Estado das sprints

| Sprint | Entrega disponível | Verificação e pendências |
|---|---|---|
| 1 — Contratos/diagnóstico | Contratos, fonte sintética, serial/storage/API substituíveis e regressões | Automatizada; parâmetros físicos ainda precisam de medição |
| 2 — Replay temporal | Catálogo por sessão, entrada RTSP, janela original, reservas, corte e validação | Vídeo sintético e RTSP TCP local verificados; Intelbras real e latência pendentes |
| 3 — Aceite durável/botão | Evento + reserva + identidade em um commit; CL1; firmware sem delay e com confirmação | SQLite/protocolo verificados; firmware compilado; botão/LCD físicos pendentes |
| 4 — Retomada/cloud/configuração | Etapas persistentes, backoff, envio condicional, API autenticada e configuração/cache | Simuladores HTTP/S3; conta AWS e backend real pendentes |
| 5 — Windows/operação | Supervisão, capacidade/retenção, mutexes, saúde, scripts e manual | Falhas de rede, limites e backup/restore verificados; tarefa/boot reais pendentes |
| 6 — Piloto | Matriz automática, demonstração recuperável e documentação operacional | Jornada, qualidade de imagem, celulares, métricas e piloto ainda pendentes |

## Evidências automáticas

Suíte completa: **67 testes aprovados**, sem falhas esperadas nem testes ignorados
na execução com FFmpeg, ffprobe e MediaMTX. Resumo exportado em
[qa_resultados_2026-10-15.json](qa_resultados_2026-10-15.json).

- Vídeo sintético real: marcadores por pixels no clique e nas bordas; clipes
  no início/meio/fim de segmento; janela completa de 120 s com margens produzindo
  120,2 s, a partir de fonte 160×120 a 30 FPS. Isso não mede qualidade na quadra.
- Captura RTSP TCP por servidor MediaMTX local, interrupção de transmissão,
  reinício supervisionado e sessão nova, sem mistura dos segmentos anteriores.
- Segmentos em escrita, CSV parcial, órfãos, arquivo ausente, lacunas entre
  segmentos e descontinuidades internas de pacotes são excluídos da cobertura.
- Fila atrasada e limpeza concorrente preservam a janela e suas reservas.
- SQLite interrompido em processo real (`os._exit`) antes/depois do commit;
  rollback de reserva sem evento e recuperação de confirmação persistida.
- Confirmação serial emitida somente depois de commit; repetição após reinício
  retorna o mesmo evento; payload divergente na mesma identidade gera conflito.
- Framing longo/parcial/inválido e retorno modular do contador de 32 bits.
- Etapas de geração/upload/registro retomadas por estado; timeout e erros
  permanentes preservam os artefatos e entram em intervenção quando necessário.
- Servidor HTTP local confirma commit e perde a resposta; reenvio mantém uma
  identidade remota, sem refazer upload já confirmado.
- SDK AWS validou PutObject condicional com Stubber; objeto já existente é
  reconciliado por SHA-256/tamanho. Não houve conexão com AWS real.
- Configuração inválida preserva cache; inicialização offline recupera versão
  válida; revogação permanece inativa após reinício; evento antigo conserva fontes.
- Fila/disco saturados recusam novos cliques; captura pausa quando não é possível
  recuperar espaço sem remover reservas. Liberação de espaço permite retomada.
- Mutex Windows recusa segunda instância e libera após saída. Backup/restore
  nos mesmos caminhos recupera evento aceito e reserva.
- Scripts PowerShell passaram na análise sintática. Não foram registrados como
  tarefa nem foi reiniciado o Windows para testar boot sem login.

Ferramentas: Python 3.13, FFmpeg Essentials 9.0.1, boto3 1.43.114 e
[MediaMTX 1.21.2](https://github.com/bluenviron/mediamtx/releases/tag/v1.21.2).

## Firmware

Arduino CLI 1.5.1, core AVR 1.8.8 e LiquidCrystal I2C 1.1.2, alvo Mega 2560:
9.722 bytes de programa (3%) e 1.181 bytes de RAM global (14%). Compilação
aprovada; não houve flash. Bounce, pressão longa, LCD e temporização de serial
real ainda não foram exercitados fisicamente. Uno não é anunciado como suportado.

## Demonstração reproduzível

Em `gateway_captura`, com FFmpeg/ffprobe no PATH:

```powershell
.\.venv\Scripts\python.exe -X utf8 demo_recovery.py
```

A demonstração gera vídeo, aceita um evento com fonte temporal controlada, perde
o ACK, cria o clipe, simula falha de rede, reabre o catálogo e reencontra a mesma
confirmação. Após backoff, publica em storage/API simulados uma única vez e só
então remove o clipe. Resultado exportado em
[demo_recuperacao_2026-10-15.json](demo_recuperacao_2026-10-15.json).
O script imprime o diretório temporário de artefatos e preserva fonte/log/JSON.

Para a suíte completa:

```powershell
$env:MEDIAMTX_PATH='C:\ferramentas\mediamtx.exe'
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p 'test_*.py' -v
```

Sem `MEDIAMTX_PATH`, o teste RTSP fica explicitamente ignorado; testes de vídeo
também exigem FFmpeg/ffprobe. Não interpretar testes ignorados como aprovados.

## Pendências para encerrar homologação

1. Identificar modelo/stream da Intelbras VIP e medir FPS, GOP, bitrate, latência,
   variação do atraso e precisão entre cena/clique. Validar imagem esportiva real.
2. Executar a matriz do botão/LCD em bancada, com pressão longa, bounce, reset,
   perda de ACK e reconexão USB. Confirmar perfil de pinagem e RTT de até 250 ms.
3. Provisionar AWS e backend de homologação com o [contrato v1](contrato_cloud_gateway_v1.md),
   unicidade por evento, conflito de conteúdo, referência autorizada e objetos privados.
4. Instalar e exercitar a tarefa no PC alvo, reiniciar Windows com fila pendente,
   validar atualização/rollback e recuperação operacional pelo manual.
5. Executar jornada representativa e carga offline. Medir CPU/memória/disco,
   percentis e amostra de clique→aceite, clipe pronto e reprodução cloud.
   A referência de cinco minutos ainda não foi homologada de ponta a ponta.
6. Validar reprodução em navegadores/celulares do público e definir políticas
   de gravação, retenção e acesso antes de usar imagens de participantes.

Fontes sem fechamento podem permanecer incompletas após queda abrupta da câmera
ou equipamento. O evento confirmado permanece rastreável e pode exigir intervenção;
os testes não garantem recuperação de vídeo que não chegou ou não foi finalizado.
Também não cobrem perda física do disco. Esses limites precisam ser exercitados
e aceitos na homologação, sem prometer sucesso silencioso no LCD.

O [manual operacional](operacao_gateway_windows.md) descreve instalação,
provisionamento, saúde, backup/restauração, atualização e rollback.

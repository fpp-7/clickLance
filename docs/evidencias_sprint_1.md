# Sprint 1 — evidências de diagnóstico

Execução em 15/10/2026, Windows, Python 3.13 e FFmpeg Essentials 9.0.1.
Sem câmera, Arduino, credenciais AWS ou backend. O FFmpeg foi instalado via
`winget install --id Gyan.FFmpeg.Essentials --exact`.

## Como repetir

Em `gateway_captura`, com FFmpeg e ffprobe no PATH:

```powershell
.\.venv\Scripts\python.exe -X utf8 -m unittest discover -s tests -p "test_*.py" -v
.\.venv\Scripts\python.exe -X utf8 test_pipeline.py --synthetic
```

Após instalar FFmpeg, reabra o terminal para atualizar o PATH. `-X utf8`
evita falha de codificação dos logs no console Windows.

## Resultado

10 testes executados: 6 aprovados e 4 falhas esperadas (`expectedFailure`).
Não há testes ignorados nesta execução. Falhas esperadas registram bugs
existentes; não significam requisitos atendidos. Uma correção fará o teste
acusar sucesso inesperado até que seu decorador seja removido na sprint responsável.

| Verificação | Resultado |
|---|---|
| Captura retorna erro apesar de conter `frame=` | Rejeitada |
| Diretório de captura preexistente | Rejeitado antes de iniciar FFmpeg |
| Pipeline chama storage e API substitutos | Aprovado, sem rede |
| Serial simulada: linha desconhecida e duas mensagens em cooldown | Um evento, conexão encerrada |
| Vídeo corrompido | Rejeitado por ffprobe |
| Captura sintética, concatenação de produção e decodificação | Três segmentos de 2 s; clipe de 4 s |
| GW-04: reinício mistura arquivo antigo com sessão nova | Defeito reproduzido, falha esperada |
| GW-05: segmento do clique é excluído | Defeito reproduzido, falha esperada |
| GW-05: atraso na fila muda fontes do mesmo evento | Defeito reproduzido, falha esperada |
| GW-07: falha no upload apaga clipe local | Defeito reproduzido, falha esperada |

As regressões instanciam DVRManager, SerialListener e VideoProcessor reais.
A concatenação é substituída somente nos testes de seleção e falha de rede;
o teste sintético executa o comando de captura compartilhado e a concatenação real.
Mocks ficam nas fronteiras serial, storage e HTTP. O cliente S3 pode ser
injetado no construtor para impedir consulta a credenciais/rede durante testes.

## Demonstração reproduzível

`test_pipeline.py --synthetic` cria diretório temporário exclusivo e imprime
seu caminho. Nele ficam segmentos, log do FFmpeg, `report.json` e dois clipes:

- `lance_teste.mp4`: concatenação verificável dos segmentos 1 e 2 (conteúdo t=2–6).
- `replay_atual_clique_t5.mp4`: seleção atual do DVR, segmentos 0 e 1 (t=0–4).

O relógio PTS e o número de frame estão sobrepostos ao vídeo; um quadrado
vermelho marca o início de cada intervalo de 2 s. Para um clique em t=5,
o replay atual exclui o segmento 2 que contém a jogada. O JSON registra
`click_segment_missing: true`. Este exemplo usa janela reduzida para diagnóstico;
não valida o contrato de replay de 120 s nem o corte exato da Sprint 2.
`ok: true` no relatório significa captura/concatenação decodificáveis, não
correção do replay. A verificação temporal nesta etapa usa as fontes selecionadas;
a medição das bordas e do conteúdo final é aceite da Sprint 2.

## Pendências

- GW-01/GW-02: revisão dos contratos e medição de câmera, bitrate e sincronização em bancada.
- Captura DirectShow com hardware real e identificação da porta do Arduino.
- Protocolo v1, persistência e LCD ainda não implementados; serial simulada exercita o protocolo legado.
- Corrigir GW-04/GW-05 na Sprint 2 e conservação/retomada de envios em GW-07.
- Cloud real e homologação do piloto permanecem fora desta evidência.

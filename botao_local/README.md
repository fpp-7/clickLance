# Botão CL1 — Arduino Mega 2560

Firmware de desenvolvimento alinhado ao gateway 0.3.0. Compilado em 15/10/2026
com Arduino CLI 1.5.1, core `arduino:avr` 1.8.8 e `LiquidCrystal I2C` 1.1.2:
9.722 bytes de programa e 1.181 bytes de RAM global. Nenhum upload nem teste
físico foi executado nesta entrega. Uno é recusado na compilação.

## Ligações e configuração

| Componente | Mega 2560 |
|---|---|
| Botão normalmente aberto | Entre D22 e GND; `INPUT_PULLUP` interno |
| LCD I2C SDA | D20 / SDA |
| LCD I2C SCL | D21 / SCL |
| LCD GND | GND comum |
| LCD alimentação | Conforme especificação do módulo |
| Comunicação com o gateway | USB, serial 9600 baud |

Pinagem I2C confirmada no [pinout oficial do Mega](https://docs.arduino.cc/resources/pinouts/A000067-full-pinout.pdf).
O firmware usa endereço I2C `0x27` e LCD 16×2; confirme o módulo na bancada.
O `DEVICE` no sketch precisa ser igual a `DEVICE_ID` no `.env` do gateway.

Na raiz do projeto:

```powershell
arduino-cli core install arduino:avr@1.8.8
arduino-cli lib install 'LiquidCrystal I2C@1.1.2'
arduino-cli compile --fqbn arduino:avr:mega botao_local
```

Só depois de identificar a placa/porta corretas, faça upload pela IDE ou CLI.
O gateway precisa do firmware novo: a mensagem de texto antiga não recebe ACK
no protocolo de produção atual. O monitor serial deve ficar fechado durante uso.

## Comportamento

O botão precisa ficar estável por 50 ms; uma pressão longa gera um acionamento
e exige soltura antes do próximo. Cooldown de cinco segundos é independente.
Temporização usa subtração de `millis()` sem `delay()`, inclusive na virada do contador.
Buffers têm tamanho fixo e linhas inválidas/longas/parciais são descartadas.

Após HELLO/WELCOME e sincronização, o LCD acompanha estado do gateway. O clique
fica em `Confirmando...`; até quatro envios preservam a mesma sessão, sequência
e contador original. Somente `ACCEPTED` correspondente mostra `Evento aceito`.
Rejeição/cooldown não mostram sucesso. Sem resposta, mostra `Sem confirmacao`.
Aceito significa persistência/reserva local, não publicação do vídeo na internet.

Sem EEPROM, reset perde um pendente do botão; o LCD volta a conectar sem informar
sucesso. Um evento já aceito continua no gateway. Confira os limites e o formato
das mensagens no [contrato serial](../docs/contratos_gateway_botao.md).

## Bancada pendente

Verificar bounce real, pressão mantida, LCD, desconexão USB, reset durante ACK,
perda de confirmação, RTT serial de até 250 ms, leitura simultânea de mensagens
e retorno coerente com câmera/rede/disco indisponíveis. Compilação não comprova
esses comportamentos no hardware físico.

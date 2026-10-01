"""
serial_listener.py — Escuta a porta serial do Arduino.

Quando recebe a mensagem "Botao foi Pressionado!", registra o timestamp
e coloca o evento numa Queue thread-safe. Nunca bloqueia o processamento.

Inclui debounce de software para ignorar bounces elétricos do botão
(múltiplas mensagens em milissegundos para um único clique físico).
"""

import time
from queue import Queue

import serial

from .config import Config
from .logger import logger

# Mensagem esperada do Arduino
TRIGGER_MESSAGE = "Botao foi Pressionado!"

# Tempo mínimo entre dois cliques válidos (em segundos).
# Ignora bounces elétricos e cliques acidentais muito rápidos.
DEBOUNCE_SECONDS = 5


class SerialListener:
    """Produtor: escuta a serial e enfileira eventos de clique."""

    def __init__(self, event_queue: Queue) -> None:
        self.queue = event_queue
        self.port: str = Config.SERIAL_PORT
        self.baud_rate: int = Config.BAUD_RATE
        self._running = True
        self._last_trigger_time: float = 0.0  # Controle de debounce

    def start_listening(self) -> None:
        """
        Loop bloqueante que lê a porta serial.
        Deve rodar na Thread Principal ou em uma Thread dedicada.
        """
        self._running = True
        connection: serial.Serial | None = None

        while self._running:
            try:
                logger.info("Serial: Conectando em %s @ %d baud...", self.port, self.baud_rate)
                connection = serial.Serial(self.port, self.baud_rate, timeout=1)

                # O Arduino reseta ao abrir a serial — aguardamos estabilizar
                time.sleep(2)
                logger.info("Serial: Conectado! Aguardando cliques do botão...")

                while self._running:
                    if connection.in_waiting > 0:
                        raw_line = connection.readline()
                        line = raw_line.decode("utf-8", errors="ignore").strip()

                        if not line:
                            continue

                        logger.debug("Serial: Recebido → '%s'", line)

                        if line == TRIGGER_MESSAGE:
                            now = time.time()
                            elapsed = now - self._last_trigger_time

                            # DEBOUNCE: ignora cliques dentro da janela de cooldown
                            if elapsed < DEBOUNCE_SECONDS:
                                logger.debug(
                                    "Serial: Bounce ignorado (%.1fs desde o último clique válido, "
                                    "cooldown=%ds).",
                                    elapsed,
                                    DEBOUNCE_SECONDS,
                                )
                                continue

                            self._last_trigger_time = now
                            self.queue.put({"timestamp": now})
                            logger.info(
                                "Serial: ✅ BOTÃO PRESSIONADO! Evento enfileirado. "
                                "Fila contém %d evento(s) pendente(s).",
                                self.queue.qsize(),
                            )

            except serial.SerialException as exc:
                logger.error("Serial: Erro de conexão → %s", exc)
                logger.info("Serial: Tentando reconectar em 5 segundos...")
                time.sleep(5)

            except Exception as exc:
                logger.error("Serial: Erro inesperado → %s", exc)
                time.sleep(5)

            finally:
                if connection and connection.is_open:
                    connection.close()
                    logger.debug("Serial: Porta fechada.")

        logger.info("Serial: Listener finalizado.")

    def stop(self) -> None:
        """Sinaliza para o loop de escuta encerrar."""
        self._running = False


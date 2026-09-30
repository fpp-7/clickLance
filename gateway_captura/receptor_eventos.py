from datetime import datetime

import serial
import time

# --- CONFIGURACOES ---
# Troque 'COM3' pela porta que aparece na sua Arduino IDE
porta_serial = 'COM6' 
baud_rate = 9600

try:
    # Inicia a conexao com o Arduino
    print(f"Tentando conectar na porta {porta_serial}...")
    arduino = serial.Serial(porta_serial, baud_rate, timeout=1)
    
    # Aguarda 2 segundos para o Arduino reiniciar (comportamento padrao ao conectar)
    time.sleep(2) 
    print("Conectado! Aguardando o clique do botao...\n")

    # Loop infinito para ficar escutando a porta
    while True:
        # Verifica se ha dados esperando para serem lidos
        if arduino.in_waiting > 0:
            # Le a linha, decodifica para texto e remove espacos/quebras de linha
            mensagem = arduino.readline().decode('utf-8').strip()
            
            # Se a mensagem nao for vazia, exibe na tela
            if mensagem:
                print(f"Recebido do Arduino: {mensagem}")
                
                # Voce pode criar acoes baseadas na mensagem:
                if mensagem == "Botao foi Pressionado!":
                    horario_atual = datetime.now().strftime("%H:%M:%S")
                    print(f"--> Acao em Python detectada: O botao foi ativado as {horario_atual}.")
                    # Aqui voce poderia, por exemplo, simular o clique de um mouse, 
                    # abrir um programa, enviar um e-mail, etc.

except serial.SerialException:
    print(f"ERRO: Nao foi possivel abrir a porta {porta_serial}.")
    print("Verifique se o cabo esta conectado e se o Monitor Serial da IDE esta FECHADO.")
except KeyboardInterrupt:
    print("\nPrograma encerrado pelo usuario.")
finally:
    # Garante que a porta sera fechada ao sair do programa
    if 'arduino' in locals() and arduino.is_open:
        arduino.close()
        print("Conexao serial fechada.")
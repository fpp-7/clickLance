#include <Wire.h>
#include <LiquidCrystal_I2C.h>

// Definicao do pino do botao
const int PINO_SW = 22;

// Variavel para guardar o estado anterior do botao
bool estadoAnteriorBotao = HIGH; 

LiquidCrystal_I2C lcd(0x27, 16, 2);

void setup() {
  // Inicia a comunicacao serial a 9600 bps
  Serial.begin(9600);
  
  // Configura o pino 22 como entrada com pull-up interno
  pinMode(PINO_SW, INPUT_PULLUP);
  
  Serial.println("Sistema iniciado. Aguardando clique...");

  // Inicializa o LCD
  lcd.init();
  lcd.backlight();

  // Tela de boas-vindas
  lcd.setCursor(0, 0);
  lcd.print("Joystick + LCD");
  lcd.setCursor(0, 1);
  lcd.print("Arduino Mega");
  delay(1500);
  lcd.clear();
}

void loop() {
  // Le o estado atual do pino 
  bool estadoAtualBotao = digitalRead(PINO_SW);
  
  // Posiciona o cursor na linha debaixo
  lcd.setCursor(0, 1);

  // 1. LOGICA DO LCD: Exibe o texto continuamente dependendo do estado atual
  if (estadoAtualBotao == LOW) {
    lcd.print("Botao: APERTADO "); // Os espacos no fim apagam os caracteres restantes
  } else {
    lcd.print("Botao: SOLTO    ");
  }

  // 2. LOGICA DA SERIAL: Verifica a transicao para enviar a mensagem APENAS UMA VEZ
  if (estadoAnteriorBotao == HIGH && estadoAtualBotao == LOW) {
    // Envia a informacao pro Python
    Serial.println("Botao foi Pressionado!");
    
    // Pequeno atraso para debounce
    delay(50);
  }

  // Atualiza o estado anterior
  estadoAnteriorBotao = estadoAtualBotao;

  // Um pequeno atraso no final do loop ajuda a estabilizar as leituras
  // e evita que o Arduino rode milhares de vezes por segundo desnecessariamente
  delay(10); 
}
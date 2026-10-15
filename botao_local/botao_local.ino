#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#if !defined(__AVR_ATmega2560__)
#error "Perfil suportado: Arduino Mega 2560, botao no pino 22"
#endif

const uint8_t BUTTON_PIN = 22;
const char DEVICE[] = "mega01";
LiquidCrystal_I2C lcd(0x27, 16, 2);
char session[33] = "", line[192], pendingLine[192];
uint16_t lineLength = 0;
bool discardLine = false, pending = false, synced = false, syncing = false;
bool rawButton = HIGH, stableButton = HIGH, acceptedOnce = false, timedOut = false;
bool acceptedFeedback = false;
uint32_t rawSince = 0, lineSince = 0, helloAt = 0, heartbeatAt = 0;
uint32_t syncAt = 0, syncCounter = 0, lastSync = 0, sequence = 0;
uint32_t pendingAt = 0, pendingSequence = 0, acceptedAt = 0;
uint8_t sends = 0;
char gatewayState[16] = "CONNECTING";
const char *screen = "";

void show(const char *text) {
  if (strcmp(screen, text) == 0) return;
  screen = text;
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ClickLance");
  lcd.setCursor(0, 1); lcd.print(text);
}

bool identifier(const char *s) {
  size_t n = strlen(s);
  if (n == 0 || n > 32) return false;
  for (size_t i = 0; i < n; ++i) {
    char c = s[i];
    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
          (c >= '0' && c <= '9') || c == '_' || c == '-')) return false;
  }
  return true;
}

bool parseNumber(const char *s, uint32_t &value) {
  if (!*s) return false;
  value = 0;
  while (*s) {
    if (*s < '0' || *s > '9') return false;
    uint8_t digit = *s++ - '0';
    if (value > (UINT32_MAX - digit) / 10) return false;
    value = value * 10 + digit;
  }
  return true;
}

void sendSync(uint32_t now) {
  syncCounter = syncAt = now;
  syncing = true; synced = false;
  Serial.print("CL1|SYNC|"); Serial.print(DEVICE); Serial.print('|');
  Serial.print(session); Serial.print('|'); Serial.println(syncCounter);
}

void resetSession() {
  session[0] = 0; synced = syncing = false;
  strcpy(gatewayState, "CONNECTING");
}

void handleLine(uint32_t now) {
  // Split estrito: campos vazios e campos extras sao invalidos.
  char *fields[7]; uint8_t count = 1;
  fields[0] = line;
  for (char *p = line; *p; ++p) {
    if (*p == '|') {
      *p = 0;
      if (count >= 7) return;
      fields[count++] = p + 1;
    }
  }
  for (uint8_t i = 0; i < count; ++i) if (!fields[i][0]) return;
  if (count < 4 || strcmp(fields[0], "CL1") || strcmp(fields[2], DEVICE)) return;
  if (!identifier(fields[3])) return;
  if (!strcmp(fields[1], "WELCOME") && count == 4 && !pending && !session[0]) {
    strcpy(session, fields[3]); sequence = 0; heartbeatAt = now;
    sendSync(now); return;
  }
  if (strcmp(fields[3], session)) return;
  if (!strcmp(fields[1], "SYNCED") && count == 5 && syncing) {
    uint32_t echo;
    if (!parseNumber(fields[4], echo) || echo != syncCounter) return;
    syncing = false;
    if ((uint32_t)(now - syncAt) <= 250) { synced = true; lastSync = now; }
    heartbeatAt = now; return;
  }
  if (!strcmp(fields[1], "STATE") && count == 5) {
    if (strcmp(fields[4], "READY") && strcmp(fields[4], "WARMING_UP") &&
        strcmp(fields[4], "UNAVAILABLE") && strcmp(fields[4], "CONNECTING")) return;
    strncpy(gatewayState, fields[4], sizeof(gatewayState) - 1);
    gatewayState[sizeof(gatewayState) - 1] = 0;
    heartbeatAt = now; return;
  }
  if (count == 6 && pending) {
    uint32_t ackSequence;
    if (!parseNumber(fields[4], ackSequence) || ackSequence != pendingSequence) return;
    if (!strcmp(fields[1], "ACCEPTED") && identifier(fields[5])) {
      pending = false; acceptedOnce = true; acceptedAt = now; timedOut = false;
      acceptedFeedback = true;
    } else if (!strcmp(fields[1], "REJECTED") && identifier(fields[5])) {
      pending = false; timedOut = false;
      acceptedFeedback = false;
      if (!strcmp(fields[5], "COOLDOWN")) { acceptedOnce = true; acceptedAt = now; }
      else strcpy(gatewayState, "UNAVAILABLE");
    }
  }
}

void receive(uint32_t now) {
  if (lineLength && (uint32_t)(now - lineSince) > 1000) {
    lineLength = 0; discardLine = true;
  }
  // Limita trabalho por loop para continuar lendo o botao.
  for (uint16_t budget = 0; budget < 192 && Serial.available(); ++budget) {
    char c = Serial.read();
    if (c == '\n') {
      if (!discardLine) {
        if (lineLength && line[lineLength - 1] == '\r') --lineLength;
        line[lineLength] = 0; handleLine(now);
      }
      lineLength = 0; discardLine = false;
    } else if (!discardLine) {
      if (!lineLength) lineSince = now;
      if ((unsigned char)c < 32 && c != '\r') { discardLine = true; continue; }
      if ((unsigned char)c > 126 || lineLength >= sizeof(line) - 1) {
        lineLength = 0; discardLine = true;
      } else line[lineLength++] = c;
    }
  }
}

void trigger(uint32_t now) {
  if (pending || !synced || strcmp(gatewayState, "READY") ||
      (uint32_t)(now - heartbeatAt) > 3000 ||
      (acceptedOnce && (uint32_t)(now - acceptedAt) < 5000)) return;
  if (sequence == UINT32_MAX) { resetSession(); return; }
  pendingSequence = ++sequence;
  snprintf(pendingLine, sizeof(pendingLine), "CL1|TRIGGER|%s|%s|%lu|%lu",
           DEVICE, session, (unsigned long)pendingSequence, (unsigned long)now);
  pending = true; sends = 1; pendingAt = now; timedOut = false;
  Serial.println(pendingLine);
}

void setup() {
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  Serial.begin(9600);
  lcd.init(); lcd.backlight(); show("Conectando");
}

void loop() {
  uint32_t now = millis();
  receive(now);
  bool raw = digitalRead(BUTTON_PIN);
  if (raw != rawButton) { rawButton = raw; rawSince = now; }
  if (raw != stableButton && (uint32_t)(now - rawSince) >= 50) {
    stableButton = raw;
    if (stableButton == LOW) trigger(now);
  }
  if (pending && (uint32_t)(now - pendingAt) >= 2000) {
    if (sends < 4) { Serial.println(pendingLine); ++sends; pendingAt = now; }
    else { pending = false; timedOut = true; }
  }
  if (!pending) {
    if (session[0] && (uint32_t)(now - heartbeatAt) > 3000) resetSession();
    if (!session[0] && (uint32_t)(now - helloAt) >= 1000) {
      Serial.print("CL1|HELLO|"); Serial.println(DEVICE); helloAt = now;
    } else if (session[0] && ((!synced && !syncing) ||
               (synced && (uint32_t)(now - lastSync) >= 3600000UL))) sendSync(now);
    if (syncing && (uint32_t)(now - syncAt) > 1000) syncing = false;
  }
  if (pending) show("Confirmando...");
  else if (timedOut) show("Sem confirmacao");
  else if (acceptedFeedback && (uint32_t)(now - acceptedAt) < 1500) show("Evento aceito");
  else if (!synced) show("Conectando");
  else if (!strcmp(gatewayState, "WARMING_UP")) show("Preparando...");
  else if (strcmp(gatewayState, "READY")) show("Indisponivel");
  else if (acceptedOnce && (uint32_t)(now - acceptedAt) < 5000) show("Aguarde");
  else show("Pronto");
}

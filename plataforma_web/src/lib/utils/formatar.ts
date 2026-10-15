const LOCALE = "pt-BR";

const formatadorHora = new Intl.DateTimeFormat(LOCALE, { hour: "2-digit", minute: "2-digit" });
const formatadorDiaSemana = new Intl.DateTimeFormat(LOCALE, { weekday: "short" });
const formatadorMes = new Intl.DateTimeFormat(LOCALE, { month: "short" });
const formatadorDataCompleta = new Intl.DateTimeFormat(LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

const semPonto = (texto: string) => texto.replace(/\./g, "");
const capitalizar = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/** "20:35" */
export function formatarHora(iso: string): string {
  return formatadorHora.format(new Date(iso));
}

/** "20h – 21h". Meia-noite como fim vira "24h" para não ler "23h – 0h". */
export function formatarFaixaHoraria(inicioIso: string, fimIso: string): string {
  const inicio = new Date(inicioIso).getHours();
  const fimBruto = new Date(fimIso).getHours();
  const fim = fimBruto === 0 ? 24 : fimBruto;
  return `${inicio}h – ${fim}h`;
}

function mesmoDia(a: Date, b: Date): boolean {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

/** "Hoje", "Ontem" ou "Sáb, 3 de out". */
export function formatarDataRelativa(iso: string, agora: Date = new Date()): string {
  const data = new Date(iso);
  if (mesmoDia(data, agora)) return "Hoje";

  const ontem = new Date(agora);
  ontem.setDate(ontem.getDate() - 1);
  if (mesmoDia(data, ontem)) return "Ontem";

  const diaSemana = capitalizar(semPonto(formatadorDiaSemana.format(data)));
  const mes = semPonto(formatadorMes.format(data));
  return `${diaSemana}, ${data.getDate()} de ${mes}`;
}

/** "05/10/2026" */
export function formatarData(iso: string): string {
  return formatadorDataCompleta.format(new Date(iso));
}

/** 75 → "1:15"; 15 → "0:15" */
export function formatarDuracao(segundos: number): string {
  const total = Math.max(0, Math.round(segundos));
  const minutos = Math.floor(total / 60);
  const resto = total % 60;
  return `${minutos}:${String(resto).padStart(2, "0")}`;
}

export function apenasDigitos(texto: string): string {
  return texto.replace(/\D/g, "");
}

/**
 * Máscara progressiva de celular: aceita o que a pessoa digitou até agora.
 * "11999998888" → "(11) 99999-8888"; "1133334444" → "(11) 3333-4444"
 */
export function formatarTelefone(texto: string): string {
  const digitos = apenasDigitos(texto).slice(0, 11);
  if (digitos.length === 0) return "";
  if (digitos.length <= 2) return `(${digitos}`;
  const ddd = digitos.slice(0, 2);
  const numero = digitos.slice(2);
  if (numero.length <= 4) return `(${ddd}) ${numero}`;
  const corte = numero.length > 8 ? 5 : 4;
  return `(${ddd}) ${numero.slice(0, corte)}-${numero.slice(corte)}`;
}

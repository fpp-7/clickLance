/** Junta classes ignorando valores falsos. Suficiente para o que precisamos sem mais uma dependência. */
export function cn(...classes: Array<string | false | null | undefined>): string {
  return classes.filter(Boolean).join(" ");
}

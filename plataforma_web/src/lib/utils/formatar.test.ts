import { describe, expect, it } from "vitest";
import {
  formatarDataRelativa,
  formatarDuracao,
  formatarFaixaHoraria,
  formatarHora,
  formatarTelefone,
} from "./formatar";

const local = (ano: number, mes: number, dia: number, hora = 0, minuto = 0) =>
  new Date(ano, mes - 1, dia, hora, minuto).toISOString();

describe("formatarHora", () => {
  it("mostra hora e minuto com dois dígitos", () => {
    expect(formatarHora(local(2026, 10, 5, 20, 35))).toBe("20:35");
    expect(formatarHora(local(2026, 10, 5, 8, 5))).toBe("08:05");
  });
});

describe("formatarFaixaHoraria", () => {
  it("monta a faixa da partida", () => {
    expect(formatarFaixaHoraria(local(2026, 10, 5, 20), local(2026, 10, 5, 21))).toBe("20h – 21h");
  });

  it("usa 24h quando a partida termina à meia-noite", () => {
    expect(formatarFaixaHoraria(local(2026, 10, 5, 23), local(2026, 10, 6, 0))).toBe("23h – 24h");
  });
});

describe("formatarDataRelativa", () => {
  const agora = new Date(2026, 9, 5, 21, 0);

  it("reconhece hoje e ontem", () => {
    expect(formatarDataRelativa(local(2026, 10, 5, 9), agora)).toBe("Hoje");
    expect(formatarDataRelativa(local(2026, 10, 4, 23), agora)).toBe("Ontem");
  });

  it("escreve dia da semana e mês por extenso curto", () => {
    expect(formatarDataRelativa(local(2026, 10, 3, 18), agora)).toBe("Sáb, 3 de out");
  });
});

describe("formatarDuracao", () => {
  it("formata em minutos e segundos", () => {
    expect(formatarDuracao(15)).toBe("0:15");
    expect(formatarDuracao(75)).toBe("1:15");
    expect(formatarDuracao(600)).toBe("10:00");
  });
});

describe("formatarTelefone", () => {
  it("aplica a máscara progressivamente", () => {
    expect(formatarTelefone("")).toBe("");
    expect(formatarTelefone("11")).toBe("(11");
    expect(formatarTelefone("119")).toBe("(11) 9");
    expect(formatarTelefone("1133334444")).toBe("(11) 3333-4444");
    expect(formatarTelefone("11999998888")).toBe("(11) 99999-8888");
  });

  it("ignora o que não é dígito e corta o excesso", () => {
    expect(formatarTelefone("+55 (11) 99999-8888 ramal")).toBe("(55) 11999-9988");
  });
});

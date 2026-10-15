import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { Arena } from "@/lib/api";
import { CartaoArena } from "./CartaoArena";

const arena: Arena = {
  id: "a1",
  slug: "arena-bola-na-rede",
  nome: "Arena Bola na Rede",
  cidade: "São Paulo",
  uf: "SP",
  bairro: "Tatuapé",
  logoUrl: null,
  corPrimaria: "#a3e635",
  totalQuadras: 3,
};

describe("CartaoArena", () => {
  it("mostra nome, localização e leva para a página da arena", () => {
    render(<CartaoArena arena={arena} />);

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/arenas/arena-bola-na-rede");
    expect(link).toHaveTextContent("Arena Bola na Rede");
    expect(link).toHaveTextContent("Tatuapé, São Paulo · SP");
    expect(link).toHaveTextContent("3 quadras");
  });

  it("usa singular quando há uma quadra só", () => {
    render(<CartaoArena arena={{ ...arena, totalQuadras: 1 }} />);
    expect(screen.getByRole("link")).toHaveTextContent("1 quadra");
  });
});

import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Desmonta o que cada teste renderizou para um não enxergar o DOM do outro
afterEach(() => {
  cleanup();
});

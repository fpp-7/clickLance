import { describe, expect, it } from "vitest";
import { safePlayerReturn } from "./navigation";

describe("sign-in return routes", () => {
  it.each([
    "https://evil.example",
    "//evil.example",
    "/\n/evil.example",
    "/\\evil.example",
    "/admin",
    "javascript:alert(1)",
  ])("rejects external or unsupported return %j", (value) => {
    expect(safePlayerReturn(value)).toBe("/arenas");
  });
  it("preserves the selected court and its local context", () => {
    expect(safePlayerReturn("/quadras/court-1?data=2026-10-30&nome=Quadra%201")).toBe(
      "/quadras/court-1?data=2026-10-30&nome=Quadra%201",
    );
  });
});

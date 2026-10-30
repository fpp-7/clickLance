import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Companies } from "./Companies";
import { AdminShell } from "./AdminShell";
import { ConfirmAction } from "./shared";

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  user: { usuario_id: "operator", email: "operator@example.invalid", platform_admin: false },
  authenticated: true,
}));
vi.mock("@/lib/platform/api", () => ({
  request: mocks.request,
  isDemo: true,
  ApiError: class extends Error {},
}));
vi.mock("@/lib/platform/session", () => ({
  useSession: () => ({
    user: mocks.authenticated ? mocks.user : null,
    loading: false,
    error: null,
    logout: vi.fn(),
  }),
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/admin",
  useRouter: () => ({ push: vi.fn() }),
}));
function setup(children: React.ReactNode) {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      {children}
    </QueryClientProvider>,
  );
}

describe("administração", () => {
  beforeEach(() => {
    mocks.request.mockReset();
    mocks.user.platform_admin = false;
    mocks.authenticated = true;
    mocks.request.mockResolvedValue({
      items: [{ empresa_id: "empresa-1", nome: "Arena de teste", active: true, retencao_dias: 2 }],
      page: 0,
      size: 20,
      total: 1,
    });
  });
  it("exige sessão antes de montar operações e consultar empresas", () => {
    mocks.authenticated = false;
    setup(
      <AdminShell>
        <Companies />
      </AdminShell>,
    );
    expect(screen.getByRole("link", { name: "Entrar na administração" })).toBeInTheDocument();
    expect(screen.queryByText("Empresas autorizadas")).not.toBeInTheDocument();
    expect(mocks.request).not.toHaveBeenCalled();
  });
  it("mostra recursos autorizados sem oferecer criação de empresa a administrador vinculado", async () => {
    setup(<Companies />);
    expect(await screen.findByRole("link", { name: "Arena de teste" })).toHaveAttribute(
      "href",
      "/admin/empresas/empresa-1",
    );
    expect(screen.queryByRole("button", { name: "Nova empresa" })).not.toBeInTheDocument();
    expect(mocks.request).toHaveBeenCalledWith("/admin/empresas?page=0&size=20", { role: "admin" });
  });
  it("só executa uma alteração após confirmação explícita", async () => {
    const action = vi.fn().mockResolvedValue(undefined);
    setup(
      <ConfirmAction
        label="Inativar"
        message="Interromper os envios desta origem?"
        action={action}
      />,
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Inativar" }));
    expect(action).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(action).not.toHaveBeenCalled();
    await user.click(screen.getByRole("button", { name: "Inativar" }));
    await user.click(screen.getByRole("button", { name: "Confirmar" }));
    await waitFor(() => expect(action).toHaveBeenCalledTimes(1));
  });
});

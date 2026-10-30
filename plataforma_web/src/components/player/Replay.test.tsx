import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ReplayView } from "./Replay";

const mocks = vi.hoisted(() => ({
  replay: vi.fn(),
  access: vi.fn(),
  save: vi.fn(),
  remove: vi.fn(),
}));
vi.mock("next/navigation", () => ({ usePathname: () => "/lances/fixture" }));
vi.mock("@/lib/platform/session", () => ({
  useSession: () => ({ user: { usuario_id: "player-a" }, loading: false }),
}));
vi.mock("@/lib/platform/api", () => ({
  platform: mocks,
  isDemo: true,
  safeMediaUrl: (value: string) => (value.startsWith("https:") ? value : null),
}));
const replay = {
  evento_id: "fixture",
  empresa_id: "arena",
  quadra_id: "court",
  timestamp: "2026-10-30T20:30:00Z",
  expira_em: "2026-11-01T20:30:00Z",
  salvo: false,
};

function mount() {
  return render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <ReplayView id="fixture" />
    </QueryClientProvider>,
  );
}

describe("Reprodução e biblioteca", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.replay.mockResolvedValue(replay);
  });

  it("solicita acesso ao vídeo somente após a ação de reproduzir", async () => {
    mocks.access.mockResolvedValue({
      url: "https://media.example.invalid/temporary.mp4",
      expira_em: "2026-10-30T21:00:00Z",
    });
    mount();
    const play = await screen.findByRole("button", { name: "Reproduzir lance" });
    expect(mocks.access).not.toHaveBeenCalled();
    await userEvent.click(play);
    await waitFor(() => expect(mocks.access).toHaveBeenCalledWith("fixture", false));
    expect(await screen.findByLabelText("Vídeo do seu lance")).toHaveAttribute(
      "src",
      "https://media.example.invalid/temporary.mp4",
    );
  });

  it("mantém salvar disponível sem anunciar sucesso quando o servidor recusa", async () => {
    mocks.save.mockRejectedValue({ status: 409, code: "LIBRARY_LIMIT_REACHED" });
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "Salvar na biblioteca" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sua biblioteca chegou a 100 lances",
    );
    expect(screen.queryByText("Lance salvo na sua biblioteca.")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar na biblioteca" })).toBeEnabled();
  });

  it("retira o player quando remover o último acesso torna o lance indisponível", async () => {
    mocks.replay
      .mockReset()
      .mockResolvedValueOnce({ ...replay, salvo: true })
      .mockRejectedValue({ status: 404 });
    mocks.remove.mockResolvedValue(undefined);
    mount();
    await userEvent.click(await screen.findByRole("button", { name: "Remover da biblioteca" }));
    expect(
      await screen.findByRole("heading", { name: "Este lance não está disponível" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reproduzir lance" })).not.toBeInTheDocument();
  });
});

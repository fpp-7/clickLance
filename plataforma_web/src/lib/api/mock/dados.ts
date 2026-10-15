import type { Arena, Lance, Modalidade, Partida, Quadra, StatusQuadra } from "../tipos";
import { criarAleatorio, hashTexto } from "./aleatorio";

/** Lances somem da plataforma depois desse prazo. Valor assumido até a política oficial. */
export const DIAS_RETENCAO = 7;

/** Geramos alguns dias além da retenção para a tela de lance expirado ter o que mostrar. */
export const DIAS_HISTORICO = 9;

const UM_MINUTO = 60_000;
const UMA_HORA = 60 * UM_MINUTO;
const UM_DIA = 24 * UMA_HORA;

// ---------------------------------------------------------------------------
// Vídeos públicos de exemplo (placeholder até termos clipes reais)
// ---------------------------------------------------------------------------

export interface VideoExemplo {
  url: string;
  thumbnailUrl: string;
  duracaoSegundos: number;
}

const BASE_VIDEOS = "https://storage.googleapis.com/gtv-videos-bucket/sample";

function video(nome: string, duracaoSegundos: number): VideoExemplo {
  return {
    url: `${BASE_VIDEOS}/${nome}.mp4`,
    thumbnailUrl: `${BASE_VIDEOS}/images/${nome}.jpg`,
    duracaoSegundos,
  };
}

export const VIDEOS_EXEMPLO: readonly VideoExemplo[] = [
  video("ForBiggerBlazes", 15),
  video("ForBiggerEscapes", 15),
  video("ForBiggerFun", 60),
  video("ForBiggerJoyrides", 15),
  video("ForBiggerMeltdowns", 15),
];

// ---------------------------------------------------------------------------
// Arenas e quadras fictícias
// ---------------------------------------------------------------------------

interface QuadraSemente {
  nome: string;
  modalidade: Modalidade;
  status: StatusQuadra;
}

interface ArenaSemente {
  slug: string;
  nome: string;
  cidade: string;
  uf: string;
  bairro: string;
  corPrimaria: string;
  quadras: QuadraSemente[];
}

const q = (
  nome: string,
  modalidade: Modalidade,
  status: StatusQuadra = "online",
): QuadraSemente => ({
  nome,
  modalidade,
  status,
});

const ARENAS_SEMENTE: ArenaSemente[] = [
  {
    slug: "arena-bola-na-rede",
    nome: "Arena Bola na Rede",
    cidade: "São Paulo",
    uf: "SP",
    bairro: "Tatuapé",
    corPrimaria: "#a3e635",
    quadras: [
      q("Quadra 1", "society"),
      q("Quadra 2", "society"),
      q("Quadra 3", "society", "manutencao"),
    ],
  },
  {
    slug: "society-vila-nova",
    nome: "Society Vila Nova",
    cidade: "Campinas",
    uf: "SP",
    bairro: "Vila Nova",
    corPrimaria: "#38bdf8",
    quadras: [q("Campo A", "society"), q("Campo B", "society")],
  },
  {
    slug: "ginasio-futsal-central",
    nome: "Ginásio Futsal Central",
    cidade: "Curitiba",
    uf: "PR",
    bairro: "Centro",
    corPrimaria: "#fb923c",
    quadras: [q("Quadra Coberta", "futsal"), q("Quadra Externa", "futsal", "offline")],
  },
  {
    slug: "arena-gol-de-placa",
    nome: "Arena Gol de Placa",
    cidade: "Belo Horizonte",
    uf: "MG",
    bairro: "Pampulha",
    corPrimaria: "#f472b6",
    quadras: [
      q("Quadra 1", "society"),
      q("Quadra 2", "society"),
      q("Quadra 3", "society"),
      q("Quadra 4", "futsal"),
    ],
  },
  {
    slug: "estacao-futebol",
    nome: "Estação Futebol",
    cidade: "Rio de Janeiro",
    uf: "RJ",
    bairro: "Barra da Tijuca",
    corPrimaria: "#facc15",
    quadras: [q("Quadra Principal", "society"), q("Quadra Society 2", "society")],
  },
  {
    slug: "arena-litoral-society",
    nome: "Arena Litoral Society",
    cidade: "Florianópolis",
    uf: "SC",
    bairro: "Campeche",
    corPrimaria: "#2dd4bf",
    quadras: [q("Quadra 1", "society"), q("Quadra 2", "society"), q("Quadra 3", "society")],
  },
];

export const ARENAS: readonly Arena[] = ARENAS_SEMENTE.map((semente, i) => ({
  id: `a${i + 1}`,
  slug: semente.slug,
  nome: semente.nome,
  cidade: semente.cidade,
  uf: semente.uf,
  bairro: semente.bairro,
  logoUrl: null,
  corPrimaria: semente.corPrimaria,
  totalQuadras: semente.quadras.length,
}));

export const QUADRAS: readonly Quadra[] = ARENAS_SEMENTE.flatMap((semente, i) =>
  semente.quadras.map((quadra, j) => ({
    id: `a${i + 1}-q${j + 1}`,
    arenaId: `a${i + 1}`,
    nome: quadra.nome,
    modalidade: quadra.modalidade,
    status: quadra.status,
  })),
);

// ---------------------------------------------------------------------------
// Ids. Os ids carregam a hierarquia (quadra → partida → lance) para o mock
// conseguir reconstruir qualquer coisa a partir de uma URL compartilhada.
//   quadra:  a1-q2
//   partida: a1-q2-2026100520   (quadra + hora local no formato AAAAMMDDHH)
//   lance:   a1-q2-2026100520-l3  (gerado pela semente) ou -s1 (simulado ao vivo)
// ---------------------------------------------------------------------------

const dois = (n: number) => String(n).padStart(2, "0");

export function inicioDaHora(data: Date): Date {
  const copia = new Date(data);
  copia.setMinutes(0, 0, 0);
  return copia;
}

export function chaveDaHora(data: Date): string {
  return `${data.getFullYear()}${dois(data.getMonth() + 1)}${dois(data.getDate())}${dois(data.getHours())}`;
}

export function idPartida(quadraId: string, inicio: Date): string {
  return `${quadraId}-${chaveDaHora(inicio)}`;
}

export function decodificarIdPartida(id: string): { quadraId: string; inicio: Date } | null {
  const resultado = /^(.+)-(\d{4})(\d{2})(\d{2})(\d{2})$/.exec(id);
  if (!resultado) return null;
  const [, quadraId, ano, mes, dia, hora] = resultado;
  const inicio = new Date(Number(ano), Number(mes) - 1, Number(dia), Number(hora));
  if (Number.isNaN(inicio.getTime())) return null;
  return { quadraId, inicio };
}

export function decodificarIdLance(
  id: string,
): { partidaId: string; origem: "semente" | "simulado"; indice: number } | null {
  const resultado = /^(.+)-([ls])(\d+)$/.exec(id);
  if (!resultado) return null;
  const [, partidaId, tipo, indice] = resultado;
  return { partidaId, origem: tipo === "l" ? "semente" : "simulado", indice: Number(indice) };
}

// ---------------------------------------------------------------------------
// Partidas
// ---------------------------------------------------------------------------

/** Horários (hora cheia) em que costuma ter jogo. Fim de semana tem manhã e tarde. */
function horariosDeFuncionamento(dia: Date): number[] {
  const fimDeSemana = dia.getDay() === 0 || dia.getDay() === 6;
  return fimDeSemana ? [8, 9, 10, 11, 16, 17, 18, 19, 20, 21, 22] : [18, 19, 20, 21, 22];
}

export function montarPartida(quadra: Quadra, inicio: Date, agora: Date): Partida {
  const fim = new Date(inicio.getTime() + UMA_HORA);
  const aoVivo = agora.getTime() >= inicio.getTime() && agora.getTime() < fim.getTime();
  const partida: Partida = {
    id: idPartida(quadra.id, inicio),
    quadraId: quadra.id,
    inicio: inicio.toISOString(),
    fim: fim.toISOString(),
    status: aoVivo ? "ao_vivo" : "encerrada",
    totalLances: 0,
  };
  partida.totalLances = gerarLancesSemente(partida, quadra, agora).length;
  return partida;
}

/**
 * Partidas da quadra nos últimos `dias` dias, da mais recente para a mais antiga.
 * A hora atual sempre entra como partida ao vivo: é nela que o QR code cai.
 */
export function gerarPartidas(quadra: Quadra, agora: Date, dias = DIAS_HISTORICO): Partida[] {
  const horaAtual = inicioDaHora(agora);
  const partidas: Partida[] = [montarPartida(quadra, horaAtual, agora)];

  for (let d = 0; d <= dias; d++) {
    const dia = new Date(horaAtual);
    dia.setDate(dia.getDate() - d);
    dia.setHours(0, 0, 0, 0);

    for (const hora of horariosDeFuncionamento(dia)) {
      const inicio = new Date(dia);
      inicio.setHours(hora);
      if (inicio.getTime() >= horaAtual.getTime()) continue;
      partidas.push(montarPartida(quadra, inicio, agora));
    }
  }

  return partidas.sort((a, b) => b.inicio.localeCompare(a.inicio));
}

// ---------------------------------------------------------------------------
// Lances
// ---------------------------------------------------------------------------

function montarLance(
  id: string,
  partida: Partida,
  quadra: Quadra,
  momentoMs: number,
  video: VideoExemplo,
  agora: Date,
  processando = false,
): Lance {
  const expiraEm = momentoMs + DIAS_RETENCAO * UM_DIA;
  return {
    id,
    partidaId: partida.id,
    quadraId: quadra.id,
    arenaId: quadra.arenaId,
    momento: new Date(momentoMs).toISOString(),
    duracaoSegundos: video.duracaoSegundos,
    videoUrl: video.url,
    thumbnailUrl: video.thumbnailUrl,
    expiraEm: new Date(expiraEm).toISOString(),
    status: processando ? "processando" : expiraEm <= agora.getTime() ? "expirado" : "disponivel",
  };
}

/**
 * Lances "históricos" de uma partida, sempre os mesmos para o mesmo id.
 * Numa partida ao vivo só entram lances até o instante atual, e a terceira
 * quadra de cada arena começa vazia para a gente enxergar o estado "nenhum lance".
 */
export function gerarLancesSemente(partida: Partida, quadra: Quadra, agora: Date): Lance[] {
  const rng = criarAleatorio(hashTexto(partida.id));
  const inicio = Date.parse(partida.inicio);
  const fim = Date.parse(partida.fim);
  const aoVivo = partida.status === "ao_vivo";

  const limite = aoVivo ? Math.min(agora.getTime() - 30_000, fim) : fim - 30_000;
  if (limite - inicio < 2 * UM_MINUTO) return [];

  const quadraVazia = quadra.id.endsWith("-q3");
  const quantidade = aoVivo ? (quadraVazia ? 0 : rng.entre(2, 4)) : rng.entre(3, 10);

  const momentos = Array.from(
    { length: quantidade },
    () => inicio + UM_MINUTO + rng.proximo() * (limite - inicio - UM_MINUTO),
  ).sort((a, b) => a - b);

  return momentos.map((momento, i) =>
    montarLance(
      `${partida.id}-l${i + 1}`,
      partida,
      quadra,
      momento,
      rng.escolher(VIDEOS_EXEMPLO),
      agora,
    ),
  );
}

/** Primeiro lance simulado aparece 15 s depois de abrir a partida; depois, um a cada 30 s. */
const SIMULACAO_PRIMEIRO_EM = 15_000;
const SIMULACAO_INTERVALO = 30_000;
/** Tempo que um lance novo fica como "processando" antes de liberar o vídeo. */
const SIMULACAO_PROCESSANDO_POR = 8_000;

/**
 * Lances que "acontecem" enquanto a partida ao vivo está aberta. Não usa timer:
 * cada chamada calcula quantos já deveriam existir desde que a simulação começou,
 * o que deixa o comportamento previsível e fácil de testar.
 */
export function gerarLancesSimulados(
  partida: Partida,
  quadra: Quadra,
  inicioSimulacaoMs: number,
  agora: Date,
): Lance[] {
  const fim = Date.parse(partida.fim);
  const decorrido = agora.getTime() - inicioSimulacaoMs;
  if (decorrido < SIMULACAO_PRIMEIRO_EM) return [];

  const quantidade = 1 + Math.floor((decorrido - SIMULACAO_PRIMEIRO_EM) / SIMULACAO_INTERVALO);
  const lances: Lance[] = [];

  for (let i = 0; i < quantidade; i++) {
    const momento = inicioSimulacaoMs + SIMULACAO_PRIMEIRO_EM + i * SIMULACAO_INTERVALO;
    if (momento >= fim) break;
    const id = `${partida.id}-s${i + 1}`;
    const rng = criarAleatorio(hashTexto(id));
    const processando = agora.getTime() - momento < SIMULACAO_PROCESSANDO_POR;
    lances.push(
      montarLance(id, partida, quadra, momento, rng.escolher(VIDEOS_EXEMPLO), agora, processando),
    );
  }

  return lances;
}

export type Role = "player" | "admin";
export interface User {
  usuario_id: string;
  email: string;
  platform_admin?: boolean;
  empresa_ids?: string[];
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export interface Arena {
  empresa_id: string;
  nome: string;
}
export interface Court {
  quadra_id: string;
  nome: string;
  timezone: string;
  empresa_id?: string;
}
export interface Replay {
  evento_id: string;
  empresa_id: string;
  quadra_id: string;
  timestamp: string;
  expira_em: string;
  salvo: boolean;
}
export interface Availability {
  quadra_id: string;
  timezone: string;
  hoje: string;
  data: string;
  datas: string[];
  slots: { hora_inicio: number; hora_fim: number; total: number }[];
  page: number;
  size: number;
}
export interface MediaAccess {
  url: string;
  expira_em: string;
}
export interface ReplayFilters {
  data?: string;
  hora_inicio?: number;
  hora_fim?: number;
  page?: number;
  size?: number;
}
export interface RequestOptions {
  method?: string;
  body?: unknown;
  role?: Role;
}
export interface Violation {
  field: string;
  code: string;
}

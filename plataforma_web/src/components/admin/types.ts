export interface Company {
  empresa_id: string;
  nome: string;
  active: boolean;
  retencao_dias: number;
}
export interface AdminCourt {
  quadra_id: string;
  empresa_id: string;
  nome: string;
  active: boolean;
  timezone: string;
  perfil_captura: {
    REPLAY_BEFORE_SECONDS: number;
    REPLAY_AFTER_SECONDS: number;
    SEGMENT_DURATION_SECONDS: number;
    KEYFRAME_INTERVAL: number;
  };
}
export interface Device {
  dispositivo_id: string;
  quadra_id: string;
  empresa_id: string;
  active: boolean;
  bucket: string;
}
export interface IssuedDevice {
  dispositivo: Device;
  device_token: string;
}
export interface AuditEntry {
  audit_id: string;
  correlation_id: string;
  occurred_at: string;
  action: string;
  outcome: string;
  resource_type: string;
  resource_id: string | null;
  code: string;
  http_status: number | null;
}
export interface Health {
  dispositivo_id: string;
  contact_state: "UNKNOWN" | "CURRENT" | "STALE";
  health?: {
    received_at: string;
    observed_at: string;
    gateway_version: string;
    capture_state: string;
    pending_events: number;
    oldest_pending_age_seconds: number;
    capture_progress_age_seconds: number | null;
  };
}

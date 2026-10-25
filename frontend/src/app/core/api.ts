import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';

export type Operation = 'START' | 'STOP' | 'RESTART';
export interface ServerProfile {
  id: string; name: string; reportedMinecraftVersion: string | null; reportedLoader: string | null;
  verifiedAt: string | null; role: 'VIEWER' | 'OPERATOR' | 'ADMIN'; operationsAvailable: boolean;
  statusSource: 'DISCONNECTED' | 'HOST_AGENT_READ_ONLY' | 'HOST_AGENT_TEST_CONTROL';
  environmentLabel: string;
}
export interface Status {
  serverId: string; lifecycle: 'UNKNOWN' | 'OFFLINE' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'ERROR';
  health: 'OK' | 'DEGRADED' | 'FAILED' | 'UNKNOWN'; activity: 'NONE' | 'BACKUP' | 'RESTORE' | 'MOD_CHANGE';
  minecraftVersion: string | null; loader: string | null; loaderVersion: string | null;
  observedAt: string | null; reasonCode: string | null; playersOnline: number | null;
}
export interface Job {
  jobId: string; serverId: string; type: Operation;
  actor: string; reason: string;
  state: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN';
  createdAt: string; updatedAt: string; errorCode: string | null;
}
export interface JobPage { items: Job[]; nextCursor: string | null; blockingJob: Job | null; }
export interface AuditEvent {
  id: string; actor: string; serverId: string; jobId: string | null;
  action: Operation | 'INVENTORY_UPDATED'; outcome: Job['state'] | 'RECORDED'; occurredAt: string;
  subjectKey: string | null; revision: number | null;
}
export interface AuditPage { items: AuditEvent[]; nextCursor: string | null; }
export interface InventoryEvidence {
  key: string; label: string; value: string | null; source: string | null; observedAt: string | null;
  recordedAt: string | null; recordedBy: string | null; revision: number | null;
}
export interface InventorySnapshot { revision: number; documentedCount: number; requiredCount: number; fields: InventoryEvidence[]; }
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  me() { return this.http.get<{ name: string }>('/api/v1/me'); }
  auth() { return this.http.get<{ loginUrl: string }>('/api/v1/auth'); }
  servers() { return this.http.get<ServerProfile[]>('/api/v1/servers'); }
  status(id: string) { return this.http.get<Status>(`/api/v1/servers/${encodeURIComponent(id)}/status`); }
  job(server: string, id: string) {
    return this.http.get<Job>(`/api/v1/servers/${encodeURIComponent(server)}/jobs/${encodeURIComponent(id)}`);
  }
  jobs(server: string, before: string | null = null, limit = 20) {
    const params: Record<string, string> = { limit: String(limit) };
    if (before) params['before'] = before;
    return this.http.get<JobPage>(`/api/v1/servers/${encodeURIComponent(server)}/jobs`, { params });
  }
  audit(server: string, jobId: string | null = null, before: string | null = null) {
    const params: Record<string, string> = { limit: '20' };
    if (jobId) params['jobId'] = jobId;
    if (before) params['before'] = before;
    return this.http.get<AuditPage>(`/api/v1/servers/${encodeURIComponent(server)}/audit`, { params });
  }
  inventory(server: string) {
    return this.http.get<InventorySnapshot>(`/api/v1/servers/${encodeURIComponent(server)}/inventory`);
  }
  recordInventory(server: string, field: string, revision: number, value: string, source: string, observedAt: string) {
    return this.csrf().pipe(switchMap(csrf => this.http.put<InventorySnapshot>(
      `/api/v1/servers/${encodeURIComponent(server)}/inventory/${encodeURIComponent(field)}`, { value, source, observedAt },
      { headers: { [csrf.headerName]: csrf.token, 'If-Match': '"' + revision + '"' } })));
  }
  operation(server: string, type: Operation, reason: string, key: string) {
    return this.csrf().pipe(switchMap(csrf => this.http.post<Job>(
      `/api/v1/servers/${encodeURIComponent(server)}/operations`, { type, reason },
      { headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': key } })));
  }
  logout() {
    return this.csrf().pipe(switchMap(csrf => this.http.post('/api/v1/logout', {},
      { headers: { [csrf.headerName]: csrf.token } })));
  }
  private csrf() { return this.http.get<{ headerName: string; token: string }>('/api/v1/csrf'); }
}
export function errorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Unerwarteter Fehler.';
  if (error.status === 401) return 'Bitte anmelden. Die Sitzung fehlt oder ist abgelaufen.';
  if (error.status === 403) return 'Keine Berechtigung oder Sitzung abgelaufen. Bitte neu anmelden.';
  if (error.status === 0) return 'Backend nicht erreichbar. Angezeigte Daten können veraltet sein.';
  return error.error?.message ?? 'Anfrage fehlgeschlagen. Bitte erneut versuchen.';
}

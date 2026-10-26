import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';

export type Operation = 'START' | 'STOP' | 'RESTART' | 'WHITELIST_ADD' | 'WHITELIST_REMOVE' | 'OP_GRANT' | 'OP_REVOKE' | 'CONSOLE' | 'BACKUP' | 'RESTORE_TEST' | 'CONFIG_APPLY' | 'RESTORE_APPLY' | 'MOD_APPLY';
export interface ServerProfile {
  id: string; name: string; reportedMinecraftVersion: string | null; reportedLoader: string | null;
  verifiedAt: string | null; role: 'VIEWER' | 'OPERATOR' | 'ADMIN'; operationsAvailable: boolean;
  statusSource: 'DISCONNECTED' | 'HOST_AGENT_READ_ONLY' | 'HOST_AGENT_TEST_CONTROL';
  environmentLabel: string;
  playerOperationsAvailable: boolean;
  operatorOperationsAvailable: boolean;
  consoleAvailable: boolean;
  backupsAvailable: boolean;
  restoreAvailable:boolean;
  modsAvailable:boolean;
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
  targetName: string | null;
  commandText: string | null; resultText: string | null;
  snapshotId: string | null;
  configurationJson:string|null;configurationRevision:string|null;
  restorePlanId:string|null;
  modPlanId:string|null;
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
export interface OnlinePlayers { serverId: string; names: string[] | null; observedAt: string | null; reasonCode: string | null; }
export interface PlayerEntry { name: string; uuid: string; }
export interface ContainerMetrics { CPUPerc: string; MemUsage: string; MemPerc: string; NetIO: string; BlockIO: string; PIDs: string; }
export interface Diagnostics<T> { serverId: string; observedAt: string | null; data: T | null; reasonCode: string | null; }
export interface BackupSnapshot { id:string; createdAt:string; integrity:string; restoreVerifiedAt:string|null; scope:string; bootVerifiedAt:string|null; }
export interface BackupPolicy {enabled:boolean;dailyTime:string;zone:string;nextRunAt:string|null;state:string;}
export interface BackupRetentionPolicy {enabled:boolean;days:number;lastAppliedAt:string|null;lastForgottenCount:number|null;lastState:string;}
export interface BackupRetentionPreview {observedAt:string;cutoff:string;revision:string;latestSuccessfulId:string|null;stale:boolean;candidates:{id:string;createdAt:string;verified:boolean;referenced:boolean}[];retained:number;unmanaged:number;}
export interface RestorePlan {planId:string;snapshotId:string;createdAt:string;expiresAt:string;minecraftVersion:string;loaderVersion:string;components:string[];startAfterRestore:boolean;state:string;}
export interface ConfigurationField {key:string;label:string;type:'enum'|'integer'|'text';min:number|null;max:number|null;options:string[];value:string|null;managedByEnvironment:boolean;}
export interface ConfigurationSnapshot {revision:string;fields:ConfigurationField[];writable:boolean;restartRequired:boolean;source:string;}
export interface ModFile {name:string;bytes:number;provenance:string;}
export interface CatalogMod {id:number;name:string;summary:string;file:{id:number;name:string;bytes:number;requiredDependencies:number[]};serverSuitability:string;}
export interface CatalogSearch {version:string|null;loader:string|null;configured:boolean;items:CatalogMod[];reasonCode:string|null;}
export interface DependencyPlan {version:string;loader:string;files:{projectId:number;fileId:number;name:string;bytes:number;sha1:string;required:number[];optional:number[]}[];totalBytes:number;state:string;remainingChecks:string[];}
export interface ModInspection {version:string;loader:string;loaderVersion:string;javaVersion:string;files:{projectId:number;fileId:number;name:string;bytes:number;sha256:string;fabricMods:string[]}[];state:string;warnings:string[];remainingChecks:string[];}
export interface MetricSample {observedAt:string;cpuPercent:number;memoryUsed:number;memoryLimit:number;networkReceived:number;networkSent:number;diskRead:number;diskWritten:number;pids:number;}
export interface MetricHistory {configured:boolean;intervalSeconds:number;retentionDays:number;samples:MetricSample[];collectionStatus?:{checkedAt:string|null;code:string};}
export interface ModPin {projectId:number;fileId:number;bytes:number;sha256:string;sha1:string;required:number[];provenance:'CURSEFORGE'|'LOCAL_ACCEPTANCE_FIXTURE';}
export interface ManagedModState {managed:boolean;revision:string;manifest:{schema:number;roots:{projectId:number;fileId:number}[];files:ModPin[]};actualFiles:Record<string,string>;reasonCode:string|null;}
export interface ModChangePlan {planId:string;createdAt:string;expiresAt:string;minecraftVersion:string;loaderVersion:string;javaVersion:string;files:ModPin[];changes:string[];state:string;}
export interface ModImportAssignment {name:string;projectId:number;fileId:number;}
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  me() { return this.http.get<{ name: string }>('/api/v1/me'); }
  auth() { return this.http.get<{ loginUrl: string; environmentLabel:string }>('/api/v1/auth'); }
  loginToken() { return this.csrf(); }
  servers() { return this.http.get<ServerProfile[]>('/api/v1/servers'); }
  status(id: string) { return this.http.get<Status>(`/api/v1/servers/${encodeURIComponent(id)}/status`); }
  onlinePlayers(id: string) { return this.http.get<OnlinePlayers>(`/api/v1/servers/${encodeURIComponent(id)}/players/online`); }
  metricHistory(id:string,hours=24){return this.http.get<MetricHistory>(`/api/v1/servers/${encodeURIComponent(id)}/metrics/history`,{params:{hours}});}
  membership(id: string) { return this.http.get<Diagnostics<{ whitelist: PlayerEntry[]; operators: PlayerEntry[] }>>(`/api/v1/servers/${encodeURIComponent(id)}/players/membership`); }
  metrics(id: string) { return this.http.get<Diagnostics<ContainerMetrics>>(`/api/v1/servers/${encodeURIComponent(id)}/metrics`); }
  logs(id: string) { return this.http.get<Diagnostics<{ lines: string[] }>>(`/api/v1/servers/${encodeURIComponent(id)}/logs`); }
  backups(id:string){return this.http.get<Diagnostics<{available:boolean;snapshots:BackupSnapshot[];scope:string;bootTestsAvailable:boolean;restoreAvailable:boolean;retention?:BackupRetentionPolicy}>>(`/api/v1/servers/${encodeURIComponent(id)}/backups`);}
  backupRetention(id:string){return this.http.get<Diagnostics<BackupRetentionPreview>>(`/api/v1/servers/${encodeURIComponent(id)}/backups/retention`);}
  backupPolicy(id:string){return this.http.get<BackupPolicy>(`/api/v1/servers/${encodeURIComponent(id)}/backup-policy`);}
  prepareRestore(id:string,snapshotId:string,startAfterRestore:boolean){return this.csrf().pipe(switchMap(csrf=>this.http.post<RestorePlan>(`/api/v1/servers/${encodeURIComponent(id)}/restore-plans`,{snapshotId,startAfterRestore},{headers:{[csrf.headerName]:csrf.token}})));}
  cancelRestore(id:string,plan:string){return this.csrf().pipe(switchMap(csrf=>this.http.delete(`/api/v1/servers/${encodeURIComponent(id)}/restore-plans/${encodeURIComponent(plan)}`,{headers:{[csrf.headerName]:csrf.token}})));}
  executeRestore(id:string,plan:string,reason:string,key:string){return this.csrf().pipe(switchMap(csrf=>this.http.post<Job>(`/api/v1/servers/${encodeURIComponent(id)}/restore-plans/${encodeURIComponent(plan)}/execute`,{reason},{headers:{[csrf.headerName]:csrf.token,'Idempotency-Key':key}})));}
  configuration(id:string){return this.http.get<Diagnostics<ConfigurationSnapshot>>(`/api/v1/servers/${encodeURIComponent(id)}/configuration`);}
  saveConfiguration(id:string,revision:string,values:Record<string,string>,reason:string,key:string){return this.csrf().pipe(switchMap(csrf=>this.http.post<Job>(`/api/v1/servers/${encodeURIComponent(id)}/configuration`,{revision,values,reason},{headers:{[csrf.headerName]:csrf.token,'Idempotency-Key':key}})));}
  mods(id:string){return this.http.get<Diagnostics<{files:ModFile[]}>>(`/api/v1/servers/${encodeURIComponent(id)}/mods`);}
  searchMods(id:string,q:string){return this.http.get<CatalogSearch>(`/api/v1/servers/${encodeURIComponent(id)}/mods/search`,{params:{q}});}
  previewMods(id:string,projectId:number,fileId:number){return this.csrf().pipe(switchMap(csrf=>this.http.post<DependencyPlan>(`/api/v1/servers/${encodeURIComponent(id)}/mods/preview`,{projectId,fileId},{headers:{[csrf.headerName]:csrf.token}})));}
  inspectMods(id:string,projectId:number,fileId:number){return this.csrf().pipe(switchMap(csrf=>this.http.post<ModInspection>(`/api/v1/servers/${encodeURIComponent(id)}/mods/inspect`,{projectId,fileId},{headers:{[csrf.headerName]:csrf.token}})));}
  managedMods(id:string){return this.http.get<ManagedModState>(`/api/v1/servers/${encodeURIComponent(id)}/mods/state`);}
  getModPlan(id:string,plan:string){return this.http.get<ModChangePlan>(`/api/v1/servers/${encodeURIComponent(id)}/mods/plans/${encodeURIComponent(plan)}`);}
  planMods(id:string,action:'INSTALL'|'UPDATE'|'REMOVE',projectId:number,fileId:number|null){return this.csrf().pipe(switchMap(csrf=>this.http.post<ModChangePlan>(`/api/v1/servers/${encodeURIComponent(id)}/mods/plans`,{action,projectId,fileId},{headers:{[csrf.headerName]:csrf.token}})));}
  planModImport(id:string,assignments:ModImportAssignment[]){return this.csrf().pipe(switchMap(csrf=>this.http.post<ModChangePlan>(`/api/v1/servers/${encodeURIComponent(id)}/mods/import-plans`,{assignments},{headers:{[csrf.headerName]:csrf.token}})));}
  cancelModPlan(id:string,plan:string){return this.csrf().pipe(switchMap(csrf=>this.http.delete(`/api/v1/servers/${encodeURIComponent(id)}/mods/plans/${encodeURIComponent(plan)}`,{headers:{[csrf.headerName]:csrf.token}})));}
  executeModPlan(id:string,plan:string,reason:string,key:string){return this.csrf().pipe(switchMap(csrf=>this.http.post<Job>(`/api/v1/servers/${encodeURIComponent(id)}/mods/plans/${encodeURIComponent(plan)}/execute`,{reason},{headers:{[csrf.headerName]:csrf.token,'Idempotency-Key':key}})));}
  settings(id: string) { return this.http.get<{grants:{subject:string;role:string}[];hostSource:string;lifecycleEnabled:boolean;whitelistEnabled:boolean;operatorEnabled:boolean;consoleEnabled:boolean;backupRetentionDays:number}>(`/api/v1/servers/${encodeURIComponent(id)}/settings`); }
  grant(id: string, subject: string, role: string) { return this.csrf().pipe(switchMap(csrf=>this.http.put(`/api/v1/servers/${encodeURIComponent(id)}/settings/grants`,{subject,role},{headers:{[csrf.headerName]:csrf.token}}))); }
  revokeGrant(id: string, subject: string) { return this.csrf().pipe(switchMap(csrf=>this.http.delete(`/api/v1/servers/${encodeURIComponent(id)}/settings/grants`,{params:{subject},headers:{[csrf.headerName]:csrf.token}}))); }
  job(server: string, id: string) {
    return this.http.get<Job>(`/api/v1/servers/${encodeURIComponent(server)}/jobs/${encodeURIComponent(id)}`);
  }
  reconcileJob(server:string,id:string){return this.csrf().pipe(switchMap(csrf=>this.http.post<Job>(`/api/v1/servers/${encodeURIComponent(server)}/jobs/${encodeURIComponent(id)}/reconcile`,{},{headers:{[csrf.headerName]:csrf.token}})));}
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
  operation(server: string, type: Operation, reason: string, key: string, targetName: string | null = null, commandText: string | null = null, snapshotId: string | null = null) {
    return this.csrf().pipe(switchMap(csrf => this.http.post<Job>(
      `/api/v1/servers/${encodeURIComponent(server)}/operations`, { type, reason, ...(targetName ? { targetName } : {}), ...(commandText ? { commandText } : {}), ...(snapshotId ? { snapshotId } : {}) },
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

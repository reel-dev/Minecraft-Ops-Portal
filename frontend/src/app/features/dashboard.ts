import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, switchMap, timer } from 'rxjs';
import { Api, errorMessage, Job, JobPage, Operation, ServerProfile, Status, ContainerMetrics, BackupSnapshot, ModFile, ConfigurationSnapshot } from '../core/api';
import { Icon } from '../core/icon';
import { jobStateLabel, operationLabel } from '../core/job-presentation';

@Component({
  imports: [RouterLink, Icon],
  templateUrl: './dashboard.html'
})
export class Dashboard {
  private readonly api = inject(Api);
  private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null);
  readonly status = signal<Status | null>(null);
  readonly metrics = signal<ContainerMetrics | null>(null);
  readonly onlineNames = signal<string[] | null>(null);
  readonly logs = signal<string[] | null>(null);
  readonly backups = signal<BackupSnapshot[]>([]);
  readonly mods = signal<ModFile[] | null>(null);
  readonly configuration = signal<ConfigurationSnapshot | null>(null);
  readonly recentJobs = signal<Job[]>([]);
  readonly blockingJob = signal<Job | null>(null);
  readonly jobsLoading = signal(true);
  readonly jobError = signal('');
  readonly stateLabel = jobStateLabel;
  readonly operation = operationLabel;
  readonly error = signal('');
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly selected = signal<Operation | null>(null);
  private pendingRequest: { type: Operation; reason: string; key: string } | null = null;
  readonly actions: { type: Operation; label: string }[] = [
    { type: 'START', label: 'Starten' }, { type: 'RESTART', label: 'Neustarten' }, { type: 'STOP', label: 'Stoppen' }
  ];
  constructor() {
    this.refresh();
    timer(0, 10000).pipe(takeUntilDestroyed(this.destroy)).subscribe(() => this.loadOverview());
    timer(5000, 5000).pipe(takeUntilDestroyed(this.destroy), switchMap(() => {
      const server = this.server();
      return server ? this.api.status(server.id).pipe(catchError(err => { this.error.set(errorMessage(err)); if([401,403].includes(err.status))this.clearSessionData(); return EMPTY; })) : EMPTY;
    })).subscribe(value => { this.status.set(value); this.error.set(''); });
    timer(2000, 3000).pipe(takeUntilDestroyed(this.destroy), switchMap(() => {
      const server = this.server();
      return server && server.role !== 'VIEWER'
        ? this.api.jobs(server.id, null, 5).pipe(catchError(err => { this.jobFailure(err); return EMPTY; })) : EMPTY;
    })).subscribe(value => this.applyJobs(value));
  }
  private loadOverview() {
    const server = this.server(); if (!server) return;
    this.api.onlinePlayers(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => this.onlineNames.set(value.names), error: () => this.onlineNames.set(null)});
    this.api.metrics(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => this.metrics.set(value.data), error: () => this.metrics.set(null)});
    if (server.role !== 'VIEWER') this.api.logs(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => this.logs.set(value.data?.lines ?? null), error: () => this.logs.set(null)});
    if (server.role !== 'VIEWER') this.api.backups(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>this.backups.set(value.data?.snapshots??[]),error:()=>this.backups.set([])});
    this.api.mods(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>this.mods.set(value.data?.files??null),error:()=>this.mods.set(null)});
    if (server.role === 'ADMIN') this.api.configuration(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:value=>this.configuration.set(value.data),error:()=>this.configuration.set(null)});
  }
  lifecycle() {
    const labels = { UNKNOWN: 'Status unbekannt', OFFLINE: 'Offline', STARTING: 'Startet', RUNNING: 'Online', STOPPING: 'Wird beendet', ERROR: 'Serverfehler' };
    return labels[this.status()?.lifecycle ?? 'UNKNOWN'];
  }
  health() {
    return { OK: 'In Ordnung', DEGRADED: 'Eingeschränkt', FAILED: 'Fehler', UNKNOWN: 'Unbekannt' }[this.status()?.health ?? 'UNKNOWN'];
  }
  activity() {
    return { NONE: 'Keine', BACKUP: 'Backup läuft', RESTORE: 'Wiederherstellung läuft', MOD_CHANGE: 'Modänderung läuft' }[this.status()?.activity ?? 'NONE'];
  }
  formatTime(value: string | null | undefined) {
    return value ? new Intl.DateTimeFormat('de-DE', { dateStyle: 'short', timeStyle: 'medium', timeZone: 'Europe/Berlin' }).format(new Date(value)) : 'noch keine';
  }
  statusReason(code: string) {
    const messages: Record<string, string> = {
      HOST_AGENT_NOT_CONFIGURED: 'Es ist noch keine Hostverbindung konfiguriert.',
      AGENT_SERVER_NOT_CONFIGURED: 'Für diesen Server ist keine Statusquelle registriert.',
      AGENT_AUTH_FAILED: 'Der Host-Agent hat die Anmeldung abgewiesen.',
      AGENT_HTTP_ERROR: 'Der Host-Agent meldet einen Verbindungsfehler.',
      AGENT_INVALID_RESPONSE: 'Die Statusantwort ist unvollständig oder widersprüchlich.',
      AGENT_STALE_STATUS: 'Die Messung ist veraltet oder ihre Zeitangabe liegt in der Zukunft. Der aktuelle Serverstatus ist unbekannt.',
      AGENT_TIMEOUT: 'Die Statusabfrage hat zu lange gedauert.',
      AGENT_UNAVAILABLE: 'Der Host-Agent ist nicht erreichbar oder seine Antwort konnte nicht verarbeitet werden.',
      AGENT_INTERRUPTED: 'Die Statusabfrage wurde unterbrochen.'
    };
    return messages[code] ?? 'Die Statusquelle meldet einen Fehler.';
  }
  refresh() {
    this.loading.set(true); this.error.set('');
    this.api.servers().pipe(takeUntilDestroyed(this.destroy), switchMap(servers => {
      this.server.set(servers[0] ?? null);
      this.recentJobs.set([]); this.blockingJob.set(null); this.jobError.set('');
      const server = servers[0];
      this.jobsLoading.set(!!server && server.role !== 'VIEWER');
      if (server && server.role !== 'VIEWER') this.api.jobs(server.id, null, 5).pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: value => this.applyJobs(value),
        error: err => this.jobFailure(err)
      });
      return servers.length ? this.api.status(servers[0].id) : EMPTY;
    }), finalize(() => this.loading.set(false))).subscribe({ next: value => { this.status.set(value); this.loadOverview(); }, error: err => this.error.set(errorMessage(err)) });
  }
  canRequest(type:Operation){
    const server=this.server(),status=this.status();if(!server?.operationsAvailable||server.role==='VIEWER'||this.loading()||this.busy()||this.jobsLoading()||this.jobError()||this.blockingJob()||!status?.observedAt||status.reasonCode||Date.now()-Date.parse(status.observedAt)>30000)return false;
    return type==='START'?status.lifecycle==='OFFLINE':type==='RESTART'?status.lifecycle==='RUNNING':type==='STOP'&&(status.lifecycle==='RUNNING'||status.lifecycle==='STARTING');
  }
  execute(type: Operation, reason: string) {
    const server = this.server();
    if (!server || !this.canRequest(type) || !reason.trim()) return;
    this.busy.set(true); this.error.set('');
    const normalized = reason.trim();
    if (!this.pendingRequest || this.pendingRequest.type !== type || this.pendingRequest.reason !== normalized) {
      this.pendingRequest = { type, reason: normalized, key: crypto.randomUUID() };
    }
    this.api.operation(server.id, type, normalized, this.pendingRequest.key).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: value => {
        this.pendingRequest = null; this.selected.set(null); this.busy.set(false);
        this.recentJobs.update(items => [value, ...items.filter(item => item.jobId !== value.jobId)].slice(0, 5));
        if (['QUEUED', 'RUNNING', 'UNKNOWN'].includes(value.state)) this.blockingJob.set(value);
      },
      error: err => { this.error.set(errorMessage(err)); this.busy.set(false); }
    });
  }
  private applyJobs(page: JobPage) {
    this.recentJobs.set(page.items); this.blockingJob.set(page.blockingJob); this.jobsLoading.set(false); this.jobError.set('');
  }
  private jobFailure(error: HttpErrorResponse) {
    this.jobError.set(errorMessage(error)); this.jobsLoading.set(false);
    if (error.status === 401 || error.status === 403) { this.error.set(errorMessage(error));this.clearSessionData(); }
  }
  private clearSessionData(){this.server.set(null);this.status.set(null);this.metrics.set(null);this.onlineNames.set(null);this.logs.set(null);this.backups.set([]);this.mods.set(null);this.configuration.set(null);this.recentJobs.set([]);this.blockingJob.set(null);}
}

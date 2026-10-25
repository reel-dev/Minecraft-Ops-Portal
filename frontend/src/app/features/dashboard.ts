import { Component, DestroyRef, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, finalize, switchMap, timer } from 'rxjs';
import { Api, errorMessage, Job, JobPage, Operation, ServerProfile, Status } from '../core/api';
import { jobStateLabel, operationLabel } from '../core/job-presentation';

@Component({
  imports: [RouterLink],
  template: `
    <header><p class="eyebrow">SERVERÜBERSICHT</p><h1>Dein SMP. Ein Überblick.</h1><p class="muted">Betrieb, Spieler und Datensicherheit an einem Ort.</p></header>
    @if (error()) { <div class="error" role="alert">{{ error() }} <button (click)="refresh()">Erneut laden</button></div> }
    @if (loading()) { <p role="status">Serverprofil wird geladen …</p> }
    @if (server(); as s) {
      @if (s.environmentLabel) { <p class="notice" role="status">{{ s.environmentLabel }}</p> }
      <section class="panel statusbar">
        <div><p class="eyebrow">{{ s.name }}</p><h2>{{ lifecycle() }}</h2><p class="muted">Gesundheit: {{ health() }} · Aktivität: {{ activity() }}</p></div>
        <div><strong>Bestandsprüfung {{ s.verifiedAt ? 'dokumentiert' : 'ausstehend' }}</strong>
          <p class="muted">Nutzerangabe: Minecraft {{ s.reportedMinecraftVersion ?? 'unbekannt' }} · {{ s.reportedLoader ?? 'unbekannt' }}</p>
          <small>Gemessene Version: {{ status()?.minecraftVersion ?? 'nicht verfügbar' }} · Loader: {{ status()?.loaderVersion ?? 'nicht verfügbar' }}</small>
          @if (s.role === 'ADMIN') { <a routerLink="/inventory">Bestandsaufnahme dokumentieren →</a> }
        </div>
        <div class="actions">
          @for (action of actions; track action.type) {
            <button [class.primary]="action.type === 'RESTART'" [class.danger]="action.type === 'STOP'"
              [disabled]="!s.operationsAvailable || s.role === 'VIEWER' || busy() || jobsLoading() || !!jobError() || !!blockingJob()" (click)="selected.set(action.type)">{{ action.label }}</button>
          }
        </div>
      </section>
      <p class="muted source">Messung: {{ formatTime(status()?.observedAt) }} · Quelle: {{ s.statusSource === 'HOST_AGENT_TEST_CONTROL' ? 'Lokaler Test-Host-Agent' : s.statusSource === 'HOST_AGENT_READ_ONLY' ? 'Lesender Host-Agent' : 'nicht angebunden' }} · Portalrolle: {{ s.role }}</p>
      @if (status()?.reasonCode; as code) { <p class="muted" role="status">{{ statusReason(code) }}</p> }
      @if (selected(); as type) {
        <section class="panel" aria-label="Serveraktion bestätigen"><h2>{{ type }} für {{ s.name }}</h2>
          <p>Die Aktion verändert den Serverbetrieb. Eine Begründung ist erforderlich; keine Zugangsdaten eintragen.</p>
          <label>Begründung <input #reason maxlength="240" placeholder="Geplante Wartung"></label>
          <div class="actions"><button (click)="selected.set(null)">Abbrechen</button><button class="primary" [disabled]="busy()" (click)="execute(type, reason.value)">Aktion bestätigen</button></div>
        </section>
      }
      <div class="metrics">
        <section class="panel"><p class="muted">Spieler online</p><strong class="metric">{{ status()?.playersOnline ?? '—' }}</strong><a routerLink="/players">Spielerverwaltung →</a></section>
        @for (label of ['TPS / MSPT', 'CPU · Server', 'RAM · JVM / Container']; track label) {
          <section class="panel"><p class="muted">{{ label }}</p><strong class="metric">—</strong><small>Keine Messquelle angebunden</small></section>
        }
      </div>
      <div class="columns">
        <section class="panel"><div class="panel-heading"><h2>Spieler</h2><a routerLink="/players">Alle anzeigen →</a></div><p class="empty">Die Online-Liste wird mit M06 angebunden.</p></section>
        <section class="panel"><div class="panel-heading"><h2>Datensicherheit</h2><a routerLink="/backups">Backups →</a></div><p class="empty">Keine Sicherungsdaten angebunden.<br>Backup und Proberestore stehen vor der ersten Modinstallation.</p></section>
      </div>
      <div class="columns">
        <section class="panel"><div class="panel-heading"><h2>Serverjobs</h2><a routerLink="/jobs">Historie ansehen →</a></div>
          @if (s.role === 'VIEWER') { <p class="empty">Jobdetails erfordern Operator- oder Adminrechte.</p> }
          @else {
            @if (jobsLoading()) { <p role="status">Jobstatus wird geladen …</p> }
            @if (jobError()) { <p role="alert">{{ jobError() }} Serveraktionen bleiben bis zur nächsten erfolgreichen Abfrage gesperrt.</p> }
            @if (blockingJob(); as blocking) { <p class="job-warning" role="status">Server gesperrt: {{ stateLabel(blocking.state) }}. @if (blocking.state === 'UNKNOWN') { Der tatsächliche Ausgang muss am Host geprüft werden. Keine automatische Wiederholung. }</p> }
            @for (item of recentJobs(); track item.jobId) {
              <p><a [routerLink]="['/jobs', item.jobId]">{{ operation(item.type) }} · {{ stateLabel(item.state) }} →</a><small>{{ formatTime(item.updatedAt) }}</small></p>
            } @empty { @if (!jobsLoading() && !jobError()) { <p class="empty">Noch keine Jobs gespeichert.</p> } }
          }
        </section>
        <section class="panel"><h2>Weltkarte</h2><p class="empty">Keine Browserkartenquelle bestätigt.<br>Xaero allein belegt keine verfügbare Webkarte.</p></section>
      </div>
    } @else if (!loading() && !error()) { <section class="panel empty">Kein freigegebener Server vorhanden.</section> }
  `
})
export class Dashboard {
  private readonly api = inject(Api);
  private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null);
  readonly status = signal<Status | null>(null);
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
    timer(5000, 5000).pipe(takeUntilDestroyed(this.destroy), switchMap(() => {
      const server = this.server();
      return server ? this.api.status(server.id).pipe(catchError(err => { this.error.set(errorMessage(err)); return EMPTY; })) : EMPTY;
    })).subscribe(value => { this.status.set(value); this.error.set(''); });
    timer(2000, 3000).pipe(takeUntilDestroyed(this.destroy), switchMap(() => {
      const server = this.server();
      return server && server.role !== 'VIEWER'
        ? this.api.jobs(server.id, null, 5).pipe(catchError(err => { this.jobFailure(err); return EMPTY; })) : EMPTY;
    })).subscribe(value => this.applyJobs(value));
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
    }), finalize(() => this.loading.set(false))).subscribe({ next: value => this.status.set(value), error: err => this.error.set(errorMessage(err)) });
  }
  execute(type: Operation, reason: string) {
    const server = this.server();
    if (!server || !server.operationsAvailable || server.role === 'VIEWER' || !reason.trim() || this.busy() || this.jobsLoading() || this.jobError() || this.blockingJob()) return;
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
    if (error.status === 401 || error.status === 403) { this.recentJobs.set([]); this.blockingJob.set(null); }
  }
}

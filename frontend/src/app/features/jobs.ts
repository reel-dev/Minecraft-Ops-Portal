import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, combineLatest, exhaustMap, catchError, forkJoin, map, merge, of, switchMap, timer } from 'rxjs';
import { Api, errorMessage, Job, JobPage, ServerProfile } from '../core/api';
import { berlinTime, jobExplanation, jobStateLabel, operationLabel } from '../core/job-presentation';

@Component({
  imports: [RouterLink],
  template: `
    <header><p class="eyebrow">BETRIEB · JOBHISTORIE</p><h1>Serverjobs</h1><p class="muted">Gespeicherte Aktionen und ihr bestätigter oder ungeklärter Ausgang.</p></header>
    @if (loading()) { <p role="status">Jobdaten werden geladen …</p> }
    @if (error()) { <div class="error" role="alert">{{ error() }} <button (click)="reload.next()">Erneut laden</button></div> }
    @if (server(); as s) {
      @if (s.environmentLabel) { <p class="notice">{{ s.environmentLabel }}</p> }
      @if (s.role === 'VIEWER') { <section class="panel empty">Jobdetails erfordern die Portalrolle Operator oder Admin.</section> }
      @else {
        @if (page()?.blockingJob; as blocker) {
          <section class="panel job-warning" aria-label="Server gesperrt"><h2>Serveraktionen gesperrt · {{ stateLabel(blocker.state) }}</h2>
            <p>{{ explanation(blocker) }}</p><a [routerLink]="['/jobs', blocker.jobId]">Sperrenden Job ansehen →</a></section>
        }
        @if (page(); as p) {
          <section class="panel"><div class="panel-heading"><h2>{{ s.name }} · {{ before() ? 'Ältere Jobs' : 'Neueste Jobs' }}</h2><span class="muted">{{ stale() ? 'Letzte Daten · Aktualisierung fehlgeschlagen' : 'Aktualisierung alle 5 Sekunden' }}</span></div>
            @if (p.items.length) {
              <div class="table-scroll"><table><thead><tr><th>Aktion</th><th>Ergebnis</th><th>Erstellt (Berlin)</th><th>Auftraggeber</th><th>Details</th></tr></thead>
                <tbody>@for (item of p.items; track item.jobId) { <tr>
                  <td>{{ operation(item.type) }}</td><td>{{ stateLabel(item.state) }}</td><td>{{ time(item.createdAt) }}</td><td>{{ item.actor }}</td>
                  <td><a [routerLink]="['/jobs', item.jobId]" [queryParams]="before() ? {before: before()} : {}" [attr.aria-label]="'Job ' + item.jobId + ' ansehen'">Ansehen →</a></td>
                </tr> }</tbody></table></div>
            } @else { <p class="empty">Für diese Seite sind keine Jobs gespeichert.</p> }
            <div class="actions">
              @if (before()) { <a class="button" routerLink="/jobs">Neueste Jobs</a> }
              @if (p.nextCursor) { <a class="button" routerLink="/jobs" [queryParams]="{before: p.nextCursor}">Ältere Jobs →</a> }
            </div>
          </section>
        }
        @if (detail(); as job) {
          <section class="panel" aria-label="Jobdetails"><div class="panel-heading"><h2>{{ operation(job.type) }} · {{ stateLabel(job.state) }}</h2><a routerLink="/jobs" [queryParams]="before() ? {before: before()} : {}">Details schließen</a></div>
            <p>{{ explanation(job) }}</p><dl class="job-details">
              <dt>Job-ID</dt><dd><code>{{ job.jobId }}</code></dd><dt>Server</dt><dd>{{ job.serverId }}</dd>
              <dt>Auftraggeber</dt><dd>{{ job.actor }}</dd><dt>Begründung</dt><dd class="preserve-text">{{ job.reason }}</dd>
              @if (job.targetName) { <dt>Spielerziel</dt><dd>{{ job.targetName }}</dd> }
              @if (job.commandText) { <dt>Minecraft-Kommando</dt><dd><code>{{ job.commandText }}</code></dd> }
              @if (job.snapshotId) { <dt>Snapshotziel</dt><dd><code>{{job.snapshotId}}</code></dd> }
              @if(job.restorePlanId){<dt>Restoreplan</dt><dd><code>{{job.restorePlanId}}</code></dd>}
              @if(job.modPlanId){<dt>Modplan</dt><dd><code>{{job.modPlanId}}</code></dd>}
              @if (job.resultText !== null) { <dt>{{job.type==='CONSOLE'?'Minecraft-Antwort':'Bestätigtes Ergebnis'}}</dt><dd class="preserve-text">{{ job.resultText }}</dd> }
              <dt>Erstellt</dt><dd>{{ time(job.createdAt) }}</dd><dt>Letzte Änderung</dt><dd>{{ time(job.updatedAt) }}</dd>
              @if (job.errorCode) { <dt>Fehlercode</dt><dd><code>{{ job.errorCode }}</code></dd> }
            </dl>
            @if(job.state==='UNKNOWN' && s.role==='ADMIN'){
              <section class="panel confirmation"><h3>Ausgang anhand des Hostnachweises abgleichen</h3><p>Der Agent prüft gespeicherte Ergebnisse und bei Konfigurationsjobs die tatsächliche Datei. Es wird keine Serveraktion erneut ausgeführt. Ohne eindeutigen Nachweis bleibt der Job gesperrt.</p>
              @if(reconcileSelected()===job.jobId){<div class="actions"><button [disabled]="reconciling()" (click)="reconcileSelected.set(null)">Abbrechen</button><button class="primary" [disabled]="reconciling()" (click)="reconcile(s.id,job.jobId)">Lesenden Hostabgleich bestätigen</button></div>}
              @else{<button [disabled]="reconciling()" (click)="reconcileSelected.set(job.jobId)">Abgleich vorbereiten</button>}
            </section>}
            @if (s.role === 'ADMIN') { <a routerLink="/audit" [queryParams]="{jobId: job.jobId}">Audit für diesen Job ansehen →</a> }
          </section>
        }
      }
    } @else if (!loading() && !error()) { <section class="panel empty">Kein freigegebener Server vorhanden.</section> }
  `
})
export class Jobs {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null);
  readonly page = signal<JobPage | null>(null);
  readonly detail = signal<Job | null>(null);
  readonly before = signal<string | null>(null);
  readonly loading = signal(true);
  readonly stale = signal(false);
  readonly error = signal('');
  readonly reconcileSelected=signal<string|null>(null);
  readonly reconciling=signal(false);
  readonly reload = new Subject<void>();
  readonly operation = operationLabel;
  readonly stateLabel = jobStateLabel;
  readonly explanation = jobExplanation;
  readonly time = berlinTime;
  reconcile(server:string,id:string){if(this.reconciling()||this.reconcileSelected()!==id)return;this.reconciling.set(true);this.api.reconcileJob(server,id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.reconciling.set(false);this.reconcileSelected.set(null);this.reload.next();},error:err=>{this.reconciling.set(false);this.error.set(errorMessage(err));}});}
  constructor() {
    combineLatest([this.route.paramMap, this.route.queryParamMap]).pipe(
      map(([params, query]) => ({ id: params.get('jobId'), cursor: query.get('before') })),
      switchMap(selection => {
        this.page.set(null); this.detail.set(null); this.error.set(''); this.loading.set(true); this.stale.set(false);
        this.before.set(selection.cursor);
        return merge(timer(0, 5000), this.reload).pipe(exhaustMap(() => this.api.servers().pipe(
          switchMap(servers => {
            const server = servers[0] ?? null; this.server.set(server);
            if (!server || server.role === 'VIEWER') { this.page.set(null); this.detail.set(null); return of(null); }
            return forkJoin({ page: this.api.jobs(server.id, selection.cursor), detail: selection.id ? this.api.job(server.id, selection.id) : of(null) });
          }),
          catchError(err => {
            this.loading.set(false); this.stale.set(true); this.error.set(errorMessage(err));
            if (err.status === 401 || err.status === 403) { this.server.set(null); this.page.set(null); this.detail.set(null); }
            return EMPTY;
          })
        )));
      }), takeUntilDestroyed(this.destroy)
    ).subscribe(result => {
      if (result) { this.page.set(result.page); this.detail.set(result.detail); }
      this.loading.set(false); this.stale.set(false); this.error.set('');
    });
  }
}

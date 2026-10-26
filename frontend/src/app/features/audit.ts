import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, exhaustMap, merge, of, switchMap, timer } from 'rxjs';
import { Api, AuditPage, errorMessage, ServerProfile } from '../core/api';
import { auditActionLabel, auditOutcomeLabel, berlinTime } from '../core/job-presentation';

@Component({
  imports: [RouterLink],
  template: `
    <header><p class="eyebrow">BETRIEB · ADMIN</p><h1>Audit-Protokoll</h1><p class="muted">Serverjobs, Statusübergänge und dokumentierte Bestandsänderungen nachvollziehen.</p></header>
    @if (loading()) { <p role="status">Audit wird geladen …</p> }
    @if (error()) { <div class="error" role="alert">{{ error() }} <button (click)="reload.next()">Erneut laden</button></div> }
    @if (server(); as s) {
      @if (s.environmentLabel) { <p class="notice">{{ s.environmentLabel }}</p> }
      @if (s.role !== 'ADMIN') { <section class="panel empty">Das Audit-Protokoll erfordert Adminrechte für diesen Server.</section> }
      @else {
        @if (jobId(); as id) { <section class="panel"><p>Gefiltert auf Job <code>{{ id }}</code></p><div class="actions"><a [routerLink]="['/jobs', id]">Zum Job →</a><a routerLink="/audit">Alle Ereignisse</a></div></section> }
        @if (page(); as p) {
          <section class="panel"><div class="panel-heading"><h2>{{ s.name }} · {{ before() ? 'Ältere Ereignisse' : 'Neueste Ereignisse' }}</h2><small>{{ stale() ? 'Letzte Daten · Aktualisierung fehlgeschlagen' : 'Aktualisierung alle 5 Sekunden' }}</small></div>
            <p class="muted">Erfasst werden Serverjobs, ihre Statusübergänge und Änderungen der Bestandsaufnahme. Begründungen, Bestandswerte, Quellen, Zugangsdaten und Idempotency-Keys werden hier nicht ausgegeben.</p>
            @if (p.items.length) {
              <div class="table-scroll"><table><thead><tr><th>Zeit (Berlin)</th><th>Auftraggeber</th><th>Aktion</th><th>Ergebnis</th><th>Details</th></tr></thead>
                <tbody>@for (event of p.items; track event.id) { <tr><td>{{ time(event.occurredAt) }}</td><td>{{ event.actor }}</td><td>{{ operation(event.action) }}</td><td>{{ stateLabel(event.outcome) }}</td><td>
                  @if (event.jobId) { <a [routerLink]="['/jobs', event.jobId]" [attr.aria-label]="'Job ' + event.jobId + ' ansehen'">Job ansehen →</a> }
                  @else { <a routerLink="/inventory">Bestandsaufnahme →</a><small>Revision {{ event.revision }}</small> }
                </td></tr> }</tbody></table></div>
            } @else { <p class="empty">Keine Audit-Ereignisse für diese Auswahl gespeichert.</p> }
            <div class="actions">
              @if (before()) { <a class="button" routerLink="/audit" [queryParams]="jobId() ? {jobId: jobId()} : {}">Neueste Ereignisse</a> }
              @if (p.nextCursor) { <a class="button" routerLink="/audit" [queryParams]="{jobId: jobId(), before: p.nextCursor}">Ältere Ereignisse →</a> }
            </div>
          </section>
        }
      }
    } @else if (!loading() && !error()) { <section class="panel empty">Kein freigegebener Server vorhanden.</section> }
  `
})
export class Audit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null);
  readonly page = signal<AuditPage | null>(null);
  readonly jobId = signal<string | null>(null);
  readonly before = signal<string | null>(null);
  readonly loading = signal(true);
  readonly stale = signal(false);
  readonly error = signal('');
  readonly reload = new Subject<void>();
  readonly time = berlinTime;
  readonly operation = auditActionLabel;
  readonly stateLabel = auditOutcomeLabel;
  constructor() {
    this.route.queryParamMap.pipe(switchMap(query => {
      this.jobId.set(query.get('jobId')); this.before.set(query.get('before'));
      this.page.set(null); this.loading.set(true); this.stale.set(false); this.error.set('');
      return merge(timer(0, 5000), this.reload).pipe(exhaustMap(() => this.api.servers().pipe(
        switchMap(servers => {
          const server = servers[0] ?? null; this.server.set(server);
          if (!server || server.role !== 'ADMIN') { this.page.set(null); return of(null); }
          return this.api.audit(server.id, query.get('jobId'), query.get('before'));
        }), catchError(err => {
          this.loading.set(false); this.stale.set(true); this.error.set(errorMessage(err));
          if (err.status === 401 || err.status === 403) { this.server.set(null); this.page.set(null); }
          return EMPTY;
        })
      )));
    }), takeUntilDestroyed(this.destroy)).subscribe(page => {
      this.page.set(page); this.loading.set(false); this.stale.set(false); this.error.set('');
    });
  }
}

import { afterNextRender, Component, DestroyRef, Injector, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, of, switchMap } from 'rxjs';
import { Api, errorMessage, InventoryEvidence, InventorySnapshot, ServerProfile } from '../core/api';
import { berlinTime } from '../core/job-presentation';

@Component({
  imports: [RouterLink],
  template: `
    <header><p class="eyebrow">BESTANDSAUFNAHME · ADMIN</p><h1>Serverbestand dokumentieren</h1><p class="muted">Angaben mit Quelle und Erfassungszeit festhalten. Es wird keine Hostverbindung hergestellt.</p></header>
    @if (loading()) { <p role="status">Bestandsaufnahme wird geladen …</p> }
    @if (error()) { <div class="error" role="alert">{{ error() }}</div> }
    @if (success()) { <p role="status">{{ success() }}</p> }
    @if (server(); as s) {
      @if (s.role !== 'ADMIN') { <section class="panel empty">Die Bestandsaufnahme erfordert Adminrechte für diesen Server.</section> }
      @else if (snapshot(); as data) {
        <section class="panel"><div class="panel-heading"><h2>{{ data.documentedCount }} / {{ data.requiredCount }} Angaben dokumentiert</h2><small>Revision {{ data.revision }}</small></div>
          <p>Nutzerangabe: Minecraft {{ s.reportedMinecraftVersion ?? 'unbekannt' }} · {{ s.reportedLoader ?? 'unbekannt' }}. Diese Angaben werden nicht automatisch als Nachweis übernommen.</p>
          <p class="muted">„Dokumentiert“ bedeutet manuell erfasst. Die technische Gesamtabnahme bleibt ausstehend, auch bei vollständig ausgefüllter Liste. Nur redigierte Angaben eintragen: keine Passwörter, API-Keys oder vollständigen Secretdateien.</p>
          <div class="actions"><button [disabled]="saving() || loading()" (click)="load()">Daten neu laden</button><a routerLink="/audit">Änderungen im Audit →</a></div>
          <small>Neu laden schließt die Bearbeitung und verwirft ungespeicherte Eingaben. Es gibt kein automatisches Neuladen während der Bearbeitung.</small>
        </section>
        @if (editing(); as field) {
          <section class="panel"><h2>{{ field.label }}</h2>
            <form (submit)="save($event, value.value, source.value, observed.value)">
              <label for="evidence-value">Redigierter Wert / Prüfergebnis</label>
              <textarea id="evidence-value" #value required maxlength="2000" rows="4" [value]="field.value ?? ''" [disabled]="saving()"></textarea>
              <label for="evidence-source">Quelle oder Verweis auf den Nachweis</label>
              <input id="evidence-source" #source required maxlength="240" [value]="field.source ?? ''" [disabled]="saving()" placeholder="Zum Beispiel internes Prüfprotokoll, Abschnitt Versionsprüfung">
              <label for="evidence-observed">Zeitpunkt der Quellenerfassung (UTC, ISO 8601)</label>
              <input id="evidence-observed" #observed required [value]="field.observedAt ?? ''" [disabled]="saving()" placeholder="JJJJ-MM-TTThh:mm:ssZ" aria-describedby="evidence-time-hint">
              <small id="evidence-time-hint">Zeitpunkt, zu dem der Nachweis erhoben wurde; kein zukünftiger Zeitpunkt. Die Speicherzeit und dein Portalname werden zusätzlich automatisch protokolliert.</small>
              <div class="actions"><button class="primary" type="submit" [disabled]="saving()">{{ saving() ? 'Wird gespeichert …' : 'Angabe dokumentieren' }}</button><button type="button" [disabled]="saving()" (click)="editing.set(null)">Abbrechen</button></div>
            </form>
          </section>
        }
        <div class="inventory-grid">
          @for (field of data.fields; track field.key) {
            <section class="panel"><div class="panel-heading"><h2>{{ field.label }}</h2><span class="muted">{{ field.value === null ? 'Offen' : 'Dokumentiert' }}</span></div>
              @if (field.value !== null) {
                <p class="preserve-text">{{ field.value }}</p><dl class="job-details"><dt>Quelle</dt><dd>{{ field.source }}</dd><dt>Erfasst</dt><dd>{{ field.observedAt ? time(field.observedAt) : '—' }} (Berlin)</dd><dt>Dokumentiert</dt><dd>{{ field.recordedBy }} · {{ field.recordedAt ? time(field.recordedAt) : '—' }} (Berlin)</dd></dl>
              } @else { <p class="muted">Noch kein Nachweis erfasst.</p> }
              <button [disabled]="saving()" (click)="edit(field)">{{ field.value === null ? 'Angabe erfassen' : 'Angabe bearbeiten' }}</button>
            </section>
          }
        </div>
      }
    } @else if (!loading() && !error()) { <section class="panel empty">Kein freigegebener Server vorhanden.</section> }
    @if (error() && !snapshot()) { <button (click)="load()">Erneut laden</button> }
  `
})
export class Inventory {
  private readonly api = inject(Api);
  private readonly destroy = inject(DestroyRef);
  private readonly injector = inject(Injector);
  readonly server = signal<ServerProfile | null>(null);
  readonly snapshot = signal<InventorySnapshot | null>(null);
  readonly editing = signal<InventoryEvidence | null>(null);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly time = berlinTime;
  constructor() { this.load(); }
  load() {
    if (this.saving() || this.loading()) return;
    this.loading.set(true); this.error.set(''); this.success.set(''); this.editing.set(null); this.snapshot.set(null);
    this.api.servers().pipe(switchMap(servers => {
      const server = servers[0] ?? null; this.server.set(server);
      return server?.role === 'ADMIN' ? this.api.inventory(server.id) : of(null);
    }), finalize(() => this.loading.set(false)), takeUntilDestroyed(this.destroy)).subscribe({
      next: data => this.snapshot.set(data), error: error => this.failed(error)
    });
  }
  edit(field: InventoryEvidence) {
    this.error.set(''); this.success.set(''); this.editing.set(field);
    // Die Bearbeitung steht vor der Feldliste, auch auf kleinen Bildschirmen.
    afterNextRender(() => document.getElementById('evidence-value')?.focus(), { injector: this.injector });
  }
  save(event: Event, value: string, source: string, observedAt: string) {
    event.preventDefault();
    const server = this.server(), field = this.editing(), snapshot = this.snapshot();
    if (!server || server.role !== 'ADMIN' || !field || !snapshot || this.saving()) return;
    const utc = observedAt.trim();
    const timestamp = Date.parse(utc);
    const validUtc = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/.test(utc)
      && Number.isFinite(timestamp) && new Date(timestamp).toISOString().slice(0, 19) === utc.slice(0, 19);
    if (!value.trim() || !source.trim() || !validUtc) {
      this.error.set('Wert, Quelle und ein gültiger UTC-Zeitpunkt mit abschließendem Z sind erforderlich.'); return;
    }
    this.saving.set(true); this.error.set(''); this.success.set('');
    this.api.recordInventory(server.id, field.key, snapshot.revision, value.trim(), source.trim(), utc)
      .pipe(finalize(() => this.saving.set(false)), takeUntilDestroyed(this.destroy)).subscribe({
        next: data => { this.snapshot.set(data); this.editing.set(null); this.success.set('Angabe dokumentiert. Die technische Gesamtabnahme bleibt ausstehend.'); },
        error: error => this.failed(error)
      });
  }
  private failed(error: HttpErrorResponse) {
    this.error.set(errorMessage(error));
    if (error.status === 401 || error.status === 403) { this.server.set(null); this.snapshot.set(null); this.editing.set(null); }
  }
}

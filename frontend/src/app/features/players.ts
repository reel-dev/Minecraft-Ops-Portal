import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of, switchMap } from 'rxjs';
import { Api, errorMessage, Job, OnlinePlayers, ServerProfile } from '../core/api';
import { jobStateLabel, operationLabel } from '../core/job-presentation';
import { Icon } from '../core/icon';
import { PlayerEntry } from '../core/api';

type WhitelistAction = 'WHITELIST_ADD' | 'WHITELIST_REMOVE' | 'OP_GRANT' | 'OP_REVOKE';
interface WhitelistPlan { type: WhitelistAction; targetName: string; reason: string; }

@Component({
  imports: [RouterLink, Icon],
  templateUrl: './players.html'
})
export class Players {
  private readonly api = inject(Api);
  private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null);
  readonly tab = signal<'online'|'whitelist'|'operators'>('online');
  readonly whitelist = signal<PlayerEntry[] | null>(null);
  readonly operators = signal<PlayerEntry[] | null>(null);
  readonly snapshot = signal<OnlinePlayers | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly busy = signal(false);
  readonly jobsKnown = signal(false);
  readonly blockingJob = signal<Job | null>(null);
  readonly selected = signal<WhitelistPlan | null>(null);
  readonly lastJob = signal<Job | null>(null);
  readonly actionLabel = operationLabel;
  readonly stateLabel = jobStateLabel;
  private pending: (WhitelistPlan & { key: string }) | null = null;
  constructor() { this.refresh(); }
  refresh() {
    if (this.loading() || this.busy()) return;
    this.loading.set(true); this.error.set(''); this.snapshot.set(null); this.server.set(null);
    this.lastJob.set(null);
    this.jobsKnown.set(false); this.blockingJob.set(null); this.selected.set(null);
    this.api.servers().pipe(switchMap(servers => {
      const server = servers[0];
      if (!server) throw new Error('NO_SERVER');
      this.server.set(server);
      if (server.role !== 'VIEWER') this.api.membership(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: value => {this.whitelist.set(value.data?.whitelist ?? null);this.operators.set(value.data?.operators ?? null);},error:()=>{this.whitelist.set(null);this.operators.set(null);}});
      return forkJoin({ players: this.api.onlinePlayers(server.id), jobs: server.role !== 'VIEWER' && server.playerOperationsAvailable
        ? this.api.jobs(server.id).pipe(catchError(() => of(null))) : of(null) });
    }), takeUntilDestroyed(this.destroy), finalize(() => this.loading.set(false))).subscribe({
      next: value => { this.snapshot.set(value.players); this.jobsKnown.set(value.jobs !== null); this.blockingJob.set(value.jobs?.blockingJob ?? null); },
      error: error => this.error.set(error instanceof Error && error.message === 'NO_SERVER' ? 'Kein freigegebener Server vorhanden.' : errorMessage(error))
    });
  }
  canChange() {
    return !!this.server()?.playerOperationsAvailable && this.server()?.role !== 'VIEWER' && this.snapshot()?.names != null
      && this.jobsKnown() && !this.blockingJob() && !this.loading() && !this.busy();
  }
  plan(type: WhitelistAction, name: string, reason: string) {
    if (!this.canChange()) return;
    if ((type === 'OP_GRANT' || type === 'OP_REVOKE') && (this.server()?.role !== 'ADMIN' || !this.server()?.operatorOperationsAvailable)) return;
    if (!/^[A-Za-z0-9_]{3,16}$/.test(name.trim()) || !reason.trim()) { this.error.set('Gültigen Minecraft-Namen und eine Begründung eingeben.'); return; }
    this.error.set(''); this.selected.set({type, targetName: name.trim(), reason: reason.trim()});
  }
  confirm() {
    const plan = this.selected(), server = this.server();
    if (!plan || !server || !this.canChange()) return;
    if (!this.pending || this.pending.type !== plan.type || this.pending.targetName !== plan.targetName || this.pending.reason !== plan.reason)
      this.pending = { ...plan, key: crypto.randomUUID() };
    this.busy.set(true); this.error.set('');
    this.api.operation(server.id, plan.type, plan.reason, this.pending.key, plan.targetName).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.busy.set(false))).subscribe({
      next: job => { this.lastJob.set(job); this.selected.set(null); this.pending = null;
        if (['QUEUED','RUNNING','UNKNOWN'].includes(job.state)) this.blockingJob.set(job); },
      error: error => { this.error.set(errorMessage(error)); this.jobsKnown.set(false); }
    });
  }
  time(value: string | null) { return value ? new Date(value).toLocaleString('de-DE') : 'nicht verfügbar'; }
  isOperator(name: string) { return this.operators()?.some(entry => entry.name.toLowerCase() === name.toLowerCase()) ?? false; }
}


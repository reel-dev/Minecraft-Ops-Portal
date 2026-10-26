import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';
import { Api, errorMessage, Job, ServerProfile } from '../core/api';
import { Icon } from '../core/icon';
import { jobStateLabel } from '../core/job-presentation';
@Component({ imports:[Icon, RouterLink], template: `
  <header class="page-heading"><div><p class="eyebrow">MINECRAFT / RCON</p><h1>Konsole</h1><p class="muted">Minecraft-Kommandos und ihre tatsächlichen Antworten. <a routerLink="/logs">Zum separaten Serverlog →</a></p></div><span class="badge" [class.success]="server()?.consoleAvailable">{{ server()?.consoleAvailable ? 'RCON freigeschaltet' : 'RCON nicht freigeschaltet' }}</span></header>
  @if (server()?.environmentLabel) { <p class="environment-banner"><smp-icon name="shield" />{{ server()?.environmentLabel }}</p> }
  @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  @if (blocker(); as job) { <p class="job-warning">Server gesperrt: {{ stateLabel(job.state) }}. <a [routerLink]="['/jobs',job.jobId]">Job ansehen →</a></p> }
  <section class="panel flush"><div class="card-heading"><h2><smp-icon name="console" />Minecraft-Antworten</h2><small>Persistente Jobhistorie</small></div><div class="terminal" style="height:380px" role="log" aria-label="Minecraft-Kommandoantworten">
    @for (job of commands(); track job.jobId) { <div style="margin-bottom:18px"><div class="text-green">[{{ time(job.createdAt) }}] {{ job.actor }} &gt; {{ job.commandText }}</div><div>{{ job.resultText ?? stateLabel(job.state) }}</div><a [routerLink]="['/jobs',job.jobId]">{{ job.jobId }} · {{ stateLabel(job.state) }}</a></div> } @empty { <span class="terminal-muted">&gt; Noch keine Minecraft-Kommandos gespeichert.</span> }
  </div><div class="card-body"><div class="actions" style="margin-bottom:16px">@for (command of templates; track command) { <button class="quiet compact" [disabled]="!canSend()" (click)="draft.set(command)">{{ command }}</button> }</div>
    <form (submit)="$event.preventDefault(); prepare()"><label for="minecraft-command">{{ server()?.role === 'ADMIN' ? 'Minecraft-Kommando' : 'Freigegebene Vorlage wählen' }}</label><input id="minecraft-command" [value]="draft()" (input)="draft.set($any($event.target).value)" [readOnly]="server()?.role !== 'ADMIN'" maxlength="512" autocomplete="off" placeholder="list" [disabled]="!canSend()"><button type="submit" class="primary" [disabled]="!canSend() || !draft().trim()"><smp-icon name="arrow" />Kommando prüfen</button></form>
    @if (confirmation()) { <section class="confirmation panel" style="margin-top:16px" aria-label="Minecraft-Kommando bestätigen"><h2>Kommando ausführen?</h2><code>{{ confirmation() }}</code><p>Wird mit deinem Konto als dauerhafter Job protokolliert. Keine Passwörter oder Tokens eingeben. Spielerrechte und Neustarts werden in ihren eigenen Ansichten verwaltet.</p><div class="actions"><button (click)="confirmation.set(null)">Abbrechen</button><button class="primary" (click)="send()" [disabled]="!canSend()">Bestätigen und senden</button></div></section> }
  </div></section><p class="source">Operatoren dürfen ausschließlich die angezeigten Vorlagen senden. Freie Minecraft-Kommandos erfordern Adminrechte. Die Konsole führt keine Betriebssystembefehle aus.</p>
` })
export class Console {
  private readonly api = inject(Api); private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile|null>(null); readonly commands = signal<Job[]>([]); readonly blocker = signal<Job|null>(null);
  readonly error = signal(''); readonly draft = signal('list'); readonly confirmation = signal<string|null>(null); readonly busy = signal(false); readonly jobsKnown = signal(false);
  readonly stateLabel = jobStateLabel;
  readonly templates = ['list','time query daytime','time set day','time set night','weather clear','weather rain','save-all flush'];
  private pending: { command: string; key: string } | null = null;
  constructor() { this.api.servers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next: s => this.server.set(s[0] ?? null), error: e => this.error.set(errorMessage(e))});
    timer(300,2500).pipe(takeUntilDestroyed(this.destroy), switchMap(() => { const s=this.server(); return s && s.role!=='VIEWER' ? this.api.jobs(s.id,null,50).pipe(catchError(e => { this.jobsKnown.set(false); this.error.set(errorMessage(e)); if([401,403].includes(e.status)) this.commands.set([]); return EMPTY; })) : EMPTY; })).subscribe(page => { this.commands.set(page.items.filter(j => j.type==='CONSOLE').reverse()); this.blocker.set(page.blockingJob); this.jobsKnown.set(true); this.error.set(''); });
  }
  canSend() { return !!this.server()?.consoleAvailable && this.server()?.role!=='VIEWER' && this.jobsKnown() && !this.blocker() && !this.busy(); }
  prepare() { if(this.canSend()) this.confirmation.set(this.draft().trim()); }
  send() { const s=this.server(), command=this.confirmation(); if(!s || !command || !this.canSend()) return;
    if(!this.pending || this.pending.command!==command) this.pending={command,key:crypto.randomUUID()}; this.busy.set(true);
    this.api.operation(s.id,'CONSOLE','Minecraft-Kommando im Portal bestätigt',this.pending.key,null,command).pipe(takeUntilDestroyed(this.destroy)).subscribe({next: j => { this.blocker.set(j); this.commands.update(items=>[...items,j]); this.confirmation.set(null); this.pending=null; this.busy.set(false); },error:e=>{this.error.set(errorMessage(e));this.busy.set(false);}});
  }
  time(at:string) { return new Date(at).toLocaleTimeString('de-DE'); }
}

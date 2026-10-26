import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api, errorMessage, ServerProfile } from '../core/api';
import { Icon } from '../core/icon';

@Component({ imports: [Icon], template: `
  <header class="page-heading"><div><p class="eyebrow">BEOBACHTEN</p><h1>Serverlogs</h1><p class="muted">Der eigene Logstrom des Minecraft-Containers. Vertrauliche Zeilen und IPs werden redigiert.</p></div></header>
  @if (server()?.environmentLabel) { <p class="environment-banner"><smp-icon name="shield" />{{ server()?.environmentLabel }}</p> }
  @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
  <section class="panel flush"><div class="card-heading"><h2><smp-icon name="log" />Docker-Serverlog <span class="badge" [class.success]="connected() && !paused()">{{ paused() ? 'Pausiert' : connected() ? 'Verbunden' : 'Wiederverbindung / keine Quelle' }}</span></h2><div class="actions"><button class="quiet compact" (click)="togglePause()">{{ paused() ? 'Fortsetzen' : 'Pausieren' }}</button><button class="quiet compact" (click)="lines.set([])">Ansicht leeren</button></div></div>
  <div class="card-body filter-bar"><input aria-label="Logzeilen filtern" placeholder="Logzeilen filtern …" (input)="filter.set($any($event.target).value)"><label><input type="checkbox" [checked]="autoScroll()" (change)="autoScroll.set($any($event.target).checked)">Auto-Scroll</label><small>Letzte Abfrage: {{ observedAt() ? time(observedAt()!) : '—' }} · höchstens 200 Zeilen</small></div>
  <div #terminal class="terminal" style="height:520px" role="log" aria-label="Redigierter Serverlog">@for (line of filtered(); track $index) { <div [class.text-green]="line.includes('Done (')">{{ line }}</div> } @empty { <span class="terminal-muted">{{ connected() ? 'Keine passenden Logzeilen.' : 'Keine Logquelle verbunden. Der Host-Agent muss den festen Serverlog bereitstellen.' }}</span> }</div></section>
  <p class="source">Live-Strom mit Wiederverbindung und Logcursor. Ist die Unterbrechung länger als das verfügbare Fenster, wird die Lücke angezeigt. Pausieren hält die Verbindung an; „Ansicht leeren“ löscht keine Serverdateien.</p>
` })
export class Logs {
  private readonly api = inject(Api); private readonly destroy = inject(DestroyRef);
  readonly server = signal<ServerProfile | null>(null); readonly lines = signal<string[]>([]); readonly filter = signal('');
  readonly paused = signal(false); readonly autoScroll = signal(true); readonly connected = signal(false); readonly error = signal(''); readonly observedAt = signal<string | null>(null);
  private stream:EventSource|null=null;
  private cursor:string|null=null;
  constructor() {
    this.destroy.onDestroy(()=>this.stream?.close());
    this.api.servers().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:servers=>{this.server.set(servers[0]??null);this.open();},error:err=>this.error.set(errorMessage(err))});
  }
  private open(){
    const server=this.server();if(!server||server.role==='VIEWER'||this.paused())return;this.stream?.close();
    const stream=new EventSource(`/api/v1/servers/${encodeURIComponent(server.id)}/logs/stream`+(this.cursor?'?after='+encodeURIComponent(this.cursor):''));this.stream=stream;
    stream.addEventListener('log',event=>{if(this.stream!==stream)return;try{const value=JSON.parse((event as MessageEvent).data);if(typeof value.text!=='string'||value.text.length>2100)return;this.cursor=(event as MessageEvent).lastEventId;this.lines.update(lines=>[...lines,value.text].slice(-200));if(this.autoScroll())requestAnimationFrame(()=>{const element=document.querySelector('[role="log"]');if(element)element.scrollTop=element.scrollHeight;});}catch{this.error.set('Ungültige Logantwort.');stream.close();}});
    stream.addEventListener('status',event=>{if(this.stream!==stream)return;try{const value=JSON.parse((event as MessageEvent).data);this.connected.set(value.connected===true);if(typeof value.observedAt==='string')this.observedAt.set(value.observedAt);if(value.connected)this.error.set('');}catch{this.connected.set(false);}});
    stream.addEventListener('gap',()=>{this.lines.update(lines=>[...lines,'[Loglücke: ältere Zeilen liegen außerhalb des verfügbaren Fensters]'].slice(-200));});
    stream.onerror=()=>{if(this.stream!==stream)return;this.connected.set(false);this.api.logs(server.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({error:err=>{if([401,403].includes(err.status)){stream.close();this.lines.set([]);this.error.set(errorMessage(err));}}});};
  }
  togglePause(){this.paused.set(!this.paused());if(this.paused()){this.stream?.close();this.stream=null;this.connected.set(false);}else this.open();}
  filtered() { return this.lines().filter(line => line.toLowerCase().includes(this.filter().toLowerCase())); }
  time(at: string) { return new Date(at).toLocaleTimeString('de-DE'); }
}

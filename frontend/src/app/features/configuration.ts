import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { forkJoin, switchMap } from 'rxjs';
import { Api, ConfigurationSnapshot, errorMessage, Job, ServerProfile } from '../core/api';
import { Icon } from '../core/icon';
@Component({imports:[Icon,RouterLink],template:`
<header class="page-heading"><div><p class="eyebrow">SERVER.PROPERTIES</p><h1>Server-Konfiguration</h1><p class="muted">Erlaubte Eigenschaften, feste Wertebereiche und Schutz vor gleichzeitigem Überschreiben.</p></div><button (click)="load()" [disabled]="busy()"><smp-icon name="refresh" />Neu laden</button></header>
@if(error()){<p class="error" role="alert">{{error()}}</p>}
@if(server()?.environmentLabel){<p class="environment-banner"><smp-icon name="shield" />{{server()?.environmentLabel}}</p>}
@if(snapshot();as value){<section class="panel"><div class="section-heading"><h2><smp-icon name="configuration" />{{value.source}}</h2><span class="badge">Neustart nach Änderung erforderlich</span></div><p class="muted">Docker-Vorgaben sind gesperrt, damit sie gespeicherte Änderungen beim nächsten Start nicht unbemerkt überschreiben. Nicht verwaltete Eigenschaften und Kommentare bleiben erhalten.</p>
<form (submit)="$event.preventDefault();prepare()"><div class="form-grid">@for(field of value.fields;track field.key){<label [attr.for]="'property-'+field.key">{{field.label}} <small>{{field.key}} @if(field.managedByEnvironment){· Docker-Vorgabe}</small>
@if(field.type==='enum'){<select [id]="'property-'+field.key" [value]="draft()[field.key]??''" (change)="change(field.key,$any($event.target).value)" [disabled]="field.value===null||field.managedByEnvironment||!value.writable||busy()" style="display:block;width:100%;margin:10px 0 20px"><option value="" disabled>Vom Server nicht bereitgestellt</option>@for(option of field.options;track option){<option [value]="option" [selected]="option===draft()[field.key]">{{option}}</option>}</select>}
@else{<input [id]="'property-'+field.key" [type]="field.type==='integer'?'number':'text'" [min]="field.min" [max]="field.max" maxlength="120" [value]="draft()[field.key]??''" (input)="change(field.key,$any($event.target).value)" [disabled]="field.value===null||field.managedByEnvironment||!value.writable||busy()">}</label>}</div>
<label>Begründung<input #reason maxlength="240" placeholder="Anlass der Konfigurationsänderung" (input)="reasonText.set(reason.value)"></label><div class="actions"><button class="primary" type="submit" [disabled]="!canSave()">Änderungen prüfen</button><a class="button" routerLink="/dashboard">Zum Serverbetrieb</a></div></form><p class="source" style="margin-top:18px">Revision: <code>{{value.revision.slice(0,16)}}</code> · Die gespeicherten Werte werden erst beim nächsten kontrollierten Neustart aktiv.</p></section>
@if(confirming()){<section class="panel confirmation" aria-label="Konfigurationsänderung bestätigen"><h2>Diese Eigenschaften speichern?</h2><dl class="summary-list">@for(entry of changes();track entry.key){<dt>{{entry.key}}</dt><dd>{{entry.before??'Nicht gesetzt'}} → {{entry.after}}</dd>}</dl><p>Die Datei wird atomar ersetzt. Diese Aktion startet den Server nicht neu.</p><div class="actions"><button (click)="confirming.set(false)" [disabled]="busy()">Abbrechen</button><button class="primary" (click)="save()" [disabled]="!canSave()">Speichern bestätigen</button></div></section>}
}@else{<section class="panel empty-state"><smp-icon name="configuration" /><strong>{{server()?.role==='ADMIN'?'Konfigurationsquelle nicht verfügbar':'Adminrechte erforderlich'}}</strong><p>Es werden ausschließlich bestätigte Werte aus server.properties angezeigt.</p></section>}
@if(lastJob();as job){<p class="notice">Änderung als Job angefordert. <a [routerLink]="['/jobs',job.jobId]">Ergebnis und Neustartbedarf ansehen →</a></p>}
`})
export class Configuration {
 private readonly api=inject(Api);private readonly destroy=inject(DestroyRef);
 readonly server=signal<ServerProfile|null>(null);readonly snapshot=signal<ConfigurationSnapshot|null>(null);readonly draft=signal<Partial<Record<string,string>>>({});readonly reasonText=signal('');readonly confirming=signal(false);readonly busy=signal(false);readonly blocked=signal(true);readonly error=signal('');readonly lastJob=signal<Job|null>(null);
 private pending:{signature:string;key:string}|null=null;
 constructor(){this.load();}
 load(){if(this.busy())return;this.error.set('');this.blocked.set(true);this.confirming.set(false);this.api.servers().pipe(switchMap(servers=>{const s=servers[0];this.server.set(s??null);if(!s)throw new Error();return forkJoin({config:this.api.configuration(s.id),jobs:this.api.jobs(s.id)});}),takeUntilDestroyed(this.destroy)).subscribe({next:v=>{this.snapshot.set(v.config.data);this.draft.set(Object.fromEntries((v.config.data?.fields??[]).map(f=>[f.key,f.value??''])));this.blocked.set(!!v.jobs.blockingJob);},error:e=>{this.snapshot.set(null);this.error.set(errorMessage(e));}});}
 change(key:string,value:string){this.draft.update(v=>({...v,[key]:value}));this.confirming.set(false);}
 changes(){return (this.snapshot()?.fields??[]).filter(f=>!f.managedByEnvironment&&(this.draft()[f.key]??'')!==(f.value??'')).map(f=>({key:f.key,before:f.value,after:this.draft()[f.key]??''}));}
 canSave(){return this.server()?.role==='ADMIN'&&!!this.snapshot()?.writable&&!this.blocked()&&!this.busy()&&this.changes().length>0&&!!this.reasonText().trim();}
 prepare(){if(this.canSave())this.confirming.set(true);}
 save(){const s=this.server(),value=this.snapshot();if(!s||!value||!this.canSave()||!this.confirming())return;const changes=Object.fromEntries(this.changes().map(c=>[c.key,c.after]));const reason=this.reasonText().trim(),signature=JSON.stringify({revision:value.revision,changes,reason});if(!this.pending||this.pending.signature!==signature)this.pending={signature,key:crypto.randomUUID()};this.busy.set(true);this.error.set('');this.api.saveConfiguration(s.id,value.revision,changes,reason,this.pending.key).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:j=>{this.lastJob.set(j);this.blocked.set(true);this.confirming.set(false);this.pending=null;this.busy.set(false);},error:e=>{this.busy.set(false);this.error.set(errorMessage(e));}});}
}


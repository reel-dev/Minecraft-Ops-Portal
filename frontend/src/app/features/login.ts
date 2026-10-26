import { Component, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Api, errorMessage } from '../core/api';
import { Icon } from '../core/icon';
@Component({imports:[Icon],template:`
<section class="login-panel panel"><div class="empty-state"><span class="grass-block" aria-hidden="true" style="width:44px;height:46px;margin-bottom:20px"></span><p class="eyebrow">ELBRACHT SMP CONTROL</p><h1>Willkommen zurück.</h1><p>Dein Server. Deine Community. Alles an einem Ort.</p></div>
@if(loginFailed){<p class="error" role="alert">Anmeldung fehlgeschlagen. Kontoname und Passwort prüfen.</p>}
@if(error()){<p class="error" role="alert">{{error()}}</p>}
@if(oidcUrl()){<a class="button primary" [href]="oidcUrl()">Mit deinem Anmeldedienst fortfahren <smp-icon name="arrow" /></a>}
@else {<form method="post" action="/login"><input type="hidden" name="_csrf" [value]="token()"><label for="portal-username">Kontoname</label><input id="portal-username" name="username" autocomplete="username" required placeholder="local-admin"><label for="portal-password">Passwort</label><input id="portal-password" name="password" type="password" autocomplete="current-password" required placeholder="Dein Portalpasswort"><button class="primary" type="submit" style="width:100%" [disabled]="!token()">Anmelden <smp-icon name="arrow" /></button></form>}
<p class="source" style="margin-top:22px;text-align:center"><smp-icon name="shield" /> Portalzugang und Minecraft-Spielkonto sind unabhängig.</p></section>
`})
export class Login {
 private readonly api=inject(Api);private readonly destroy=inject(DestroyRef);
 readonly loginFailed=inject(ActivatedRoute).snapshot.queryParamMap.has('error');readonly token=signal('');readonly error=signal('');readonly oidcUrl=signal('');
 constructor(){this.api.auth().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:v=>{if(v.loginUrl.startsWith('/oauth2/'))this.oidcUrl.set(v.loginUrl);},error:e=>this.error.set(errorMessage(e))});this.api.loginToken().pipe(takeUntilDestroyed(this.destroy)).subscribe({next:v=>this.token.set(v.token),error:e=>this.error.set(errorMessage(e))});}
}

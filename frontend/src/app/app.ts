import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Api, errorMessage } from './core/api';
import { navigation } from './routes';
import { Icon } from './core/icon';
import { SessionState } from './core/session';

@Component({
  selector: 'smp-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, Icon],
  template: `
    <a class="skip" href="#content">Zum Inhalt</a>
    <aside>
      <a class="brand" routerLink="/dashboard"><span class="grass-block" aria-hidden="true"><i></i></span><span>Elbracht SMP<small>Minecraft Server</small></span></a>
      <div class="nav-caption">SERVERVERWALTUNG</div>
      <nav aria-label="Hauptnavigation">
        @for (item of navigation; track item.path) {
          <a [routerLink]="'/' + item.path" routerLinkActive="active" ariaCurrentWhenActive="page"><smp-icon [name]="item.icon" />{{ item.label }}</a>
        }
      </nav>
      <div class="account">
        @if (user()) {
          <div class="account-identity"><span class="avatar"><smp-icon name="user" /></span><div><strong>{{ user() }}</strong><small>Portal-Konto</small></div></div><button class="quiet" (click)="logout()">Abmelden</button>
        } @else {
          <span>Keine aktive Sitzung</span><a class="button primary" [href]="loginUrl()">Anmelden</a>
        }
        @if (error()) { <p role="alert">{{ error() }}</p> }
      </div>
    </aside>
    <main id="content"><div class="workspace-bar"><span><smp-icon name="shield" /> Elbracht SMP Control <span class="muted">/ Verwaltung</span></span><span class="workspace-local"><span class="dot"></span>{{ environment() }}</span></div><router-outlet /><footer>Elbracht SMP Control <span>Serververwaltung</span></footer></main>
  `
})
export class App {
  private readonly api = inject(Api);
  readonly navigation = navigation;
  readonly user = inject(SessionState).name;
  readonly loginUrl = signal('/sign-in');
  readonly environment=signal('Umgebung wird geladen');
  readonly error = signal('');
  constructor() {
    this.api.auth().subscribe({ next: value => {this.loginUrl.set(value.loginUrl);this.environment.set(value.environmentLabel??'Verwaltung');}, error: () => {} });
    this.api.me().subscribe({ next: value => this.user.set(value.name), error: () => {} });
  }
  logout() {
    this.api.logout().subscribe({ next: () => location.assign('/'), error: err => this.error.set(errorMessage(err)) });
  }
}

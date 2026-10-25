import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Api, errorMessage } from './core/api';
import { navigation } from './routes';

@Component({
  selector: 'smp-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <a class="skip" href="#content">Zum Inhalt</a>
    <aside>
      <a class="brand" routerLink="/dashboard"><span class="brand-icon" aria-hidden="true">▧</span><span>Elbracht SMP<small>CONTROL PLANE</small></span></a>
      <nav aria-label="Hauptnavigation">
        @for (item of navigation; track item.path) {
          <a [routerLink]="'/' + item.path" routerLinkActive="active" ariaCurrentWhenActive="page">{{ item.label }}</a>
        }
      </nav>
      <div class="account">
        @if (user()) {
          <strong>{{ user() }}</strong><button (click)="logout()">Abmelden</button>
        } @else {
          <span>Keine aktive Sitzung</span><a class="button primary" [href]="loginUrl()">Anmelden</a>
        }
        @if (error()) { <p role="alert">{{ error() }}</p> }
      </div>
    </aside>
    <main id="content"><div class="notice"><span class="dot"></span> Entwicklungsstand · Echte Messquellen erforderlich · Produktionsanbindung noch ausstehend</div><router-outlet /></main>
  `
})
export class App {
  private readonly api = inject(Api);
  readonly navigation = navigation;
  readonly user = signal<string | null>(null);
  readonly loginUrl = signal('/login');
  readonly error = signal('');
  constructor() {
    this.api.auth().subscribe({ next: value => this.loginUrl.set(value.loginUrl), error: () => {} });
    this.api.me().subscribe({ next: value => this.user.set(value.name), error: () => {} });
  }
  logout() {
    this.api.logout().subscribe({ next: () => location.assign('/'), error: err => this.error.set(errorMessage(err)) });
  }
}

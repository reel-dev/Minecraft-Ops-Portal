import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

@Component({
  imports: [RouterLink],
  template: `<header><p class="eyebrow">MVP-BACKLOG · {{ data['milestone'] }}</p><h1>{{ data['label'] }}</h1></header>
    <section class="panel empty"><h2>Für den nächsten Ausbau vorbereitet</h2>
    <p>Diese Funktion ist noch nicht implementiert. Es werden keine Serverdaten oder Modtreffer simuliert.</p>
    @if (data['path'] === 'players') { <p>„Spieler hinzufügen“ wird die Whitelist verwalten. OP-Rechte erhalten eine eigene Admin-Aktion.</p> }
    @if (data['path'] === 'backups') { <p>Cloud-Sicherung und isolierter Proberestore müssen vor Modänderungen abgenommen werden.</p> }
    @if (data['path'] === 'console' || data['path'] === 'logs') { <p>Minecraft-Antworten über RCON und echte Serverlogs werden als getrennte Quellen angebunden.</p> }
    <a routerLink="/dashboard">Zurück zum Dashboard →</a></section>`
})
export class PlannedFeature { readonly data = inject(ActivatedRoute).snapshot.data; }

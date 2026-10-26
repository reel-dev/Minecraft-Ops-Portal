# Angular-Frontend

Angular 21.2.25 mit Standalone-Komponenten, Router, Signals und typisiertem HttpClient. Node 24.16.0 und TypeScript 5.9.3 sind festgelegt; `npm ci` verwendet die Lockdatei. `npm start` startet ausschließlich das Portal auf localhost, keine Minecraft-Komponenten.

`npm run build` erstellt `dist/portal/browser`, `npm run typecheck` prüft auch Angular-Templates. CI führt beides aus.

- `src/app/core/api.ts`: relativer API-Client, serverseitig ausgestellte CSRF-Token, verständliche Fehler. Keine Tokens im Browser-Storage.
- `src/app/features/dashboard.ts`: Serverprofil, getrennte Statusachsen, Quellen-/Messzeitangaben, Operationsbestätigung und die fünf neuesten Jobs. Status alle 5 Sekunden, Jobübersicht alle 3 Sekunden; laufende/ungeklärte Jobs und nicht abrufbarer Jobstatus sperren neue Aktionen. Abos enden beim Verlassen der Ansicht.
- `src/app/features/jobs.ts`: serverbezogene Jobhistorie mit 20 Einträgen je Seite, Detailroute `/jobs/:jobId`, stabilen Cursor-Links und gesonderter Warnung für den sperrenden Job. Aktualisierung alle 5 Sekunden ohne überlappende Requests; vollständiges Neuladen erhält die URL-Auswahl. Zeitangaben gelten für Europe/Berlin, Status und Hinweise sind auf Deutsch.
- `src/app/features/audit.ts`: serverbezogenes Admin-Audit, Jobfilter über URL und Link aus der Jobdetailansicht, Cursor-Blättern und Aktualisierung alle 5 Sekunden. Andere Rollen sehen einen Berechtigungshinweis; die eigentliche Autorisierung liegt im Backend. Bei Session-/Rechteverlust werden gespeicherte Ereignisse verworfen.
- `src/app/features/inventory.ts`: Adminformular für die 15 M01-Nachweispunkte mit redigiertem Wert, Quelle und UTC-Erfassungszeit; Erfassungs-/Speicherzeit werden in Berlin angezeigt. Keine Vorbelegung mit Nutzerangaben, kein periodisches Neuladen während der Bearbeitung. HTTP 412 lässt die Eingaben stehen und fordert zum erneuten Laden/Prüfen auf. Zugriff aus Sidebar und Dashboard, Änderungen im Audit verlinkt. „Dokumentiert“ wird ausdrücklich von technischer Abnahme unterschieden.
- `src/app/routes.ts`: gemeinsame Spielerroute für Sidebar und Dashboard; weitere MVP-Ansichten mit ausdrücklicher Kennzeichnung als noch nicht implementiert.
- `src/styles.css`: dunkles, responsives Layout nach der echten Bildreferenz, Tastaturfokus, Textstatus und Sprunglink.

Der Login läuft vollständig über Spring Security; das Frontend speichert keine Zugangsdaten. Entwicklungsproxy für `/api`, `/login` und `/oauth2` unter `proxy.conf.json`. Produktion benötigt eine gemeinsame HTTPS-Origin und eine SPA-Fallbackroute auf `index.html`; API-/Authpfade zuerst ans Backend routen.

Es gibt weder Mocktelemetrie noch Mod-Beispieltreffer. Gemeldete Version 26.3 ist als Nutzerangabe sichtbar. Status kann aus dem optionalen lesenden Hostadapter stammen; ohne Konfiguration bleibt er unbekannt. Quelle, Messzeit und feste Fehlercodes erscheinen verständlich auf Deutsch. Echte Hostabnahme, SSE/Logs, Spieler und Backup-/Modaktionen sind ausstehend. Netzwerkfehler lassen die letzte Messzeit sichtbar; daraus keine aktuelle Readiness ableiten. Jobhistorie und Details stammen aus der Portal-Datenbank und werden nach Browserreload neu geladen. Keine Zugangsdaten oder Jobdaten werden dafür in Browser-Storage geschrieben. Bei 401/403 werden zwischengespeicherte Jobdetails verworfen. UNKNOWN bietet keine Wiederholungs-/Entsperraktion.

## Spieleransicht

`/players` zeigt Online-Namen und Messzeit über die serverbezogene Portal-API. Anmeldung, Fehler, nicht verfügbare Quelle und bestätigt leere Listen sind getrennt. Keine UUIDs/OP-Rechte werden geraten. Nur bei zusätzlicher Testfähigkeit werden Operatoren/Admins Whitelist-Aufträge mit Vorschau/Bestätigung angeboten; fehlende Readiness, Jobstatus oder Serversperre verhindern den Request. OP bleibt gesperrt. Jobs zeigen das Spielerziel; Jobs/Audit tragen das Umgebungslabel. [Lesender Abruf](../docs/16-rcon-online-spieler.md), [Whitelist-Testjobs und Browserprüfung](../docs/17-whitelist-testjobs.md).

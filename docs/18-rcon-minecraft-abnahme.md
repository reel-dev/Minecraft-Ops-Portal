# RCON und Whitelist gegen den echten lokalen Minecraft-Testserver

Stand: 25.10.2026. Docker Desktop antwortete beim erneuten lesenden Abgleich wieder; es wurde weder neu gestartet noch zurückgesetzt. Die folgende Prüfung lief ausschließlich auf dem lokalen Kontext `desktop-linux`. **Kein Kontakt zu docker-01 oder dessen produktiver Minecraft-Welt.**

## Ziel und Trennung

Die vorhandene Test-Compose wurde mit dem neuen optionalen [RCON-Override](../infra/test-server/compose.rcon.yml) gestartet. Container, Projekt, gepinntes Image und Testweltvolume blieben gleich. Vor dem Neuerstellen wurden Test-/Server-/Projektlabels, der einzige Weltmount und das Image geprüft. Spielport bleibt `127.0.0.1:25585`; RCON wird für diesen Test zusätzlich nur an `127.0.0.1:25595` veröffentlicht. Die produktive Compose wurde nicht geändert.

Das zufällige Test-RCON-Passwort liegt in einer ignorierten externen Datei und wurde ausschließlich als Serverumgebung sowie Agent-Secret verwendet. Es steht nicht in Git, Angular, dieser Dokumentation oder dem Abnahmenachweis. Die Basis-Compose lässt RCON weiterhin aus; das Override ist ein ausdrücklicher Testschritt. [Offizielle itzg-RCON-Konfiguration](https://docker-minecraft-server.readthedocs.io/en/latest/configuration/server-properties/).

Vorhandener Agentledger und vorhandene Test-Portal-Datenbank wurden weiterverwendet. Vor Beginn waren jeweils drei erfolgreiche Lifecycle-Jobs und keine offenen/ungeklärten Jobs vorhanden. Keine Ledgerlöschung oder Entsperrung. Das Portal migrierte seine Datenbank tatsächlich von Flyway V4 auf V5; alte Einträge blieben erhalten.

## Gefundener und behobener Transportfehler

Der bisherige RCON-Client schickte Befehl und Abschlussanforderung unmittelbar hintereinander. Beim echten Minecraft 26.3 schloss die Verbindung nach der Authentisierung; die Simulatoren hatten diese Annahme nicht aufgedeckt. Der direkte Vergleich mit nacheinander gesendeten Requests funktionierte.

Der Client wartet jetzt auf das erste Antwortpaket des Befehls, bevor er die korrelierte Abschlussanforderung sendet. Weitere Antwortfragmente werden weiterhin gesammelt, bis die Abschlussantwort mit passender ID eintrifft. Gesamtdeadline, Paketgrenzen und Fehlerredaktion bleiben erhalten. Ein Regressionstest verhindert erneutes Pipelining vor der ersten Antwort. Der korrigierte Client und anschließend der authentisierte Agent lieferten die tatsächliche Online-Liste erfolgreich.

## Eindeutige Testdateiquelle

`agent.test-whitelist.source` hat zwei feste Werte:

- `directory` (Standard): bestehender lesender Zugriff auf ein ausdrücklich zugeordnetes Verzeichnis; Dateirechte und Zuordnung sind Betriebsanforderungen.
- `docker-test`: nur im bereits abgesicherten Teststeuerungsmodus. Der Agent prüft erneut die feste Testcontainer-Zuordnung und liest genau `/data/whitelist.json` bzw. `/data/ops.json` aus diesem Container. Die festen Argumentlisten verwenden `docker container exec --user 1000 … /bin/cat`; keine Shell und kein Pfad aus einem HTTP-Request. Ausgabe ist auf 2 MiB und Laufzeit auf drei Sekunden begrenzt. Dateiinhalte gelangen nur in die bestehende interne Nachprüfung.

Damit benötigt der Windows-Testagent keinen zusätzlichen oder möglicherweise falsch zugeordneten Host-Weltpfad. RCON-Port und Dateien gehören zum selben geprüften Testcontainer. Der neue Zugriff ist kein Produktions-Dateiadapter und wird im Docker-01-Lesemodus abgewiesen.

Die Docker-Quelle setzt die regulären, von Minecraft verwalteten Testdateien voraus. Anders als die Verzeichnisquelle bietet der feste `cat`-Aufruf keine atomare NOFOLLOW-Semantik für mögliche Symlinks innerhalb des Containers. Deshalb bleibt diese Quelle auf die vertrauenswürdige isolierte Testwelt begrenzt; sie ist keine Freigabe für beliebige Containerdateien oder Produktionsdaten.

## Echte durchgängige Prüfung

Portalprofile `local,test-server`, expliziter Teststeuerungsadapter und Whitelist-Freischaltung. Agent mit `docker-test-control`, `agent.rcon.mode=test-control`, Loopback-RCON, `agent.test-whitelist.enabled=true` und `source=docker-test`. Die Oberfläche kennzeichnet die eigene Testwelt. Minecraft 26.3, Fabric Loader 0.19.5, echte Readiness und RCON-Antwort wurden unabhängig gelesen.

Baseline: Whitelist leer, OP-Datei leer. Als temporärer Testeintrag wurde der bestehende öffentliche Minecraft-Name `Notch` verwendet; Minecraft löste dessen Profil selbst auf. Es wurden keine UUIDs erfunden und kein Login oder OP vergeben. Die Mutationen liefen ausschließlich über die normalen Portal-Operationsendpunkte mit Session, CSRF und Idempotency-Key:

| Aktion | Job-ID | Ergebnis | Tatsächliche Nachprüfung (UTC) |
|---|---|---|---|
| WHITELIST_ADD | `f7bd7ea0-8c10-4f2b-abe7-7d5912c1b921` | SUCCEEDED | 17:48:24Z: Eintrag mit aufgelöster UUID in Minecraft-Whitelist bestätigt |
| WHITELIST_REMOVE | `bedfd01f-7f46-4a57-bfb7-d093789bef04` | SUCCEEDED | 17:48:28Z: Eintrag wieder entfernt, Whitelist leer |

Jede Portal-Anfrage wurde mit demselben Idempotency-Key wiederholt und lieferte dieselbe Job-ID. Pro Job wurden drei echte Auditübergänge geprüft. OP-Datei blieb unverändert leer. Die beiden neuen Jobs ergänzen die drei bereits abgenommenen Lifecycle-Jobs.

Agent und Portal wurden danach mit denselben Dateien/Datenbanken neu gestartet. Eine erneute direkte Agent-Anfrage mit der abgeschlossenen ADD-ID lieferte den gespeicherten Erfolg; die Whitelist blieb trotzdem leer. Die alte Aktion wurde somit nicht erneut ausgeführt. Portalhistorie: fünf erfolgreiche Jobs mit erhaltenen Spielerzielen, 15 Audit-Einträge, kein sperrender Job. Maschinenlesbarer [Abnahmenachweis](../references/rcon-test-server-acceptance.json).

Im Browser nach Prozessneustart geprüft: Anmeldung, echte RCON-Online-Liste mit Messzeit, fünf gespeicherte Jobs, Whitelist-Jobdetail mit Zielname und Reload. Auf dem Testserver war zum Messzeitpunkt niemand online. Dies ist die echte Testanbindung, nicht der frühere API-Simulator.

## Prüfstand und Grenzen

57 Portaltests zuletzt erfolgreich, keine Portalquelländerung in diesem Schritt. Agent-Paketbuild: **60 Tests erfolgreich, keine übersprungen**, darunter vier ausdrücklich aktivierte lesende Live-Proben für Docker/Minecraft/RCON/Testdateien. Ohne Live-Flags werden diese vier weiterhin übersprungen. Die durchgängigen Portal-Whitelist-Mutationen wurden zusätzlich manuell gegen den echten Testserver geprüft und sind nicht Teil der automatisch laufenden Standard-CI.

Für die vier lesenden Proben nach separat eingerichteter Test-Compose:

```powershell
$env:SMP_LIVE_TEST = 'true'
$env:SMP_LIVE_RCON = 'true'
$env:SMP_LIVE_RCON_PASSWORD_FILE = '<absolute externe Test-Secret-Datei>'
# aus agent/
../backend/mvnw.cmd verify
```

Das erweitert die frühere Simulatorabnahme um echte RCON-/Whitelist-Nachweise. OP-Administration, freie Konsole mit Rollenmodell, separater Live-Logstrom, vollständige UUID-/Offline-Fehleranzeige sowie konsistentes Cloudbackup/Proberestore bleiben offen. Produktions-RCON und produktive Steuerung sind nicht freigegeben. Das Containerdeployment des produktiven Leseagenten ist weiterhin eine eigene noch nicht abgeschlossene Abnahme.

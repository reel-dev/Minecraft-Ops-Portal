# Elbracht SMP Control

Projektstart für Renés Minecraft-Homelab. Stand: 25.10.2026.

## Ziel

Eine eigene Control Plane mit Angular und Spring Boot für den bestehenden Minecraft-Server auf Proxmox/Docker mit `itzg/minecraft-server`. Status, Betrieb, Konsole, Spieler, Mods, Konfiguration, Backups, Logs und Monitoring werden in einer übersichtlichen Anwendung gebündelt.

**Status:** Lokal ausführbares Angular-/Spring-Boot-Grundgerüst mit Sessionanmeldung, serverbezogenen Rollen, Status-API, persistenten Jobs, Idempotenz, Serversperre und Audit implementiert. Ein optionaler lesender HTTP-Statusadapter ist gegen einen lokalen Testdienst geprüft; Standard bleibt ohne Hostverbindung. Keine echten Servermessungen oder Serveraktionen wurden ausgeführt. Bestehende Infrastruktur wurde nicht verändert. [Umsetzungsstand und offene Abnahmen](docs/10-implementierungsstand.md).

## Lokal starten

Voraussetzungen: Node **24.16.0**, JDK **21** (auch Java 25 unterstützt), Internetzugriff für den ersten Dependency-Download. Maven **3.9.16** wird über den Wrapper geladen. Keine Minecraft-Zugangsdaten erforderlich.

Backend in PowerShell:

```powershell
cd backend
# JAVA_HOME muss auf ein vollständiges JDK zeigen.
$credential = Get-Credential -UserName 'local-admin' -Message 'Lokales Portalpasswort festlegen'
$env:PORTAL_LOCAL_PASSWORD = $credential.GetNetworkCredential().Password
./mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Frontend in einem zweiten Terminal:

```powershell
cd frontend
npm ci
npm start
```

[Portal öffnen](http://localhost:4200), „Anmelden“ wählen und mit `local-admin` und dem selbst gesetzten Passwort anmelden. Das Backend bindet nur `127.0.0.1:8080`; Angular leitet API und Anmeldung weiter. Ohne explizites lokales Passwort startet das Backend nicht. Der lokale Admin gilt nur außerhalb des Produktionsprofils. Nach Ende der Sitzung die Passwortvariable entfernen: `Remove-Item Env:PORTAL_LOCAL_PASSWORD`.

Die lokale Portal-Datenbank liegt unter `backend/data/` und enthält keine Minecraft-Welt. Start/Stop/Restart bleiben deaktiviert; direkte autorisierte API-Versuche liefern 503. „Status unbekannt“ ist hier der korrekte Zustand.

## Prüfen

```powershell
cd backend
./mvnw.cmd verify
cd ../frontend
npm run typecheck
npm run build
```

40 Backendtests decken Sicherheits-/Jobabläufe, Bestandsaufnahme mit Revisionsschutz, serverbezogene Jobhistorie, Admin-Audit und Cursor-Blättern sowie den lesenden Agenttransport und dessen Konfiguration ab. CI ist für Backend mit Java 21 sowie Angular-Build und Typen eingerichtet. Produktionsprofil, PostgreSQL-/OIDC-Konfiguration und verbleibende Integrationen: [Backend](backend/README.md), [Frontend](frontend/README.md), [Infrastruktur](infra/README.md).

Die Seite **Serverjobs** zeigt gespeicherte Jobs und ihre Details auch nach Browser-Neuladen. UNKNOWN-Jobs bleiben sichtbar und sperren weitere Aktionen; das Portal bietet keine automatische Wiederholung oder ungeprüfte Entsperrung. Neue Installationen zeigen eine echte Leerliste, keine Beispieljobs.

Unter **Audit-Protokoll** sehen Admins Serverjobs, ihre tatsächlichen Statusübergänge und Änderungen der Bestandsaufnahme. Jobübergänge lassen sich nach Job filtern. Der Zugriff wird serverseitig pro Server geprüft. Dieses Protokoll enthält keine Freitextbegründungen, Bestandswerte, Quellen oder Idempotency-Keys; es ist noch kein vollständiges Sicherheitsprotokoll für Logins, abgewiesene Requests oder sämtliche späteren Integrationen.

Die Seite **Bestandsaufnahme** erfasst die 15 Punkte der M01-Vorlage mit redigiertem Wert, Quelle und UTC-Erfassungszeit. Portalnutzer, Speicherzeit und Revision werden ergänzt. Gleichzeitiges Überschreiben wird mit `If-Match`/HTTP 412 verhindert. Auch eine ausgefüllte Liste bestätigt keine vollständige technische Abnahme und aktiviert keine Hostverbindung. Das tatsächliche Minecraft-/Fabric-Profil wird dadurch nicht automatisch geändert.

## Einstieg

- [Kontext und Herkunft](docs/01-kontext.md)
- [Architektur und Entscheidungen](docs/02-architektur.md)
- [Sicherheit und Rollen](docs/03-sicherheit-rollen.md)
- [Anforderungen und MVP-Backlog](docs/04-anforderungen-backlog.md)
- [API-Skizze](api/API-SKIZZE.md)
- [Backup und Restore](docs/05-backups.md)
- [Mod-Management](docs/06-mod-management.md)
- [UI/UX und Screenshots](docs/07-ui-ux.md)
- [Nächste Schritte](docs/08-naechste-schritte.md)
- [Quellen und Prüfstand](docs/09-quellen.md)
- [Anweisungen für das spätere Work-Projekt](PROJEKTANWEISUNGEN.md)
- [Implementierungsstand und Abgrenzung](docs/10-implementierungsstand.md)
- [Lesender Host-Agent-Vertrag](docs/11-host-agent-vertrag.md)
- [Vorlage für die Bestandsaufnahme](docs/12-bestandsaufnahme.md)

## Struktur

```text
frontend/       Angular-Anwendung, API-Client, Dashboard, Ausbaurouten
backend/        Spring Boot, Security, Serverprofil, Jobs, Audit, Flyway
infra/          Deployment-Leitplanken, noch keine Hostanbindung
api/            Entwurf des API-Vertrags
docs/           Fachkonzept, Architektur und Arbeitsplan
references/     Originalreferenz und abrufbarer Chat-Export
```

## Verbindliche Leitplanken

- Nutzerangabe zum Ist-Stand: Minecraft `26.3`, Fabric. Exakte Minecraft- und Fabric-Loader-Versionen vor Implementierung am laufenden Server prüfen; keine automatische Korrektur oder Versionserweiterung.
- CurseForge-Key ausschließlich serverseitig; echte API-Daten und passende Dateien, keine erfundenen Mod-Ergebnisse.
- Kompatibilität bedeutet passende Metadaten und geprüfte Abhängigkeiten, keine Garantie für konfliktfreien Betrieb.
- Minecraft-Kommandos laufen über RCON. Live-Serverlogs sind ein eigener Datenstrom.
- Keine generische Betriebssystem-Shell. Privilegierte Aktionen sind begrenzte Backend-Operationen.
- Cloud-Backups mit 7-Tage-Retention und erprobtem Restore.
- Referenzbilder bestimmen die Gestaltung; ihre Zahlen, Domains, Spielerversionen und Modlisten sind keine Bestandsaufnahme.

## In ChatGPT/Work übernehmen

1. Ein Projekt namens **Elbracht SMP Control** anlegen.
2. Dieses Paket entpacken und die Dokumentation als Projektquellen hinzufügen bzw. den Ordner als lokalen Projektordner anbinden, soweit die Oberfläche dies unterstützt.
3. `PROJEKTANWEISUNGEN.md` als Projektanweisung übernehmen.
4. `references/generated-image.png` als visuelle Quelle hinzufügen. Das zweite Bild `elbracht_smp_server_dashboard.png` muss noch aus dem Ursprungs-Chat exportiert oder erneut angehängt werden.
5. Mit dem Bestandsabgleich aus `docs/08-naechste-schritte.md` beginnen.

Der über die Referenz abrufbare Chat enthält fünf Turns und meldet keine weitere Seite. Frühere, hier nicht gelieferte Nachrichten können daher nicht als vollständig archiviert gelten. Der gesamte verfügbare Export liegt unter `references/homelab-export.json`; die aktuelle Nutzeranforderung ist in `docs/01-kontext.md` aufgenommen.

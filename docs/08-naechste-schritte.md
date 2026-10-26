# Nächste Schritte

Aktueller Stand: 26.10.2026. Verbindliche Übersicht: [Vollendung – Arbeitsstand](19-vollendung-arbeitsstand.md). OP, Konsole/SSE, Eigenschaften, lokale vollständige Backups, Restore mit Rückweg, Modpläne einschließlich explizitem Import und Containerhistorie sind inzwischen implementiert und separat am isolierten Testziel geprüft. Der tägliche Backup-Zeitplan hat einen echten Snapshot erzeugt und einen Portal-Neustart ohne zweiten Tagesauftrag bestanden; danach wieder ausgeschaltet. [Zeitplan-Abnahme](23-backup-zeitplan-abnahme.md).

## Jetzt fortsetzen

1. Verschlüsseltes externes Backupziel als ausdrücklich konfigurierten Adapter vorbereiten; ohne Ziel/Zugangsdaten weiterhin als nicht eingerichtet anzeigen. Die [lokale siebentägige Aufbewahrung](24-aufbewahrung-abnahme.md) mit Schutz der letzten erfolgreichen Sicherung, Admin-Vorschau und tatsächlicher synthetischer Prune-/Restoreprobe ist umgesetzt. Cloud-Replikation und die Aufbewahrung am externen Ziel fehlen weiterhin.
2. Konsistente Portal-Datenbanksicherung und gesondertes Secret-/Entschlüsselungs-Wiederanlaufpaket ergänzen und lokal wiederherstellen.
3. Produktionsreife Bereitstellung mit PostgreSQL, OIDC-Erstadmin, TLS und festen Hostgrenzen vorbereiten und gegen isolierte Fixtures prüfen. Keine Installation oder Mutation auf `docker-01` ohne konkreten Betriebsauftrag.
4. Externe Monitoring-/Logkonfiguration vorbereiten und alle Fachansichten, Fehlerfälle und M01–M14 abschließend abgleichen. CurseForge bleibt ohne lokal hinterlegten Schlüssel unkonfiguriert, TPS/MSPT und Karte ohne bestätigte Quelle nicht verfügbar.

## Historische Planung vom 25.10.2026

Die folgenden Angaben beschreiben den damaligen Stand. Gemeinsame Produktbasis ist dieses Angular-/Spring-Boot-Repository. Der zweite Projektchat wurde anhand seines tatsächlichen Quellstands abgeglichen; kein zweites Verwaltungstool wurde auf docker-01 installiert. [Quellabgleich](15-abgleich-projektchats.md).

### Damals abgeschlossen

- Lokales Portal mit Anmeldung, serverbezogenen Rollen, persistenten Jobs, gemeinsamer Serversperre, Idempotenz, Historie und Audit.
- Start/Stop/Neustart am getrennten lokalen Minecraft-Testserver abgenommen.
- RCON-Online-Liste und Whitelist hinzufügen/entfernen gegen denselben echten Minecraft-Testserver abgenommen. Serverdateien bestätigen die Änderungen; Testeintrag danach entfernt, OP unverändert.
- Agent- und Portal-Prozessneustart geprüft: alte Aktion nicht erneut ausgeführt, fünf echte Testjobs und 15 Audit-Einträge erhalten. [Aktuelle Abnahme](18-rcon-minecraft-abnahme.md).
- Docker Desktop antwortet wieder; für diese Abnahme wurde kein Desktop-Neustart oder Reset vorgenommen.
- Produktionsangaben und erfolgreiches Proxmox-VM-Backup dokumentiert. Gesamte Betriebs-/Restore-Abnahme weiterhin offen; [Bestandsaufnahme](12-bestandsaufnahme.md).

Prüfstand: 57 Portaltests und 60 Agenttests erfolgreich, darunter vier ausdrücklich aktivierte lesende Live-Proben. Diese vier bleiben im Standardlauf übersprungen. Frontend-Build und Typprüfung erfolgreich; aktuelle echte Testanbindung im Browser geprüft. Produktionsserver nicht kontaktiert oder geändert.

### Damals nächste Inkremente

1. **OP-Verwaltung ausschließlich für Admins:** getrennte Operationsarten und Fähigkeit, persistenter Job mit Spielerziel, gemeinsame Sperre/Audit, tatsächliche Bestätigung durch Serverdateien. Operator/Viewer müssen auch bei direkten API-Aufrufen abgewiesen werden. Zunächst nur am isolierten Testanschluss abnehmen.
2. **Konsole und eigener Logstrom (M05):** erlaubte Kommandos anhand der Portalrolle begrenzen und eindeutig von Serverlogs trennen. Keine generische Shell; Reconnect, Frische, Puffer und Sessionablauf testen. Kein freier RCON-Umweg an den Job-/Auditregeln vorbei.
3. **Container und produktiver Leseanschluss:** die vorbereiteten Agent-/Proxyimages tatsächlich bauen und Socket-/Netzgrenzen prüfen. Danach Docker-01-Anschluss mit Dienstkonto, Tokenablage und sicherem Transport vorbereiten. Ein Proxmox-Administratorzugang ist für normale Portalaktionen nicht vorgesehen. Produktion zunächst ausschließlich lesend; keine Testlabels oder Teststeuerung auf den bestehenden Container übertragen.
4. **Datensicherheit (M07/M08):** konsistente Weltsicherung, verschlüsseltes externes Backupziel, Retention und isolierter Proberestore. Das erfolgreiche lokale Proxmox-Backup belegt weder Cloudkopie noch Minecraft-Weltkonsistenz oder Restore.
5. **Danach Eigenschaften, CurseForge und Monitoring:** echte Quellen und feste Versionen, keine erfundenen Mod-/Messwerte. Keine Modmutation vor gesicherter Wiederherstellbarkeit. Karte erst nach bestätigter Browserkartenquelle.

## Testbetrieb und Grenzen

Die normale Portal-Voreinstellung bleibt ohne Hostverbindung. Test-Whitelist benötigt ausdrückliche Freischaltungen in Portal und Agent sowie eine passende RCON-Zuordnung; [Gates](17-whitelist-testjobs.md), [neue Docker-Testdateiquelle und RCON-Override](18-rcon-minecraft-abnahme.md). OP bleibt noch gesperrt. Keine Ledger-/Datenbanklöschung zum Umgehen ungeklärter Jobs.

Konten, Serverrechte und Hosting bleiben getrennt: Mitverwalter benötigen kein eigenes Hosting. Selfservice-Registrierung weiterer Hostinganschlüsse, Einladungen und Mehrmandantenbetrieb sind noch nicht implementiert; [Produktmodell](14-konten-und-hosting.md).

Produktive OIDC-Anmeldung, PostgreSQL-Betrieb, TLS/Proxygrenzen, Backupziel und Monitoringquellen müssen vor Deployment konkret geprüft werden. Genau eine Portal- und Agentinstanz pro zugeordneter Steuerung, bis ein gesondertes Lease-/Recovery-Konzept umgesetzt ist.

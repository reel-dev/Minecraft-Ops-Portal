# Elbracht SMP Control

Projektstart für Renés Minecraft-Homelab. Stand: 24.10.2026.

## Ziel

Eine eigene Control Plane mit Angular und Spring Boot für den bestehenden Minecraft-Server auf Proxmox/Docker mit `itzg/minecraft-server`. Status, Betrieb, Konsole, Spieler, Mods, Konfiguration, Backups, Logs und Monitoring werden in einer übersichtlichen Anwendung gebündelt.

**Status:** Konzept und Arbeitsstruktur vorbereitet; keine produktive Anwendung implementiert und keine Infrastruktur verändert. Das Paket ist lokal erstellt. Ein neues ChatGPT/Work-Projekt konnte mit den verfügbaren Werkzeugen nicht angelegt werden. Die Browser-Verbindung scheiterte ebenfalls. Dieses Paket ist zur Übernahme in ein solches Projekt vorbereitet.

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

## Struktur

```text
frontend/       Angular-Arbeitsbereich, derzeit Planungsnotiz
backend/        Spring-Boot-Arbeitsbereich, derzeit Planungsnotiz
infra/          Docker, Monitoring und Backup, derzeit Planungsnotiz
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

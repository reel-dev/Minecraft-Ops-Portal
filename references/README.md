# Referenzen

- server-compose.supplied.yml: vom Nutzer gelieferte Produktionskonfiguration; kein Laufzeitnachweis, nicht zum lokalen Start verwenden.
- docker-01-inspect.supplied.txt: übermittelte lesende Containerabfrage mit Zustand und Image-Konfigurations-ID; kein Minecraft-Readiness- oder Versionsnachweis.
- docker-01-versions.supplied.txt: übermittelte Startmeldungen des produktiven Containers mit Minecraft 26.3 und Fabric Loader 0.19.5; Logdatum und Zeitzone fehlen.
- docker-01-mounts.supplied.txt: übermittelte Mountabfrage; schreibbarer Bind-Mount auf /data, Hostquelle nicht abgefragt.
- docker-01-backup-screen.supplied.md: Auswertung der Proxmox-Screenshots: VM 100, Juni-Snapshots, leere Backupansicht; kein aktuelles geprüftes Backup nachgewiesen.
- docker-01-disk-usage.supplied.txt: übermittelte Gastbelegung des Root-Dateisystems (21G benutzt); keine garantierte Backupgrößenschätzung.
- docker-01-backup-success.supplied.md: Proxmox meldet erfolgreiches Backup von VM 100 (TASK OK, Archiv 11,43 GB); Restore und Minecraft-Datenumfang noch ungeprüft.
- docker-01-data-filesystem.supplied.txt: Minecraft-Bind-Mountquelle liegt auf dem Root-Dateisystem /dev/sda2 der VM; kein Restore- oder Weltkonsistenznachweis.
- docker-01-host-runtime.supplied.txt: Java auf dem Host im PATH nicht verfügbar; Spielport 25565 über IPv4-/IPv6-Wildcard veröffentlicht.
- test-server-acceptance.json: tatsächliche lokale Portaljobs STOP/START/RESTART am isolierten Testcontainer; siehe docs/13-testserver-abnahme.md.
- rcon-test-server-acceptance.json: echte lokale RCON-/Whitelist-Abnahme mit Serverdateiprüfung, Portal-Idempotenz und Prozessneustart; siehe docs/18-rcon-minecraft-abnahme.md. Kein Simulator- oder Produktionsnachweis.

- generated-image.png: tatsächlicher verfügbarer Anhang aus Homelab, unverändert übernommen; primäre visuelle Referenz.
- homelab-export.json: gesamte über read_thread gelieferte Referenzantwort. Sie enthält nur die vom Dienst gelieferten fünf Turns, kein behauptetes Vollarchiv.
- elbracht_smp_server_dashboard.png: angefordert, aber nicht im abrufbaren Export verfügbar. Fehlend; noch hinzuzufügen.

Ursprüngliche /mnt/data-Pfade sind in dieser Windows-Sitzung keine verfügbaren Dateien. Der erste Screenshot wurde über den bereitgestellten Anhang gesichert. Keinen Screenshot neu erzeugen oder umbenennen, um einen fehlenden Originalanhang vorzutäuschen.

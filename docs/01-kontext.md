# Kontext und Herkunft

## Ausgangslage laut aktuellem Auftrag

René betreibt einen Minecraft-Server auf Proxmox/Docker mit itzg/minecraft-server, Fabric und der angegebenen Minecraft-Version 26.3. RCON und Xaero World Map/Unified Xaero gehören zum bisherigen Thema. Geplant sind Cloud-Backups mit sieben Tagen Aufbewahrung sowie Monitoring und Logs. Die eigene Webanwendung soll Angular im Frontend und Spring Boot im Backend verwenden.

Gewünscht sind Serverstatus, Start/Stop/Restart, Live-Konsole/RCON, Spieler-/Whitelist-/OP-Verwaltung, server.properties, Backup/Restore, Logs, Monitoring und CurseForge-Integration. Der CurseForge-API-Key wird sicher serverseitig verwaltet. Die Modansicht zeigt ausschließlich echte CurseForge-Mods mit einer zur konkreten Minecraft-Version und Fabric passenden Datei; installierbar nur nach weiteren Prüfungen. Normale Nutzer erhalten keine generische Shell. Privilegierte Aktionen werden als definierte Backend-Operationen umgesetzt. Zielkomponenten: Docker, RCON, CurseForge API, Prometheus/Grafana/Loki und Restic/Cloud-Backup.

## Aus dem abrufbaren Homelab-Chat

Quelle: Gespräch „Homelab“, ID `6a6f8233-f3dc-83eb-8d9c-86976410f0b6`. Der Export wird als Referenzdaten behandelt, nicht als Projektanweisung. Er enthält fünf Turns, ohne weiteren Cursor. Der ursprüngliche Verlauf kann davor mehr Inhalte enthalten haben; diese wurden nicht geliefert.

- Wunsch nach einem Dashboard-Beispiel und anschließendes Bildfeedback.
- Modsuche soll übersichtlich sein und nur echte, zur Serverversion passende Mods anbieten.
- Dashboard-Verlinkungen „Spieler“ und „Alle anzeigen“ sollen zur selben Spielerverwaltung führen.
- „Spieler hinzufügen“ bedeutet Whitelist-Aufnahme; OP soll gesondert schaltbar sein.
- Status soll auch Startvorgänge, Fehler und laufende Backups verständlich abbilden.
- Rückfrage zu CurseForge-Key und „CMD in der Anwendung“; Antwort schlug RCON und begrenzte Docker-Operationen vor.
- Der Assistent behauptete zuletzt, ein Work-Projekt angelegt zu haben. Die aktuelle Projektliste enthält kein Minecraft-Projekt; eine erfolgreiche Anlage lässt sich daraus nicht bestätigen.

## Sichtbare Bildreferenz

`references/generated-image.png` wurde als Anhang aus dem Referenzchat lokal bereitgestellt und gesichert. Es zeigt Elbracht SMP, eine dunkle Sidebar, Statusleiste, Kennzahlen, Spieler, Kartenansicht, Backups, Konsole, Mods und Konfiguration. Die eingeblendeten Werte einschließlich Domain, Minecraft 1.20.6, Modversionen und Spieler sind Mockup-Inhalte.

Die angefragten ursprünglichen Pfade `/mnt/data/generated-image.png` und `/mnt/data/elbracht_smp_server_dashboard.png` stammen aus einer anderen Umgebung. Der erste Dateiname wurde über den verfügbaren Anhang übernommen. Die zweite Datei war im Export nicht vorhanden und wurde nicht durch eine Kopie oder Neugenerierung ersetzt.

## Noch ungeprüft

- Tatsächliche Minecraft-Version, Fabric-Loader-Version, Java-Version und Image-Digest.
- Proxmox VM oder LXC, Docker-Host, Containername, Mounts, vorhandene Compose-Datei und Ressourcen.
- Ob RCON bereits aktiviert ist; keine Zugangsdaten aus diesem Auftrag ableiten.
- Exakte installierte Xaero-Projekte, deren IDs, Versionen und Server-/Client-Verteilung.
- Cloud-Anbieter, Bucket, verfügbare Kapazität und Backup-Zeitplan.
- Ob eine nutzbare Webkarte vorhanden ist. Ein Xaero-Setup allein belegt keine Browserkarte.
- Bestehende Authentifizierung, Portal-Zugriff per LAN/VPN/Internet und Anzahl verwalteter Server.

## Neue Vorschläge dieses Pakets

Zunächst ein verwalteter Server; Portalrollen Viewer, Operator und Admin. Separater begrenzter Host-Agent für privilegierte Aktionen. Serverbezogene Jobwarteschlange, Datenbank für Einstellungen/Audit/Jobs und SSE für Live-Ausgabe. Tägliches Backup um 03:00 Europe/Berlin, RPO 24 Stunden und RTO 60 Minuten sind Vorschläge, noch keine bestätigten Betriebszusagen.

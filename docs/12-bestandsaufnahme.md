# M01: Bestandsaufnahme und Nachweise

Produktiver Laufzeitstand **teilweise anhand übermittelter Terminalausgaben erfasst**. Die [Startmeldungen des produktiven Containers](../references/docker-01-versions.supplied.txt) belegen Minecraft `26.3` mit Fabric Loader `0.19.5` für die gezeigten Starts. Logdatum/Zeitzone und aktuelle Minecraft-Readiness sind daraus nicht ersichtlich. Keine direkte Hostverbindung durch das Portal und keine technische Gesamtabnahme.

Der Nutzer hat am 25.10.2026 eine [Docker-Inspect-Ausgabe von docker-01](../references/docker-01-inspect.supplied.txt) übermittelt: Container `/minecraft-server`, Zustand `running`, Image-Konfigurations-ID `sha256:5a2ab74ae179c11e2dbbce67f307cdcf2c1257b3abb0f654d3a310b3d8ea57da`. Exakte UTC-Erfassungszeit fehlt. Diese Ausgabe bestätigt weder Minecraft-Readiness noch Laufzeitversionen; die Image-Konfigurations-ID ist kein Repository-Manifest-Digest. Keine direkte Hostverbindung durch das Portal.

Dokumentierte Nutzerangaben: Homelab → Proxmox-Instanz `docker-01` → Docker, Container `minecraft-server`. Die [gelieferte Compose](../references/server-compose.supplied.yml) deklariert Fabric Loader `0.19.5`, Image `stable`, `./data:/data` und 8G JVM-Speicher. Die nachgereichten [Proxmox-Screenshots](../references/docker-01-backup-screen.supplied.md) zeigen docker-01 als VM 100 auf Knoten pve; drei Snapshots vom 27./28.06.2026 und eine leere Backupansicht. Eine aktuelle geprüfte Sicherung ist nicht nachgewiesen. Die separate [lokale Testserver-Abnahme](13-testserver-abnahme.md) überträgt keine Nachweise auf Produktion.

Für den nächsten Abgleich liegt eine [lesende Bestandsprüfung](../infra/docker-01/README.md) bereit. Sie wurde nicht auf docker-01 ausgeführt und benötigt keine Änderung der bestehenden Compose-Datei.

## Erfassung im Portal

Die Seite `/inventory` bildet diese 15 Nachweispunkte ab. Nur Admins des jeweiligen Servers dürfen lesen oder schreiben. Jeder Eintrag benötigt einen redigierten Wert, eine Quelle und den tatsächlichen UTC-Zeitpunkt der Quellenerfassung. Der Server ergänzt Portalnutzer, Speicherzeit und Revision. Keine Quellen-URLs werden abgerufen und keine Hostverbindungen hergestellt.

Anfangszustand: alle Felder offen. Eine gespeicherte Angabe heißt **dokumentiert**, nicht automatisch technisch bestätigt. Auch 15/15 dokumentierte Angaben setzen `managed_server.verified_at` nicht und ändern weder die gemeldete Minecraft-Version noch den Loader. Die spätere technische Gesamtabnahme bleibt ein eigener Arbeitsschritt.

Bei gleichzeitiger Bearbeitung verhindert eine veraltete Revision das Speichern (HTTP 412). Eingaben bleiben im Formular stehen; nach bewusstem Neuladen müssen sie mit dem aktuellen Stand abgeglichen werden. Neu laden und Abbrechen verwerfen ungespeicherte Eingaben. Es gibt kein automatisches Überschreiben oder automatisches Neuladen im Formular.

Änderungen erzeugen Audit-Metadaten mit Feld und Revision, jedoch ohne Wert/Quelle. Frühere Werte werden durch Bearbeitung ersetzt; das Audit ist kein Archiv alter Nachweisdateien oder -inhalte. Es gibt derzeit keine Löschschnittstelle. Nachweisdateien selbst bleiben in ihrer dokumentierten internen Ablage.

## Nachweistabelle

Keine Passwörter, API-Keys, vollständigen Secretdateien oder unredigierten Compose-Exporte eintragen. Adressen und Weltpfade nur in einem passenden internen Betriebsdokument festhalten; das Git-Repository kann öffentlich werden.

| Gegenstand | Nachgewiesener Wert | Quelle | Erfasst am (UTC) | Geprüft durch |
|---|---|---|---|---|
| Proxmox VM/LXC und Docker-Host-Zuordnung | VM 100 (docker-01), Knoten pve laut Proxmox-Ansicht; Dockercontainer laut Terminalausgaben | references/docker-01-backup-screen.supplied.md und Inspect-Ausgabe | Aufnahmezeit unbekannt; übermittelt 25.10.2026 | Nutzer liefert Screenshots; hier ausgewertet |
| Zielcontainer und eindeutige Serverzuordnung | `/minecraft-server`, Docker-Zustand `running` auf docker-01 laut übermittelter Ausgabe; weitere Zuordnung offen | references/docker-01-inspect.supplied.txt | nicht mitgeliefert; übermittelt 25.10.2026 | Nutzer führt Befehl aus; Ausgabe hier ausgewertet |
| itzg-Image mit Digest | Image-Konfigurations-ID erfasst, siehe oben; Repository-Manifest-Digest offen | references/docker-01-inspect.supplied.txt | nicht mitgeliefert; übermittelt 25.10.2026 | Ausgabe ausgewertet; kein Registry-Abgleich |
| Tatsächliche Minecraft-Version | `26.3` laut Startmeldungen des Containers | references/docker-01-versions.supplied.txt | Logdatum/Zeitzone fehlen; übermittelt 25.10.2026 | Nutzer liest Logs; Ausgabe hier ausgewertet |
| Tatsächlicher Loader und Loader-Version | Fabric Loader `0.19.5` laut Startmeldungen | references/docker-01-versions.supplied.txt | Logdatum/Zeitzone fehlen; übermittelt 25.10.2026 | Nutzer liest Logs; Ausgabe hier ausgewertet |
| Java-Runtime des Minecraft-Containers | offen | offen | offen | offen |
| Datenvolume und Mountart, ohne Geheimnisse | Schreibbarer Bind-Mount auf `/data`; dessen Quelle liegt auf `/dev/sda2`, Root-Dateisystem der VM. Absoluter Quellpfad bewusst nicht erfasst | references/docker-01-mounts.supplied.txt und references/docker-01-data-filesystem.supplied.txt | nicht mitgeliefert; übermittelt 25.10.2026 | Nutzer führt Inspect/df aus; Ausgabe hier ausgewertet |
| Bestehende Quelle für server.properties / Umgebungswerte | offen | offen | offen | offen |
| RCON-Aktivierung, interne Erreichbarkeit, Secretablage | offen | offen | offen | offen |
| Existierende Readiness-Prüfung | offen | offen | offen | offen |
| Installierter Modbestand mit Dateihashes | offen | offen | offen | offen |
| Xaero-Projekte und Server-/Clientverteilung | offen | offen | offen | offen |
| Letzte erfolgreiche Sicherung und Integritätsnachweis | Proxmox-Backup VM 100 mit TASK OK, 11,43 GB Archiv, 127 s; zuvor Ziel local gewählt. Datenumfang/Weltkonsistenz, externe Kopie und Restore noch ungeprüft | references/docker-01-backup-success.supplied.md | Abschlussanzeige 2026-10-25 11:22:09 ohne bestätigte Zeitzone | Nutzer führt Backup aus; Abschluss-Screenshot hier ausgewertet |
| Isolierter Testserver und separate Testdaten | offen | offen | offen | offen |
| Portalzugriff LAN/VPN/Internet und OIDC-Provider | offen | offen | offen | offen |

## Abnahme vor einer Hostverbindung

1. Redigierte vorhandene Compose-/Containerinformationen und Start-/Versionsnachweise abgleichen; Referenzbild zählt nicht als Nachweis.
2. Container und Datenvolume eindeutig zuordnen; tatsächliche Version/Loader übernehmen, ohne automatisch benachbarte Versionen auszuwählen.
3. Bestehende Sicherung samt letzter erfolgreicher Prüfung dokumentieren.
4. Getrennten Testserver bestätigen, der keine produktiven Weltmounts oder Secrets übernimmt.
5. Den lesenden Agentvertrag gegen diesen Testserver prüfen: fehlendes Token, fremde Server-ID, fehlende Readiness, veraltete Messung, Offline und Timeout.
6. Erst nach dokumentierter Prüfung eine konkrete Betriebsintegration planen. Start/Stop/Restart am bestehenden Server sind durch diese Vorlage nicht freigegeben.

`managed_server.verified_at` bleibt bis zur vollständigen Prüfung leer. Der lesende Adapter setzt diesen Wert nicht automatisch; eine einzelne Statusantwort belegt weder Mounts noch Backupqualität.

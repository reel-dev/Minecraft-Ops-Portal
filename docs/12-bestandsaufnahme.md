# M01: Bestandsaufnahme und Nachweise

Noch **nicht erhoben**. Diese Vorlage ist keine Bestätigung des Ist-Zustands. Minecraft `26.3` und Fabric bleiben Nutzerangaben, bis unabhängige Nachweise vorliegen.

## Erfassung im Portal

Die Seite `/inventory` bildet diese 15 Nachweispunkte ab. Nur Admins des jeweiligen Servers dürfen lesen oder schreiben. Jeder Eintrag benötigt einen redigierten Wert, eine Quelle und den tatsächlichen UTC-Zeitpunkt der Quellenerfassung. Der Server ergänzt Portalnutzer, Speicherzeit und Revision. Keine Quellen-URLs werden abgerufen und keine Hostverbindungen hergestellt.

Anfangszustand: alle Felder offen. Eine gespeicherte Angabe heißt **dokumentiert**, nicht automatisch technisch bestätigt. Auch 15/15 dokumentierte Angaben setzen `managed_server.verified_at` nicht und ändern weder die gemeldete Minecraft-Version noch den Loader. Die spätere technische Gesamtabnahme bleibt ein eigener Arbeitsschritt.

Bei gleichzeitiger Bearbeitung verhindert eine veraltete Revision das Speichern (HTTP 412). Eingaben bleiben im Formular stehen; nach bewusstem Neuladen müssen sie mit dem aktuellen Stand abgeglichen werden. Neu laden und Abbrechen verwerfen ungespeicherte Eingaben. Es gibt kein automatisches Überschreiben oder automatisches Neuladen im Formular.

Änderungen erzeugen Audit-Metadaten mit Feld und Revision, jedoch ohne Wert/Quelle. Frühere Werte werden durch Bearbeitung ersetzt; das Audit ist kein Archiv alter Nachweisdateien oder -inhalte. Es gibt derzeit keine Löschschnittstelle. Nachweisdateien selbst bleiben in ihrer dokumentierten internen Ablage.

## Nachweistabelle

Keine Passwörter, API-Keys, vollständigen Secretdateien oder unredigierten Compose-Exporte eintragen. Adressen und Weltpfade nur in einem passenden internen Betriebsdokument festhalten; das Git-Repository kann öffentlich werden.

| Gegenstand | Nachgewiesener Wert | Quelle | Erfasst am (UTC) | Geprüft durch |
|---|---|---|---|---|
| Proxmox VM/LXC und Docker-Host-Zuordnung | offen | offen | offen | offen |
| Zielcontainer und eindeutige Serverzuordnung | offen | offen | offen | offen |
| itzg-Image mit Digest | offen | offen | offen | offen |
| Tatsächliche Minecraft-Version | offen | offen | offen | offen |
| Tatsächlicher Loader und Loader-Version | offen | offen | offen | offen |
| Java-Runtime des Minecraft-Containers | offen | offen | offen | offen |
| Datenvolume und Mountart, ohne Geheimnisse | offen | offen | offen | offen |
| Bestehende Quelle für server.properties / Umgebungswerte | offen | offen | offen | offen |
| RCON-Aktivierung, interne Erreichbarkeit, Secretablage | offen | offen | offen | offen |
| Existierende Readiness-Prüfung | offen | offen | offen | offen |
| Installierter Modbestand mit Dateihashes | offen | offen | offen | offen |
| Xaero-Projekte und Server-/Clientverteilung | offen | offen | offen | offen |
| Letzte erfolgreiche Sicherung und Integritätsnachweis | offen | offen | offen | offen |
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

# Anforderungen und MVP-Backlog

## Umfang

Erster Ausbau für einen bestehenden Server; Datenmodell mit Server-ID für spätere Erweiterung. Keine Proxmox-Gesamtverwaltung und kein öffentlicher Mod-Marktplatz. Freie Dateiverwaltung aus dem Mockup ist nachrangig; im MVP nur bekannte Konfiguration, Logs und Backup-Artefakte über begrenzte Schnittstellen.

| ID | Priorität | Ergebnis / Abnahme |
|---|---|---|
| M01 | P0 | Bestand: Minecraft/Fabric/Java, Container und Volumes belegt; Quelle und Zeitpunkt dokumentiert. |
| M02 | P0 | Anmeldung und Rollen: unberechtigte direkte API-Aufrufe liefern 401/403; OP bleibt getrennt. |
| M03 | P0 | Dashboard: echter Status, Readiness, Aktualisierungszeit, Spielerzahl und verständliche Fehler; keine Mockwerte im Produktivmodus. |
| M04 | P0 | Start/Stop/Restart: fester Zielcontainer, dauerhafter Job, Idempotenz und Sperre; sauberer Stop und überprüfter Abschluss. |
| M05 | P0 | Konsole: echtes RCON-Ergebnis; eigener Live-Logstrom, Reconnect und Rechteprüfung. Operator kann keine freien Admin-Kommandos senden. |
| M06 | P0 | Spieler: Online-Liste, Whitelist hinzufügen/entfernen; OP separat nur Admin. UUID-Auflösung und Fehler sichtbar; Offline-Verhalten definiert. |
| M07 | P0 | Backup: konsistente Sicherung, verschlüsseltes Cloud-Repository, Ergebnis und letzte erfolgreiche Sicherung sichtbar; kein Erfolg bei Teilfehler. |
| M08 | P0 | Restore: isolierter Proberestore erfolgreich; Admin-Plan, Vorabsicherung, Stop, Wiederherstellung, Healthcheck und Rückweg. |
| M09 | P1 | Eigenschaften: typisierte erlaubte server.properties-Werte, Validierung, Revision und Neustartbedarf; keine freie Secretbearbeitung. |
| M10 | P1 | Mods: reale CurseForge-Suche, exakte Version/Fabric-Dateiprüfung und serverseitiger Key; leere Ergebnisse bei fehlendem Match. |
| M11 | P1 | Modänderung: Abhängigkeiten, Downloadfreigabe, gepinnte Datei-IDs, Backup, kontrollierter Neustart und Rollback. |
| M12 | P1 | Monitoring: CPU/RAM/Netz/Disk aus echter Quelle; TPS/MSPT nur mit nachgewiesener Quelle, sonst klar nicht verfügbar. |
| M13 | P1 | Betriebsqualität: Audit, redigierte Fehler, Offline-/Timeout-Ansichten und Wiederanlauf nach Backend-Ausfall. |
| M14 | P2 | Xaero-/Kartenintegration nach technischer Prüfung; ohne Webkartenquelle Platzhalter mit Erklärung. |

## Inkremente

1. Bestand und sichere Basis: M01–M04.
2. Konsole und Spieler: M05–M06.
3. Datensicherheit: M07–M08, vor erster Modmutation.
4. Konfiguration und Mods: M09–M11.
5. Monitoring/Ausbau: M12–M14; grundlegende Logs und Backup-Alarme bereits in den früheren Inkrementen.

## Definition of Done

Jedes Inkrement hat echte Datenanbindung oder ausdrücklich gekennzeichnete Entwicklungsdaten, verständliche Fehler, serverseitige Autorisierung, Audit für Mutationen und eine passende Prüfung der kritischen Abläufe. Keine Schlüssel im Client oder Repository. Container-/Framework-Versionen beim Implementierungsstart auswählen und festschreiben; dieses Konzept installiert keine ungeprüften „latest“-Versionen.

# Erste RCON-Übernahme: Online-Spieler ausschließlich lesen

Nachtrag: [Whitelist-Aktionen als Jobs](17-whitelist-testjobs.md) sind inzwischen separat für den ausdrücklichen Testmodus implementiert. `read-only` bleibt unverändert lesend; die folgenden Abschnitte beschreiben das erste Inkrement und dessen damalige Prüfung.

Stand: 25.10.2026. Aus dem zweiten Quellstand (Commit siehe [Abgleich](15-abgleich-projektchats.md)) sind RCON-Paketablauf und Simulatoransätze in den vorhandenen Host-Agenten übertragen. Das zweite Backend wird nicht zusätzlich gestartet. Die Portal-Anwendung behält ihre Sessionanmeldung, serverbezogenen Rollen, Jobs und Auditpipeline.

## Vorhanden

- `RconPlayerReader` im Agent sendet nach Authentisierung ausschließlich den fest eingebauten Befehl `list` und eine leere Abschlussanforderung zur Zuordnung mehrteiliger Antworten. Kein Parameter für freie Befehle und kein mutierender RCON-Endpunkt.
- Paket-/Antwortgrößen, Antwortanzahl, Korrelations-IDs, UTF-8 und Endmarken sind begrenzt bzw. validiert. Eine Gesamtdeadline schließt den Socket auch bei blockierendem Lesen/Schreiben. Keine DNS-Auflösung, keine automatische Wiederholung. Fehler enthalten weder Passwort noch Remoteantwort oder Secretpfad.
- Parser prüft Vanilla/Fabric-Antwortformat, gemeldete Anzahl, Maximum, Namen und Duplikate. Höchstens 500 Namen, höchstens 16 KiB zusammengesetzte Antwort. Unbekanntes Format oder widersprüchliche Anzahl bedeutet Fehler, keine scheinbar leere Liste. Es werden keine UUIDs, Whitelist- oder OP-Rechte geraten.
- Agent: authentisiertes `GET /agent/v1/servers/{id}/players/online`. Server-ID fest, eine gleichzeitige Spielerprobe, Docker-Zuordnung und laufender Container müssen vor RCON passen. Bei fehlender Anbindung/Readiness oder Fehler 503 ohne Remoteinhalt.
- Portal: `GET /api/v1/servers/{id}/players/online` für Viewer/Operator/Admin mit Servergrant. Der bestehende authentisierte HTTP-Agenttransport prüft Server-ID, JSON-Typen, Namen, Zeitstempel/Frische, Gesamttimeout, Bodylimit und Redirectverbot.
- Angular: `/players` ist eine echte lesende Seite mit Aktualisieren, Messzeit, Online-Namen, bestätigt leerer Liste und nicht verfügbarem Zustand. Daten werden vor erneuter Abfrage verworfen; ein Fehler erhält keine veraltete Online-Liste. Whitelist/OP bleiben als noch nicht integriert gekennzeichnet.

## Sichere Voreinstellung und lokale Konfiguration

`agent.rcon.mode` bleibt standardmäßig `disconnected`. Keine Secret-Datei wird dann gelesen und keine RCON-Verbindung hergestellt. Auch der Portal-Standard bleibt ohne Hostanbindung. Keine Produktionseinstellung wurde aktiviert.

Für einen ausdrücklich eingerichteten lokalen Simulator oder einen bereits vorhandenen separaten Testanschluss können Agentparameter gesetzt werden:

```text
--agent.rcon.mode=read-only
--agent.rcon.host=127.0.0.1
--agent.rcon.port=<lokaler Testport zwischen 1024 und 65535>
--agent.rcon.password-file=<absolute externe Secret-Datei>
--agent.rcon.timeout=1500ms
```

Die Secret-Datei enthält das RCON-Passwort, maximal 4096 UTF-8-Bytes, optional genau einen abschließenden Zeilenumbruch. Bedeutungsvolle Leerzeichen bleiben erhalten. Keine Zeilenumbrüche/NUL im Passwort. Die Datei gehört nicht in Git oder Angular. Das Agent-Bearertoken und RCON-Passwort sind verschiedene Geheimnisse.

**Dieses erste Inkrement erlaubt ausschließlich IPv4-Loopback.** Docker- und RCON-Zuordnung müssen den konfigurierten Testserver betreffen. Die vorhandene Test-Compose deaktiviert RCON weiterhin; sie wurde nicht dafür verändert. Auf docker-01 gibt es keinen bestätigten RCON-Anschluss auf Loopback und keine RCON-Freigabe durch dieses Inkrement. Port 25575 wurde nicht veröffentlicht. Das Docker-01-Containerpaket aktiviert RCON nicht und enthält kein RCON-Secretmount. Für den späteren produktiven Anschluss ist eine gesondert geprüfte interne Netzzuordnung nötig; Host- oder Minecraft-Konfiguration nicht einfach für diesen Test ändern.

## Datenvertrag

Erfolgreicher Agent-Snapshot: `serverId`, `names` (Stringliste) und `observedAt` (UTC). Das Portal ergänzt im Fehlerfall einen festen `reasonCode`.

- `names: []` mit Messzeit ohne Fehlercode: bestätigt niemand online.
- `names: null`, `observedAt: null`, fester Fehlercode: Zustand nicht verfügbar; keine Aussage über Online-Spieler.
- Fremde Server-ID oder fehlender Grant: Ablehnung vor Hostzugriff.

## Prüfung und verbleibender Umfang

Agentbuild: 41 Tests erfasst, **38 erfolgreich**, drei Docker-Live-Proben bewusst übersprungen. Neun neue Tests prüfen RCON gegen echte lokale TCP-Simulatoren und den Agent-Zugriffsschutz. Portalbuild: **50 Tests erfolgreich**, davon sechs neue für Serverrechte und tatsächlichen lokalen HTTP-Transport inklusive Frische, Redirect, falschen Typen, Bodylimit und Timeout. Angular-Typ-/Templateprüfung und Produktionsbuild erfolgreich.

Im Browser geprüft: nicht angemeldete Ansicht, Anmeldung mit flüchtigem lokalem Testkonto, autorisierte Spieleransicht, Aktualisieren und Neuladen mit nicht angebundener Quelle. Die Vorschau benutzt eine eigene In-Memory-Datenbank und `disconnected`, keine Beispieldaten. Positive RCON-/Spielerantworten sind durch Simulator-/API-Tests geprüft, noch nicht durch einen durchgängigen Browserlauf mit Minecraft. Kein Zugriff auf docker-01 und kein erneuter Docker-Desktop-Start.

M05/M06 sind weiterhin teilweise offen: freie Konsole mit begrenzten Rollen, echter separater Live-Logstrom, Whitelist-/OP-Mutationen über gemeinsame Sperre/Job/Audit, UUID-/Offline-Verhalten und Abnahme am getrennten Minecraft-Testserver. Ein erfolgreicher Spielerabruf ist keine Freigabe für diese Mutationen.

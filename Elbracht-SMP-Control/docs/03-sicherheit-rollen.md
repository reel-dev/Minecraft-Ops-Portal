# Sicherheit und Rollen

Rollen gelten im Portal und pro Server. Minecraft-OP ist davon unabhängig. Portalnutzer werden nicht automatisch Minecraft-OP, und ein Spieler mit OP erhält keinen Portalzugang.

| Funktion | Viewer | Operator | Admin |
|---|---|---|---|
| Status, freigegebene Metriken | Ja | Ja | Ja |
| Online-Spieler ansehen | Ja | Ja | Ja |
| Rohlogs/Konsolenausgabe lesen | Nein | Ja, redigiert | Ja, redigiert |
| Start/Stop/Restart | Nein | Ja | Ja |
| Freigegebene RCON-Aktionen | Nein | Ja | Ja |
| Whitelist verwalten | Nein | Ja | Ja |
| OP vergeben/entziehen | Nein | Nein | Ja |
| Rohes Minecraft-RCON-Kommando | Nein | Nein | Ja |
| server.properties ändern | Nein | Nein | Ja |
| Backup auslösen/auflisten | Nein | Ja | Ja |
| Backup herunterladen/Restore | Nein | Nein | Ja |
| Mods suchen/Inventar sehen | Ja | Ja | Ja |
| Mods installieren/entfernen/updaten | Nein | Nein | Ja |
| Schlüssel, Rollen, Retention | Nein | Nein | Ja |

## Maßnahmen

- Vorschlag: OIDC-Anmeldung über vorhandenen Provider, Spring-Security-Session mit Secure/HttpOnly/SameSite-Cookie, CSRF-Schutz für Mutationen. Kein langlebiger Zugangstoken im Browser-Storage. Fehlende Identität wird abgewiesen; Rollenprüfung stets im Backend, auch bei SSE und Downloads.
- HTTPS am Reverse Proxy. RCON-Port, Docker, Datenbank, Agent und Monitoring ausschließlich intern. RCON über Host-/Privatnetz; bei Hostgrenzen geschützter Tunnel. Zugriffskonzept LAN/VPN/Internet vor Exposition festlegen.
- CurseForge-Key nie an Angular zurückgeben. Secret-Eingabe nur für Admin, danach nur `configured`, Fingerprint/Änderungszeit und Prüfergebnis anzeigen; keine Klartext-Rücklesefunktion. Rotation, Redigierung und Schutz von Diagnose-/Actuator-Endpunkten berücksichtigen.
- Definierte Aktionen wie Whitelist-Aufnahme nehmen einen validierten Spielernamen/UUID entgegen. Operator-Kommandos als strukturierte Templates; kein String-Prefix-Test, der etwa OP-Befehle durchlässt. Admin-RCON bleibt hochprivilegiert, jedoch ohne Betriebssystem-Shell.
- Keine Command-Injection: feste Prozesse/Argumentlisten, keine Shell-Auswertung. Keine benutzerdefinierten Docker-Argumente. Pfade kanonisch innerhalb fester Wurzeln prüfen; Traversal und Symlink-Ausbruch verhindern.
- Downloads nur anhand serverseitig validierter CurseForge-Datei-IDs, mit HTTPS-, Zielhost-/Redirect-Prüfung, Größenlimit und Prüfsumme. Keine beliebigen URL-Downloads aus dem Frontend.
- Logausgabe als Text rendern, HTML/ANSI-Inhalte sicher behandeln. IPs, Chat und Spielerinformationen nur bedarfsgebunden anzeigen; Log-Aufbewahrung getrennt von Backup-Aufbewahrung festlegen.
- Audit: Nutzer, Aktion, Server-ID, Zeit, redigierte Parameter, Job-ID, Ergebnis und Fehlercode. Rohkommandos können vertrauliche Daten enthalten; Redigierung vor Speicherung. Keine Secrets im Audit.
- Restore und OP-Änderung zeigen Wirkung und Ziel zur ausdrücklichen Bestätigung. Rückgabe von 403/409/422 statt stiller Teilaktionen; fehlgeschlagene Teilaktionen sichtbar machen.

## Bedrohungen und Abnahme

Vor Freigabe prüfen: direkter API-Zugriff trotz ausgeblendeter Buttons, fremde Server-ID, abgelaufene Session im Stream, doppelte Requests, paralleler Restore/Restart, Pfad-/URL-Manipulation, Log-XSS, Secret-Leaks und Agentzugriff von außen. Ein kompromittierter Minecraft-Mod läuft als Code im Server: Container ohne Hostprivilegien und mit minimalen Mounts betreiben.

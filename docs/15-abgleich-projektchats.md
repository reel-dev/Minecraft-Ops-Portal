# Abgleich der beiden Entwicklungsstände

Stand: 25.10.2026. Der Nutzer bestätigt: ZIP aus „Weiter Minecraft Verwaltung“ noch nicht nach docker-01 übertragen oder gestartet. Damit gibt es nach aktuellem Nachweis kein zweites installiertes Verwaltungstool. Keine Nachricht an den anderen Chat gesendet, keine Site geändert oder veröffentlicht.

## Tatsächlich geprüfte Quellen

- Lokales Hauptrepository: Minecraft-Ops-Portal, Angular + Spring Boot mit persistenten Jobs, Audit und serverbezogenen Rollen.
- Zweiter Quellstand: über Sites-Quellzugriff separat nach `.tools/other-chat-source` abgerufen, Commit `2cef254f5b155b0d774266b0c62c3aa9e0e92c3d`. Zugehöriger Chat „Weiter Minecraft Verwaltung“, Site „Elbracht SMP · Serververwaltung“, veröffentlichte Version 2. Die dortige Cloudoberfläche ist ausdrücklich ein Demo-Prototyp.
- Im abgerufenen Repository liegen zusätzlich das zweite Backend, seine Live-Oberfläche, Tests, Compose-Dateien und ein Quell-ZIP. Das ist ein tatsächlicher Codevergleich, nicht nur eine Übernahme der Aussagen aus dem Chat.
- Das zweite Backend wurde unverändert lokal gebaut: **20 Tests erfolgreich**, keine Fehler oder übersprungenen Tests. Dieser Lauf nutzt Simulatoren/Mocks und beweist keine RCON-Verbindung zum produktiven Server. Diese 20 Tests gehören zum zweiten Backend und werden nicht den Tests des Hauptrepositories zugerechnet.

## Entscheidung und Übernahmepunkte

Das bestehende Angular-/Spring-Boot-Repository bleibt die gemeinsame Produktbasis entsprechend dem ursprünglichen Auftrag. Kein paralleles Ausrollen der zwei Verwaltungstools und kein Ersatz der vorhandenen Security-/Jobimplementierung durch die zweite Anwendung.

| Bereich | Vergleich | Weiteres Vorgehen |
|---|---|---|
| Frontend | Hier Angular mit API-Client und echten Job-/Auditansichten; dort statischer Demo-Prototyp plus separate Live-Seite | Angular behalten; Gestaltung und Interaktionen als Referenz prüfen, keine Demo-Spieler/Mods/Metriken als Messwerte übernehmen |
| Anmeldung und Rollen | Hier Session/CSRF, serverbezogene Viewer/Operator/Admin-Grants und OIDC-Vorbereitung; dort einzelne Adminanmeldung | Bestehendes Rollenmodell behalten; OP bleibt unabhängig |
| Jobs | Hier persistente Zustände, Idempotenz, UNKNOWN-Sperre und Audit; dort maximal 100 Arbeitsspeicherjobs, Verlaufverlust bei Neustart | Bestehende Jobpipeline behalten; keine Übernahme von Jobs.java |
| Unklarer Aktionsausgang | Zweites Backend setzt Fehler auf FAILED und löst seine aktive Sperre; bestätigt Docker-Abschluss ohne Minecraft-Readiness | Keine Übernahme dieses Abschluss-/Sperrverhaltens; konservative UNKNOWN-Behandlung und Readiness behalten |
| RCON | Zweiter Stand enthält Authentisierung, Paket-/Antwortlimits, Mehrpaketantworten, Zeitgrenzen und Simulatorfälle | Transport und Tests als Grundlage für M05 prüfen und gezielt in die vorhandene Architektur integrieren; kein freigeschalteter Produktionsendpunkt durch den Abgleich |
| Spieler | Zweiter Stand vereint Whitelist, OP-Datei und Online-Liste; Änderungen über RCON, Bestätigung über erneut gelesene Dateien | Fachliche Abläufe übernehmen, zuvor Serverrollen, gemeinsame Mutationssperre, Audit, Offline-/UUID-Verhalten und Fehlerrückmeldung ergänzen |
| Konsole | Zweiter Stand bietet freie Admin-RCON-Kommandos; Stop/Restart werden dort nur textuell herausgefiltert | Nicht als Umgehung definierter Operationen übernehmen; M05 benötigt eigenes Berechtigungskonzept und gemeinsamen Job-/Auditpfad. Live-Logs separat |
| Dockerbereitstellung | Beide Stände bereiten separate Container vor; beide tatsächlichen Container-Laufzeittests offen | Ein gemeinsames Deployment vorbereiten. Aktuell keine Produktionsteuerung aktivieren |
| Backup | Lokales Proxmox-VM-Backup wurde durch den Nutzer erfolgreich erstellt | Datenquelle auf Root-Dateisystem zugeordnet; Weltkonsistenz, externe Kopie und Restore bleiben eigene Abnahmen |

## Bekannte Infrastrukturangaben aus dem zweiten Chat

Der Nutzer lieferte dort zusätzlich Docker Engine 29.6.1 und den Netzwerknamen `minecraft_default`. Der konkrete Hostpfad wurde in diesem Abgleich gelesen, aber wegen der bestehenden Repository-Leitplanke nicht nochmals in öffentliche Betriebsdokumente kopiert. Diese Angaben ersetzen keine RCON-Aktivierungs-/Erreichbarkeitsprüfung. Insbesondere wurde kein vorhandenes RCON-Passwort übermittelt oder ausgelesen.

## Nächste Umsetzung

1. RCON-Transport und Simulatorfälle aus dem geprüften Quellstand in die vorhandene Architektur übertragen; standardmäßig getrennt von Produktion halten. Zeit-/Paketgrenzen, Geheimnisbehandlung und Verhalten bei unklarem Ausgang erneut prüfen.
2. Spieler-/Konsolenabläufe in die bestehende serverbezogene Rechte-, Sperr- und Auditpipeline integrieren. Vor Freigabe mutierender Spieleraktionen getrennte Testserver-Abnahme.
3. Gemeinsame Containerkonfiguration bauen und Proxy-/Socket-/Netzgrenzen lokal prüfen. Docker Desktop auf dem Entwicklungs-PC ist nach dem gemeldeten Startfehler nicht durch Reset oder wiederholte Startversuche verändert worden; Containerprüfung bleibt offen.
4. Erst den zusammengeführten und geprüften Stand kontrolliert auf docker-01 anbinden, zunächst lesend. Die alte ZIP und das vorbereitete Agentpaket sind aktuell keine Aufforderung, beide parallel zu installieren.

Der Quellabgleich ist abgeschlossen. RCON-/Spielerfunktionen sind durch dieses Dokument noch nicht in die Angular-Anwendung integriert oder am produktiven Server freigegeben.

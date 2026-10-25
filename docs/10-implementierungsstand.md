# Implementierungsstand des Grundgerüsts

Stand: 25.10.2026. Es wurde ausschließlich lokal implementiert. Kein Minecraft-Host wurde kontaktiert oder verändert.

## Bestätigte Angaben und offene Prüfung

Minecraft `26.3` und Fabric bleiben ungeprüfte Nutzerangaben. Sie stehen getrennt von Messwerten im Serverprofil. `verifiedAt`, gemessene Version, Loader-Version, Spielerzahl und Messzeit bleiben `null`. Das mitgelieferte Profil `main` ist eine Portalregistrierung, keine Hostverbindung.

M01 bleibt offen: Container, Mounts, Image-Digest, Minecraft/Fabric/Java und bestehende Sicherung sind anhand redigierter Bestandsdaten mit Quelle und Erfassungszeit zu dokumentieren. Es wurden keine Adressen oder Credentials aus dem Referenzbild übernommen.

## Technische Umsetzung

| Backlog | Vorhanden | Noch offen zur MVP-Abnahme |
|---|---|---|
| M01 | Registriertes Serverprofil, getrennte Nutzerangaben/Messwerte, Nachweisvorlage und Adminformular mit Quelle/Zeit/Revision für 15 Angaben | Tatsächliche Bestandsdaten und technische Gesamtabnahme |
| M02 | Lokaler Sessionlogin, CSRF, serverbezogene Viewer/Operator/Admin-Prüfung; OIDC-Konfiguration für `prod` | Echter Provider, Provisionierung der Serverrechte, Deploymentprüfung |
| M03 | API, Dashboard, UNKNOWN-/Fehler-/Leerzustände, Aktualisierung alle 5 Sekunden; lesender HTTP-Statusadapter mit Frische-/Readiness-Validierung | Hostdienst und tatsächliche Mess-/Readiness-Quelle gegen separaten Testserver abnehmen |
| M04 | START/STOP/RESTART-Vertrag, persistente Jobs, Sperre, Idempotenz, Audit, Worker, Neustartbehandlung, Jobhistorie/Detailroute und sichtbare UNKNOWN-Sperre | Mutierender authentisierter Host-Agent und separater Testserver |
| M13 | Jobhistorie, UNKNOWN-Recovery, serverbezogene Admin-Auditansicht und Jobfilter | Vollständige Fehler-/Timeout-Abnahme am Host, Audit-Retention, Login-/Ablehnungsereignisse, Deploymentprüfung |
| M05–M12, M14 | Navigierbare, ausdrücklich beschriftete Ausbauansichten; Portgrenze zum Host | RCON, SSE/Logstream, Spieler, Backup/Restore, Konfiguration, CurseForge, Metriken, Karte |

Standard bleibt `DisconnectedHostAgent` ohne Netzwerk-, RCON-, Prozess- oder Dockerzugriffe. Optional ist nun `HttpStatusHostAgent` für authentisierte, ausschließlich lesende Statusanfragen vorhanden; [Vertrag und Konfiguration](11-host-agent-vertrag.md). Dieser wurde nur gegen einen lokalen Testdienst geprüft und ist nicht aktiviert. Beide Adapter sperren Schreibversuche mit HTTP 503 ohne vorgetäuschte Jobs. Ein Konfigurationsschalter allein kann keine echten Serveraktionen aktivieren.

## Entscheidungen dieses Implementierungsschritts

- Angular 21.2.25 (LTS), CLI/Build 21.2.28, TypeScript 5.9.3, Node 24.16.0; direkte Pakete und npm-Lockdatei gepinnt. Kompatibilitätsbasis: [Angular-Versionstabelle](https://angular.dev/reference/versions).
- Spring Boot 3.5.16, Java-21-Sprach-/Bytecodeziel, Maven 3.9.16 über Wrapper. [Spring-Systemanforderungen](https://docs.spring.io/spring-boot/3.5/system-requirements.html). Lokale Prüfung nutzt die vorhandene JDK-25.0.4-Runtime; CI ist auf Java 21 eingerichtet.
- Modularer Monolith mit Paketen `identity`, `servers`, `operations`, `audit`, `api`. Weitere Fachmodule erst bei ihrer Implementierung; keine leeren ausführbaren Integrationen.
- Spring JDBC und Flyway statt zusätzlicher ORM-Abstraktion: explizite Transaktionen und `SELECT … FOR UPDATE` auf dem registrierten Server serialisieren die Jobannahme. H2-Dateidatenbank lokal, PostgreSQL im Produktionsprofil. Kein Docker-Socket und keine Weltvolumes im Portal.
- Status und Jobanzeige verwenden zunächst REST-Polling. SSE inklusive Cursor, Puffer und Sessionablauf wird mit M05 umgesetzt.
- Das Grundgerüst unterstützt genau **eine Backend-Instanz**. Recovery markiert beim Start alle QUEUED/RUNNING-Jobs als UNKNOWN; sie werden nicht automatisch wiederholt. UNKNOWN hält die Serversperre, bis ein späterer autorisierter Abgleich den tatsächlichen Zustand geklärt hat. Dafür gibt es bewusst noch keinen Entsperrendpunkt. Keine horizontale Skalierung vor Lease-/Recovery-Konzept.
- `HostAgent.execute` darf Erfolg erst nach geprüftem Zielzustand zurückgeben. Adapterfehler gelten konservativ als unbekannter Ausgang; Exceptiontexte gelangen nicht in Job oder Audit. Konkrete Timeouts, authentisierter Transport und Stop-/Readiness-Prüfungen sind Aufgaben des künftigen Adapters.
- Audit speichert Identität, Server, Operation, optionale Job-ID, Zeit und Übergang atomar mit der Jobänderung bzw. Feld/Revision einer Bestandsänderung. Freitext, Bestandswerte und Quellen werden nicht ins Audit übernommen; keine Secrets eingeben. Ein serverbezogener Admin-Leseendpunkt und eine Auditansicht mit Jobfilter sind implementiert. Cursor sind an Server und gewählten Job gebunden. Wiederholte Abschlussaufrufe erzeugen ohne echten Statuswechsel keinen neuen Eintrag. Es gibt keine Audit-Schreib-/Lösch-API; Datenbank-Löschschutz und Retention sind noch nicht implementiert.
- Jobhistorie wird ab Operator pro Server gelesen. Begrenzte Seiten (1–50, Standard 20) verwenden Erstellungszeit plus UUID als stabile Reihenfolge; der Cursor ist eine vorhandene Job-ID desselben Servers. Der aktuell sperrende Job wird zusätzlich geliefert, auch auf einer älteren Seite. Daten kommen nach Browserreload aus der Portal-Datenbank; UNKNOWN bleibt gesperrt. Dashboard und Detailseite bieten keine Entsperrung oder automatische Wiederholung.

## Prüfung und Grenzen

Integrationstests prüfen 401/403, Serverzugriff, echten lokalen Sessionlogin mit CSRF-Logout, Eingabevalidierung, HTTP 202/Location, Idempotenzkonflikte, parallele Jobannahme, persistente Lesbarkeit, UNKNOWN-Recovery, Workerfehler und den deaktivierten Adapter. PostgreSQL, OIDC-Provider und echte Minecraft-Readiness sind nicht durch H2-/Mocktests abgenommen.

Erweiterungsprüfung: zwölf zusätzliche Tests für Agentkonfiguration und den lesenden Transport mit einem kurzlebigen Loopback-HTTP-Testdienst. Geprüft sind Headerauthentisierung, feste Serverzuordnung, Readiness-Widersprüche, Frische/Zukunftszeit, Redirectverbot, redigierte Fehler, Bodylimit, ungültiges JSON, Gesamttimeout einschließlich Body, Secretdatei und Ablehnung unsicherer Produktionskonfiguration. Kein Testprozess greift auf einen Minecraft-Host zu.

Prüfung am 25.10.: insgesamt 27 erfolgreiche Backendtests samt Paketbuild. Fünf neue Historientests prüfen Zugriffsschutz, Limits/ungültige Cursor, UNKNOWN nach Recovery ohne Idempotency-Key-Leak, gleiche Zeitstempel plus neue Jobs während des Blätterns und fremde Server-IDs. Flyway V2 ergänzt einen Index für die Historie. Angular-Produktionsbuild und strikte Typ-/Templateprüfung erfolgreich. Im Browser wurden Login, Dashboardlink zur Historie, Leerliste, Browserreload und Fehleransicht für nicht vorhandene Job-ID gegen eine flüchtige Portal-Testdatenbank geprüft. Gefüllte Historie, Blättern und Sperrstatus sind über API-/Datenbanktests geprüft, noch nicht mit einem echten Hostauftrag.

Audit-Erweiterung am 25.10.: insgesamt 32 erfolgreiche Backendtests samt Paketbuild. Fünf neue Tests prüfen Admin-/Serverscope, tatsächliche Übergänge ohne Duplikate, Ausschluss von Freitext/Idempotency-Key, Jobfilter und Cursorvalidierung, Seitengrenzen bei Zeitgleichheit sowie fremde Server-IDs. Flyway V3 ergänzt Audit-Indizes. Angular-Build und Typprüfung sind erfolgreich. Im Browser wurden Dashboard und Audit-Leerzustand einschließlich Reload gegen eine flüchtige lokale Datenbank geprüft und als aktuelle Screenshots aufgenommen. Gefülltes Audit ist durch Datenbank-/API-Tests geprüft; keine gefälschten Ereignisse wurden in die Produktansicht aufgenommen.

Bestandsformular am 25.10.: insgesamt 40 erfolgreiche Backendtests samt Paketbuild. Acht neue Tests prüfen Admin-/Serverscope, CSRF, Pflichtrevision, ETag, Validierung, erneutes Lesen, Revisionskonflikte und Audit ohne Wert-/Quellenkopie. Auch 15/15 dokumentierte Felder lassen Nutzerangaben und technische Gesamtabnahme unverändert; keine Serverjobs werden erzeugt. Flyway V4 ergänzt Nachweise und Revisionsmetadaten. Angular-Build und Typprüfung erfolgreich. Im Browser wurden das Formular, Fokusführung, Speichern mit echtem Session-/CSRF-Ablauf, Reload und Audit geprüft. Ein ausdrücklich als lokaler UI-Test bezeichneter Eintrag liegt ausschließlich in der flüchtigen Vorschau-Datenbank; er bestätigt keinen echten Testserver oder Servernachweis.

Erstprüfung am 24.10.: zehn Integrationstests unter JDK 25.0.4 mit Java-21-Bytecodeziel, Angular-Produktionsbuild und strikte Typ-/Templateprüfung. Im Browser wurden Sessionlogin, UNKNOWN-Dashboard mit deaktivierten Aktionen, Spielerroute und CSRF-Abmeldung gegen die lokale Portal-API geprüft. Die Vorschau verwendete eine flüchtige H2-Testdatenbank und ein temporäres lokales Passwort.

Vor der ersten Hostanbindung: Bestandsprüfung abschließen, separaten Testserver festlegen, Agenttransport/-rechte prüfen und M01–M04 dort abnehmen. Danach M05/M06 und konsistente Cloud-Sicherung plus isolierten Proberestore umsetzen. Keine Modmutation vor M07/M08.

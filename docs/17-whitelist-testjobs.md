# Whitelist-Aktionen in der gemeinsamen Jobpipeline

**Aktueller Nachtrag:** Die [echte Minecraft-RCON-Abnahme](18-rcon-minecraft-abnahme.md) ist inzwischen erfolgreich. Docker war wieder erreichbar; es wurde nicht neu gestartet. Der notwendige RCON-Reihenfolgefix und eine feste Docker-Testdateiquelle sind implementiert. Die folgenden Abschnitte dokumentieren den vorherigen Simulatorstand und bleiben als Herkunft dieser Erweiterung nachvollziehbar.

Stand: 25.10.2026. Implementiert für einen ausdrücklich freigeschalteten **isolierten Testanschluss**, gegen Simulatoren geprüft. Kein RCON- oder Docker-Schreibzugriff auf docker-01. Docker Desktop ist nicht erreichbar; die echte Minecraft-RCON-/Whitelist-Abnahme ist weiterhin offen. Kein erneuter Start- oder Resetversuch.

## Funktionsumfang

`WHITELIST_ADD` und `WHITELIST_REMOVE` verwenden denselben Portal-Endpunkt `/api/v1/servers/{id}/operations`, dieselbe SQL-Serversperre, Jobhistorie, Recovery und Auditpipeline wie Start/Stop/Neustart. Der Request benötigt zusätzlich `targetName`; Minecraft-Namen sind auf 3–16 ASCII-Buchstaben, Ziffern und Unterstrich begrenzt. Andere Operationsarten akzeptieren kein Spielerziel. OP-Vergabe/-Entzug und freie RCON-Kommandos sind nicht implementiert.

Beispielpayload (synthetischer Testname):

```json
{"type":"WHITELIST_ADD","targetName":"Fixture_Player","reason":"Isolierter Test"}
```

Session, CSRF, mindestens Operator-Grant für den Zielserver und Idempotency-Key sind erforderlich. Typ, Grund und exakter Zielname gehören zum Idempotenzvergleich; gleicher Schlüssel mit anderem Namen ergibt 409. `targetName` liegt dauerhaft am Job (Flyway V5), nicht in Freitext oder einem Kommando. Audit protokolliert Typ und Übergänge mit Jobreferenz; Ziel und Begründung werden nicht nochmals ins Audit kopiert. Jobdetails zeigen das Spielerziel.

Der Worker übergibt Portal-Job-ID und Zielname an den Agenten. Erfolg erfordert eine exakt korrelierte Agentantwort einschließlich Zielname und anschließend bestätigte Minecraft-Readiness. Der Agent führt Lifecycle und Whitelist im selben synchronisierten Dienst und dauerhaften Ledger aus. Gleiche ID/gleicher Typ/gleiches Ziel wird nicht wiederholt; abweichender Typ oder Name ergibt 409. Ein ungeklärter Ausgang sperrt sowohl weitere Spieler- als auch Lifecycle-Aktionen. Es gibt keine automatische Entsperrung oder Wiederholung.

## Agent: feste Grenzen und Bestätigung

- Agentmodus muss `docker-test-control` für `elbracht-smp-control-test-minecraft` sein; Testprojekt-/Serverlabels, eigenes Testweltvolume und lokales Spielportbinding bleiben erforderlich.
- Vor einer möglichen RCON-Mutation wird zusätzlich das RCON-Portbinding **desselben geprüften Containers** gelesen: genau eine Veröffentlichung von `25575/tcp` an `127.0.0.1` und dem fest konfigurierten RCON-Testport. Wildcard- oder zusätzliche RCON-Bindings werden abgewiesen. Das prüft nicht automatisch die lokale Dateiverzeichnis-Zuordnung; diese muss dem tatsächlichen Testweltvolume entsprechen und ist vor einer Live-Abnahme gesondert zu belegen.
- RCON benötigt nun ausdrücklich den zusätzlichen Modus `test-control`; `read-only` erlaubt weiterhin ausschließlich `list`. Keine freie Kommandozeichenfolge gelangt aus HTTP zum Transport. Die neuen Methoden bauen nur `whitelist add <Name>` bzw. `whitelist remove <Name>` aus validiertem Namen auf.
- `whitelist.json` und `ops.json` werden ausschließlich aus einem fest konfigurierten Testdatenverzeichnis gelesen, niemals vom Agenten geschrieben. Das Verzeichnis muss auf die eigene Testwelt zeigen und für den Agenten nur lesend eingebunden sein. Keine Benutzerpfade aus dem Request. Fehlende/fehlerhafte Dateien, Symlinks, doppelte Namen/UUIDs und zu große Dateien werden abgewiesen. UUIDs stammen aus den Serverdateien und werden nicht erfunden.
- Vor Entfernen werden OP-Einträge sowohl über Namen als auch UUID geprüft; auch umbenannte OP-Einträge blockieren. Bereits bestätigter Zielzustand benötigt keinen weiteren RCON-Schreibbefehl. Nach einem Befehl werden beide Dateien erneut gelesen; eine positive RCON-Antwort allein ist kein Erfolg.
- Wird das Ergebnis nicht bestätigt, bleibt UNKNOWN erhalten. Auch ein vor dem Befehl erkannter Guard-/Datei-/OP-Konflikt wird derzeit konservativ UNKNOWN, sofern der Agentjob bereits begonnen wurde. Das kann einen Testanschluss sperren, obwohl keine Mutation gesendet wurde; es gibt noch keine Reconciliation-API. Ledger nicht löschen, um dies zu umgehen.
- Fremde direkte Änderungen außerhalb des Panels können nicht mit dessen Sperre serialisiert werden. Eine während des Ablaufs neu auftauchende OP-Zuordnung wird bei der Nachprüfung erkannt und verhindert eine Erfolgsmeldung. Eine über externe Administration hinweg atomare Transaktion wird nicht behauptet.

## Freischaltung nur im Test

Standard bleibt überall gesperrt. Vor einer späteren eigenen Testabnahme sind alle Voraussetzungen erforderlich:

| Komponente | Konfiguration |
|---|---|
| Portal | Profile `local,test-server`, kein `prod`, `portal.agent.mode=http-test-control`, Loopback-Agent |
| Portal | zusätzlich `portal.test-whitelist-enabled=true` |
| Agent | `agent.mode=docker-test-control` und feste Testcontainer-Zuordnung |
| RCON | `agent.rcon.mode=test-control`, `127.0.0.1`, fester Testport und externe Passwortdatei |
| Testdateien | `agent.test-whitelist.enabled=true`, `agent.test-whitelist.data-directory` auf tatsächlich zugeordnete eigene Testwelt |
| Container | existierendes, genau passendes Loopback-RCON-Binding desselben Testcontainers |

Dies ist eine Beschreibung der Gates, **keine Aufforderung, die produktive Compose zu ändern**. Die vorhandene lokale Test-Compose lässt RCON weiterhin ausgeschaltet; diese Konfiguration wurde nicht umgestellt oder gestartet. `docker-host-read-only` und lesender Portaladapter erlauben keine Whitelist-Mutationen. Das vorbereitete Docker-01-Containerpaket aktiviert weder RCON noch Testdateimounts.

RCON-Einstellungen des Minecraft-Images vor späterer Änderung mit der [offiziellen itzg-Dokumentation](https://docker-minecraft-server.readthedocs.io/en/latest/configuration/server-properties/) abgleichen. Keine globale Veröffentlichung von RCON und keine Secretwerte in Compose-Exporte oder den Chat übernehmen.

## Oberfläche

Nur ein Backend mit aktivierter Testfähigkeit zeigt Operatoren/Admins das Whitelist-Testformular. Bestätigte Online-Lesequelle und erfolgreich geladener Job-/Sperrstatus sind zusätzliche UI-Voraussetzungen. Name und Begründung werden vorab geprüft; eine zweite Ansicht zeigt die konkrete Änderung vor dem Absenden. Während des Jobs sperrt die Oberfläche weitere Änderungen und verlinkt auf die Jobdetails. OP bleibt als gesperrt gekennzeichnet; Entfernen kickt keinen bereits verbundenen Spieler automatisch.

Fehler führen nicht zum automatischen Retry. Für eine ausdrücklich wiederholte identische Anfrage bleibt der Idempotency-Key erhalten. Bei fehlender Bestätigung des Jobstatus werden keine weiteren Requests angeboten. Jobs und Audit zeigen jetzt ebenfalls das konfigurierbare Umgebungslabel, damit Simulator-/Testdaten erkennbar bleiben.

## Prüfung

- Portal: **57 erfolgreiche Tests**, einschließlich Serverrollen/CSRF, deaktivierter Fähigkeit, Validierung, dauerhaftem Ziel, Idempotenzkonflikt, parallelem Lifecycle-/Whitelist-Auftrag, Workerübergabe, Audit und UNKNOWN-Sperre.
- Agent: **52 erfolgreiche Tests**, drei Docker-Live-Proben übersprungen. Zusätzliche Fälle: benannte RCON-Mutation gegen TCP-Simulator, Portbinding, Nachweis über Dateifixtures, OP-/UUID-Schutz, fehlerhafte Dateien, persistenter Zielvergleich, HTTP-Replay und additive Erweiterung eines alten Ledgers.
- Angular: strikte Typ-/Templateprüfung und Produktionsbuild erfolgreich.
- Browser: separate Instanz mit eigener flüchtiger H2-Datenbank und eigenem Sessioncookie; deutliches Label **LOKALER API-SIMULATOR – KEIN MINECRAFT / KEINE PRODUKTION**. Getestet wurden Namensablehnung, Bestätigung, Hinzufügen, Entfernen, Jobsperre, Jobdetail mit Ziel, Reload und die drei Auditübergänge des Hinzufügen-Jobs. Der Simulator bestätigt nur die HTTP-Vertragsantwort; er besitzt weder Docker noch RCON. Das beweist keinen Minecraft-Weltzustand. Temporäre Simulatorprozesse nach Prüfung beendet.

Browsernachweise (nur Simulatorjobs, synthetischer Name `Fixture_Player`):

| Aktion | Job-ID | UI-/Portalabschluss |
|---|---|---|
| WHITELIST_ADD | `e30892ad-5762-49ce-9bef-a9f9634e2742` | SUCCEEDED laut API-Simulator |
| WHITELIST_REMOVE | `eef886aa-0499-4774-a8b9-fecca6a3476d` | SUCCEEDED laut API-Simulator |

Die produktive Abnahme ist nicht erfolgt. Weiter offen: Minecraft-RCON-Testserverablauf, sichere Dateimount-Zuordnung im laufenden Agentcontainer, OP-Administration, UUID-/Offline-Fehleranzeige, freie Konsole mit Rollenmodell sowie eigener Logstrom. M05/M06 sind deshalb weiterhin nicht vollständig abgeschlossen.

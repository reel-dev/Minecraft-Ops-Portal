# Modpläne und lokale Ablaufabnahme

Der schreibende Modanschluss ist ausschließlich für den fest isolierten Testcontainer implementiert. Er ist standardmäßig deaktiviert und im produktiven bzw. lesenden Profil nicht verfügbar. Das produktive Minecraft auf `docker-01` wurde nicht kontaktiert oder verändert.

## Nachweise vor einer Änderung

Die serverseitige CurseForge-Anbindung prüft konkrete Projekt-/Datei-IDs, die tatsächliche Minecraft-Version und Fabric, Downloadfreigabe, Pflichtabhängigkeiten und explizite Inkompatibilitäten. Optionales wird separat ausgewiesen. Explizit gewählte Dateien bleiben auch dann gepinnt, wenn ein Hauptmod zugleich Abhängigkeit eines anderen Hauptmods ist.

Downloads verwenden ausschließlich die erlaubten HTTPS-CDNs ohne API-Schlüssel und ohne Redirects. Größe und veröffentlichter SHA-1 werden geprüft; anschließend wird SHA-256 berechnet. Die JAR-Prüfung extrahiert oder startet keinen Code: Sie prüft Archivpfade, Größen, Fabric-Metadaten, Serverumgebung, verschachtelte Bibliotheken, IDs und Versionsbedingungen. Nicht unterstützte Versionsbedingungen blockieren die Bestätigung.

Ein unveränderlicher Ausführungsplan bindet den vollständigen Zielbestand an Inventarrevision, Containeridentität, Laufzeit und Dateidigest. Er gilt 15 Minuten und zeigt Hinzufügen/Entfernen/Ersetzen, Datei-IDs, Prüfsummen und den vollständigen Zielbestand. Admin bestätigt mit Begründung und `ANWENDEN`. Der aktuelle Transport begrenzt einen vollständigen Modsatz auf 8 MiB; größere Pläne werden vor Serveränderungen abgewiesen.

## Ausführung und Rückweg

1. Portal erzeugt einen dauerhaften idempotenten `MOD_APPLY`-Job mit gebundener Plan-ID. Die gemeinsame Serversperre verhindert konkurrierende Mutationen.
2. Agent prüft Plan, Ziel, Laufzeit, Inventarrevision und Stagingdigest erneut.
3. Neue konsistente vollständige Vorabsicherung nach sauberem Stop, verschlüsselt mit Restic und vollständig gelesen/geprüft.
4. Geplanter Modsatz wird ausschließlich in der privaten Datenkopie eingesetzt. Ein weiterer unabhängiger Datenstand startet als Probecontainer ohne Netz und veröffentlichte Ports. Die aktive Welt erhält keine während dieser Probe entstandenen Weltänderungen.
5. Erst nach erfolgreicher Probe erfolgt der kontrollierte Datenwechsel am gestoppten Testcontainer. Der vorherige vollständige Stand bleibt auf dem Testvolume erhalten.
6. Minecraft-/Fabric-Readiness, RCON sowie tatsächliche Dateihashes und Manifest müssen bestätigt sein. Bei Fehler versucht der Agent den vorherigen vollständigen Welt-, Mod- und Betriebszustand wiederherzustellen. Ungeklärte Ergebnisse bleiben UNKNOWN und gesperrt; keine automatische Wiederholung.

Ein leeres oder vollständig zum Manifest passendes Inventar lässt sich verwalten. Unbekannte manuelle JAR-Dateien blockieren normale Änderungen. Für sie gibt es einen ausdrücklichen vollständigen Import: Jede vorhandene Datei muss einer konkreten CurseForge-Projekt-/Datei-ID zugeordnet werden. Der heruntergeladene Inhalt muss exakt denselben SHA-256 wie die vorhandene Datei haben. Teilzuordnungen, doppelte Projekte, abweichende Bytes und widersprüchliche vorhandene Manifeste werden vor dem Plan abgewiesen. Inventarrevision und vollständiger Bestand werden bei der Ausführung erneut geprüft. Die alten Dateien werden erst in der privaten Kandidatenkopie durch den gepinnten Dateisatz ersetzt; Vorabsicherung, isolierter Weltstart, Datentausch und Rückweg entsprechen dem normalen Modablauf. Updates erzeugen einen neuen Plan. Beim Entfernen eines Hauptmods bleiben weiterhin erforderliche Abhängigkeiten im Zielplan erhalten.

## Echte lokale Abnahme am 26.10.2026

Die verwendete Fabric-Mod ist eine selbst erstellte **codefreie lokale Abnahmefixture**, kein CurseForge-Projekt. Die internen Prüfpakete sind entsprechend `LOCAL_ACCEPTANCE_FIXTURE` gekennzeichnet und nur mit dem zusätzlichen Testschalter zulässig. Kein echter CurseForge-Schlüssel war konfiguriert. Diese Tests belegen den Host-/Job-/Backup-/Wechselablauf; sie belegen keine reale externe CurseForge-Abfrage oder Lizenzfreigabe.

| Ablauf | Job | Ergebnis |
|---|---|---|
| Fehler nach Datenwechsel | `7fdd7313-44e7-4927-814e-0cb8b59bf1ae` | FAILED, ursprüngliche Welt, leerer Modbestand und Betriebszustand bestätigt |
| Fixture 1.0.0 einspielen | `218d0174-f19d-44ae-824c-5a027fbb35b6` | SUCCEEDED |
| Auf Fixture 1.1.0 aktualisieren | `33ec64e7-7f48-479e-af58-e50b26c6cbac` | SUCCEEDED; Fabric hat die konkrete Mod-ID und Version im isolierten Probestart geloggt |
| Über den Portal-Planungsweg entfernen | `3f346f2c-6790-4d17-a6af-225cc0b5139a` | SUCCEEDED; verwalteter Modbestand danach wieder leer |

Die zusätzliche Welt-Beweisdatei ist nach Rückweg, Update und Entfernen weiterhin vorhanden. Readiness, RCON und Idempotenz wurden geprüft. Die tatsächlich verwendeten Probecontainer sind gestoppt, `network=none`, `ports={}`. Vorabsicherungen, vollständige Rückwege und Job-/Agent-Ledger bleiben erhalten.

Nach Abschluss sind `agent.test-mods.fixtures-enabled=false` und `agent.test-mods.fault=none` gesetzt. Detaillierte private Belege liegen unter `.tools/rcon-live-20261025/mod-*-acceptance.json`; Paketinhalte, Secrets und Weltdaten gehören nicht in das Repository.

## Vollständiger Import eines manuellen Bestands

Zusätzlich wurde eine zweite selbst erstellte codefreie Fabric-Fixture als `manual-import-fixture.jar` ausdrücklich manuell abgelegt. Das zuvor leere Manifest blieb reversibel als gesonderte Datei erhalten. Der normale Portal-Neustart `4716426e-d833-46ed-a98b-a19f0ecae721` bestätigte anschließend den tatsächlichen Zustand `MANUAL_MODS_REQUIRE_IMPORT`.

| Prüfung | Nachweis |
|---|---|
| Falscher Dateihash | HTTP 422, Inventarrevision unverändert, keine Serveränderung |
| Fehler nach Import-Datentausch | Job `756634d5-2891-4269-a981-b09a3cb55c0a`: FAILED; ursprünglicher **unverwalteter** Bestand, Welt und Betriebszustand wiederhergestellt |
| Vollständiger Import | Job `1447b515-c68a-477a-babb-b1d25fffc337`: SUCCEEDED; unveränderte Datei-Bytes als gepinnter verwalteter Satz, Readiness und RCON bestätigt |
| Entfernung über die tatsächliche Browseroberfläche | Plan `6f217923-1d25-484b-bb0f-d7f01c0747cf`, Job `45254026-f32d-48b7-8c3b-e994bbe38d0a`: SUCCEEDED; aktiver verwalteter Bestand wieder leer |

Der Import-Replay mit demselben Idempotency-Key lieferte jeweils denselben Job. Die ursprüngliche manuelle JAR ist im erhaltenen vorherigen vollständigen Datenstand mit identischem SHA-256 vorhanden. Die unabhängige Welt-Beweisdatei blieb erhalten. Der Fixture-Testschalter ist nach Abschluss wieder deaktiviert, Fehlerauslösung ebenfalls aus. Belege: `.tools/rcon-live-20261025/import-*-acceptance.json`.

Die BFF-Zuordnung an echte CurseForge-Dateien ist zusätzlich mit ausdrücklich benannten Unit-Fixtures geprüft: vollständige Zuordnung, Abweisung bei fehlenden/doppelten Zuordnungen oder Hashabweichung sowie Abweisung eines widersprüchlichen Agent-Zielmanifests. Die Host-Abnahme nutzt private lokale Fixture-Pakete und den normalen Portal-Ausführungsjob. Ohne echten API-Schlüssel ist damit weiterhin **keine externe CurseForge-Suche oder echte CurseForge-Importdatei** abgenommen. Die Oberfläche zeigt diesen unkonfigurierten Zustand ausdrücklich.

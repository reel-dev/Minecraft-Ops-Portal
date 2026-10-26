# Bestätigte lokale Wiederherstellung

Die beschriebenen Schreibabläufe gelten ausschließlich für `elbracht-smp-control-test-minecraft`, dessen eigenes Testvolume und das gepinnte Testimage. `minecraft-server` auf `docker-01` wurde nicht angesprochen oder verändert. Der normale Agentmodus bleibt getrennt bzw. lesend.

## Ablauf in der Anwendung

1. Eine vollständige verschlüsselte Sicherung erstellen. Teilweise fehlgeschlagene Sicherungen werden nicht als erfolgreich registriert.
2. Einen Proberestore durchführen: Restic stellt in ein neues Verzeichnis mit `--verify` wieder her. Die gesicherte Welt startet in einem eigenen Container ohne Netz und veröffentlichte Ports; Minecraft-/Fabric-Profil und Readiness werden geprüft. Der Probecontainer wird sauber gestoppt.
3. Admin erstellt einen Restoreplan für einen Snapshot mit bestätigtem Vollumfang und Weltstart. Er enthält Snapshot, Zielprofil, Umfang und das Verhalten nach dem Restore; er gilt 15 Minuten. Zielidentität und vorbereiteter Dateibestand werden gehasht. Es können höchstens zwei offene Pläne existieren.
4. Die Oberfläche zeigt den überschriebenen Umfang und verlangt Begründung sowie `WIEDERHERSTELLEN`. Ausführung ist ein normaler dauerhafter, pro Server gesperrter und idempotenter Job.
5. Agent prüft Plan, Zielidentität und Dateidigest erneut. Vor jedem Austausch entsteht eine neue konsistente, verschlüsselte und vollständig gelesene Vorabsicherung. Der Testserver bleibt für den Wechsel gestoppt.
6. Der bisherige Datenbestand wird auf dem Testvolume als Rückweg erhalten. Erst danach wird der vorbereitete Stand eingesetzt. Die aktuellen RCON-Zugangsdaten werden beibehalten.
7. Bei geplantem Wiederanlauf müssen Minecraft 26.3, Fabric 0.19.5 und RCON bestätigt werden. Bei Fehler versucht der Agent den vorherigen Daten- und Betriebszustand wiederherzustellen. Nur nach geprüftem Rückweg lautet das Ergebnis FAILED mit bestätigtem Originalzustand; sonst UNKNOWN mit weiter aktiver Sperre.

Pläne lassen sich vor Ausführung abbrechen. Die Hintergrundbereinigung entfernt ausschließlich private Planverzeichnisse unter dem konfigurierten Stagingverzeichnis: abgebrochene/abgelaufene Pläne sowie abgeschlossene Pläne nach sieben Tagen. Aktive und ungeklärte Vorgänge sowie auf dem Testvolume erhaltene Rückwege werden dabei nicht gelöscht. Vertrauliche Dateien werden nicht an Angular ausgegeben.

## Echte Abnahme am 25.10.2026

| Nachweis | Ergebnis |
|---|---|
| Quellsnapshot | `832970d0c18b306b810b74c30edfbf76ebbd9785f3cc0225ec09c95a0e457942` |
| Proberestore mit isoliertem Weltstart | `0cbf59d2-cfe6-471c-97fa-2acf2bec85b5`, erfolgreich |
| Absichtlicher Fehler nach Datenwechsel | `fd6a8a2a-3479-4857-9188-5d367b24280f`, FAILED / bestätigter Rückweg |
| Erfolgreicher vollständiger Restore | `6eb28489-29e4-4914-b56c-f9794d4880af`, SUCCEEDED |
| Unabhängige Dateiprüfung | Neuere Beweisdatei nach Rückweg vorhanden; nach erfolgreichem Restore im aktiven Snapshotstand nicht vorhanden und in der erhaltenen vorherigen Welt vorhanden |
| Readiness | Gesunder laufender Container, Minecraft 26.3/Fabric 0.19.5 und RCON nach beiden Vorgängen bestätigt |
| Idempotenz | Erneute erfolgreiche Restoreanfrage mit gleichem Schlüssel liefert denselben Job ohne weiteren Austausch |

Der lokale Fehlerschalter `agent.test-restore.fault=after-swap` war nur während der Rückwegabnahme aktiv und ist anschließend auf `none` gesetzt worden. Ohne `agent.test-restore.enabled` und `portal.test-restore-enabled` ist der Ablauf nicht verfügbar. Host-Leseprofile erhalten keine Schreibrechte.

## Grenzen und Wiederanlauf

Ein Prozessabbruch während des Austauschs wird nicht automatisch als Erfolg oder bestätigter Rückweg gedeutet. Die dauerhaften Job-/Agent-Ledger behalten den ungeklärten Zustand und die Sperre. Datenkopien, Vorabsicherung, Plan und Swapjournal bleiben für die Untersuchung erhalten. Vor weiteren Mutationen ist der tatsächliche Daten- und Betriebszustand zu prüfen; keine Ledgerlöschung, SQL-Entsperrung oder blinde Wiederholung.

Die Abnahme umfasst die Minecraft-Daten im lokalen verschlüsselten Repository. Cloudkopie, Portal-Datenbank-/Secret-Wiederanlauf und Produktionsinstallation sind damit nicht bestätigt. Detaillierte private Laufzeitbelege befinden sich unter `.tools/rcon-live-20261025/`; Schlüssel und Weltdaten gehören nicht in das Repository.

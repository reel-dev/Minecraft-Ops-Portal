# Aufbewahrung und überprüfte Bereinigung

Stand: 26.10.2026. Die Aufbewahrung ist implementiert und standardmäßig **ausgeschaltet**. Der aktuelle Adapter gilt ausschließlich für das feste lokale Test-Backuprepository. Weder das produktive Minecraft auf `docker-01` noch dessen Backups wurden geöffnet oder verändert. Ein Cloudziel ist damit weiterhin nicht eingerichtet.

## Regel und Vorschau

Die Entscheidung verwendet die tatsächlichen Restic-Snapshotzeiten und UTC-jetzt. Erfolgreiche Sicherungen innerhalb der letzten **7 × 24 Stunden einschließlich der Grenze** bleiben erhalten. Die letzte bestätigte erfolgreiche Sicherung bleibt auch bei einem längeren Ausfall geschützt; eine veraltete oder fehlende Sicherung wird in der Vorschau ausdrücklich angezeigt.

Nur im Agent-Ledger als `READ_DATA_OK` erfasste und tatsächlich im Repository vorhandene Snapshots mit dem festen Server-Tag `smp:main` kommen infrage. Nicht verwaltete Snapshots werden behalten. Fehlende bekannte Snapshots, ungültige Kennungen, doppelte Einträge, widersprüchliche Zuordnung oder deutlich zukünftige Zeiten blockieren die Bereinigung. Es gibt keine Umdeutung einer fehlgeschlagenen Bestandsprüfung in eine leere Liste.

Aktive Restore-/Modpläne schützen ihre Snapshot- und Vorabsicherungsreferenzen. Restore-Planung und tatsächliche Bereinigung verwenden innerhalb der einzigen Agentinstanz dieselbe Repositorysperre. So kann die Bereinigung nicht zwischen dem Staging einer Wiederherstellung und dem Speichern ihrer Referenz eingreifen.

Admins sehen unter **Backups → Aufbewahrung prüfen → Nur Vorschau anzeigen** den gemessenen Grenzzeitpunkt, die Kandidaten, die Zahl erhaltener und nicht verwalteter Snapshots. `GET /api/v1/servers/{id}/backups/retention` ist ausschließlich für serverbezogene Admins freigegeben. Dieser GET-Aufruf löscht nichts. Operatoren können weiter normale Backups auslösen, aber weder die Regel ändern noch die Bereinigung über einen gesonderten API-Aufruf aktivieren.

## Aktivierung und Ablauf

```yaml
agent:
  test-backup:
    retention-enabled: false
```

Nur ein Administrator des Deployments kann diese feste Hostkonfiguration aktivieren. Nach Aktivierung ist der Ablauf Teil eines normalen Backupjobs:

1. Neue vollständige Sicherung erstellen und `check --read-data` erfolgreich abschließen.
2. Prüfen, dass diese neue Sicherung tatsächlich die jüngste bestätigte Sicherung ist. Vorabsicherungen laufender Mod-/Restoreänderungen lösen keine Bereinigung aus.
3. Unveränderten Repositorybestand und Schutzreferenzen prüfen. Kandidaten in begrenzten Batches zunächst mit `forget --dry-run` prüfen.
4. Bestand, jüngste Sicherung und aktuelle Schutzreferenzen unmittelbar vor jeder tatsächlichen Entfernung erneut prüfen.
5. Nur die konkreten bestätigten Snapshot-IDs entfernen. Erwarteten Restbestand einschließlich der jüngsten Sicherung abgleichen; bestätigte Entfernungen im Ledger als `EXPIRED` markieren.
6. Nicht mehr referenzierte Repositorydaten mit `prune` bereinigen und anschließend erneut `check --read-data` ausführen.

Der Retention-Lauf speichert die gemeinsame Backup-Job-ID, Ausgangssnapshot, Inventarrevision, vorgesehene Kandidaten und bestätigte Entfernungen im Agent-Ledger. Der normale Portaljob und Auditablauf bleiben erhalten. Scheitert ein Schritt, wird **kein vollständiger Erfolg** behauptet. Der Lauf bleibt UNKNOWN; nach Prozessabbruch ebenfalls UNKNOWN, ohne automatische Wiederholung. Eine bereits bestätigte lokale Sicherung bleibt im Inventar sichtbar, auch wenn die nachgelagerte Bereinigung ungeklärt ist. Ein ungeklärter Backupjob sperrt weitere Mutationen bis zur tatsächlichen Prüfung.

Die letzte Entfernungszahl bedeutet nur „bestätigte Entfernungen“. Bei UNKNOWN ist sie kein Beleg, dass darüber hinaus nichts geändert wurde. Es gibt keinen automatischen `unlock`- oder Wiederholungsweg. Der aktuelle Bestandsabgleich ist auf 5.000 Snapshots begrenzt; größere oder unvollständig gelieferte Bestände werden abgewiesen.

Die Umsetzung verwendet absichtlich konkrete IDs und UTC-Zeitpunkte. Restics relative Gruppenregeln allein entsprechen nicht der geforderten Wanduhrregel. Grundlagen: [Restic: Entfernen und Prune](https://restic.readthedocs.io/en/stable/060_forget.html), [Repositoryprüfung](https://restic.readthedocs.io/en/stable/045_working_with_repos.html).

## Tatsächliche Prüfung

Die regulären Tests prüfen Zeitgrenze, langen Ausfall, unbekannte und referenzierte Snapshots, widersprüchlichen Bestand, fehlgeschlagene Datenprüfung, neue Restore-Referenz während der Vorschau, unterbrochenes Prune, Wiederanlauf ohne Wiederholung und gemeinsame Repositorysperre. Der serverbezogene Adminschutz der reinen Vorschau ist im Backend geprüft.

Die opt-in Prüfung `BackupRetentionLiveTest` erzeugt **ein eigenes kurzlebiges verschlüsseltes Repository ausschließlich mit synthetischen Textdateien**. Sie bestätigt mit Restic 0.19.1:

- eine abgelaufene bestätigte Fixturesicherung wird entfernt;
- ein älterer nicht verwalteter Snapshot bleibt erhalten;
- der jüngste bestätigte Snapshot bleibt erhalten;
- Prune und vollständige Datenprüfung sind erfolgreich;
- die jüngste Sicherung lässt sich danach mit `restore --verify` wiederherstellen; der tatsächliche Textinhalt stimmt;
- Entfernungsstatus und konkrete IDs sind dauerhaft erfasst.

Diese Probe öffnet weder Docker noch das bestehende Minecraft-Testrepository. Privater Beleg: `.tools/rcon-live-20261025/retention-real-repository-proof.json`. Aktivierung nur mit `SMP_RETENTION_LIVE_TEST=true` und dem Pfad zum vorher geprüften offiziellen Restic-Programm in `SMP_RETENTION_TEST_EXECUTABLE`. Ohne diese Variablen ist die Probe übersprungen. Die normalen vier Minecraft-Live-Proben bleiben davon getrennt.

Das vorhandene Restic-ZIP `restic_0.19.1_windows_amd64.zip` besitzt den veröffentlichten SHA-256 `da948ad707ed690426473aaba2046cd61f8f90f6f0e7dab6be0d5796531de67d`. Die entpackte EXE wurde zusätzlich bytegenau gegen den Eintrag in diesem geprüften Archiv abgeglichen; ihr SHA-256 lautet `b0dd1fd21eea5d8fe1325f55f7118213c21f36de8a261e04c0624a5ab9fd7830`. Der ZIP-Hash ist nicht der EXE-Hash.

Cloud-Replikation und ihre eigene Aufbewahrung sowie Portal-Datenbank-/Secret-Wiederherstellung bleiben gesonderte offene Schritte.

## Bestehende lokale Minecraft-Testumgebung

Die normale Vollsicherung wurde nach dem Ausbau tatsächlich über die Browseroberfläche ausgeführt: BACKUP `a930106f-be35-4ab3-bfd9-81b978311bb0`, SUCCEEDED, Snapshot `b72944aa3c0e3dd1b8c04f86154cecbf70a23ca0c665ba0cd4f9f4cd8585f8fd`. Die automatische Bereinigung blieb ausdrücklich ausgeschaltet. Alle zwölf vorherigen Snapshot-IDs sind danach weiter vorhanden; genau eine neue Sicherung ist hinzugekommen. Die reine Vorschau bestätigt 13 erhaltene Sicherungen und keine abgelaufenen Kandidaten. Minecraft 26.3/Fabric 0.19.5, RCON und die zusätzliche Welt-Beweisdatei sind bestätigt.

Privater Beleg: `.tools/rcon-live-20261025/retention-runtime-acceptance.json`. Damit sind der neue begrenzte Restic-Prozesstransport, normale Backupablauf und die tatsächliche Vorschau am Minecraft-Testziel geprüft. Die echte Entfernung/Prune-Probe bleibt vollständig auf das gesonderte synthetische Repository beschränkt. Kein bestehender Minecraft-Snapshot wurde gelöscht.

Bei der Browserabnahme wurde außerdem die verzögerte Aktivierung der Bestätigungsknöpfe behoben: Begründung und Restore-Bestätigung verwenden jetzt ausdrückliche Eingabezustände. Das Backupformular hat unmittelbar auf die Eingabe reagiert; die erfolgreiche Sicherung wurde darüber ausgelöst.

# Backup- und Restore-Konzept

Lokale Umsetzung und tatsächliche Nachweise: [vollständige Restore-Abnahme](21-wiederherstellung-abnahme.md), [tägliche Planung](23-backup-zeitplan-abnahme.md), [siebentägige Aufbewahrung](24-aufbewahrung-abnahme.md). Externes Cloudziel und Portal-/Secret-Wiederherstellung sind weiterhin offen; die nachstehenden Vorgaben bleiben der vollständige Zielumfang.

Vorgabe: Cloud-Backups mit sieben Tagen Aufbewahrung, Restic als Werkzeug. Anbieter und Repositoryziel sind offen. Vorschlag: täglich 03:00 Europe/Berlin plus manuell und vor Änderungen. RPO 24 Stunden, RTO zunächst 60 Minuten als zu messende Zielwerte. Zeitplanung muss Sommer-/Winterzeit eindeutig behandeln und einen täglichen Lauf sicherstellen.

## Umfang

Sichern: Weltdaten aller Dimensionen, erlaubte Serverkonfiguration, Whitelist/OP-Daten, Modmanifest mit CurseForge-Datei-IDs, Modkonfiguration und benötigte Moddateien soweit zulässig, Deployment-Metadaten, Portal-Datenbank über konsistenten Dump sowie vorhandene serverseitige Karten-/Xaero-Daten nach Bestandsprüfung. Clientlokale Xaero-Karten sind nicht automatisch im Serverbackup enthalten. Portal-Sicherungen und Weltsicherungen getrennt inventarisieren und gemeinsam wiederherstellbare Versionen dokumentieren.

Secrets gehören in einen separaten verschlüsselten Wiederanlaufprozess, mit gesichertem Zugriff auf Restic-Passwort und Cloud-Zugang. Ohne extern gesicherten Entschlüsselungszugang ist das Cloudbackup im Notfall unbrauchbar. Keine Secrets in Modmanifest oder allgemeinem Dokumentationspaket.

## Konsistenter Ablauf

1. Serverbezogene Mutationssperre nehmen, Speicherplatz und Repository-Erreichbarkeit prüfen.
2. MVP-Standard: kurzer geplanter Stillstand, Minecraft sauber stoppen und tatsächliches Prozessende bestätigen. Dann das unveränderte Datenvolume sichern. Falls Stopp scheitert, keine konsistente Sicherung behaupten.
3. Nach erfolgreicher lokaler Aufnahme den vorherigen Betriebszustand wiederherstellen. Nicht versehentlich einen vorher ausgeschalteten Server starten.
4. Cloud-Erfolg, Snapshot-ID, Umfang, Dauer und Integritätsprüfung erfassen; Fehler alarmieren. Sperren kontrolliert lösen, keine falsche Erfolgsmeldung.

Spätere Online-Optimierung: RCON `save-off`, `save-all flush`, bestätigten Flush abwarten, schnellen konsistenten Dateisystem-Snapshot erzeugen und `save-on` im garantiert ausgeführten Cleanup/Watchdog wiederherstellen. Restic liest anschließend den unveränderlichen Snapshot. Ein einfacher langlaufender Restic-Lauf auf einer aktiv beschriebenen Welt ist keine zugesicherte konsistente Sicherung. Ohne Snapshot-Unterstützung bleibt der Offline-Weg Standard. Diese Optimierung vor Einsatz separat testen.

## Sieben Tage Retention

Produktregel: erfolgreiche Sicherungen innerhalb der letzten 7 × 24 Stunden erhalten, letzte erfolgreiche Sicherung als Sicherheitsausnahme auch bei längeren Ausfällen behalten und dann alarmieren. „Sieben tägliche Sicherungen“ ist bei Ausfällen nicht dasselbe wie ein Sieben-Tage-Zeitfenster.

Restic trennt das Entfernen von Snapshot-Metadaten (`forget`) und Speicherbereinigung (`prune`). Zeitbezogene `keep-within`-Regeln beziehen sich auf den jüngsten Snapshot der Gruppe; das ist kein universeller harter Ablauf nach Wanduhrzeit. Ein Umsetzungsplan muss deshalb Snapshotzeiten gegen UTC-jetzt prüfen, pro stabilem Server-Tag gruppieren und die Sicherheitsausnahme ausdrücklich bewahren. Vor Aktivierung Löschkandidaten im Dry Run überprüfen, neueste Sicherung schützen und bei Cloud-/Backupfehlern keine automatische Bereinigung starten. Prune außerhalb des Backupfensters planen; Repository-Locks beachten. [Restic-Dokumentation](https://restic.readthedocs.io/en/stable/060_forget.html).

Provider-Lifecycle-Regeln dürfen keine noch referenzierten Restic-Objekte eigenständig löschen. Optionaler Schutz gegen Löschung erfordert ein mit Restic getestetes Object-Lock-/Berechtigungskonzept; separate Schreib-/Prune-Rechte erwägen. Keine harte Löschgarantie bei aktivem Object Lock versprechen.

## Restore-Runbook

1. Admin wählt Snapshot; Backend prüft Existenz, Integrität, Serverzuordnung und Versions-/Modmanifest.
2. Plan zeigt Ziel, Zeitpunkt, überschriebenen Datenumfang und Stillstand. Bestätigung bindet sich an genau diesen Plan.
3. Sperre, erfolgreiche Vorabsicherung, sauberer Stop. Bei gescheiterter Vorabsicherung abbrechen.
4. Zunächst in isoliertes Staging wiederherstellen, Pfade/Rechte/Platz prüfen. Keine laufende Welt überschreiben.
5. Kontrolliert Daten austauschen, passende Mods/Konfiguration und Imageversion herstellen. Alte Daten bis zur Abnahme als Rückweg behalten.
6. Start, Readiness und Stichprobe der Welt. Bei Fehlschlag nicht in eine Neustartschleife geraten; vorherigen konsistenten Stand wiederherstellen und Zustand melden.

Monatlicher Restoretest in getrenntem Verzeichnis/Container, zusätzlich nach wesentlichen Versionswechseln. Abnahme dokumentiert Weltstart, Spieler-/Whitelistdaten, Modstand und tatsächliche Wiederherstellungsdauer. Proxmox-Snapshots ergänzen dieses Konzept; sie ersetzen das externe Cloudbackup nicht.

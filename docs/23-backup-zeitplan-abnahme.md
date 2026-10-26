# Tägliche Backup-Planung

Die automatische Planung ist standardmäßig **ausgeschaltet**. Sie ist eine Portal-Konfiguration und erteilt dem Host-Agent keine zusätzlichen Rechte. Ein freigeschalteter Backupanschluss bleibt Voraussetzung. Der aktuelle schreibende Anschluss ist weiterhin ausschließlich das feste isolierte lokale Testziel. Produktion wurde nicht kontaktiert oder verändert.

## Konfiguration

```yaml
portal:
  backup-schedule:
    enabled: false
    server-id: main
    daily-time: '03:00'
    zone: Europe/Berlin
    dispatch-window: 2h
```

Die Uhrzeit ist ein Vorschlag und wird nicht durch das bloße Öffnen der Oberfläche aktiviert. Ein Administrator konfiguriert sie beim Deployment. Der Operator kann die bestätigte Konfiguration über `GET /api/v1/servers/{id}/backup-policy` lesen; Viewer und fremde Konten haben keinen Zugriff. Die Antwort enthält keine Zugangsdaten oder Repositorypfade. Es gibt keine schreibende Zeitplan-API im Browser.

Der Planer prüft nach dem Portalstart alle 30 Sekunden auf den fälligen Tagestermin. Er verwendet die konfigurierte Zeitzone und höchstens das eingestellte Nachholfenster am aktuellen Kalendertag. Vor dem Termin, nach Ablauf des Fensters und für ältere Kalendertage werden keine neuen Aufträge erzeugt. Verpasste Fenster sind als `MISSED_WINDOW` sichtbar. Bei einem Sommerzeit-Sprung wird eine nicht vorhandene Uhrzeit nach vorn verschoben; bei einer doppelt vorkommenden Uhrzeit zählt der erste Zeitpunkt. Beide Fälle verwenden genau einen Tages-Schlüssel.

## Dauerhafter Auftrag und Fehler

- Actor `system:backup`, Typ `BACKUP`, Schlüssel `daily-backup_YYYY-MM-DD`.
- Die normale SQL-Idempotenz, gemeinsame Serversperre, Jobverwaltung und Audit bleiben verpflichtend.
- Ein belegter Server kann innerhalb des Nachholfensters erneut auf einen freien Platz geprüft werden. Dabei entsteht kein zweiter Schlüssel.
- Ein bereits erzeugter Tagesauftrag wird nach Prozessneustart wiedergefunden. FAILED oder UNKNOWN wird nicht erneut ausgeführt; UNKNOWN hält die Serversperre.
- `DISPATCHED` bedeutet „Auftrag angelegt oder wiedergefunden“, **nicht** „Backup erfolgreich“. Das Ergebnis und der konkrete Snapshot stehen im Jobverlauf bzw. der Backupansicht.
- Planung und Messwerterfassung besitzen eigene Ausführungsthreads; ein längerer Minecraft-Job blockiert die Terminprüfung nicht.

## Nachweise vom 26.10.2026

Neun zusätzliche automatische Prüfungen decken deaktivierte/fehlende Quelle, Zeitfenster, Sommerzeit, stabilen Schlüssel, belegte Server, SQL-Persistenz, UNKNOWN nach Wiederanlauf und Zugriffsschutz ab. Die SQL-Prüfung erzeugt echte lokale H2-Jobs, startet den Planer neu und bestätigt denselben gesperrten UNKNOWN-Job ohne neue Mutation.

Im lokalen Testportal wurde vorübergehend ein heutiger Termin konfiguriert. Der Planer erzeugte tatsächlich BACKUP `630c55db-858c-4799-b2fa-32d75e5ac294`, Actor `system:backup`, Ergebnis SUCCEEDED. Vollsnapshot `2b201773e9459f73da85b8fbdcb4012654492df784e4ab556a0fbbc29244e37b`, Umfang `LOCAL_ENCRYPTED_FULL_MINECRAFT_DATA`, Integrität `READ_DATA_OK`. Minecraft war anschließend wieder spielbereit. Dieser Nachweis ist eine **lokale verschlüsselte Sicherung**, keine Cloudkopie.

Nach tatsächlichem Portal-Prozessneustart hat der aktivierte Planer denselben Job wiedergefunden; die Zahl der Tagesaufträge blieb eins. Danach wurde die temporäre Planung ausgeschaltet und das Portal erneut gestartet: API bestätigt `enabled=false`/`DISABLED`, derselbe erfolgreiche Snapshot bleibt vorhanden. Belege: `.tools/rcon-live-20261025/backup-schedule-collect-proof.json`, `backup-schedule-replay-proof.json` und `backup-schedule-disabled-proof.json`. Diese privaten Dateien gehören nicht in Git. Cloudziel, tatsächliche siebentägige Retention und gesonderte Portal-/Secret-Wiederherstellung sind weiterhin offen.

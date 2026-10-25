# Erfolgreicher Proxmox-Backuplauf laut übermitteltem Screenshot

Quelle: Nutzer-Screenshot des Task viewers „VM/CT 100 - Backup“, übermittelt am 25.10.2026. Kein eigener Hostzugriff.

Sichtbare Abschlussmeldungen:

```text
INFO: backup is sparse: 227.28 GiB (90%) total zero data
INFO: transferred 250.00 GiB in 127 seconds (2.0 GiB/s)
INFO: archive file size: 11.43GB
INFO: adding notes to backup
INFO: Finished Backup of VM 100 (00:02:07)
INFO: Backup finished at 2026-10-25 11:22:09
INFO: Backup job finished successfully
INFO: notified via target `mail-to-root`
TASK OK
```

Einordnung: Proxmox meldet erfolgreichen VM-Backuplauf. Zeitangabe ohne nachgewiesene Zeitzone, daher keine Umrechnung in UTC. Ziel `local` und Snapshot/ZSTD stammen aus dem zuvor gezeigten Dialog; Archivpfad und vollständiges Tasklog wurden nicht geliefert. Benachrichtigungsmeldung bestätigt keinen gelesenen oder zugestellten Mailinhalt.

Noch offen: Backup-Einschluss aller relevanten Disks, Zuordnung der Minecraft-Bind-Mountquelle zum gesicherten Dateisystem, anwendungskonsistenter Weltzustand, Integritäts-/isolierter Restoretest und unabhängige externe Kopie. Erfolg des Jobs ist keine vollständige Wiederherstellungsabnahme. Keine Servermutation durch das Portal.

Nachtrag: Die [nachgereichte Dateisystemabfrage](docker-01-data-filesystem.supplied.txt) ordnet die Minecraft-Bind-Mountquelle dem VM-Root-Dateisystem `/dev/sda2` zu. Weltkonsistenz, Integritätsprüfung, externe Kopie und Restore bleiben ungeprüft.

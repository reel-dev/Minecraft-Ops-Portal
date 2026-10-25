# Übermittelte Proxmox-Ansichten

**Nachtrag:** Der anschließend vom Nutzer gestartete VM-Backuplauf ist inzwischen [mit TASK OK abgeschlossen](docker-01-backup-success.supplied.md). Die folgenden Absätze dokumentieren die vorherigen Erfassungsschritte; fehlender Backupnachweis dort beschreibt den jeweiligen damaligen Stand. Umfang, Weltkonsistenz, externe Kopie und Restore bleiben ungeprüft.

Quelle: zwei vom Nutzer am 25.10.2026 angehängte Screenshots; keine direkte Verbindung zum Host. Aufnahmezeit und Anzeigezeitzone nicht bekannt. Nutzer: Vorhandene Sicherung unbekannt, Snapshots vorhanden, Aktualität unbekannt.

- Oberfläche zeigt Proxmox VE 9.2.3, Knoten `pve`, **Virtual Machine 100 (docker-01)**. Damit ist die bisher offene VM/LXC-Frage anhand der Ansicht als VM eingeordnet.
- Snapshotliste zeigt drei Einträge mit RAM: 2026-06-27 23:58:56, 2026-06-28 01:28:39 und 2026-06-28 02:33:07. Die Markierung NOW ist die aktuelle Position im Baum, kein zusätzlicher datierter Snapshot.
- Die gezeigte Backupansicht für VM 100 enthält keine Einträge. Das beweist nicht, dass außerhalb dieser Ansicht keine Sicherungen existieren. Backupjobs, andere Speicher, Aufbewahrung und Restore wurden nicht geprüft.

Ergebnis: Kein aktuelles, unabhängiges und wiederherstellbar geprüftes Backup nachgewiesen. Nicht belegt, ob der produktive Minecraft-Datenbestand bereits in den Juni-Snapshots enthalten war. Snapshots wurden weder erstellt noch zurückgerollt. Keine Backupaktion ausgeführt.

Nachgereichter Screenshot des Backupdialogs für VM 100: Storage `local`, Mode `Snapshot`, Compression `ZSTD (fast and good)`, Notification `Use global settings`, Protected nicht angehakt. Der Dialog belegt ein angebotenes Backupziel, keinen erfolgreichen Lauf. Freier Speicher, physische Trennung vom VM-Datenträger, enthaltene VM-Disks, Gastagent und Minecraft-Konsistenz sind nicht geprüft. „Snapshot“ ist hier der Live-Backupmodus und nicht gleichbedeutend mit einem bestehenden VM-Snapshot. Noch nicht starten; zunächst Kapazität von `local (pve)` und danach Umfang/Platzbedarf prüfen.

Weiterer Screenshot: `local (pve)` ist aktiviert und aktiv, Typ Directory, zulässige Inhalte einschließlich Backup. Anzeige: 6,70 % bzw. 6,76 GB von 100,86 GB belegt, rechnerisch etwa 94,10 GB frei zum Aufnahmezeitpunkt. Daraus folgt noch keine ausreichende Kapazität für VM 100: Anzahl/Größen und Backup-Einschluss ihrer virtuellen Datenträger sind noch zu prüfen. Physische Trennung und Restore bleiben offen. Nächste lesende Ansicht: VM 100 → Hardware. Keine Backupaktion ausgeführt.

Nachgereichte Hardwareansicht für VM 100: RAM 12,00 GiB (balloon=0), vier CPU-Kerne (host), OVMF/UEFI, q35, VirtIO SCSI single. Eine Systemdisk `scsi0` auf `local-lvm`, angezeigte Größe `250G`, discard=on und iothread=1; EFI-Disk auf `local-lvm`, angezeigte Größe `4M`. CD/DVD ohne Medium. Die Ansicht enthält keine belegten Gastdateisystemgrößen und keine verbindliche Backupgrößenschätzung. Die etwa 94,10 GB freien Zielplatzes reichen nicht als Nachweis für ein vollständiges Backup dieser VM; Kompression darf nicht als garantierte Platzersparnis eingeplant werden. Nächste lesende Prüfung: `df -h /` in docker-01. Datenmountquelle und mögliche externe Dateisysteme sind weiterhin gesondert zuzuordnen. Keine Hardware- oder Backupänderung ausgeführt.

Nachgereichte [Gastbelegung](docker-01-disk-usage.supplied.txt): `/dev/sda2`, 235G Gesamtgröße, 21G benutzt, 202G verfügbar, 10 %, eingehängt auf `/`. Das spricht für mögliche ausreichende Kapazität für eine erste VM-Sicherung auf local, garantiert deren Größe aber nicht. Ein Live-Backup im Modus Snapshot kann als zusätzliche VM-Sicherung erfolgen, ohne Minecraft gezielt zu stoppen; es ist kein abgenommener konsistenter Minecraft-Weltbackup-/Restorepfad. Abschluss, Zielrestplatz und enthaltene Datenträger sind danach zu prüfen. Bisher kein erfolgreicher Lauf belegt. Externes Ziel, Datenpfadzuordnung und isolierter Restore bleiben offen.

Nächster Schritt: Nur den Dialog „Backup now“ öffnen, verfügbaren Zielspeicher und dessen Kapazität prüfen. Noch kein Backupstart und kein VM-Stopp. Vor einer konsistenten Minecraft-Sicherung Datenpfad/Backupumfang und Vorgehen mit geplantem Stillstand oder erprobtem Flush-/Snapshotablauf klären.

Technische Grundlage: [Proxmox Backup und Restore](https://github.com/proxmox/pve-docs/blob/master/vzdump.adoc). Proxmox unterscheidet VM-Snapshots von Backups auf einem Backupziel. Live-Backup im Modus snapshot ist kein Nachweis für anwendungskonsistente Minecraft-Daten.

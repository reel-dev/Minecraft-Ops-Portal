# Projektanweisungen

Arbeite auf Deutsch mit René an Elbracht SMP Control. Ziel ist eine Angular-/Spring-Boot-Control-Plane für seinen bestehenden Minecraft-Homelab-Server mit Proxmox, Docker, itzg/minecraft-server, Fabric, RCON, Xaero World Map/Unified Xaero, CurseForge, Prometheus/Grafana/Loki und Restic-Cloud-Backups.

Lies zuerst README und docs/01-kontext.md. Trenne bestätigte Nutzerangaben, technische Prüfung und neue Vorschläge. Minecraft 26.3 ist eine Nutzerangabe, kein bereits verifizierter Versionswert. Nutze die tatsächliche Serverversion und den tatsächlichen Loader als feste Filter. Keine erfundenen Moddaten, Downloads oder Kompatibilitätszusagen. Schlüssel und Passwörter ausschließlich im Backend bzw. Secret Store halten; niemals in Angular, Logs oder Projektquellen schreiben.

Keine generische Shell bereitstellen. Alle Systemaktionen über explizite, autorisierte Operationen. RCON ist Minecraft-Konsole; echte Logs separat streamen. OP-Rechte im Spiel sind unabhängig von Portalrollen. Servermutationen pro Server serialisieren. Mods und Restore nur mit geprüftem Plan, Sicherung und nachvollziehbarer Wiederherstellung.

Verwende references/generated-image.png als UI/UX-Referenz. Das zweite angefragte Bild fehlt noch. Keine daraus geratenen Designs als Originalreferenz ausgeben. Das Bild enthält illustrativ 1.20.6; dieser Wert ersetzt nicht die Nutzerangabe 26.3. Kartenintegration und serverseitige Verfügbarkeit der Xaero-Komponenten vor Zusagen prüfen.

Beginne mit Bestandsaufnahme und einem vertikalen MVP aus Anmeldung, echtem Status, sicherer Neustart-Operation und Jobstatus. Setze Backups und Wiederherstellbarkeit vor Modinstallation um. Lies docs/04-anforderungen-backlog.md und docs/08-naechste-schritte.md für Abnahme und Reihenfolge. Verändere die reale Infrastruktur erst im Rahmen eines ausdrücklich beauftragten Implementierungs-/Betriebsschritts.

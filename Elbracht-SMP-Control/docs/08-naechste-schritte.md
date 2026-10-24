# Nächste Schritte

## 1. Projektübernahme abschließen

Das neue ChatGPT/Work-Projekt muss noch in einer funktionierenden Oberfläche angelegt werden. Name: Elbracht SMP Control. Dieses Paket und das verfügbare Referenzbild übernehmen, Projektanweisungen setzen. Zweiten Screenshot aus dem Ursprungs-Chat exportieren oder erneut anhängen. Frühere Chatteile nur ergänzen, wenn sie tatsächlich verfügbar werden; der vorhandene Export wird nicht als Vollarchiv ausgegeben.

## 2. Bestandsabgleich

Mit redigierten Informationen arbeiten, keine Schlüssel in den Chat kopieren: Compose-Konfiguration ohne Secrets, Image-Digest, tatsächliche Minecraft-/Fabric-/Java-Versionen, Containername, VM-/LXC-Aufteilung und Mounts erfassen. mods-Verzeichnis inventarisieren, bekannte Projekte mit Dateihashes zuordnen und RCON-Erreichbarkeit intern prüfen. Vor Änderungen eine bestehende Sicherung prüfen.

## 3. Offene Entscheidungen treffen

| Entscheidung | Vorschlag | Noch zu klären |
|---|---|---|
| Umfang | Ein Server im MVP | Weitere Server absehbar? |
| Zugriff | Zunächst LAN/VPN | Öffentliche Erreichbarkeit benötigt? |
| Anmeldung | OIDC + Backend-Session | Vorhandener Identity Provider? |
| Betrieb | Separater begrenzter Host-Agent | VM/LXC und Hostgrenzen? |
| Cloud | Restic-kompatibles Ziel | Anbieter, Bucket, Kosten, Schutzrechte? |
| Sicherung | Täglich 03:00 Europe/Berlin | Wartungsfenster, Weltgröße, RPO/RTO? |
| Mods | Portal-Manifest, gepinnte Dateien | itzg-Verwaltung bereits aktiv? |
| Karte | Später, nach Quellprüfung | Welche Xaero-Komponenten genau? |
| Monitoring | Prometheus/Grafana/Loki | Vorhandene Installation/Exporter? |

## 4. Ersten vertikalen MVP implementieren

Framework-/Java-Versionen auswählen und pinnen. Angular-Grundlayout aus Bildreferenz, Spring-Boot-Anmeldung, Serverprofil, echter Statusadapter, begrenzte Restart-Operation und dauerhafter Jobstatus. Zunächst gegen einen getrennten Testserver prüfen. Keine produktive Serveradresse oder Zugangsdaten im Quellcode.

## 5. Daten schützen und Funktionsumfang ausbauen

Konsistentes Cloudbackup und isolierten Restoretest fertigstellen. Dann Spieler/RCON/Logs, Eigenschaften und Modmanagement in der Reihenfolge des Backlogs umsetzen. Jede neue privilegierte Aktion erhält Rollenprüfung, Audit, Sperre, Fehlerbehandlung und klare UI-Rückmeldung.

## Übergabe an eine nächste Arbeitssitzung

„Lies README, Projektanweisungen und docs/01-kontext.md von Elbracht SMP Control. Beginne mit dem Bestandsabgleich. Behandle 26.3 als ungeprüfte Nutzerangabe und den Screenshot ausschließlich als Gestaltungsvorlage. Implementiere anschließend M01–M04 gegen einen Testserver und dokumentiere offene Infrastrukturfragen. Verändere keine bestehende produktive Welt im Rahmen der Bestandsaufnahme.“

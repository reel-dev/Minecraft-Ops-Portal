# Nächste Schritte

## Vorrangiger Abgleich der Projektchats

Die zweite Anwendung aus „Weiter Minecraft Verwaltung“ wurde laut Nutzer noch nicht auf docker-01 gestartet. Ihr tatsächlicher Quellstand ist inzwischen separat abgerufen und lokal mit 20 erfolgreichen Tests gebaut. [Vergleich und Übernahmeplan](15-abgleich-projektchats.md): Dieses Angular-/Spring-Boot-Repository bleibt Produktbasis; RCON und Spielerabläufe werden gezielt integriert, bestehende Rollen, persistente Jobs, UNKNOWN-Sperre und Audit bleiben erhalten. Vor einem Produktionsdeployment zuerst den gemeinsamen Stand und seine Container prüfen. Weder die alte ZIP noch das vorbereitete Agentpaket jetzt parallel installieren.

## Aktuelle Priorität: docker-01 lesend erfassen

Das Grundgerüst und der vertikale Testpfad sind bereits implementiert. Status sowie Stop/Start/Restart wurden an einer separaten lokalen Testwelt erfolgreich geprüft; [Abnahme](13-testserver-abnahme.md). Der produktive Anschluss bleibt unverändert.

Container, Startversionen, VM 100, Datenquelle auf dem VM-Root-Dateisystem und ein erfolgreicher Proxmox-Backuplauf sind inzwischen anhand übermittelter Ausgaben/Screenshots dokumentiert. Restore und konsistente Weltsicherung bleiben offen. Ein eigener [Host-Lesemodus](../infra/docker-01/agent-read-only.md) ist lokal implementiert und geprüft. Als Nächstes Host-Java und Portbindung prüfen, danach den lesenden Dienst mit eigenem Token und sicherem Transport kontrolliert einrichten. Keine Testlabels auf Produktion setzen und keine produktive Steuerung aktivieren.

Nachtrag: Host-Java ist nicht im PATH verfügbar, Port 25565 ist über IPv4-/IPv6-Wildcard veröffentlicht. Eine [eigene Containerbereitstellung](../infra/docker-01/container/README.md) mit Java und gefiltertem Dockerzugriff ist vorbereitet und als Paket erzeugt; Compose-Syntax geprüft. Die lokale Docker-Desktop-Instanz scheiterte beim Start mit einer gesperrten `sailor-ingest.sock`-Datei. Kein automatischer Neustart-/Resetversuch; Containerbuild und Laufzeittest bleiben offen. Kein Deployment auf docker-01.

Konten und Hosting werden [getrennt behandelt](14-konten-und-hosting.md): Mitverwalter brauchen nur Zugriff auf einen registrierten Server. Selfservice-Registrierung weiterer Hostinganschlüsse ist ein späterer Schritt.

Die folgenden Punkte sind der ursprüngliche Gesamtplan; bereits implementierte Teile werden im [aktuellen Stand](10-implementierungsstand.md) ausgewiesen.

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

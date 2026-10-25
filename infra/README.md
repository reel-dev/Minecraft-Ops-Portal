# Infrastruktur-Arbeitsbereich

Noch keine ausrollbare Compose-Konfiguration. Zuerst bestehende VM-/LXC-/Docker-Topologie und Datenmounts erfassen. Danach getrennte Netze für Reverse Proxy, Backend, Agent/Minecraft und Monitoring planen. Docker-Host-Agent auf festen Container und Pfade begrenzen. PostgreSQL, Prometheus, Grafana, Loki und Restic mit gepinnten Versionen und persistenten Volumes einplanen. Secrets extern einbinden. Bestehenden Minecraft-Dienst und Weltpfad nicht durch ein unpassendes neues Compose-Projekt überschreiben.

Das implementierte Grundgerüst benötigt lokal nur Node/JDK und seine eigene H2-Dateidatenbank. Es startet keine Docker-Dienste und enthält weder Docker-Socket-Mount noch Minecraft-Weltpfad. Für den späteren Portalbetrieb werden ausschließlich Angular-Hosting/Reverse-Proxy, Backend und eine separate PostgreSQL-Datenbank benötigt. Der Host-Agent bleibt bis zur separaten Testserver-Abnahme aus dem Deployment ausgeschlossen.

Vor Deployment: TLS und Proxyvertrauen festlegen, OIDC-Client/Redirects und Serverrollen provisionieren, PostgreSQL/Flyway gegen eine Testdatenbank prüfen, Secret-Dateien bereitstellen und Portal-Datenbank sichern. Zunächst genau eine Backend-Instanz; kein automatisches Rolling Deployment mit parallel laufenden Workern. Es wird kein neuer Minecraft-Container und kein Ersatz der bestehenden Compose-Konfiguration vorbereitet.

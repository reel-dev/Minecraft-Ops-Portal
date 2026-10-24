# Infrastruktur-Arbeitsbereich

Noch keine ausrollbare Compose-Konfiguration. Zuerst bestehende VM-/LXC-/Docker-Topologie und Datenmounts erfassen. Danach getrennte Netze für Reverse Proxy, Backend, Agent/Minecraft und Monitoring planen. Docker-Host-Agent auf festen Container und Pfade begrenzen. PostgreSQL, Prometheus, Grafana, Loki und Restic mit gepinnten Versionen und persistenten Volumes einplanen. Secrets extern einbinden. Bestehenden Minecraft-Dienst und Weltpfad nicht durch ein unpassendes neues Compose-Projekt überschreiben.

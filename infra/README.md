# Infrastruktur-Arbeitsbereich

Die [Test-Compose](test-server/README.md) startet ausschließlich einen separaten lokalen Minecraft-Testcontainer mit eigenem Weltvolume. Status und Steuerung sind dort [abgenommen](../docs/13-testserver-abnahme.md). Für Produktion gibt es noch keine ausrollbare Portal-/Agentkonfiguration. Die [lesende Bestandsprüfung für docker-01](docker-01/README.md) ist vorbereitet, dort aber nicht ausgeführt.

Das Portal benötigt lokal Node/JDK und seine eigene H2-Dateidatenbank. Sein Standardprofil startet keine Docker-Dienste und bindet weder Docker-Socket noch Minecraft-Welt ein. Der separat gestartete Agent übernimmt ausschließlich begrenzte Proben und ausdrücklich aktivierte Teststeuerung. Produktive Dienstkonten, Dockerberechtigungen und sichere Netzgrenzen bleiben zu prüfen.

Vor Produktionsdeployment: Topologie und Weltmounts erfassen, Sicherung prüfen, TLS/Proxyvertrauen festlegen, OIDC und Serverrollen provisionieren, PostgreSQL/Flyway prüfen und Secrets extern bereitstellen. Genau eine Backend- und eine Agentinstanz; kein paralleles Rolling Deployment der Worker. Die bestehende Minecraft-Compose wird nicht ersetzt.

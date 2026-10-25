# Separater lesender Agent als Container

Vorbereitung für Linux-Docker auf docker-01. **Noch nicht dort gestartet.** Der Host benötigt kein Java: Java 21 und Docker CLI werden in einem eigenen Image mitgeliefert. Die vorhandene Minecraft-Compose bleibt unverändert.

## Aufbau

- `agent`: UID/GID 10001, schreibgeschütztes Root-Dateisystem, eigene Datenbank im Volume. Kein Mount von Minecraft-Daten oder dem echten Docker-Socket. Linux-Hostnetz für die feste Minecraft-Abfrage an 127.0.0.1:25565; Agent-HTTP bleibt 127.0.0.1:8091.
- `docker-reader`: separater Nginx-Proxy ohne Netzwerk, eigener Unix-Socket im gemeinsamen Volume. Nur er erhält den echten Docker-Socket und dessen Gruppen-ID. Er erlaubt GET/HEAD für Docker-Versionsaushandlung und die Inspektion exakt von `minecraft-server`; alles andere ergibt 403, einschließlich Start/Stop/Restart, Logs, Dateiexport und anderer Container.
- Eigener Compose-Projektname `elbracht-smp-read-only`, keine Verbindung zu einem Minecraft-Compose-Projekt, kein automatischer Neustart. Keine veröffentlichten Containerports, kein Privileged-Modus und keine zusätzlichen Linux-Capabilities.

Ein Socket-Mount mit `read_only: true` allein verhindert keine schreibenden Docker-API-Aufrufe. Hier begrenzt zusätzlich der Proxy die erlaubten Methoden und Originalpfade. Der Proxy selbst hat weiterhin privilegierten Socketzugriff; seine Integrität ist Teil der Vertrauensgrenze. Die zugelassene Docker-Inspektion enthält intern vollständige Metadaten, möglicherweise auch Container-Umgebungswerte. Das Docker CLI wählt daraus nur die im Agent festgelegten Statusfelder; Rohantworten werden nicht protokolliert oder ans Portal weitergegeben. Proxy und Agent besitzen keine Debuglogs für HTTP-Inhalte.

Technische Grundlagen: [Docker-Daemon-Zugriff](https://docs.docker.com/engine/security/), [Linux-Hostnetz](https://docs.docker.com/engine/network/drivers/host/), [Nginx Unix-Socket-Proxy](https://nginx.org/en/docs/http/ngx_http_proxy_module.html).

## Vor einem ersten Start

1. Auf docker-01 Socketgruppe mit `stat -c '%g' /var/run/docker.sock` ermitteln und Compose-Verfügbarkeit mit `docker compose version` prüfen. Socketpfad und Image-ID müssen weiterhin zum dokumentierten Anschluss passen.
2. Externe zufällige Token-Datei bereitstellen. Sie muss im Container für UID 10001 lesbar sein, beispielsweise Modus 0400 und Eigentümer 10001; nicht allgemein lesbar machen. Den Token nicht in den Chat, Git, die Compose-Datei oder Angular kopieren. Das Backend erhält später denselben Token über seinen separaten Secretpfad.
3. `.env.example` nach `.env` kopieren und ausschließlich Gruppen-ID, absoluten Token-Dateipfad und geprüfte Image-ID eintragen. Compose bricht bei fehlenden Werten ab. Ein Hinweiswert wie `REPLACE_WITH_SOCKET_GROUP_ID` ist vorab zu ersetzen. Der Beispielpfad existiert nicht automatisch.
4. Eigene Volumes/Ports auf Konflikte prüfen. Port 8091 muss lokal frei sein. Kein bestehendes Agentvolume oder Ledger löschen. Ein eigenständiger Compose-Projektname verhindert nicht von sich aus alle Hostportkonflikte.
5. Containerbuild, Proxyfilter und Statusprüfung erst in der getrennten lokalen Testumgebung abnehmen. Danach kontrolliert auf docker-01 starten. **Die Container-Laufzeitprüfung ist derzeit offen:** Docker Desktop auf dem Entwicklungs-PC scheiterte beim Start. Der Dienst wurde deshalb nicht zum Test gestartet und es wurde kein Produktionsstart vorgenommen.

## Paket und Befehle

Das Paket enthält den bereits lokal gebauten Agent-JAR, Dockerfiles, Proxyregeln und Compose-Datei. Auf docker-01 werden keine Maven- oder Java-Installation benötigt; Docker lädt beim Build die drei per Digest festgelegten Basisimages. Keine Abhängigkeiten werden auf dem Host installiert.

Für später, nach erfüllten Voraussetzungen, aus diesem Verzeichnis:

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

Diese Befehle sind eine Betriebsvorlage, keine bereits ausgeführte Installation. Das Portal bleibt bis zur eigenen Abnahme im getrennten Standardbetrieb. Einen bestätigten RUNNING-Status gibt es erst nach Image-/Portprüfung und echter Minecraft-Antwort. Produktive Serveroperationen bleiben gesperrt.

Die Basisimage-Digests wurden am 25.10.2026 direkt aus der Registry aufgelöst. Compose kann ohne laufenden Docker-Daemon syntaktisch geprüft werden; das ersetzt weder einen Imagebuild noch Tests der Unix-Socket-Berechtigungen, des Proxyfilters und des Hostnetzes.

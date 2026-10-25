# Begrenzter Host-Agent

Spring Boot 3.5.16, Java-Ziel 21, lokal mit JDK 25 geprüft. Der Dienst läuft separat vom Portal und bindet standardmäßig nur `127.0.0.1:8091`. Er besitzt keine generische Shell, keinen Docker-Proxy und kein frei konfigurierbares Kommando aus einem HTTP-Request.

## Modi

| Modus | Wirkung |
|---|---|
| `disconnected` (Standard) | Keine Docker-/Minecraft-Abfrage, UNKNOWN |
| `docker-read-only` | Begrenzte Docker-Inspektion und Minecraft-Server-List-Status |
| `docker-test-control` | Zusätzlich START/STOP/RESTART ausschließlich für die unten festgelegte Test-Compose |

Für alle Modi ist eine externe Token-Datei erforderlich: `AGENT_TOKEN_FILE`. Sie enthält ein zufälliges Token mit 32–4096 Zeichen aus Buchstaben, Ziffern und `._~-`; ein abschließender Zeilenumbruch ist erlaubt. Token nie in Git, URLs oder Angular übernehmen. Der Dienst akzeptiert nur `Authorization: Bearer ...`, verwendet keine Browser-Session und vergleicht Token-Hashes zeitkonstant. Fehlendes/falsches Token ergibt 401. TLS/Keystore für eine spätere Hostgrenze separat bereitstellen; die aktuelle Prüfung bleibt vollständig auf Loopback.

## Feste Zielzuordnung

`AGENT_CONTAINER` (Standard `elbracht-smp-control-test-minecraft`), `AGENT_DOCKER_CONTEXT` (Standard `default`), `agent.server-id` (Standard `main`) und `agent.minecraft-port` (Standard `25585`) kommen ausschließlich aus der Backendkonfiguration.

Inspektion liest gezielt Zustand, veröffentlichte Ports und zwei Kennzeichnungslabels. Keine Containerumgebung oder Healthcheck-Rohlogs. Docker läuft über eine feste `ProcessBuilder`-Argumentliste ohne Shell. Ausgabe und Wartezeit sind begrenzt. Unter Windows verwendet das Go-Template einzelne JSON-Werte und Raw-String-Schlüssel; ein Live-Test deckt die Windows-Argumentübergabe ab.

Readiness stammt aus einer echten Java-Edition-Server-List-Abfrage an den geprüften lokalen Port. Kein Login, RCON oder Spielkommando. Paketlänge, VarInts, Antwortfelder und Gesamtzeit sind begrenzt. Nur eine erfolgreiche Minecraft-Antwort ergibt RUNNING. Docker „running“ allein ergibt bei fehlender Minecraft-Antwort UNKNOWN/DEGRADED. Spielversion und Spielerzahl werden gemessen; Loader-/Loader-Version bleiben im Status null, weil dieser Datenstrom sie nicht nachweist.

## Teststeuerung

POST `/agent/v1/servers/main/operations` ist nur im Modus `docker-test-control` autorisiert. Body: `operationId` (UUID des Portaljobs) und `type` (`START`, `STOP`, `RESTART`). Keine zusätzlichen Container-, Pfad-, Docker- oder Kommandoargumente.

Vor jeder Aktion müssen alle Bedingungen passen:

- Container exakt `elbracht-smp-control-test-minecraft`.
- Labels `de.elbracht.smp.environment=test`, passende Server-ID und Compose-Projekt `elbracht-smp-control-test`.
- Genau ein Mount: Docker-Volume `elbracht-smp-control-test_test-world` auf `/data`; keine Bind-Mounts oder weiteren Mounts.
- Konfigurierter Spielport ausschließlich an `127.0.0.1` veröffentlicht.

Stop verwendet `docker container stop --timeout=-1`, wartet also ohne automatischen SIGKILL-Fallback. Ein Agenttimeout bricht nur den wartenden CLI-Prozess ab und speichert UNKNOWN; er erzwingt kein Container-Kill. Neustart ist geprüfter Stop, dann Start und Minecraft-Readiness. Nach Start wird bis zu zwölf Sekunden auf die Minecraft-Probe gewartet. Befehle und Proben besitzen eigene Grenzen; das Portal wartet höchstens 55 Sekunden auf die Agentantwort. Bei Zeitüberschreitung wird kein Erfolg behauptet und keine Aktion automatisch wiederholt. [Docker-Stop-Semantik](https://docs.docker.com/reference/cli/docker/container/stop/).

Agentoperationen liegen dauerhaft in einer eigenen H2-Dateidatenbank (`AGENT_DATABASE_URL`, Standard `jdbc:h2:file:./data/agent-operations;DB_CLOSE_ON_EXIT=FALSE`). Genau eine Agentinstanz pro Ledger. Gleiche UUID/gleicher Typ liefert den gespeicherten Ausgang, anderer Typ ergibt 409. Ein unbekannter oder noch laufender Ausgang sperrt neue Operationen. Beim Agentstart werden zurückgebliebene RUNNING-Einträge UNKNOWN. Es gibt derzeit keine automatische Entsperrung oder Reconciliation-API. Ledger nicht löschen, um eine ungeklärte Aktion einfach erneut auszuführen.

## Bauen und prüfen

Aus `agent/`:

```powershell
../backend/mvnw.cmd verify
```

Standardtests benötigen keinen Docker-Daemon; drei Live-Proben sind dann übersprungen. Nur bei bereits gestarteter [Test-Compose](../infra/test-server/compose.yml):

```powershell
$env:SMP_LIVE_TEST = 'true'
../backend/mvnw.cmd verify
Remove-Item Env:SMP_LIVE_TEST
```

Die Live-Proben prüfen Zustand, Minecraft-Handshake und Schutzbedingungen des fest benannten Testcontainers lesend. Die wirkliche Stop/Start/Restart-Abnahme ist unter [Testserver-Abnahme](../docs/13-testserver-abnahme.md) dokumentiert.

## Lokal starten

Token-Datei vorher außerhalb der Quellen erstellen. Aus dem Repositoryroot:

```powershell
$env:AGENT_TOKEN_FILE = '<absoluter Pfad zur Secret-Datei>'
$env:AGENT_DOCKER_CONTEXT = 'desktop-linux'
$env:AGENT_MODE = 'docker-read-only'
java -jar agent/target/smp-host-agent-0.1.0-SNAPSHOT.jar
```

Teststeuerung benötigt zusätzlich den ausdrücklichen Modus `docker-test-control`. Für das Portal ist dann `local,test-server` plus `portal.agent.mode=http-test-control` erforderlich; `prod` verbietet diesen Adapter. Für echte Produktionscontainer gibt es noch keine freigegebene Mutationsimplementierung. Docker-Zugriff ist ein weitreichendes Hostprivileg: Dienstkonto, Dockerberechtigungen, TLS und Netzgrenzen vor Deployment auf `docker-01` prüfen.

# Isolierter lokaler Minecraft-Testserver

Diese Compose-Datei ersetzt niemals die unter `references/server-compose.supplied.yml` dokumentierte bestehende Konfiguration. Sie verwendet ein eigenes Compose-Projekt, Containernamen, Netzwerk und Docker-Volume. Kein Zugriff auf das vorhandene `./data`.

| Bestandteil | Testumgebung |
|---|---|
| Projekt | `elbracht-smp-control-test` |
| Container | `elbracht-smp-control-test-minecraft` |
| Weltvolume | `elbracht-smp-control-test_test-world` |
| Spielport | `127.0.0.1:25585` → Container `25565` |
| Minecraft / Fabric Loader | `26.3` / `0.19.5` |
| Java | 25, Image per Registry-Digest gepinnt |
| Speicher | JVM 2 GiB, Containerlimit 3 GiB, 2 CPUs |
| Zusätzliche Mods / RCON | keine / deaktiviert |

Die Basisdatei lässt RCON deaktiviert. Für die erfolgreiche [echte RCON-/Whitelist-Abnahme](../../docs/18-rcon-minecraft-abnahme.md) wurde ausdrücklich `compose.rcon.yml` zusätzlich geladen. Das Override aktiviert nur am lokalen Testcontainer RCON auf `127.0.0.1:25595`, verlangt `SMP_TEST_RCON_PASSWORD` aus einer externen Secretquelle und verändert keine produktive Compose. Vor einem Aufruf beide Dateien zusammen mit `config --quiet` prüfen; vollständige Konfigurations-/Env-Ausgaben enthalten sonst das Testpasswort. Daten und Ledger erhalten. Das Override nicht mit Produktionsdateien kombinieren.

EULA=TRUE stammt aus der vom Nutzer ausdrücklich zur Anpassung übergebenen Konfiguration. Es werden weder produktive Whitelist-/OP-Einträge noch CurseForge-Zugangsdaten übernommen. Das Testimage erzeugt eine neue Welt. Der geprüfte Digest ist kein Nachweis des auf `docker-01` tatsächlich laufenden Images.

Aus dem Repositoryroot unter Windows/Docker Desktop:

```powershell
docker --context desktop-linux compose -f infra/test-server/compose.yml config --quiet
docker --context desktop-linux compose -f infra/test-server/compose.yml up -d
```

Vor dem ersten Start prüfen, dass der Containername nicht zu einem anderen Projekt gehört. Die Agent-Teststeuerung validiert die genaue Zuordnung vor jeder Aktion. Für den lokal geprüften Portalmodus siehe [Host-Agent](../../agent/README.md) und [Abnahme](../../docs/13-testserver-abnahme.md).

Nur diesen Testserver sauber stoppen, ohne Daten zu löschen:

```powershell
docker --context desktop-linux container stop --timeout=-1 elbracht-smp-control-test-minecraft
```

Weltvolume, Portal-Datenbank und Agentledger behalten. Die Beispiele enthalten bewusst keinen Lösch-/Prune-Befehl. Der produktive Container `minecraft-server` und seine Compose-Datei sind kein Ziel dieser Befehle.

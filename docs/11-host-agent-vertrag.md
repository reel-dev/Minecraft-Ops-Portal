# Lesender Host-Agent-Vertrag

Dieser Schritt implementiert den **Portaladapter**, keinen auf dem Docker-Host installierten Dienst. Der Adapter wurde ausschließlich gegen einen kurzlebigen lokalen HTTP-Testdienst getestet. Es gibt weiterhin keine Verbindung zum bestehenden Minecraft-Host.

## Aktivierung

Standard bleibt `portal.agent.mode=disconnected`. In diesem Modus werden weder Endpoint noch Token-Datei verwendet. Der optionale Modus `http-read-only` benötigt folgende Backendkonfiguration; sie wird hier nur beschrieben, nicht aktiviert:

```yaml
portal:
  agent:
    mode: http-read-only
    base-url: ${HOST_AGENT_BASE_URL}
    token-file: ${HOST_AGENT_TOKEN_FILE}
    server-id: main
    connect-timeout: 2s
    request-timeout: 3s
    max-status-age: 30s
```

`base-url` muss eine HTTPS-Origin ohne Pfad, Query, Fragment oder eingebettete Zugangsdaten sein. Nur im expliziten `local`-Profil ohne gleichzeitiges `prod` ist HTTP auf der literalen Loopbackadresse `127.0.0.1` oder `[::1]` erlaubt. Es werden keine Proxies, Redirects oder abgeschalteten Zertifikatsprüfungen verwendet. Andere Modi führen zum Startfehler.

Die Token-Datei enthält ein extern bereitgestelltes zufälliges Token mit 32–4096 Zeichen aus `A-Z a-z 0-9 . _ ~ -`; ein abschließender Zeilenumbruch ist erlaubt. Datei nur für den Backendprozess lesbar mounten. Der Adapter liest sie beim Start, verwendet sie nur im Authorization-Header und gibt sie weder an Angular noch in Fehlermeldungen zurück. Rotation benötigt einen kontrollierten Portalneustart. Niemals die Datei ins Repository aufnehmen.

## Einzige erlaubte Anfrage

`GET /agent/v1/servers/{fest-konfigurierte-server-id}/status`

Header: `Accept: application/json`, `Authorization: Bearer <Token aus Secret-Datei>`. Eine andere Portal-Server-ID löst keine Netzwerkanfrage aus. Endpoint und Zielserver lassen sich nicht über einen Browserrequest ändern. Sämtliche Operationsaufrufe bleiben auch in diesem Modus gesperrt.

Der künftige Hostdienst muss Tokens prüfen, intern erreichbar bleiben und die feste Server-ID auf genau seinen erlaubten Container abbilden. Er darf keine generische Docker-/Shell-API weiterreichen. HTTPS allein ersetzt diese Autorisierung nicht.

## Statusantwort

HTTP 200, Content-Type `application/json`, maximal 16 KiB. Das folgende **Schema** verwendet keine Bestandswerte:

| Feld | Typ / Bedeutung |
|---|---|
| `serverId` | Exakt konfigurierte Server-ID |
| `lifecycle` | UNKNOWN, OFFLINE, STARTING, RUNNING, STOPPING, ERROR |
| `health` | OK, DEGRADED, FAILED, UNKNOWN |
| `activity` | NONE, BACKUP, RESTORE, MOD_CHANGE |
| `minecraftReady` | Verpflichtendes Boolean; RUNNING nur mit bestätigter Minecraft-Readiness |
| `minecraftVersion`, `loader`, `loaderVersion` | Gemessener Wert oder null; maximal 64 Zeichen |
| `observedAt` | UTC-ISO-Zeitpunkt der tatsächlichen Messung, kein neu gesetzter Cachezeitpunkt |
| `playersOnline` | Nichtnegative Ganzzahl oder null, falls nicht ermittelt |

Docker „running“ reicht nicht für `minecraftReady=true`. Bei noch nicht bereitem Minecraft meldet der Host STARTING und `false`. Ein bekannter, weiter laufender Server mit beeinträchtigter RCON-Verbindung kann RUNNING/DEGRADED liefern, sofern seine aktuelle Readiness aus einer anderen nachgewiesenen Minecraft-Quelle bestätigt ist. Sonst UNKNOWN/DEGRADED oder STARTING passend zum tatsächlich belegten Zustand; keine Readiness erfinden.

Lifecycle, Health, Aktivität, Server-ID, Readiness und Messzeit sind Pflichtfelder. Widersprüchliche Readiness und fremde IDs werden verworfen. Messungen älter als `max-status-age` oder mehr als fünf Sekunden in der Zukunft ergeben UNKNOWN mit `AGENT_STALE_STATUS`; der gelieferte Zeitpunkt bleibt zur Diagnose sichtbar, Spielerzahlen/Versionsmesswerte werden verworfen. Ein fehlerhafter oder nicht erreichbarer Agent beweist keinen ausgeschalteten Minecraft-Server.

Die Gesamtabfrage ist einschließlich langsamem/chunked Body auf `request-timeout` begrenzt; auch bei falschem Content-Length greift das Größenlimit. Zeitgrenzen müssen zwischen 1 ms und 60 s liegen. 401/403, HTTP-Fehler, ungültige Antworten, Timeout und Verbindungsfehler ergeben feste Fehlercodes. Fremde Antworttexte, Ausnahmen und Token werden nicht in die API übernommen.

## Noch erforderlich

Hostdienst implementieren und dessen Containerzuordnung, Minecraft-Probe, Tokenprüfung, TLS und tatsächliche Zeitgrenzen gegen einen getrennten Testserver prüfen. Echte Bestandsdaten nach [Bestandsaufnahme](12-bestandsaufnahme.md) dokumentieren. Erst danach einen separat freigegebenen mutierenden Agentvertrag mit Job-ID/Idempotenz, sauberem Stop und Zielzustandsprüfung entwerfen. Dieser lesende Adapter sendet dafür keinerlei POST-/Docker-/RCON-Anfragen.

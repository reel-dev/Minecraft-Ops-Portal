# API-Skizze v0.1

Entwurf für die spätere Implementierung, noch keine implementierte oder vollständige OpenAPI-Spezifikation. Basis `/api/v1`, JSON, Sessionauthentifizierung und CSRF für Mutationen. Zeiten als UTC-ISO-8601, Anzeige in Europe/Berlin. Server-ID bezeichnet ausschließlich registrierte Server.

| Methode / Pfad | Zweck | Mindestrolle |
|---|---|---|
| GET /me | Identität und Berechtigungen | angemeldet |
| GET /servers | Sichtbare Server | Viewer |
| GET /servers/{id}/status | Lifecycle, Health, Aktivität, letzte Messung | Viewer |
| POST /servers/{id}/operations | `START`, `STOP`, `RESTART` | Operator |
| GET /servers/{id}/jobs/{jobId} | Jobfortschritt und Ergebnis | Operator |
| GET /servers/{id}/events | SSE für Status/Jobereignisse, je Ereignistyp gefiltert | Viewer |
| GET /servers/{id}/logs/stream | SSE für redigierte Logs | Operator |
| GET /servers/{id}/logs | Begrenzte historische Logs mit Cursor | Operator |
| POST /servers/{id}/console/actions | Validierte Minecraft-Aktion, z. B. `SAY` | Operator |
| POST /servers/{id}/console/commands | Freies Minecraft-RCON-Kommando | Admin |
| GET /servers/{id}/players | Online-Spieler; Felder mit Messquelle | Viewer |
| GET /servers/{id}/whitelist | Whitelist | Operator |
| PUT /servers/{id}/whitelist/{uuid} | Spieler aufnehmen, Name vorher auflösen | Operator |
| DELETE /servers/{id}/whitelist/{uuid} | Whitelist-Eintrag entfernen | Operator |
| PUT /servers/{id}/operators/{uuid} | OP vergeben | Admin |
| DELETE /servers/{id}/operators/{uuid} | OP entziehen | Admin |
| GET /servers/{id}/properties | Erlaubte Eigenschaften, Schema und Revision | Admin |
| PATCH /servers/{id}/properties | Validierte Änderungen mit If-Match | Admin |
| GET /servers/{id}/backups | Sicherungen und Integritätsstatus | Operator |
| POST /servers/{id}/backups | Sicherung als Job anstoßen | Operator |
| GET /servers/{id}/backups/{snapshotId}/download | Autorisierter Export, kein öffentlicher Bucket-Link | Admin |
| POST /servers/{id}/restore-plans | Snapshot prüfen und Restore-Plan erstellen | Admin |
| POST /servers/{id}/restores | Bestätigten unveränderten Plan ausführen | Admin |
| GET /servers/{id}/mods | Installierter Soll-/Iststand | Viewer |
| GET /servers/{id}/mods/catalog?q=...&cursor=... | CurseForge-Treffer mit passender Datei | Viewer |
| GET /servers/{id}/mods/updates | Passende gepinnte Updatekandidaten | Viewer |
| POST /servers/{id}/mod-plans | Installieren/Entfernen/Update planen | Admin |
| POST /servers/{id}/mod-changes | Validierten Plan ausführen | Admin |
| GET /servers/{id}/metrics?range=... | Begrenzte Dashboard-Zeitreihen | Viewer |
| GET /integrations/curseforge | Nur konfiguriert/Prüfstatus | Admin |
| PUT /integrations/curseforge/secret | Schlüssel setzen/rotieren, nie zurückgeben | Admin |
| POST /integrations/curseforge/check | Verbindung prüfen, Ergebnis redigieren | Admin |
| GET /audit | Gefilterte Audit-Ereignisse | Admin |

## Beispiele

Neustart: `POST /servers/main/operations`, Header `Idempotency-Key: <client-generated-id>`, Body:

```json
{"type":"RESTART","reason":"Geplante Wartung"}
```

Antwort HTTP 202 mit Location auf den serverbezogenen Job:

```json
{"jobId":"job-example","state":"QUEUED","serverId":"main"}
```

Statusschema (illustrativ):

```json
{
  "serverId":"main",
  "lifecycle":"RUNNING",
  "health":"OK",
  "activity":"BACKUP",
  "minecraftVersion":"26.3",
  "loader":"FABRIC",
  "loaderVersion":null,
  "observedAt":"2026-10-24T10:00:00Z",
  "reasonCode":null,
  "playersOnline":null,
  "jobId":"job-example"
}
```

Null heißt nicht gemessen, niemals automatisch null Spieler oder 0 % Auslastung. Dieses Beispiel bestätigt keine Versionsverfügbarkeit.

Modplan enthält `operation`, `modId`, `fileId` und `expectedManifestRevision`. Das Backend bestimmt Version und Loader aus dem Serverprofil und prüft sie erneut vor Ausführung. Ein vom Client übergebener Filter kann diese Vorgaben nicht lockern. Rückgabe: Plan-ID, Abhängigkeiten, betroffene Dateien, Neustartbedarf, Warnungen, Ablaufzeit und Revision. Die Ausführung verwendet nur die serverseitig gespeicherte Plan-ID mit ausdrücklicher Bestätigung. Bei geändertem Serverprofil/Manifest muss neu geplant werden.

Restore entsprechend mit Snapshot-ID, Zielserver, Inhalts-/Versionsprüfung, Vorabsicherung und geplantem Stillstand. Niemals ein beliebiger Dateipfad als Restoreziel. Plan und Ausführung sind gegen Wiederholung und Ablauf abgesichert.

## Semantik

- Jobs: QUEUED, RUNNING, SUCCEEDED, FAILED, UNKNOWN. Phase und Fortschritt nur aus tatsächlichen Daten; Prozentwert darf fehlen. Fehler liefert `code`, verständliche `message`, `traceId`, optional `retryable`, keine Stacktraces/Secrets.
- 401 unauthentifiziert, 403 keine Berechtigung, 404 nicht sichtbar/nicht vorhanden, 409 Sperre oder veralteter Plan, 412 veraltete Konfigurationsrevision, 422 fachlich ungültig, 429 zu viele Anfragen, 502/503 Integrationsfehler.
- RCON-Antwort ist noch kein Beweis, dass ein langlaufender Vorgang vollständig abgeschlossen ist. Kritische Zustände nachprüfen.
- SSE: `id`, `event`, `data`; Last-Event-ID für Reconnect, Snapshot-Fallback nach Pufferablauf. Keine Secrets in URL-Parametern. Rechte auch bei Wiederverbindung prüfen; Streams bei Sessionablauf beenden.
- Rohes RCON unterstützt keine beliebigen Betriebssystemkommandos. Operator-Aktionen verwenden strukturierte Argumente und serverseitige Templates.
- Whitelist/OP im MVP bei erreichbarem Minecraft per RCON. Bei Offline-Server keine ungeprüfte Live-Dateieditierung: definierter Fehler mit Anleitung zum Start. UUID und kanonischen Namen vor Aufnahme auflösen; widersprüchliche Teilaktionen sichtbar melden.

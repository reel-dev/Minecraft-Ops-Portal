# API-Skizze v0.1

Entwurf für die spätere Implementierung, noch keine implementierte oder vollständige OpenAPI-Spezifikation. Basis `/api/v1`, JSON, Sessionauthentifizierung und CSRF für Mutationen. Zeiten als UTC-ISO-8601, Anzeige in Europe/Berlin. Server-ID bezeichnet ausschließlich registrierte Server.

## Im Grundgerüst implementierte Teilmenge

`GET /auth` (Loginpfad), `GET /csrf` (Headername und maskiertes Token), `GET /me`, `GET /servers`, `GET /servers/{id}/status`, `GET /servers/{id}/inventory`, `PUT /servers/{id}/inventory/{field}`, `POST /servers/{id}/operations`, `GET /servers/{id}/jobs`, `GET /servers/{id}/jobs/{jobId}`, `GET /servers/{id}/audit` und CSRF-geschütztes `POST /logout`. Alle unten aufgeführten übrigen Endpunkte bleiben Entwurf.

Bestandsaufnahme (Admin): GET liefert `{revision, documentedCount, requiredCount, fields[]}` und ETag mit angeführter Revision. Die feste Liste enthält 15 Schlüssel gemäß M01-Vorlage; offene Felder haben null als Wert/Quelle/Zeit. PUT eines Feldes benötigt CSRF und `If-Match: "<revision>"`; Body `{value, source, observedAt}` mit nichtleerem Wert (max. 2000), Quelle (max. 240) und UTC-Erfassungszeit, nicht in der Zukunft. Antwort: vollständiger aktueller Snapshot plus neues ETag. Fehlende Revision: 428, ungültige Daten/Feldschlüssel: 422, veraltete Revision: 412, fremder Server: 403 bzw. nicht registrierter Server für lokalen Admin: 404. Änderungen werden unter Serversperre mit Metadaten auditiert. Keine Hostabfrage, keine Änderung der Nutzerangaben, kein automatisches `verifiedAt`, keine Löschschnittstelle.

Jobhistorie: `GET /servers/{id}/jobs?limit=20&before=<Job-UUID>`, mindestens Operator auf dem Zielserver. `limit` liegt zwischen 1 und 50. Ohne `before` beginnt die neueste Seite, danach folgt absteigend `createdAt` und bei gleichen Zeiten die UUID. Die Antwort enthält `{items: Job[], nextCursor: UUID|null, blockingJob: Job|null}`. `nextCursor` ist die letzte Job-ID der aktuellen Seite und nur bei weiteren Ergebnissen gesetzt. Ein fremder/fehlender Cursor liefert 404, ungültige Parameter 422. `blockingJob` zeigt QUEUED/RUNNING/UNKNOWN unabhängig von der gewählten Seite. Details enthalten Auftraggeber und Begründung, niemals den Idempotency-Key. Die Seite `/jobs/{jobId}` im Frontend ist direkt verlinkbar und lädt ihre Daten nach Reload wieder aus dem Backend. Es gibt weiterhin keine Job-Entsperr-/Wiederholungsoperation.

Audit: `GET /servers/{id}/audit?jobId=<optionale-UUID>&limit=20&before=<Ereignis-UUID>`, ausschließlich Admin auf diesem Server. Antwort `{items: AuditEvent[], nextCursor: UUID|null}` mit `id`, `actor`, `serverId`, optionaler `jobId`, `action`, `outcome`, `occurredAt`, optionalen `subjectKey` und `revision`. Neben Jobübergängen liefert `INVENTORY_UPDATED`/`RECORDED` die Änderung eines Nachweisfeldes, ohne Job-ID und ohne Wert-/Quellenkopie. Reihenfolge absteigend nach Ereigniszeit und UUID, Seitengröße 1–50. Jobfilter und Cursor müssen zum Zielserver und zueinander passen, sonst 404; syntaktisch ungültige Parameter liefern 422. Keine Freitextbegründungen oder Idempotency-Keys. Globale serverübergreifende Suche unter `/audit` bleibt Entwurf. Es gibt keine Schreib-/Löschfunktion für Auditereignisse in der API.

Die Serverliste liefert `id`, `name`, `reportedMinecraftVersion`, `reportedLoader`, `verifiedAt`, die serverbezogene `role`, `operationsAvailable` und `statusSource` (`DISCONNECTED` oder `HOST_AGENT_READ_ONLY`). Gemeldete Versionen sind keine verifizierten Messwerte. Ohne Hostanbindung: `operationsAvailable=false`, Status UNKNOWN, Messzeit/Spielerzahl/gemessene Versionen `null`, `reasonCode=HOST_AGENT_NOT_CONFIGURED`. Auch beim optionalen lesenden Adapter bleiben Operationen gesperrt; Transport und Validierung sind im [Host-Agent-Vertrag](../docs/11-host-agent-vertrag.md) beschrieben. Fehlerhafte/veraltete Hostantworten ergeben UNKNOWN, niemals automatisch OFFLINE.

Operationen akzeptieren ausschließlich `START`, `STOP`, `RESTART`, eine nichtleere Begründung bis 240 Zeichen und `Idempotency-Key` mit 8–128 Zeichen aus Buchstaben, Ziffern, `_`, `-`. Idempotenz gilt dauerhaft pro Identität und Server; derselbe Schlüssel mit anderem Payload liefert 409. Ohne Adapter liefert eine berechtigte Anfrage 503. Der 202-Job enthält `jobId`, `serverId`, `actor`, `type`, `reason`, `state`, `createdAt`, `updatedAt`, `errorCode`; `Location` verweist auf die Jobabfrage. Statuspolling ersetzt zunächst SSE. Weitere Grenzen und Backlogzuordnung: [Implementierungsstand](../docs/10-implementierungsstand.md).

| Methode / Pfad | Zweck | Mindestrolle |
|---|---|---|
| GET /me | Identität und Berechtigungen | angemeldet |
| GET /servers | Sichtbare Server | Viewer |
| GET /servers/{id}/status | Lifecycle, Health, Aktivität, letzte Messung | Viewer |
| GET /servers/{id}/inventory | Feste Bestandsfelder, Quellen, Zeiten und Revision | Admin |
| PUT /servers/{id}/inventory/{field} | Redigierten Nachweis mit CSRF und If-Match speichern | Admin |
| POST /servers/{id}/operations | `START`, `STOP`, `RESTART` | Operator |
| GET /servers/{id}/jobs | Begrenzte Jobhistorie mit Cursor und sperrendem Job | Operator |
| GET /servers/{id}/jobs/{jobId} | Jobfortschritt und Ergebnis | Operator |
| GET /servers/{id}/audit | Jobübergänge mit Jobfilter und Cursor | Admin |
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

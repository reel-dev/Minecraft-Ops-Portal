# Spring-Boot-Backend

Spring Boot 3.5.16, Java-Ziel 21, Maven-Wrapper 3.9.16. Start und Tests siehe [Projekt-README](../README.md). Lokale persistente H2-Dateidatenbank, Flyway-Schema unter `src/main/resources/db/migration`. Für Tests wird ausschließlich eine eigene In-Memory-Datenbank verwendet.

## Pakete und Grenzen

- `identity`: Backend-Session, CSRF, OIDC-Konfiguration, explizite serverbezogene Rollen.
- `servers`: Serverprofile und begrenzter `HostAgent`-Port. `DisconnectedHostAgent` bleibt Standard; `HttpStatusHostAgent` ist ein ausdrücklich zu konfigurierender, ausschließlich lesender HTTP-Adapter. Keine Docker-/RCON-Anbindung.
- `servers.InventoryService`: 15 manuelle M01-Nachweisfelder, Adminzugriff, Zeit-/Längenvalidierung und serverbezogene Revision. Schreiben mit Datenbanksperre und `If-Match`, Audit-Metadaten in derselben Transaktion. Kein Hostzugriff und keine automatische Änderung von `verified_at` oder gemeldeter Minecraft-Version.
- `operations`: dauerhafte Jobannahme, serverbezogene Datenbanksperre, Idempotenz, Worker und Auditübergänge.
- `audit`: lesende Admin-API pro Server, optionaler Jobfilter, begrenzte Cursorseiten; keine Änderungs-/Löschschnittstelle für Auditdaten.
- `api`: redigierte Fehlerantworten mit Fehlercode und Trace-ID.

Konsole, Spieler, Properties, Backups, Mods und Observability folgen in der dokumentierten MVP-Reihenfolge. Es gibt keine generische Shell-Schnittstelle.

Jobstatuswechsel und Audit werden gemeinsam in einer Transaktion gespeichert. Ein erneuter Abschluss eines bereits abgeschlossenen Jobs erzeugt keinen weiteren Audit-Eintrag. Der Audit-Leseendpunkt gibt Identität, Server-/optionale Job-ID, Aktion, Ergebnis und Zeitpunkt sowie bei Bestandsänderungen Feldschlüssel/Revision zurück; keine Bestandswerte, Quellen, Freitextbegründungen oder Idempotency-Keys. `job_id` ist für Bestandsänderungen null. Audit-Löschschutz/Retention, Loginereignisse und abgewiesene Anfragen sind noch gesondert zu planen. Das Audit enthält keine historische Kopie früherer Nachweiswerte.

## Lesenden Statusadapter vorbereiten

Konfiguration und Wire-Schema stehen im [Host-Agent-Vertrag](../docs/11-host-agent-vertrag.md). Es wurde noch kein echter Hostdienst installiert oder kontaktiert. Der Modus `http-read-only` verlangt eine feste HTTPS-Origin, feste Server-ID und externe Token-Datei. Im lokalen Profil ist HTTP ausschließlich auf literalen Loopbackadressen erlaubt. Redirects werden nie verfolgt. Gesamtdeadline, 16-KiB-Bodylimit, Aktualitätsprüfung und Readiness-Validierung begrenzen die Antworten.

Der Adapter sendet ausschließlich GET-Statusanfragen. `operationsAvailable` bleibt immer false; Start/Stop/Restart sind weiterhin gesperrt. Ohne explizite Konfiguration werden weder Endpoint noch Token-Datei verwendet. UI und API unterscheiden eine fehlende Quelle von Fehlern einer konfigurierten Quelle.

## Produktionsprofil vorbereiten

`SPRING_PROFILES_ACTIVE=prod` verwendet OIDC statt lokaler Anmeldung. Erforderliche ausschließlich backendseitige Variablen:

| Variable | Bedeutung |
|---|---|
| `DATABASE_URL` | PostgreSQL-JDBC-URL der Portal-Datenbank |
| `DATABASE_USER`, `DATABASE_PASSWORD` | Dedizierter Portal-Datenbankzugriff |
| `OIDC_ISSUER_URI` | Geprüfter OIDC-Issuer |
| `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET` | Vertraulicher Backend-Client |
| `PORTAL_UI_URL` | Feste HTTPS-URL der Angular-Anwendung nach Login |

Secrets über Deploymentumgebung bzw. Spring `configtree` aus gemounteten Secret-Dateien bereitstellen. Keine Secrets in Git, Frontend-Buildargumente oder URLs schreiben. Backend und Angular unter einer Origin betreiben. Reverse Proxy muss Session-/CSRF-Cookies und SSE später korrekt unterstützen; bei Bedarf Forwarded-Header ausschließlich für einen vertrauenswürdigen Proxy konfigurieren. Secure/HttpOnly/SameSite-Lax für Sessioncookies sind Standard; nur das explizite lokale Profil hebt `Secure` für HTTP-Entwicklung auf.

Portalrollen stehen in `server_grant(subject, server_id, portal_role)`, wobei `subject` dem Spring-OIDC-Principalnamen (`sub`) entspricht. Ohne Eintrag sieht ein OIDC-Nutzer keinen Server. Rollen derzeit über einen kontrollierten Datenbank-Provisionierungsschritt vergeben; noch keine Rollenverwaltung im Browser. Minecraft-OP wird daraus nie abgeleitet.

Nur eine Backend-Instanz einsetzen. UNKNOWN-Jobs sperren weitere Mutationen; vor einer späteren Entsperrung ist ein tatsächlicher Hostabgleich erforderlich. Recovery wiederholt keine Jobs automatisch. Agentimplementierung muss feste Containerzuordnung, authentisierten Transport, Timeouts, sauberen Stop und verifizierte Readiness erzwingen. Das aktuelle Grundgerüst ist noch keine freigegebene Betriebsplattform.

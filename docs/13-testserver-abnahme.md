# Abnahme der getrennten lokalen Testumgebung

Stand: 25.10.2026. Geprüft auf dem lokalen Docker-Desktop-Kontext `desktop-linux`. Die Proxmox-Instanz `docker-01` und der produktive Container `minecraft-server` wurden nicht verbunden oder verändert.

## Quelle und Trennung

Die [übergebene Compose-Konfiguration](../references/server-compose.supplied.yml) deklariert Minecraft 26.3, Fabric Loader 0.19.5, `stable`, 8G JVM-Speicher und `./data:/data`. Der Nutzer hat zusätzlich Homelab → Proxmox-Instanz `docker-01` bestätigt. Das sind Nutzer-/Konfigurationsangaben, keine Laufzeitnachweise des bestehenden Servers. VM/LXC, absolute Datenpfade, laufender Image-Digest und letzte Sicherung bleiben offen.

Der lokale Testserver verwendet eine [eigene Compose-Datei](../infra/test-server/compose.yml), eine neue Welt im Docker-Volume `elbracht-smp-control-test_test-world`, den festen Containernamen `elbracht-smp-control-test-minecraft` und ausschließlich Loopback-Port 25585. Keine produktiven Mounts, Spieler-/OP-Einträge oder zusätzlichen Moddownloads wurden übernommen.

Öffentliche Voraussetzung vor Start geprüft: [Mojang-Versionsmanifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) enthält 26.3 als Release; das verlinkte Versionsmanifest verlangt Java 25. Der [Fabric-Meta-Endpunkt](https://meta.fabricmc.net/v2/versions/loader/26.3/0.19.5) liefert Loader 0.19.5. Die [itzg-Dokumentation](https://docker-minecraft-server.readthedocs.io/en/latest/versions/java/) ordnet `java25` Java 25 zu. Das zu diesem Zeitpunkt aufgelöste Testimage ist auf `sha256:6fd6f9bb6998b7cd06948ec6ab03fabfdf98ba3f2ec030c44069bec9a6bdf09d` gepinnt.

Laufzeit des neuen Testservers unabhängig geprüft: Startlog meldet Minecraft 26.3, Fabric Loader 0.19.5 und Java 25, Erstellung einer neuen Welt und erfolgreichen Start. Docker bestätigt ausschließlich das neue Volume auf `/data`. Die echte Minecraft-Statusprobe liefert Version 26.3 und Spielerzahl 0. Der Statusadapter behauptet daraus keine gemessene Loader-Version; diese stammt hier gesondert aus dem Startlog.

## Durchgängige Portalprüfung

Portalprofile `local,test-server`, explizit `portal.agent.mode=http-test-control`; Hostdienst `docker-test-control`. Die Oberfläche kennzeichnet den isolierten Testserver. Beide Komponenten besitzen eigene persistente Testdatenbanken. Token bleibt in einer ignorierten Backend-Secret-Datei, nie in Angular oder diesem Dokument.

Anmeldung über Spring-Security-Session und CSRF, anschließend ausschließlich die normalen Portal-Operationsendpunkte:

| Aktion | Job-ID | Jobabschluss | Tatsächlich nachgeprüfter Zustand | Zeitpunkt der Nachprüfung (UTC) |
|---|---|---|---|---|
| STOP | `c188c055-9ff1-4c7f-8458-a778bf2a30f9` | SUCCEEDED | OFFLINE | 2026-10-25T09:33:37Z |
| START | `b0430520-b641-4ac9-a6ba-0d6e611734c7` | SUCCEEDED | RUNNING, Minecraft 26.3 | 2026-10-25T09:33:50Z |
| RESTART | `239c849b-e247-4fb2-9400-a19bf15a16ca` | SUCCEEDED | RUNNING, Minecraft 26.3 | 2026-10-25T09:34:03Z |

Jeder Request wurde mit demselben Idempotency-Key wiederholt und lieferte dieselbe Job-ID. Neun Auditübergänge (QUEUED, RUNNING, SUCCEEDED pro Job) wurden gelesen. Der Agent verwendet die Portal-Job-ID als eigene persistente Operations-ID. Start wird erst nach erfolgreicher Minecraft-Readiness bestätigt; Stop erst nach bestätigtem Prozessende. Logs dokumentieren sauberes Beenden, Speichern von Spielern/Welten und erneute Readiness, ohne erzwungenes Kill.

Zusätzlich wurde der Agent mit demselben persistenten Ledger neu gestartet und die bereits abgeschlossene STOP-Operations-ID erneut gesendet. Er lieferte den gespeicherten Erfolg zurück; der Testserver blieb RUNNING. Damit wurde auch die Idempotenz nach Agentneustart geprüft. Maschinenlesbare Ergebnisse der drei Portaljobs: [Abnahmenachweis](../references/test-server-acceptance.json).

Prüfstand dieser Abnahme: 44 Portaltests und 26 Agenttests erfolgreich, einschließlich drei lesender Live-Proben. Angular-Build und Typprüfung erfolgreich. Dies beschreibt die damalige Abnahme, keine Zusicherung, dass die lokale Vorschau dauerhaft läuft.

## Grenzen dieser Abnahme

- Gilt für die neue lokale Testwelt, nicht für `docker-01` oder produktive Backups/Mods.
- Das Standardprofil bleibt ohne Hostverbindung. Produktionsprofil kann den Teststeuerungsadapter nicht aktivieren.
- Der Hostdienst verweigert Mutationen bei anderem Container, Projekt, Testlabel, Weltmount oder Portbinding. Für beliebige Produktionscontainer gibt es noch keine freigegebene Mutationsschnittstelle.
- Agent und Portal unterstützen je genau eine Instanz. UNKNOWN hält die Sperre; Reconciliation und sichere Entsperrung sind noch offen. Keine Ledger-/Datenbanklöschung zum Umgehen einer Sperre.
- HTTPS über Hostgrenzen, OIDC-Provider, produktive Zugriffspolitik und Wiederherstellbarkeit sind noch nicht an der realen Infrastruktur abgenommen.
- Die Produktanforderung „verschiedene Hostingarten“ ist als [Trennung von Konto, Serverrechten und Anbindung](14-konten-und-hosting.md) dokumentiert, nicht als bereits implementiertes öffentliches Selfservice-Hosting ausgegeben.

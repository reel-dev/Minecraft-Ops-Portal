# ADR-006: Konten, Serverrechte und Hosting getrennt halten

Nutzeranforderung vom 25.10.2026: Das Panel soll verschiedene Hostingmöglichkeiten unterstützen. Aktueller persönlicher Betrieb: eigenes Homelab, Proxmox-Instanz `docker-01`, darin Docker mit dem deklarierten Container `minecraft-server`. Ob VM oder LXC, Netzadresse und tatsächlicher Laufzeitstand sind noch nicht technisch geprüft.

## Produktmodell

Ein Konto steht für eine Person. Die Person kann Zugriff auf einen vorhandenen Server erhalten oder als Eigentümer später eigene Server anbinden. Ein eingeladener Mitverwalter braucht keinen eigenen Hoster. Das Anlegen eines Kontos darf weder Hostingzugriff noch Minecraft-OP verleihen.

Serverrollen bleiben pro registriertem Server: Viewer, Operator, Admin. Mitgliedschaft und Hosting-Credentials sind getrennt. Der aktuelle OIDC-/Sessionentwurf sowie `server_grant(subject, server_id, portal_role)` bilden diese Rechtezuordnung bereits ab. Registrierung, Einladungen, Eigentumswechsel, Quoten und eine selbst bedienbare Serverregistrierung sind noch nicht implementiert.

Hosting ist eine eigene Integration hinter fachlichen Ports, keine im Frontend vorausgesetzte Proxmox-Verwaltung:

| Umgebung | Mögliche Anbindung | Stand |
|---|---|---|
| Eigener Docker-Host, etwa in Proxmox | Eng begrenzter Host-Agent | Lokaler Testagent implementiert und an separatem Testcontainer geprüft |
| Eigener Host ohne Docker | Passender Dienst-/Hostadapter | Späterer Ausbau |
| Externer Minecraft-Hoster | Anbieter-API mit dessen tatsächlich verfügbaren Rechten | Später, abhängig vom Anbieter |
| Nur RCON erreichbar | Minecraft-Aktionen im freigegebenen Umfang | Liefert allein keine vollständigen Hostlogs, Backups oder sicheren Prozessstart |

Die Proxmox-Instanz gehört zu deinem aktuellen Anschluss, nicht zu einem Pflichtfeld für jeden Panelnutzer. Der Docker-Agent soll auf dem Docker-Host arbeiten; normale Portalaktionen benötigen keinen Proxmox-Administratorzugang.

## Sicherheits- und Implementierungsgrenzen

- Konto-Identität wird ausschließlich serverseitig festgestellt. Jede Anfrage prüft Serverzuordnung, Rolle und später gegebenenfalls Eigentümer-/Organisationszuordnung.
- Hostinggeheimnisse bleiben im Backend/Secret Store. Das Frontend erhält nur Server-ID, erlaubte Fähigkeiten und freigegebene Messwerte.
- Ein Nutzer darf keine beliebige URL, Docker-ID, Hostdatei oder Shellzeile in einen Operationsrequest einschleusen. Spätere Registrierungs-/Pairingflows benötigen eigene Besitzprüfung, Schutz gegen SSRF und serverbezogene Secretzuordnung.
- Fähigkeiten kommen aus der tatsächlich implementierten Integration: fehlendes Restore, Start oder Dateizugriff wird nicht durch einen unsicheren Shellfallback ersetzt.
- Aktuell verwaltet der MVP eine feste Agentzuordnung pro Portalinstallation. Mehrere unabhängige Hostinganbindungen pro Installation benötigen eine neue serverbezogene Integrationsregistrierung und entsprechende Isolationstests. Diese Mehrmandantenfähigkeit ist noch keine Produkteigenschaft.

Reihenfolge: eigenen Testanschluss durchgängig abnehmen, dann den tatsächlichen Anschluss auf `docker-01` lesend prüfen. Erst danach kontrollierte Registrierung weiterer Server und weitere Hostingadapter ausbauen. Backups/Proberestore bleiben Voraussetzung für spätere Modmutationen.

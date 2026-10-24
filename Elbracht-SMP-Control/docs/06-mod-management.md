# Mod-Management

## Produktregel

Nur echte CurseForge-Projekte mit einer Datei für die **exakte Minecraft-Version und Fabric** erscheinen in der passenden Modsuche. Fehlt ein solcher Nachweis, bleibt die Trefferliste leer. Keine automatische Suche für eine benachbarte Minecraft-Version und kein Wechsel auf Forge/Quilt. Minecraft `26.3` bleibt bis zur Bestandsprüfung der angegebene Wert; ist er nicht auflösbar, zeigt das Portal den Konfigurationsfehler.

## Suche und Auswahl

1. Backend liest Serverprofil und sucht bei CurseForge nach Minecraft-Modprojekten, mit `gameVersion` und `modLoaderType=4` (Fabric). Mod-Klasse gezielt filtern, keine Modpacks/Resourcepacks. CurseForge unterstützt diese Filterkombination. [Offizielle API](https://docs.curseforge.com/rest-api/).
2. Für jeden Treffer passende konkrete Dateien prüfen; keine Installation nur anhand des Projektnamens oder eines allgemeinen Latest-Feldes.
3. Server-Eignung und benötigte Abhängigkeiten zusätzlich prüfen. Loader-/Versionstags belegen allein keine Server-Eignung. Unbekannte oder reine Client-Mods werden nicht als serverinstallierbar angeboten; unklare Metadaten führen zu „Prüfung erforderlich“.
4. Erforderliche Abhängigkeiten rekursiv auflösen, Zyklen/Duplikate behandeln, optionale Abhängigkeiten getrennt zeigen, bekannte Inkompatibilitäten und JAR-Metadaten berücksichtigen. Fehlt eine passende Abhängigkeit, Plan blockieren.
5. API-Downloadfreigabe, vorhandene Downloadadresse und Dateiintegrität prüfen. Gesperrte Downloads nicht umgehen. Bei 401/403/429/Timeout verständliche Meldung und begrenzte Wiederholungen; keine Demo-Treffer einblenden.

Das Backend bewahrt CurseForge-Projekt-ID, Datei-ID, Version, Loader, Prüfsummen und Abhängigkeitsgraph im Manifest. Suchcache nach Serverprofil und Suchparametern isolieren; vor jeder Installation erneut validieren. Beschreibung: „Laut Metadaten passend; Laufzeittest ausstehend“, niemals „garantiert konfliktfrei“.

## Schlüssel

Frontend spricht nur mit Spring Boot. Der CurseForge-Key wird serverseitig als Secret gespeichert, nur für ausgehende API-Aufrufe verwendet und redigiert. Der für das Portal gewählte Konfigurationsname kann `CURSEFORGE_API_KEY_FILE` sein; er ist Teil unseres Entwurfs und keine itzg-Variable. Ein vorhandener itzg-Schlüssel heißt gegebenenfalls `CF_API_KEY` bzw. `CF_API_KEY_FILE`; die genaue Unterstützung am gewählten Image-Digest prüfen. Kein Schlüssel aus Beispielkonfigurationen übernehmen.

## Installation und Update

Plan enthält gepinnte Datei-IDs, Abhängigkeiten, Größen, Änderungen, Neustartbedarf und Rückweg. Admin bestätigt; Backend prüft Revision und holt die Mutationssperre. Danach Vorabsicherung, Download in Staging, Hash-/JAR-/Pfadprüfung, sauberer Stop, atomarer Modsatzwechsel, Neustart und Healthcheck. Rollback stellt bei misslungenem Start den vorherigen Satz und bei Weltänderung nötigenfalls die gesicherte Welt wieder her.

Es muss genau eine Instanz den Mod-Sollzustand besitzen. Für das MVP ist ein Portal-Manifest mit kontrollierter Dateiinstallation vorgesehen. Eine alternative Integration über itzg `CURSEFORGE_FILES` wird erst nach Prüfung am gepinnten Image gewählt und getestet; sie darf nicht parallel mit einem eigenen Installer dieselben Dateien verwalten. Bestehende manuelle Mods vor Import inventarisieren und nie pauschal löschen.

Entfernen prüft rückwärts gerichtete Abhängigkeiten; benötigte Bibliotheken nicht still entfernen. Update ist ein neuer geprüfter Plan, kein automatisches ungebundenes „latest“. Die Datenquelle und letzte erfolgreiche Aktualisierungsprüfung sind sichtbar.

## Xaero World Map / Unified Xaero

Exakte Projektidentitäten, Server-/Client-Anforderungen, Lizenz und passende CurseForge-Dateien sind offen. Keine Namen aus dem Mockup als Installationsnachweis behandeln. Kartenanzeige im Browser benötigt einen nachgewiesenen Renderer oder Dienst; eine Xaero-Mod allein rechtfertigt diese Zusage nicht. Unbekannte bestehende JARs bleiben als „nicht zugeordnet“ im Inventar und werden nicht als verifiziertes CurseForge-Projekt ausgegeben.

## Kritische Prüfungen

Kein exaktes Versionsmatch; Fabric-Datei fehlt; Projekt mit nur Forge-Dateien; Client-only; fehlende Abhängigkeit; Download gesperrt; manipulierte Datei-ID; Hashfehler; laufender Restore; Serverprofil nach Planung geändert; Start nach Modwechsel fehlerhaft. Jeder Fall benötigt ein nachvollziehbares Ergebnis ohne stille Teilinstallation.

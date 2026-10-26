# Reviewbarer Umfang der CurseForge-Anbindung

Die automatische Freigabeprüfung hat am 25.10.2026 den Implementierungspatch für die externe CurseForge-Anbindung abgewiesen: Eine lokale Secret-Datei sollte gelesen und ihr API-Schlüssel an einen Drittanbieter übertragen werden; die Prüfung vermisste dafür eine ausdrückliche Erlaubnis. Der abgewiesene Patch wurde nicht ausgeführt und nicht auf anderem Weg eingespielt.

René hat den nachstehenden Umfang anschließend ausdrücklich freigegeben: **„Ja, serverseitig implementieren“**. Die serverseitige Suche ist nun implementiert. Ohne konfigurierte Schlüsseldatei wird kein externer Aufruf ausgeführt. Ein gültiger Zugang wurde noch nicht hinterlegt oder extern getestet.

## Zur Freigabe vorbereiteter Umfang

- Spring Boot darf einen ausdrücklich konfigurierten Schlüssel aus einer Secret-Datei lesen. Es gibt keine Auslieferung oder Rücklesefunktion nach Angular.
- Verwendung ausschließlich als `x-api-key`-Header bei HTTPS-Anfragen an `https://api.curseforge.com/v1/`; keine fremden Hosts, Redirects oder Schlüssel in URLs.
- Minecraft-Modsuche mit `gameId=432`, `classId=6`, exakter tatsächlich gemessener Minecraft-Version und `modLoaderType=4` (Fabric).
- Jeden Treffer anhand konkreter verfügbarer Dateien desselben Projekts erneut auf exakte Version und Fabric prüfen. Keine erfundenen oder benachbarten Treffer.
- Begrenzte Antwortgrößen, Gesamtdeadline, redigierte Fehler für 401/403/429/Timeout; Schlüssel weder speichern noch protokollieren.
- Zunächst ausschließlich Suche und Dateimetadaten. Eine spätere Installation benötigt einen eigenen Plan mit Abhängigkeiten, vollständiger Sicherung, isoliertem Laufzeittest und Rückweg. Downloadfreigaben werden nicht umgangen.

Auch bei erlaubter Implementierung ist eine echte externe Abnahme erst mit einem vom Betreiber konfigurierten gültigen API-Zugang möglich. Ein Schlüssel soll nicht im Chat bereitgestellt werden.

Technischer Vertrag: [offizielle CurseForge-API](https://docs.curseforge.com/rest-api/). Die Nutzung dieses Dienstes und seiner API-Bedingungen wird durch diesen Entwurf nicht automatisch vereinbart.

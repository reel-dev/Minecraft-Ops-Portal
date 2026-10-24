# Quellen und Prüfstand

Stand: 24.10.2026. Konzepte, Rollenzuschnitt, API und Implementierungsreihenfolge sind Entwürfe dieses Pakets. Technische Integration muss vor Implementierung an den gewählten Versionen geprüft werden.

- Nutzerauftrag in diesem Chat: maßgebliche Zielanforderungen, zusammengefasst in docs/01-kontext.md.
- Homelab, Gesprächs-ID `6a6f8233-f3dc-83eb-8d9c-86976410f0b6`: fünf abrufbare Turns, kein weiterer Cursor; vollständige verfügbare Antwort unter references/homelab-export.json. Bilder werden separat behandelt.
- [CurseForge REST API](https://docs.curseforge.com/rest-api/): Suche nach Spielversion und Loader, konkrete Dateien und Downloadschnittstellen. Fabric ist im dokumentierten Enum Wert 4. Daraus folgt keine Zusicherung der Existenz von Dateien für 26.3.
- [itzg-Variablen](https://docker-minecraft-server.readthedocs.io/en/latest/variables/): Namen CF_API_KEY/CF_API_KEY_FILE. Die im Altchat genannte CURSEFORGE_FILES-Orchestrierung ist in diesem Paket kein getesteter Implementierungsweg.
- [Restic-Retention](https://restic.readthedocs.io/en/stable/060_forget.html): Snapshot-/Datenbereinigung und Retentionsemantik; der vorgeschlagene betriebliche 7-Tage-Ablauf muss zusätzlich getestet werden.
- [Offizielle OpenAI-Dokumentation zu Projekten](https://learn.chatgpt.com/docs/projects): Projekt als Arbeitskontext für zusammengehörige Chats, Dateien und Quellen. In dieser Sitzung ist keine erfolgreiche Cloud-Projektanlage bestätigt.

## Abgrenzung

Keine Verbindung zum echten Minecraft-Server hergestellt. Keine API-Credentials getestet. Keine Mods installiert. Keine Backups, Retentionjobs oder Container ausgeführt. Keine ungeprüften Mockup-Zahlen als Telemetrie übernommen. Das Paket schafft eine reviewbare Planungsgrundlage, keine lauffähige Betriebsplattform.

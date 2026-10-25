# docker-01: zuerst ausschließlich lesen

Ziel laut Nutzer: Homelab → Proxmox-Instanz `docker-01` → Docker → `minecraft-server`. VM oder LXC ist noch offen. Diese Vorbereitung wurde nicht auf docker-01 ausgeführt und installiert dort keinen Agenten. Die bestehende Compose-Datei bleibt unverändert.

## Ein erster Schritt in Proxmox

1. In Proxmox `docker-01` auswählen und dessen **Konsole** öffnen. Gemeint ist die Konsole von docker-01, nicht die Minecraft-Konsole und nicht die Shell des Proxmox-Hauptservers.
2. Die Datei [inspect-read-only.sh](inspect-read-only.sh) nach docker-01 kopieren und im Verzeichnis dieser Datei ausführen:

   ```sh
   sh inspect-read-only.sh
   ```

Das Skript benötigt nur `sh`, Docker CLI und `timeout`, typischerweise auf einem Linux-Docker-Host vorhanden. Bei fehlenden Werkzeugen bricht es ab; es installiert nichts. Es führt genau eine begrenzte `docker container inspect`-Abfrage aus. Kein Start/Stop/Restart, kein `exec`, kein Image-Pull, keine Logs, keine Umgebungsvariablen und keine Weltdateien. Optional kann ein anderer einzelner Containername übergeben werden; das Skript akzeptiert keine Dockeroptionen oder Shellbefehle als Namen.

Die Ausgabe nennt Zustand, Image-Konfigurations-ID, Mountarten, Ressourcenlimit und Portnummern. Host-IP, Mountquellen und Volumenamen werden ausgelassen. Die Image-Konfigurations-ID ist **kein** Repository-Manifest-Digest. Auch deklarierte Compose-Versionen sind keine gemessenen Laufzeitversionen. Das Ergebnis vor Weitergabe ansehen; Containername und Portnummern sind interne Betriebsmetadaten.

## Danach

Die Ausgabe mit der [gelieferten Compose](../../references/server-compose.supplied.yml) und der [Bestandsvorlage](../../docs/12-bestandsaufnahme.md) abgleichen. Versionsnachweise, Weltzuordnung und letzte geprüfte Sicherung bleiben gesonderte Punkte. Erst anschließend wird eine konkrete lesende Agentanbindung einschließlich Dienstkonto, Netzgrenzen und Tokenablage vorbereitet.

Der bisherige Modus `docker-read-only` erwartet weiterhin Testlabels. Für den bestehenden Container ist jetzt ein eigener [Host-Lesemodus mit Imagebindung](agent-read-only.md) implementiert und lokal geprüft. Er wurde noch nicht auf docker-01 installiert oder gestartet. Die produktive Compose bekommt keine Testlabels. Die Teststeuerung bleibt auf den separaten lokalen Testcontainer fest begrenzt. Ein produktiver Docker-Socket wird nicht ins Portal eingebunden.

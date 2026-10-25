# Lesenden Agentanschluss vorbereiten

Stand: 25.10.2026. Implementiert und lokal getestet, **nicht auf docker-01 installiert oder gestartet**. Keine Minecraft-Compose-, Label-, Mount- oder Firewalländerung nötig.

**Aktueller Bereitstellungsweg:** Auf docker-01 ist laut übermittelter Ausgabe kein Java installiert. Eine [eigene Container-Konfiguration](container/README.md) liefert deshalb Java und Docker CLI mit und begrenzt den Dockerzugriff über einen separaten Unix-Socket-Proxy. Sie ist vorbereitet, aber noch nicht durch einen Container-Laufzeittest abgenommen. Die folgenden JAR-Schritte bleiben als Alternative für Hosts mit Java dokumentiert; eine Java-Installation auf docker-01 wurde nicht durchgeführt.

## Bekannter Anschluss

- Proxmox VM 100, docker-01, Dockercontainer minecraft-server.
- Minecraft 26.3 / Fabric Loader 0.19.5 laut übermittelten Startlogs.
- Image-Konfigurations-ID laut Inspect: `sha256:5a2ab74ae179c11e2dbbce67f307cdcf2c1257b3abb0f654d3a310b3d8ea57da`.
- Schreibbarer Bind-Mount auf /data, Quelle liegt auf dem VM-Root-Dateisystem /dev/sda2.
- Proxmox-Backuplauf meldet TASK OK und 11,43 GB Archiv. Ein isolierter Restore und konsistentes Minecraft-Weltbackup bleiben offen.

## Voraussetzungen vor einem Start

1. Java auf dem **Docker-Host** prüfen: `java -version`. Java im Minecraft-Container sagt nichts über Java auf docker-01 aus. Das Agent-JAR benötigt mindestens Java 21; Java 21 und 25 sind die bisher geprüften Build-/Laufzeitbasen. Keine Java-Installation oder Änderung am Minecraft-Container im Rahmen dieser Prüfung.
2. Separates Dienstkonto, Arbeitsverzeichnis für die Agentdatenbank und externe zufällige Token-Datei mit eingeschränkten Dateirechten festlegen. Dockerzugriff verleiht weitreichende Hostrechte; API-Lesemodus ist keine Betriebssystem-Sandbox.
3. Image-ID vor Start erneut prüfen. Übermittelter Portnachweis: `0.0.0.0:25565` und `[::]:25565`. Damit ist die IPv4-Wildcardbindung für die lokale Agentprobe vorhanden. Das Profil erwartet Port 25565; die neue Konfiguration verändert die bestehende Veröffentlichung nicht.
4. Das gebaute JAR kontrolliert auf den Host übertragen. Keine Tokens oder Portalpasswörter im Repository oder Chat ablegen. Backendzugriff später auf demselben Host oder über gesondert geprüften sicheren Transport; Port 8091 nicht öffentlich freigeben.

## Geplanter Start, noch nicht ausgeführt

Folgende Angaben sind eine Betriebsvorlage; Token-Datei, Konto und Verzeichnisse müssen zuvor tatsächlich eingerichtet sein:

```sh
export AGENT_TOKEN_FILE='/etc/elbracht-smp/agent-token'
export AGENT_EXPECTED_IMAGE_ID='sha256:5a2ab74ae179c11e2dbbce67f307cdcf2c1257b3abb0f654d3a310b3d8ea57da'
java -jar smp-host-agent-0.1.0-SNAPSHOT.jar --spring.profiles.active=docker-01
```

Der Start erzeugt eigene Agentdaten, verändert aber nicht die Minecraft-Welt. Der Dienst bleibt unter 127.0.0.1:8091. Er prüft den festen Container und die Image-ID und liest Minecraft-Status über 127.0.0.1:25565. Das Portal verwendet ausschließlich `http-read-only`, niemals den Teststeuerungsadapter für diesen Anschluss. Noch keine produktive Netzwerk-/Tokenabnahme.

## Lokale Prüfung

Agent-Paketbuild erfolgreich: 32 Tests erfasst, 29 erfolgreich, drei Docker-Live-Proben bewusst übersprungen. Sechs neue Tests prüfen Imagebindung, Portbinding, Metadatenauswahl, fehlende Image-ID, authentisierten Status und verweigerte START/STOP/RESTART-Requests im tatsächlich geladenen docker-01-Profil. Keine Produktionsabfragen durch diese Tests.

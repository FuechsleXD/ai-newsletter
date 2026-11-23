# ADR-001: CIB seven als Process Engine

- **Status:** Angenommen
- **Datum:** 2026-11-22

## Kontext
Der Ablauf eines Laufs soll als BPMN-Prozess modelliert werden. Ein Ziel des Projekts ist, BPM mit KI-Aufrufen zu verbinden und dabei zu lernen. Die Camunda 7 Community Edition hat mit Version 7.24 (Oktober 2025) ihr Lebensende erreicht und bekommt keine Sicherheitsupdates mehr.

## Entscheidung
CIB seven (Fork von Camunda 7 Community), eingebettet in die Spring-Boot-Anwendung; Version passend zu Spring Boot 4 (siehe ADR-012).

## Alternativen
- Camunda 7 CE: End of Life, nicht für neue Projekte
- Camunda 8: deutlich schwerere Architektur mit eigenem Cluster
- Keine Engine (einfache Pipeline im Code): technisch ausreichend, verfehlt aber das Lernziel

## Konsequenzen
- Camunda-7-Wissen und -Werkzeuge (Modeler, Konzepte) bleiben nutzbar
- Maven-Koordinaten und Starter-Versionen müssen in der CIB-seven-Doku geprüft werden
- Engine-Tabellen liegen in derselben PostgreSQL-Datenbank (Verwaltung offen, siehe `offene-punkte.md`)

# Dokumentation AI News Digest

Diese Dateien sind die fachliche und technische Grundlage für die Umsetzung. Sie werden mit dem Code gepflegt: Ändert sich eine Entscheidung, wird die Doku im selben Schritt angepasst.

## Aufbau

| Datei | Inhalt | Wann lesen |
|---|---|---|
| `01-fachkonzept.md` | Ziel, Scope, Ablauf eines Laufs, Nicht-Ziele | Immer zuerst bei neuen Features |
| `02-architektur.md` | Packages, Schichten, KI-Anbindung, Konventionen | Bei jeder Backend-Arbeit |
| `03-datenmodell.md` | Tabellen (3NF), Regeln, Flyway | Bei Migrationen und Entities |
| `04-prozess-bpmn.md` | BPMN-Prozess `digestRun`, parallele Recherche, Variablen, Fehlerpfade | Bei Prozess- und Delegate-Arbeit |
| `05-api.md` | REST-Ressourcen, Records, Fehlerbehandlung | Bei Controllern und DTOs |
| `06-security.md` | Anmeldung, Rollen, Header | Bei Security und Engine-Zugriff |
| `07-frontend.md` | React-Technik, Seiten, Engine-UI-Einbindung | Bei Frontend-Arbeit |
| `08-ui-design.md` | Look & Feel der Oberfläche | Bei jeder sichtbaren UI-Änderung |
| `09-pdf-design.md` | Aufbau und Stil von PDF und HTML-Mail, Aggregat-JSON | Bei Aggregator, Templates, PDF, Mail |
| `10-tests.md` | Teststrategie und Coverage | Bei jeder Änderung |
| `11-deployment-cicd.md` | Docker, GitHub Actions, AWS | Bei Pipeline und Betrieb |
| `12-defaults.md` | Alle Standardwerte an einer Stelle | Bei Konfiguration |
| `13-roadmap.md` | Umsetzungsschritte mit Status | Vor jedem neuen Schritt |
| `offene-punkte.md` | Noch nicht getroffene Entscheidungen | Bevor ein betroffener Bereich gebaut wird |
| `entscheidungen/` | Architecture Decision Records (ADR) | Wenn eine Entscheidung hinterfragt wird |
| `design/` | Referenzbilder (PDF-Vorlage, UI-Entwürfe) | Bei PDF- und UI-Design |

## Pflege
- Neue Entscheidung: ADR aus `entscheidungen/adr-vorlage.md` kopieren, fortlaufend nummerieren
- Offener Punkt entschieden: aus `offene-punkte.md` entfernen, ADR anlegen, betroffene Doku anpassen
- Roadmap-Status nach jedem abgeschlossenen Schritt aktualisieren

# ADR-008: Gotenberg als PDF-Renderer

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
Das PDF ist eine farbige Ein-Seiten-Infografik mit Karten, Spalten und Icons. Das Template soll aus HTML/CSS entstehen.

## Entscheidung
Gotenberg (Docker-Container mit Chromium) rendert das HTML zu PDF. Die App ruft Gotenberg intern per HTTP auf und schickt alle Ressourcen mit.

## Alternativen
- openhtmltopdf/OpenPDF: kein Zusatzcontainer, aber kein Grid/Flexbox; Layout nur mit Tabellen/Floats
- Chromium direkt in der App (z. B. Playwright): volles CSS, aber großes App-Image und hoher Speicherbedarf

## Konsequenzen
- Modernes CSS, Template im Browser vorab ansehbar
- Ein zusätzlicher Container lokal, in der CI (Testcontainers) und in Produktion
- Gotenberg muss intern bleiben und darf keine externen Ressourcen laden

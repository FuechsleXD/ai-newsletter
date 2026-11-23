# ADR-006: Engine-Schema per Flyway im eigenen Schema `bpm`

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
CIB seven braucht eigene Tabellen in PostgreSQL. Die eigenen Tabellen der App verwaltet Flyway. Offen war, wer die Engine-Tabellen anlegt und bei Versionswechseln aktualisiert.

## Entscheidung
- Eigene Tabellen im Schema `app`, Engine-Tabellen im Schema `bpm`
- Beide werden von Flyway verwaltet; für `bpm` werden ausschließlich die offiziellen SQL-Skripte von CIB seven unverändert übernommen
- Das automatische Schema-Update der Engine ist abgeschaltet

## Alternativen
- Engine legt ihre Tabellen beim Start selbst an (getrenntes oder gemeinsames Schema): einfacher, aber Upgrades sind weniger nachvollziehbar und nicht im selben Ablauf versioniert und getestet

## Konsequenzen
- Jeder Datenbankstand ist reproduzierbar und in der CI getestet
- Bei jedem Versionswechsel von CIB seven müssen die passenden Upgrade-Skripte als Migration ergänzt werden
- Engine-Konfiguration braucht Schema-Name und Tabellen-Präfix für `bpm`

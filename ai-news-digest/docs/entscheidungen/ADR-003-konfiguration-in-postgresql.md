# ADR-003: Konfiguration normalisiert in PostgreSQL mit Flyway

- **Status:** Angenommen
- **Datum:** 2026-11-22

## Kontext
Topics, Zeitpläne, Anbieter, Modelle und Empfänger sollen zur Laufzeit über API und UI änderbar sein.

## Entscheidung
Konfiguration und Historie liegen in PostgreSQL in 3. Normalform. Das Schema wird ausschließlich mit Flyway verwaltet, Hibernate validiert nur. Einzige bewusste Denormalisierung ist `digest_run.aggregate_json` (jsonb).

## Alternativen
- Konfiguration in `application.yml`: Änderungen nur mit Neustart, nicht über das UI pflegbar
- Hibernate `ddl-auto=update`: keine nachvollziehbare Schema-Historie, riskant im Betrieb

## Konsequenzen
- Tests laufen gegen echtes PostgreSQL (Testcontainers)
- Migrationen müssen abwärtskompatibel sein, damit Rollbacks möglich bleiben

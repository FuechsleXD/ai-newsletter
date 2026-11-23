# ADR-002: Modellwahl in der Datenbank, Ablauf im BPMN

- **Status:** Angenommen
- **Datum:** 2026-11-22

## Kontext
Pro Topic sollen die Recherche-Modelle und das Aggregator-Modell frei wählbar sein, auch über das React-UI. Der Ablauf selbst ist ein BPMN-Prozess.

## Entscheidung
Das BPMN-Modell beschreibt nur den Ablauf (parallele Recherche, Prüfung, Aggregation, Rendering, Mail). Welche Modelle laufen, steht in `topic_model` und `topic.aggregator_model_id`. Beim Start werden die Modell-IDs als Prozessvariablen übergeben; eine parallele Multi-Instance erzeugt pro Modell einen Zweig (siehe ADR-004).

## Alternativen
- Ein Service Task bzw. ein fester Gateway-Zweig pro Modell: jede Änderung erfordert eine neue Prozessversion, das UI könnte die Auswahl nicht ändern
- Ein BPMN pro Topic: viele fast gleiche Modelle, schwer wartbar

## Konsequenzen
- Modellwahl im UI wirkt beim nächsten Lauf ohne Deployment
- Das BPMN bleibt ein einziges, generisches Modell

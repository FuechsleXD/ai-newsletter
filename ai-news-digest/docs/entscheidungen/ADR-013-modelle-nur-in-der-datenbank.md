# ADR-013: Modelle, Preise und Aggregator nur in der Datenbank

- **Status:** Angenommen
- **Datum:** 2026-11-23

## Kontext
Offener Punkt 1 war, welche Claude-Modelle für Recherche und Aggregation verwendet werden und welche Preise gelten. Modell-IDs und Preise ändern sich laufend (neue Modelle, Preissenkungen). Bisher war ein Default-Aggregator-Modell in der Konfiguration vorgesehen.

## Entscheidung
- Anbieter, Modelle und Preise (Input, Output, pro Websuche) werden ausschließlich in der Datenbank gepflegt (`ai_provider`, `ai_model`), später über API und UI; es gibt keine Seed-Daten für Modelle und keine Modell-IDs oder Preise im Code, in `application.yml` oder in Migrationen
- Kein Default-Aggregator-Modell: Jedes Topic muss sein Aggregator-Modell selbst nennen (`topic.aggregator_model_id NOT NULL`, Migration `V4`); die Einstellung `newsdigest.ai.aggregator-model-id` entfällt
- `ai_model.temperature` bleibt einstellbar und wird nur gesendet, wenn sie gesetzt ist (NULL = Anbieter-Standard). Hinweis: Aktuelle Claude-Modelle lehnen `temperature` ab; ein gesetzter Wert führt dort zu einem fehlgeschlagenen Aufruf (`FAILED`)
- `ai_model.max_output_tokens` NULL bedeutet den Default `newsdigest.ai.default-max-output-tokens` (16000), weil die Anthropic-API einen Wert verlangt
- Ein Recherche-Modell braucht Websuche und einen Preis pro Websuche; fehlt eines davon, wird es ohne Aufruf mit `FAILED` abgelehnt (keine Kosten ohne Erfassung)
- Lehnt ein Modell eine Anfrage ab (`refusal`), ist das Ergebnis `FAILED`; ein automatischer Rückfall auf ein anderes Modell (`fallbacks` der Anthropic-API) ist vorerst nicht eingebaut

## Alternativen
- Modelle per Migration vorbelegen: Preise wären im Repo eingefroren und veralten
- Default-Aggregator in der Konfiguration: eine zweite Stelle neben der Datenbank, die beim Modellwechsel vergessen werden kann

## Konsequenzen
- Vor dem ersten echten Lauf müssen Modelle und Preise angelegt werden; bis API und UI existieren (Schritte 19 und 23) per SQL, Vorlage `src/main/resources/db/dev-data/models.example.sql`
- Offener Punkt 1 ist erledigt

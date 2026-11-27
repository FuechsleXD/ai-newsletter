# ADR-018: Preisstufen nach Prompt-Länge

- **Status:** Angenommen
- **Datum:** 2026-11-27

## Kontext
Claude Haiku 5.5 kostet bei Prompts über 100.000 Token das Fünffache (0,50/2,50 statt 0,10/0,50 USD pro MTok). `ai_model` kannte nur einen Input- und einen Output-Preis, deshalb wurde für Haiku die höhere Stufe eingetragen: kurze Aufrufe (z. B. Aggregation) wurden zu teuer gebucht. Recherche-Aufrufe mit Websuche liegen meist über der Schwelle (Eval-Lauf 11: 345.000 Input-Token).

## Entscheidung
- Drei optionale Spalten an `ai_model` (V7): `long_context_threshold_tokens`, `long_context_input_price_per_mtok`, `long_context_output_price_per_mtok`; alle drei oder keine (Constraint)
- `CostCalculator` berechnet jeden Aufruf einzeln: liegen seine Input-Token über der Schwelle, gelten die höheren Preise für alle Token dieses Aufrufs; `ModelCalls` summiert die Aufrufe einer Aufgabe
- Bei Aufrufen mit Websuche meldet der Anbieter die Input-Token summiert über seine internen Schritte. Ob er die Stufe auf diese Summe oder auf jeden Schritt anwendet, sagt die Preisseite nicht; der Vergleich mit der Summe ist eher zu hoch als zu niedrig (Goldene Regel 5)

## Alternativen
- Weiter die höhere Stufe eintragen: einfach, aber kurze Aufrufe dauerhaft zu teuer gebucht
- Eigene Tabelle mit beliebig vielen Stufen: flexibler, aber kein Anbieter hat bisher mehr als zwei Stufen

## Konsequenzen
- Preise lassen sich so eintragen, wie sie auf der Preisseite stehen
- Erledigt den offenen Punkt 6; angepasst: `03-datenmodell.md`, `12-defaults.md`, `offene-punkte.md`

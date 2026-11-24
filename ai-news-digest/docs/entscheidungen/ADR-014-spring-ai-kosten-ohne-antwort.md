# ADR-014: Spring AI bleibt, Kosten von Antworten ohne Inhalt werden geschätzt

- **Status:** Angenommen
- **Datum:** 2026-11-24
- **Entscheidet:** offenen Punkt 6

## Kontext
Spring AI 2.0.3 verwirft eine Anthropic-Antwort ohne Inhalt (z. B. eine Ablehnung mit Stop-Grund `refusal`) samt Stop-Grund und Token-Zahlen. Die abgerechneten Input-Token eines solchen Aufrufs würden damit in den Kosten fehlen (Regel 5). Spring AI 2.1.0 enthält dieselbe Stelle und setzt Spring Boot 4.2 voraus, das wegen CIB seven noch nicht in Frage kommt.

Außerdem setzt Spring AI 2.0.3 einen von der API pausierten Such-Turn (`pause_turn`) nicht fort; die Antwort ist dann unvollständig.

## Entscheidung
- Spring AI bleibt (Tech-Stack, einheitliche `ChatModel`-Schnittstelle für weitere Anbieter, eingebaute Beobachtbarkeit)
- Antwort ohne Inhalt: `AnthropicNewsProvider` schätzt die Input-Token aus dem Prompt (1 Token je 3 Zeichen, aufgerundet, eher zu hoch als zu niedrig); Output-Token und Websuchen zählen als 0. Die Antwort ist als geschätzt markiert, das Ergebnis `FAILED` mit dem Fehlertext "Modell hat keine Antwort geliefert (möglicherweise abgelehnt); Kosten geschätzt, der Anbieter hat keine Token gemeldet"
- `pause_turn`: Das Ergebnis ist `FAILED` mit Hinweis auf die unvollständige Websuche; Token und Websuchen werden aus der Antwort gebucht. Kein Reparaturversuch, weil er ohne Websuche nur die unvollständige Antwort umformatieren würde. Mit dem Default von höchstens 5 Websuchen pro Aufruf sollte das selten vorkommen

## Alternativen
- Offizielles Anthropic-Java-SDK direkt: vollständige Token-Zahlen und Fortsetzen von `pause_turn` möglich, aber ohne Spring AI und mit eigenem Code für jeden weiteren Anbieter; kurz umgesetzt und wieder verworfen, weil Spring AI bewusst Teil des Projekts ist
- Lücke ohne Schätzung hinnehmen: abgelehnte Aufrufe würden mit 0 gebucht und das Budget unterschätzt

## Konsequenzen
- Kosten abgelehnter Aufrufe sind Schätzwerte und im Fehlertext des Ergebnisses als solche erkennbar
- Bei jedem Spring-AI-Update prüfen, ob Antworten ohne Inhalt und `pause_turn` behoben sind; dann entfällt die Schätzung bzw. der Abbruch

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

## Nachtrag (Roadmap-Schritt 13a): keine Wiederholungen im SDK
Spring AI 2.0.3 konfiguriert das Anthropic-SDK mit 2 automatischen Wiederholungen (`AnthropicSetup.DEFAULT_MAX_RETRIES`), z. B. nach einem Timeout oder bei Überlastung. Im Eval-Lauf 10 brach Claude Sonnet 5.5 mit 15 Websuchen nach 3 × 120 s ab ("Request failed", 0 Token gebucht); in Lauf 9 gelang erst der zweite Versuch nach 203 s, gebucht wurde nur dieser. Der Anbieter rechnet abgebrochene Versuche trotzdem ab, ohne dass die App die Token erfährt, und drei Versuche überschreiten die Annahme der Startprüfung gegen die Sperrzeit.

Entscheidung:
- `AnthropicNewsProvider` setzt `maxRetries(0)`; Wiederholungen macht nur die Engine, sichtbar und mit Kostenerfassung
- Timeout pro Aufruf 300 s statt 120 s, Sperrzeit eines Jobs 20 statt 10 min (Startprüfung: 5 min Wartezeit + 2 × 300 s = 15 min)
- Ein Aufruf, der dennoch in den Timeout läuft, ist `FAILED` mit 0 gebuchten Kosten, obwohl der Anbieter ihn vermutlich abrechnet: offener Punkt 7

## Nachtrag 2 (Roadmap-Schritt 15): Schätzung abgebrochener Aufrufe
Bricht ein Aufruf nach dem Senden ab (Timeout, Verbindung verloren, im SDK `AnthropicIoException`), bucht `ModelCalls` eine Schätzung statt 0: Input-Token aus dem Prompt (1 Token je 3 Zeichen) und die höchstmögliche Zahl an Websuchen des Aufrufs. Ein beantworteter HTTP-Fehler (`AnthropicServiceException`) wird vom Anbieter nicht abgerechnet und bleibt bei 0. Der Fehlertext endet mit "Kosten geschätzt (Abbruch)", `provider_result.cost_estimated` bzw. `aggregation_result.cost_estimated` ist gesetzt, und die Kostenauswertung weist geschätzte Beträge getrennt aus. Die Suchinhalte, die der Anbieter in den Kontext legt, bleiben unbekannt; die Schätzung ist eine Untergrenze (offener Punkt 7).

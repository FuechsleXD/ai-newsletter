# Prompts

Versionierte Prompt-Templates. Bestehende Versionen werden nie geändert; Änderungen immer als neue Datei `v<n+1>.txt`. Die aktive Version je Prompt steht in der Konfiguration und wird in `digest_run.prompt_version` gespeichert.

| Prompt | Zweck |
|---|---|
| `research/` | Recherche durch jedes Recherche-Modell |
| `aggregate/` | Verdichtung durch das Aggregator-Modell |
| `repair-json/` | Ein Reparaturversuch, wenn eine Antwort kein gültiges JSON war |

## Platzhalter
Schreibweise `{{name}}`. Die Ersetzung übernimmt eine eigene kleine Klasse `PromptRenderer` (nicht die Template-Engine von Spring AI), weil die JSON-Beispiele in den Prompts geschweifte Klammern enthalten. Regeln:
- Fehlt ein Wert für einen Platzhalter, ist das ein Fehler (keine leere Ersetzung)
- Wird ein Wert übergeben, der im Template nicht vorkommt, ist das ebenfalls ein Fehler
- Werte werden nicht weiter interpretiert (keine verschachtelten Platzhalter)

| Platzhalter | Prompt | Quelle |
|---|---|---|
| `topicName` | research, aggregate | `topic.name` |
| `topicDescription` | research, aggregate | `topic.description` (leer -> "keiner") |
| `periodFrom`, `periodTo` | research, aggregate | Zeitraum des Laufs, Format `YYYY-MM-DD` |
| `timezone` | research | Zeitzone des Zeitplans |
| `maxItems` | research | Default "Meldungen pro Modell" |
| `language` | research, aggregate | Default "Sprache" |
| `previousHeadlines` | aggregate | Überschriften des letzten erfolgreichen Laufs, eine pro Zeile, sonst "keine" (bis Schritt 14 immer "keine") |
| `allowedIconKeys` | aggregate | Icon-Schlüssel aus `src/main/resources/pdf/icon-keys.txt`, durch Komma getrennt |
| `newsItemsJson` | aggregate | Gültige Meldungen als JSON (`id`, `headline`, `summary`, `url`, `publishedAt`, `models`); `<` und `>` als `\u003c`/`\u003e` |
| `minSections`, `maxSections` | aggregate | Defaults Themen-Karten |
| `maxHeadingChars`, `maxTextChars` | aggregate | Längengrenzen aus den Defaults (60 und 320 Zeichen) |
| `parseError`, `expectedFormat`, `previousResponse` | repair-json | Fehlermeldung, Format des ursprünglichen Prompts, vorherige Antwort |

Die Topic-spezifische Ausrichtung entsteht über `topicName` und `topicDescription`. Eigene Prompts pro Topic sind nicht vorgesehen.

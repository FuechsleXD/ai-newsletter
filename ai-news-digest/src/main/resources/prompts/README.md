# Prompts

Versionierte Prompt-Templates. Bestehende Versionen werden nie geändert; Änderungen immer als neue Datei `v<n+1>.txt`. Die aktive Version je Prompt steht in der Konfiguration und wird in `digest_run.prompt_version` gespeichert.

| Prompt | Zweck |
|---|---|
| `research/` | Recherche durch jedes Recherche-Modell |
| `aggregate/` | Verdichtung durch das Aggregator-Modell |
| `repair-json/` | Ein Reparaturversuch, wenn eine Antwort kein gültiges JSON war |

## Versionen

| Prompt | Version | Änderung |
|---|---|---|
| `research` | `v1` | Erste Fassung |
| `research` | `v2` | Lauf 1 mit `v1` lieferte trotz 5 Websuchen eine leere Liste. Neu: "Heute ist {{periodTo}}" (der Zeitraum liegt nach dem Wissensstand des Modells), geplante Suchen mit Monat und Jahr, Meldungen mit unsicherem Datum mit `publishedAt: null` aufnehmen (den Zeitraum prüft `NewsItemChecker` ohnehin), leere Liste nur nach allen Suchen. Platzhalter und Ausgabeformat wie `v1` |
| `research` | `v3` | Lauf 2 mit `v2` lieferte 5 Meldungen, aber nur von Release-Trackern und Wikipedia; zwei Meldungen teilten sich eine URL und fielen als Duplikat weg, alle stammten aus einem Bereich, Zusammenfassungen kommentierten die Recherche. Neu: Suchen auf die Aspekte des Schwerpunkts verteilen, Übersichtsseiten nur als Hinweis auf die Originalmeldung, je Meldung ein Ereignis mit eigener Artikel-URL, keine Sätze über die Recherche |
| `aggregate` | `v1` | Erste Fassung |
| `aggregate` | `v2` | Lauf 2 mit `v1` füllte die Mindestzahl Themen mit einer Karte über die Quellenlage auf. Neu: nur Themen mit eigener Meldung, weniger Themen statt Auffüllen, nichts über die Herkunft der Daten (höchstens ein Halbsatz in `watchOut`) |
| `repair-json` | `v1` | Erste Fassung |

Aktiv sind Recherche `v3`, Aggregation `v2` und JSON-Reparatur `v1` (`docs/12-defaults.md`).

Ergebnis der Eval-Läufe (Claude Sonnet 5.5, Topic "KI-News", 17.–24.11.2026):

| Lauf | Recherche | Aggregation | Websuchen | Gültige Meldungen | Kosten USD |
|---|---|---|---|---|---|
| 1 | `v1` | – | 5 | 0 (`NO_NEWS`) | 0,14 |
| 2 | `v2` | `v1` | 5 | 3 von 5, nur Tracker und Wikipedia | 0,23 |
| 3 | `v3` | `v2` | 5 | 1 | 0,23 |
| 4 | `v3` | `v2` | 10 | 4 von 4, echte Artikel, Modelle und Regulierung | 0,41 |

Offen für eine spätere Version: Zusammenfassungen der Recherche haben oft 4-6 statt 2-3 Sätze (unkritisch, gekürzt wird vor dem Rendern); Fachmedien klarer von Blogs abgrenzen; jede Meldung genau einem Thema der Aggregation zuordnen.

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

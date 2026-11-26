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
| `research` | `v4` | Englisch (ADR-017): Inhalt wie `v3`, dazu weltweite Suche auf Englisch, Blogs nur als letzte Wahl, Zusammenfassungen ausdrücklich 2-3 Sätze |
| `aggregate` | `v3` | Englisch (ADR-017): Inhalt wie `v2`, dazu jede Meldung höchstens in einem Thema, Themen-Texte ausdrücklich 2-3 Sätze, alle Texte in der Ausgabesprache |
| `research` | `v5` | Lauf 5 mit `v4`: Sprache und Struktur gut, aber alle Quellen Newsletter oder Aggregatoren, ein Name unvollständig. Neu: Quellen-Stufen (1 Primärquelle, 2 seriöse Medien mit eigener Recherche, 3 nur Hinweis, nie Quelle), Suchen in zwei Phasen (Kandidaten finden, dann gezielt die Originalquelle suchen), nur Fakten, die so in der Quelle stehen, Prüfliste vor der Antwort, lieber wenige starke Meldungen |
| `research` | `v6` | Lauf 6 mit `v5` (Sonnet und Opus, enges Topic): nur Primärquellen und sehr relevant, aber nur 3 Meldungen aus 20 Suchen, 1,49 USD, Zusammenfassungen wieder 4-5 Sätze. Neu: Ziel 5 bis {{maxItems}} Meldungen über alle Aspekte des Schwerpunkts, etwa zwei Drittel der Suchen zum Finden, Prüfsuchen nur für Kandidaten mit Stufe-3-Quelle, Stufe 2 genügt, Changelog-Seite nur für eine Meldung, Zusammenfassung höchstens 3 Sätze und 60 Wörter |
| `research` | `v7` | Lauf 8 mit `v6` und Diagnose: nur 1 Meldung; 10 Suchen einseitig (3 × Claude, 3 × GitHub Copilot, keine zu Java, LangChain4j, Kubernetes, Cloud, UI, DevOps), eine Suche doppelt; passende Primärquellen aus dem Zeitraum (Spring AI 2.1.0 GA, Copilot-Releases im GitHub-Changelog) standen in den Treffern, wurden aber nicht übernommen. Neu: Suchplan nach Aspekten, erst jeder Aspekt einmal, höchstens zwei Suchen pro Anbieter; jeder Treffer aus Stufe 1/2 im Zeitraum ist ein Kandidat; Releases und neue Funktionen zählen als relevant, nur reine Bugfix-Releases nicht; bei weniger als 5 Meldungen noch einmal prüfen. Dazu 15 statt 10 Suchen pro Aufruf |
| `aggregate` | `v4` | Kennzahl genau wie in der Meldung, Beschriftung höchstens 5 Wörter (Lauf 5 hatte 10); konkreter Titel ohne "AI Briefing:"; Meldungen, die mehrere Modelle gefunden haben, bevorzugen |
| `repair-json` | `v1` | Erste Fassung |
| `repair-json` | `v2` | Englisch (ADR-017) |

Aktiv sind Recherche `v7`, Aggregation `v4` und JSON-Reparatur `v2` (`docs/12-defaults.md`). Neue Versionen sind englisch; der Abschnitt mit dem Antwortformat beginnt mit der Zeile `OUTPUT` (in den deutschen Versionen `AUSGABE`) und wird im Reparatur-Prompt wiederholt.

Ergebnis der Eval-Läufe (Claude Sonnet 5.5, Topic "KI-News", 17.–24.11.2026):

| Lauf | Recherche | Aggregation | Websuchen | Gültige Meldungen | Kosten USD |
|---|---|---|---|---|---|
| 1 | `v1` | – | 5 | 0 (`NO_NEWS`) | 0,14 |
| 2 | `v2` | `v1` | 5 | 3 von 5, nur Tracker und Wikipedia | 0,23 |
| 3 | `v3` | `v2` | 5 | 1 | 0,23 |
| 4 | `v3` | `v2` | 10 | 4 von 4, echte Artikel, Modelle und Regulierung | 0,41 |
| 5 | `v4` | `v3` | 10 | 4 von 4, alles Englisch, 2-3 Sätze, je Meldung ein Thema; Quellen nur Newsletter und Aggregatoren; Zeitraum nur 2 Tage (25.-26.11.) | 0,35 |
| 6 | `v5` | `v4` | 2 × 10 (Sonnet und Opus) | 3 (eine von beiden Modellen gefunden); neues Topic "AI for Java & Cloud Development"; nur Primärquellen (spring.io, Microsoft Dev Blogs, Claude-Code-Changelog), sehr relevant | 1,49 |
| 7 | `v6` | `v4` | 10 (Sonnet) | 3; Primärquellen (spring.io 2×, AWS), Zusammenfassungen kürzer; keine Meldung zu Assistenten, UI oder DevOps | 0,59 |
| 8 | `v6` | `v4` | 10 (Sonnet), erstmals mit Diagnose | 1 (Codex-Changelog); Suchen nur zu Assistenten und Spring AI, gute Primärquellen in den Treffern nicht übernommen; 141k Input-Token | 0,42 |
| 9 | `v7` | `v4` | 15 (Sonnet) | 2 (Spring AI 2.1 GA, Claude Ship); Suchen decken jetzt alle Aspekte ab; 25 % der Treffer Wikipedia, wichtige Meldungen (Java4Shell, GPT-6.2 in Codex) nur mit Stufe-3-Quellen und ohne Suchbudget für die Prüfung; 399k Input-Token | 1,03 |

Nach Lauf 7 waren es zu wenige Meldungen (3 statt 5-10) aus nur einem Teil der Bereiche des Schwerpunkts. Weil Spring AI 2.0.3 nur die fertige Antwort liefert, war nicht zu sehen, welche Suchbegriffe und Treffer das Modell hatte; deshalb gibt es die Diagnose: `provider_result.search_trace` und `searches-<modell>.md` im Eval-Export zeigen Suchbegriffe und Treffer. Lauf 8 zeigte damit zwei Ursachen: die Suchen decken den Schwerpunkt nicht ab, und gute Treffer gehen zwischen Suche und Antwort verloren; `v7` setzt an beiden an. Das Suchwerkzeug filtert seine Ergebnisse zusätzlich per Code-Ausführung; diese Aufrufe haben keinen Suchbegriff und werden in der Diagnose nur gezählt. Ein zweites Recherche-Modell (Lauf 6, Opus) brachte für 0,83 USD nur eine zusätzliche Meldung.

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

Zurückgestellt (Proof of Concept, Feintuning später): Domains wie en.wikipedia.org, medium.com und dev.to über `blockedDomains` des Suchwerkzeugs ausschließen; in einer `v8` mindestens 4 Suchen für Prüfsuchen reservieren und Entwickler-Fachmedien (InfoQ, The New Stack, The Register, DevClass) als Beispiele für Stufe 2 nennen; bei weiter hohen Kosten die Recherche in mehrere kleinere Aufrufe nach Aspekten aufteilen.

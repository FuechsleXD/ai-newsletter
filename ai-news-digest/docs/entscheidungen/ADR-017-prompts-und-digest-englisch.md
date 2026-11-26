# ADR-017: Prompts und Digest auf Englisch

- **Status:** Angenommen
- **Datum:** 2026-11-26

## Kontext
Prompts, PDF und Mail waren auf Deutsch. Die Modelle sind auf Englisch am stärksten instruierbar, die meisten Primärquellen zu Technologie-Themen sind englisch, und der Digest soll auch für englischsprachige Empfänger taugen. Die festen Texte von PDF und Mail ("Kurzfazit", "Quellen" ...) und das Datumsformat waren fest auf Deutsch verdrahtet.

## Entscheidung
- Neue Prompt-Versionen auf Englisch (Regel 6, alte Versionen bleiben): Recherche `v4`, Aggregation `v3`, JSON-Reparatur `v2`; gleiche Platzhalter und gleiche JSON-Struktur wie bisher. Die Recherche sucht ausdrücklich weltweit und auf Englisch, auch wenn Topic und Schwerpunkt deutsch sind
- Nach dem ersten englischen Eval (Lauf 5) zusätzlich Recherche `v5` und Aggregation `v4` mit strengen Quellen-Stufen, Suchen in zwei Phasen und Faktentreue, danach Recherche `v6` für mehr Meldungen bei gleichen Quellen-Stufen (Begründung in der Prompt-README)
- `{{language}}` wird mit dem englischen Namen der Ausgabesprache gefüllt ("English", "German")
- Ausgabesprache des Digests weiter über `newsdigest.output.language`, Default jetzt `en`. Feste Texte von PDF, HTML-Mail und Text-Mail kommen aus `messages/digest.properties` (Englisch, Basis) bzw. `messages/digest_de.properties`; Datum im mittleren Format der Sprache (`Nov 22, 2026` bzw. `22.11.2026`), `lang`-Attribut passend. Umsetzung: `render.DigestLanguage`
- Betreiber-Mails, Fehlertexte und Doku bleiben Deutsch (Regel 13)

## Alternativen
- Deutsche Prompts mit englischer Ausgabe: mischt Sprachen in einem Prompt, ohne Gewinn
- Sprache pro Topic: mehr Flexibilität, aber eine Datenbank-Migration und UI; erst wenn ein Topic sie wirklich braucht

## Konsequenzen
- Eine weitere Ausgabesprache braucht nur eine Datei `messages/digest_<sprache>.properties`; ohne Datei werden die englischen Texte genommen, nie die Sprache des Servers
- Die Eval-Ergebnisse der neuen Versionen werden mit Lauf 4 (Recherche `v3`, Aggregation `v2`) verglichen und in `src/main/resources/prompts/README.md` festgehalten

# 09 – PDF- und Mail-Design (verbindlich)

Das Ergebnis-PDF ist immer eine ausgearbeitete, farbige Ein-Seiten-Infografik. Aus demselben Aggregat-JSON entsteht zusätzlich eine HTML-Mail (siehe Abschnitt "HTML-Mail"), damit der Inhalt auch auf dem Smartphone gut lesbar ist; das PDF hängt an.

Stilvorlage: `design/pdf-referenz.jpg`. Übernommen werden nur Aufbau und Stil, NICHT Inhalt, Logo, Markenname oder Texte der Vorlage.

## Aufbau (von oben nach unten)
1. **Kopfbanner** (dunkelblau): Titel = Topic, Untertitel mit Zeitraum und Datum, rechts ein generisches Icon/Badge (kein Fremdlogo)
2. **Obere Reihe, zwei Karten**: links "Summary"/"Kurzfazit" (hellblau, 2–3 Sätze), rechts "Key points"/"Kernpunkte" (3–4 Zeilen mit kleinen Icons); alle festen Texte kommen in der Ausgabesprache aus `messages/digest*.properties` (ADR-017)
3. **Themen-Karten** (4–6, zwei pro Reihe): nummeriert, eigene Farbe (Blau, Grün, Lila, Orange, Rot, Türkis im Wechsel), Icon, Überschrift, 2–3 Sätze, optional kleines Ablaufdiagramm oder Zahlen-Highlight, unten Quellen-Kürzel
4. **Konsens-Leiste**: welche Modelle ein Thema gemeldet haben (farbige Punkte je Modell); Meldung nur eines Modells = "unbestätigt"
5. **Fazit-Banner** (dunkelblau): "Key Takeaways" mit 4–5 Häkchen-Punkten, daneben Kasten "Worauf achten" (1–2 Sätze Ausblick)
6. **Fußzeile**: Erstellungsdatum, Zeitraum, Anzahl Modelle und Quellen, kompakte Quellenliste (Domains)

## Stilregeln
- A4 hochkant, kleine Ränder, immer genau eine Seite
- Abgerundete Karten, Pastellhintergründe mit farbigem Rahmen, serifenlose Schrift, mindestens 8 pt
- Icons als lokale SVGs in `src/main/resources/pdf/icons/`; der Aggregator wählt nur einen `iconKey` aus einer festen Liste (`src/main/resources/pdf/icon-keys.txt`, unbekannte Schlüssel werden `default`; zu jedem Schlüssel gehört ab Schritt 17 eine SVG-Datei)
- Schriften im Repo und im PDF eingebettet (Lizenz beachten), keine Webfonts zur Laufzeit
- Textlängen pro Feld begrenzt (Defaults), zu lange Texte werden vor dem Rendern gekürzt

## Aggregator-Ausgabe (JSON-Vertrag)
```json
{
  "title": "string",
  "subtitle": "string",
  "summary": "string (2-3 Sätze)",
  "keyPoints": [{ "iconKey": "string", "text": "string" }],
  "sections": [{
    "heading": "string",
    "iconKey": "string",
    "text": "string (2-3 Sätze)",
    "flowSteps": ["string"],
    "highlightNumber": { "value": "string", "label": "string" },
    "sourceIds": ["string"],
    "models": ["string (Modell-Anzeigename)"]
  }],
  "takeaways": ["string"],
  "watchOut": "string",
  "sources": [{ "id": "string", "title": "string", "url": "string" }]
}
```
- Der Aggregator liefert alle Felder außer `sources` und `models`; diese beiden ergänzt die App nach dem Parsen aus den geprüften Meldungen (Prompt: `src/main/resources/prompts/aggregate/`)
- `keyPoints` 3–4, `sections` 4–6, `takeaways` 4–5
- `flowSteps` und `highlightNumber` optional, höchstens eines davon pro Section
- Im Code als Record `DigestAggregate` mit verschachtelten Records abgebildet und nach dem Parsen validiert (Regeln in `02-architektur.md`, Abschnitt "Aggregation"); `sources` enthält die Meldungen, auf die mindestens eine Section verweist

## Quellen und Konsens
- `sources` enthält nur Meldungen mit Prüfstatus `VALID`; `id` ist die ID des `news_item`
- `models` je Section wird nicht vom Aggregator erfunden, sondern nach dem Parsen aus den zugehörigen `news_item`-Zeilen ermittelt und überschrieben
- Eine Section mit Meldungen nur eines Modells gilt als "unbestätigt"

## HTML-Mail
- Gleiche Inhalte und Reihenfolge wie das PDF, einspaltig für schmale Bildschirme, Akzentfarben wie im PDF
- Mail-taugliches HTML: Tabellen-Layout, Inline-CSS, keine externen Bilder oder Skripte; Icons als eingebettete Bilder oder weggelassen
- Thymeleaf-Template in `src/main/resources/templates/mail/`
- Alle Texte aus KI-Antworten werden escaped; Links nur für geprüfte URLs
- Zusätzlich ein Klartext-Teil für Mailprogramme ohne HTML

## Renderer: Gotenberg (siehe ADR-008)
- Das PDF-Template ist normales HTML mit modernem CSS (Grid, Flexbox, `@page` für A4), gerendert mit Thymeleaf
- Die App schickt HTML, CSS, Schriften und Icons als Multipart-Request an den Gotenberg-Container (Chromium) und bekommt das PDF zurück
- Alle Ressourcen werden im Request mitgeschickt; Chromium lädt nichts aus dem Netz (externer Zugriff in der Gotenberg-Konfiguration gesperrt, genaue Optionen in der Gotenberg-Doku prüfen)
- Papierformat A4, Ränder und `printBackground` im Request gesetzt
- Gotenberg-Version fest gepinnt
- Weil es echtes Browser-HTML ist, kann das Template lokal im Browser vorab angesehen werden (Vorschau-Datei mit Beispiel-JSON unter `target/pdf-preview/`)
- Danach prüft die App mit PDFBox, dass das Ergebnis genau eine Seite hat; sonst wird stärker gekürzt und erneut gerendert (höchstens einmal), danach Fehler

## Vorgehen
Zuerst mit festem Beispiel-JSON (kurz, normal, maximal lang) das Layout bauen und PNG-Vorschauen erzeugen, bevor Anbieter angebunden werden.

## Umsetzung (Stand Schritt 17)
- Templates: `templates/pdf/digest.html` (Infografik nach dem Aufbau oben) und `templates/mail/digest.html` (gleicher Stil, einspaltig); Beispiel-JSON in `src/test/resources/fixtures/digest/` (kurz, normal, maximal lang)
- Schrift: **Inter** v4.1 (SIL Open Font License) in vier Schnitten (400, 600, 700, 800) unter `src/main/resources/pdf/fonts/`, als base64-`@font-face` in die Seite eingebettet; im PDF eingebettet (Test prüft das)
- Icons: **Lucide** 1.80.0 (ISC-Lizenz), Umriss-Stil, eine SVG je Icon-Schlüssel unter `pdf/icons/<key>.svg` plus Layout-Icons unter `pdf/icons/ui/`; inline in die Seite eingefügt, Farbe über CSS. Fehlt zu einem Schlüssel die SVG, startet die App nicht. Zuordnung Schlüssel → Lucide-Icon und Lizenzen in `src/main/resources/pdf/README.md`
- `DigestAssets` lädt Schrift und Icons einmal beim Start; Texte aus KI-Antworten bleiben escaped (`th:text`), nur eigene Dateien werden unescaped eingefügt (`th:utext`)
- Konsens: jedes Modell bekommt eine feste Punktfarbe (Reihenfolge des ersten Auftretens), Punkte je Karte und Legende in der Fußzeile; Karten mit nur einem Modell tragen das Badge "unconfirmed"/"unbestätigt"
- Seitenprüfung (`OnePagePdfRenderer`): PDFBox zählt die Seiten; bei mehr als einer Seite wird mit 70 % der Längengrenzen neu gerendert (`newsdigest.render.shorten-attempts`, Default 1; Untergrenzen 20 Zeichen für Überschriften, 60 für Texte), danach `DigestTooLongException` (Job-Retry, dann Incident). Das maximal lange Beispiel passt erst im zweiten Versuch, kurzes und normales im ersten
- Die Mail nutzt immer die normalen Längengrenzen; nur das PDF wird bei Bedarf stärker gekürzt
- Mail ohne Icons (SVG zeigen die meisten Mailprogramme nicht): farbige Nummern-Badges, Kartenfarben mit Seitenrand, Konsens als farbige Punkte (Zeichen), Häkchen als Zeichen; Schrift Inter, falls installiert, sonst Systemschrift
- Vorschauen: `GotenbergRenderingTest` schreibt HTML, PDF und PNG (kurz, normal, normal deutsch, lang) nach `target/pdf-preview/`, `DigestRendererTest` die Mail als `normal-mail.html`

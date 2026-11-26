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

Stand Schritt 8: einfaches Layout mit allen Teilen in der richtigen Reihenfolge (`templates/pdf/digest.html`, `templates/mail/digest.html`), ohne Icons und eigene Schriften; Beispiel-JSON in `src/test/resources/fixtures/digest/`, Vorschauen (HTML, PDF, PNG) erzeugt `GotenbergRenderingTest` unter `target/pdf-preview/`. Das finale Infografik-Design, Icons, Schriften und die Seitenprüfung mit Nachkürzen folgen in Schritt 17.

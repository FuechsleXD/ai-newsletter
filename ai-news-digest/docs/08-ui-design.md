# 08 – UI-Design (Look & Feel)

> **Status: Entwurf.** Diese Datei fülle ich noch aus. Werte mit `TODO` sind offen. Werte mit "Vorschlag" gelten, bis ich sie ändere. Claude baut das UI erst, wenn die mit **Pflicht** markierten Abschnitte ausgefüllt sind, und fragt sonst nach.

Referenzbilder (Screenshots, Skizzen, Mockups) liegen in `docs/design/ui/` und werden hier verlinkt.

Technische Umsetzung: shadcn/ui + Tailwind. Jedes Token unten wird zu einer CSS-Variable in `frontend/src/theme/tokens.css` (z. B. `color.primary` -> `--color-primary`). Hell- und Dunkel-Theme sind zwei Sätze derselben Variablen.

## 1. Gesamteindruck (Pflicht)
- Stimmung in 3 Worten: TODO (z. B. "klar, technisch, freundlich")
- Vorbilder (Apps/Websites, deren Stil mir gefällt): TODO
- Was ich NICHT will: TODO
- Hell/Dunkel: Vorschlag: helles Theme als Standard, dunkles Theme optional
- Passend zum PDF-Stil (siehe `09-pdf-design.md`)? Vorschlag: ja, gleiche Akzentfarben und Icon-Stil

## 2. Farben (Pflicht)
| Token | Verwendung | Wert |
|---|---|---|
| `color.primary` | Hauptaktionen, aktive Navigation | Vorschlag: Dunkelblau wie Kopfbanner im PDF – TODO Hex |
| `color.secondary` | Nebenaktionen | TODO |
| `color.background` | Seitenhintergrund | TODO |
| `color.surface` | Karten, Dialoge | TODO |
| `color.text` / `color.textMuted` | Text | TODO |
| `color.success` / `warning` / `error` / `info` | Status | TODO |
| `color.topic.1` – `color.topic.6` | Akzentfarben für Topics/Karten | Vorschlag: Blau, Grün, Lila, Orange, Rot, Türkis wie im PDF |

Kontrast mindestens WCAG AA.

## 3. Typografie
| Token | Wert |
|---|---|
| Schriftart | TODO (Vorschlag: eine serifenlose Schrift, lokal eingebunden, keine externen Webfonts) |
| Grundgröße | Vorschlag: 14–16 px |
| Überschriften H1/H2/H3 | TODO |
| Monospace (Cron, Modell-IDs) | TODO |

## 4. Form und Abstände
- Eckenradius: Vorschlag: abgerundet wie die Karten im PDF – TODO px
- Abstandsraster: Vorschlag: 4-px- oder 8-px-Raster
- Schatten: TODO (keine / dezent / deutlich)
- Icons: TODO (Icon-Set, Stil: Linie oder gefüllt)

## 5. Layout (Pflicht)
- Navigation: TODO (Seitenleiste links / Leiste oben)
- Inhaltsbreite: TODO (volle Breite / begrenzt)
- Mobil nutzbar: TODO (ja / nur Desktop)
- Kopfbereich: TODO (Logo/Name, Benutzer, Abmelden)

## 6. Komponenten
Für jede Komponente optional ein Referenzbild in `docs/design/ui/`.
| Komponente | Wunsch |
|---|---|
| Tabellen/Listen | TODO (Zeilen oder Karten, Dichte) |
| Formulare | TODO (eine Spalte / zwei Spalten, Labels oben oder links) |
| Status-Anzeige von Läufen | Vorschlag: farbige Badges (RUNNING, COMPLETED, FAILED) |
| Drag & Drop der Modell-Reihenfolge | TODO |
| Dialoge | TODO |
| Benachrichtigungen (Erfolg/Fehler) | Vorschlag: Toasts unten rechts |
| Leerzustände | Vorschlag: Icon + kurzer Text + Aktion |

## 7. Seiten im Detail
Je Seite: Skizze/Screenshot verlinken und Besonderheiten beschreiben.
- Dashboard: TODO
- Topics (Liste und Formular): TODO
- Zeitpläne: TODO
- Anbieter und Modelle: TODO
- Empfänger: TODO
- Läufe (Liste und Detail): TODO
- Prozesse (Engine-UI): TODO

## 8. Texte und Ton
- Anrede: TODO (du / Sie / neutral)
- Tonfall: TODO
- Datumsformat: Vorschlag: `dd.MM.yyyy HH:mm`, Zeitzone `Europe/Berlin` anzeigen

## 9. Barrierefreiheit
- Tastaturbedienung, sichtbarer Fokus, Labels an allen Feldern
- Kontraste WCAG AA
- Farbe nie als einziges Unterscheidungsmerkmal (Status immer auch als Text)

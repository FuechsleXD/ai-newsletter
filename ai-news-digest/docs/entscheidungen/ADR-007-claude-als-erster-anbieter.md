# ADR-007: Claude als erster Anbieter und Aggregator, Copilot später prüfen

- **Status:** Angenommen (Aggregator-Wahl als Annahme, bis ich sie bestätige)
- **Datum:** 2026-11-23

## Kontext
Vorhandene Zugänge: Anthropic (Claude) und Microsoft Copilot. Die App braucht API-Zugriff mit Web-Suche, der aus einem zeitgesteuerten Backend-Prozess heraus funktioniert.

## Entscheidung
- Anthropic ist der erste und vorerst einzige angebundene Anbieter, für Recherche und Aggregation
- Für mehr Vielfalt in der Recherche können mehrere Claude-Modelle parallel laufen (das Datenmodell erlaubt mehrere Modelle eines Anbieters)
- Microsoft Copilot wird nicht im ersten Schritt angebunden

## Begründung zu Copilot
- Das normale Copilot-Chat-Angebot hat keine API für eigene Anwendungen
- Es gibt die Microsoft 365 Copilot Chat API mit Web-Grounding; sie ist nach Microsofts Angaben in Preview (Beta), braucht eine Microsoft-365-Copilot-Lizenz in einem Entra-Mandanten und unterstützt keine persönlichen Microsoft-Konten und keine reinen Anwendungsberechtigungen, sondern nur Aufrufe im Namen eines angemeldeten Benutzers
- Für einen Prozess, der ohne Benutzer nach Zeitplan läuft, ist das schwierig; außerdem lässt sich dort kein Modell wählen
- Wenn die Lizenz von einem Arbeitgeber stammt, ist vorher zu klären, ob die private Nutzung erlaubt ist

## Konsequenzen
- Der Konsens in der Infografik (mehrere Modelle melden dasselbe) ist mit nur einem Anbieter weniger aussagekräftig; weitere Anbieter (z. B. OpenAI, Gemini) bleiben über `NewsProvider` jederzeit ergänzbar
- Copilot steht als offener Punkt 4 in `../offene-punkte.md`

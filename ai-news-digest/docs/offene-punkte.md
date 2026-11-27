# Offene Entscheidungen

Claude baut einen betroffenen Bereich erst, wenn der Punkt entschieden ist, und fragt sonst nach. Nach der Entscheidung: Punkt hier entfernen, ADR anlegen, betroffene Doku anpassen.

| # | Thema | Optionen / Vorschlag | Betrifft | Benötigt ab Schritt |
|---|---|---|---|---|
| 4 | Zweiter Anbieter (Schritt 13b) | Microsoft Copilot nur über die Microsoft 365 Copilot Chat API (Preview, M365-Copilot-Lizenz, Entra-Konto, nur delegierte Benutzerrechte), für einen zeitgesteuerten Backend-Prozess eher ungeeignet; ein Copilot-Abo enthält keinen API-Zugang. Alternativen mit Abrechnung nach Verbrauch: OpenAI API (am einfachsten mit Spring AI), Azure OpenAI, Google Gemini API (mit Grounding) | Provider | später |
| 5 | `cibseven-keycloak` mit CIB seven 3.0 | Neueste Version ist 2.1.0 (für CIB seven 2.x). Vor Schritt 25 prüfen, ob es eine Version für CIB seven 3.0 gibt oder ob 2.1.0 funktioniert; sonst SSO der Engine-UI anders lösen (ADR-010 anpassen) | Engine-UI, Security | 25 |
| 6 | Preisstufen nach Prompt-Länge | Claude Haiku 5.5 kostet bei Prompts über 100.000 Token das Fünffache (0,50/2,50 statt 0,10/0,50 USD pro MTok); `ai_model` kennt nur einen Input- und einen Output-Preis. Vorerst wird die höhere Stufe eingetragen (Kosten eher zu hoch, nie zu niedrig); Option: Preisstufen als eigene Tabelle (neue Migration) und Berechnung pro Aufruf | Kosten, Datenmodell | 15 |
| 7 | Kosten abgebrochener Aufrufe | Läuft ein Aufruf in den Timeout, kennt die App seine Token nicht und bucht 0, obwohl der Anbieter ihn vermutlich abrechnet (ADR-014, Nachtrag). Optionen: Streaming (Token-Zahlen kommen schrittweise, auch bei Abbruch bekannt), Schätzung aus Prompt und Websuch-Limit wie bei Antworten ohne Inhalt, oder Abgleich mit der Usage-API des Anbieters | Kosten, Provider | 15 |

Außerdem offen, aber kein Entscheidungspunkt: UI-Design in `08-ui-design.md` ausfüllen (vor Roadmap-Schritt 21).
